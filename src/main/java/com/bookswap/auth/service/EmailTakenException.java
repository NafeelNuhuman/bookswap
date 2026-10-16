package com.bookswap.auth.service;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class EmailTakenException extends ApiException {
    public EmailTakenException() {
        super(HttpStatus.CONFLICT, "EMAIL_TAKEN", "An account with this email already exists");
    }
}
