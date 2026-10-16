package com.bookswap.book.service;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class BookDetailsRequiredException extends ApiException {
    public BookDetailsRequiredException() {
        super(HttpStatus.BAD_REQUEST, "BOOK_DETAILS_REQUIRED",
                "The catalog has no details for this ISBN (or is unavailable); please provide both title and author");
    }
}
