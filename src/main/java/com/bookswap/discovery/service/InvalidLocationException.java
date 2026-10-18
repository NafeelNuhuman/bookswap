package com.bookswap.discovery.service;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidLocationException extends ApiException {
    public InvalidLocationException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_LOCATION", "The city must be at most 100 characters and contain no NUL characters");
    }
}
