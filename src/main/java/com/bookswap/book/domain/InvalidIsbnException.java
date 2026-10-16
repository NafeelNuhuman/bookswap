package com.bookswap.book.domain;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidIsbnException extends ApiException {
    public InvalidIsbnException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_ISBN", "ISBN must be a valid ISBN-10 or ISBN-13");
    }
}
