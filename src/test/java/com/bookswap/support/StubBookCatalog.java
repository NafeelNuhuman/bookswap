package com.bookswap.support;

import com.bookswap.book.catalog.BookCatalog;
import com.bookswap.book.catalog.BookDetails;
import com.bookswap.book.catalog.CatalogUnavailableException;
import com.bookswap.book.domain.Isbn;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** In-memory catalog for integration tests: no network. */
public class StubBookCatalog implements BookCatalog {
    private final Map<String, BookDetails> entries = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile boolean down;

    @Override
    public Optional<BookDetails> lookup(Isbn isbn) {
        calls.incrementAndGet();
        if (down) {
            throw new CatalogUnavailableException();
        }
        return Optional.ofNullable(entries.get(isbn.value()));
    }

    public void put(String isbn13, BookDetails details) { entries.put(isbn13, details); }
    public void down(boolean down) { this.down = down; }
    public int calls() { return calls.get(); }

    public void reset() {
        entries.clear();
        calls.set(0);
        down = false;
    }
}
