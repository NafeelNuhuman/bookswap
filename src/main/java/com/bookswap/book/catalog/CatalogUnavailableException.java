package com.bookswap.book.catalog;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class CatalogUnavailableException extends ApiException {
    public CatalogUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_UNAVAILABLE", "The book catalog is currently unavailable");
    }

    public CatalogUnavailableException(Throwable cause) {
        this();
        initCause(cause);
    }
}
