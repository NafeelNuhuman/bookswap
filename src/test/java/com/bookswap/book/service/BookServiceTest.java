package com.bookswap.book.service;

import com.bookswap.book.catalog.BookCatalog;
import com.bookswap.book.catalog.BookDetails;
import com.bookswap.book.catalog.CatalogUnavailableException;
import com.bookswap.book.domain.Book;
import com.bookswap.book.domain.Isbn;
import com.bookswap.book.repository.BookRepository;
import com.bookswap.book.web.LookupResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Isbn ISBN = Isbn.parse("9780140328721");

    @Mock BookRepository books;
    @Mock BookCatalog catalog;
    BookService service;

    @BeforeEach
    void setUp() {
        service = new BookService(books, catalog, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Book stored(String title, String author, String cover) {
        return new Book(ISBN.value(), title, author, cover, NOW);
    }

    // --- lookup ---

    @Test
    void lookupPrefersTheLocalBook() {
        when(books.findById(ISBN.value())).thenReturn(Optional.of(stored("Local", "L", null)));
        LookupResponse r = service.lookup(ISBN);
        assertThat(r.source()).isEqualTo(LookupResponse.Source.LOCAL);
        assertThat(r.title()).isEqualTo("Local");
        verifyNoInteractions(catalog);
    }

    @Test
    void lookupFallsBackToTheCatalogWithoutSaving() {
        when(books.findById(ISBN.value())).thenReturn(Optional.empty());
        when(catalog.lookup(ISBN)).thenReturn(Optional.of(new BookDetails("Cat", "C", "https://c/m.jpg")));
        LookupResponse r = service.lookup(ISBN);
        assertThat(r.source()).isEqualTo(LookupResponse.Source.CATALOG);
        assertThat(r.coverUrl()).isEqualTo("https://c/m.jpg");
        verify(books, never()).insertIfAbsent(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void lookupOfUnknownBookIsNotFound() {
        when(books.findById(ISBN.value())).thenReturn(Optional.empty());
        when(catalog.lookup(ISBN)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.lookup(ISBN)).isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void lookupPropagatesCatalogUnavailable() {
        when(books.findById(ISBN.value())).thenReturn(Optional.empty());
        when(catalog.lookup(ISBN)).thenThrow(new CatalogUnavailableException());
        assertThatThrownBy(() -> service.lookup(ISBN)).isInstanceOf(CatalogUnavailableException.class);
    }

    // --- resolveForCopy ---

    @Test
    void existingLocalBookIsReusedAndNeitherCatalogNorManualDataIsUsed() {
        Book local = stored("Local", "L", null);
        when(books.findById(ISBN.value())).thenReturn(Optional.of(local));
        assertThat(service.resolveForCopy(ISBN, "Ignored", "Ignored")).isSameAs(local);
        verifyNoInteractions(catalog);
        verify(books, never()).insertIfAbsent(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void catalogDetailsAreStoredAndManualDataIsIgnored() {
        Book saved = stored("Cat", "C", null);
        when(books.findById(ISBN.value())).thenReturn(Optional.empty(), Optional.of(saved));
        when(catalog.lookup(ISBN)).thenReturn(Optional.of(new BookDetails("Cat", "C", null)));
        assertThat(service.resolveForCopy(ISBN, "Manual", "M")).isSameAs(saved);
        verify(books).insertIfAbsent(ISBN.value(), "Cat", "C", null, NOW);
    }

    @Test
    void manualDetailsAreUsedWhenTheCatalogHasNothing() {
        Book saved = stored("Manual", "M", null);
        when(books.findById(ISBN.value())).thenReturn(Optional.empty(), Optional.of(saved));
        when(catalog.lookup(ISBN)).thenReturn(Optional.empty());
        assertThat(service.resolveForCopy(ISBN, "Manual", "M")).isSameAs(saved);
        verify(books).insertIfAbsent(ISBN.value(), "Manual", "M", null, NOW);
    }

    @Test
    void manualDetailsAreUsedWhenTheCatalogIsDown() {
        Book saved = stored("Manual", "M", null);
        when(books.findById(ISBN.value())).thenReturn(Optional.empty(), Optional.of(saved));
        when(catalog.lookup(ISBN)).thenThrow(new CatalogUnavailableException());
        assertThat(service.resolveForCopy(ISBN, "Manual", "M")).isSameAs(saved);
    }

    @Test
    void noDetailsAnywhereIsBookDetailsRequired() {
        when(books.findById(ISBN.value())).thenReturn(Optional.empty());
        when(catalog.lookup(ISBN)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resolveForCopy(ISBN, null, null))
                .isInstanceOf(BookDetailsRequiredException.class);
        assertThatThrownBy(() -> service.resolveForCopy(ISBN, "Only title", null))
                .isInstanceOf(BookDetailsRequiredException.class);
        verify(books, never()).insertIfAbsent(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void catalogDownAndNoManualDetailsIsBookDetailsRequired() {
        when(books.findById(ISBN.value())).thenReturn(Optional.empty());
        when(catalog.lookup(ISBN)).thenThrow(new CatalogUnavailableException());
        assertThatThrownBy(() -> service.resolveForCopy(ISBN, null, null))
                .isInstanceOf(BookDetailsRequiredException.class);
    }
}
