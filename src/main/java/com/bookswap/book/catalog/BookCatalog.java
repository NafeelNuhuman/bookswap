package com.bookswap.book.catalog;

import com.bookswap.book.domain.Isbn;

import java.util.Optional;

/** Port to an external book catalog. */
public interface BookCatalog {
    /** Empty if the catalog does not know the book; throws {@link CatalogUnavailableException} if it cannot answer. */
    Optional<BookDetails> lookup(Isbn isbn);
}
