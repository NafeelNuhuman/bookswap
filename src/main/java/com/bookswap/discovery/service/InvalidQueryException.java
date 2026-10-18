package com.bookswap.discovery.service;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidQueryException extends ApiException {
    public InvalidQueryException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_QUERY", "The search text must be 1 to 100 characters and contain no NUL characters");
    }
}
