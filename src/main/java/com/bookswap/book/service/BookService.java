package com.bookswap.book.service;

import com.bookswap.book.catalog.BookCatalog;
import com.bookswap.book.catalog.BookDetails;
import com.bookswap.book.catalog.CatalogUnavailableException;
import com.bookswap.book.domain.Book;
import com.bookswap.book.domain.Isbn;
import com.bookswap.book.repository.BookRepository;
import com.bookswap.book.web.LookupResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

/**
 * Deliberately not @Transactional: the catalog HTTP call must not hold a database connection, and
 * each repository call is its own short transaction.
 */
@Service
public class BookService {

    private static final Logger log = LoggerFactory.getLogger(BookService.class);

    private final BookRepository books;
    private final BookCatalog catalog;
    private final Clock clock;

    public BookService(BookRepository books, BookCatalog catalog, Clock clock) {
        this.books = books;
        this.catalog = catalog;
        this.clock = clock;
    }

    public LookupResponse lookup(Isbn isbn) {
        Optional<Book> local = books.findById(isbn.value());
        if (local.isPresent()) {
            return LookupResponse.from(local.get());
        }
        BookDetails details = catalog.lookup(isbn).orElseThrow(BookNotFoundException::new);
        return new LookupResponse(isbn.value(), details.title(), details.author(), details.coverUrl(),
                LookupResponse.Source.CATALOG);
    }

    /** Local book first, then the catalog, then the caller's manual title and author. */
    public Book resolveForCopy(Isbn isbn, String manualTitle, String manualAuthor) {
        Optional<Book> local = books.findById(isbn.value());
        if (local.isPresent()) {
            return local.get();
        }
        Optional<BookDetails> fromCatalog = Optional.empty();
        try {
            fromCatalog = catalog.lookup(isbn);
        } catch (CatalogUnavailableException e) {
            log.warn("Catalog unavailable while adding {}; falling back to manual details", isbn);
        }
        BookDetails details = fromCatalog.orElseGet(() -> manual(manualTitle, manualAuthor));
        if (details == null) {
            throw new BookDetailsRequiredException();
        }
        books.insertIfAbsent(isbn.value(), details.title(), details.author(), details.coverUrl(), clock.instant());
        // Re-read: a concurrent insert of the same ISBN may have won, and the stored row is the truth.
        return books.findById(isbn.value()).orElseThrow();
    }

    private static BookDetails manual(String title, String author) {
        if (title == null || title.isBlank() || author == null || author.isBlank()) {
            return null;
        }
        return new BookDetails(title, author, null);
    }
}
