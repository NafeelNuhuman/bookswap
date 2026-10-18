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

    /**
     * What a new copy should point at: the shared book (local row first, then the catalog, which is stored for
     * everyone), or, only when neither knows the ISBN, the owner's own typed details, which are NOT stored in
     * the shared book table.
     */
    public BookResolution resolveForCopy(Isbn isbn, String manualTitle, String manualAuthor) {
        Optional<Book> local = books.findById(isbn.value());
        if (local.isPresent()) {
            return BookResolution.shared(local.get());
        }
        Optional<BookDetails> fromCatalog = Optional.empty();
        try {
            fromCatalog = catalog.lookup(isbn);
        } catch (CatalogUnavailableException e) {
            log.warn("Catalog unavailable while adding {}; falling back to manual details", isbn);
        }
        if (fromCatalog.isPresent()) {
            BookDetails details = fromCatalog.get();
            books.insertIfAbsent(isbn.value(), details.title(), details.author(), details.coverUrl(), clock.instant());
            // Re-read: a concurrent insert of the same ISBN may have won, and the stored row is the truth.
            return BookResolution.shared(books.findById(isbn.value()).orElseThrow());
        }
        BookDetails manual = manual(manualTitle, manualAuthor);
        if (manual == null) {
            throw new BookDetailsRequiredException();
        }
        return BookResolution.manual(isbn.value(), manual.title(), manual.author());
    }

    /** Either a shared {@link Book}, or the owner's own title/author for an ISBN no shared book exists for. */
    public record BookResolution(Book book, String isbn13, String manualTitle, String manualAuthor) {
        static BookResolution shared(Book book) {
            return new BookResolution(book, book.getIsbn13(), null, null);
        }

        static BookResolution manual(String isbn13, String title, String author) {
            return new BookResolution(null, isbn13, title, author);
        }
    }

    private static BookDetails manual(String title, String author) {
        if (title == null || title.isBlank() || author == null || author.isBlank()) {
            return null;
        }
        return new BookDetails(title, author, null);
    }
}
