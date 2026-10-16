package com.bookswap.book.service;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class BookNotFoundException extends ApiException {
    public BookNotFoundException() {
        super(HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND", "No book was found for this ISBN; you can enter the details manually");
    }
}
