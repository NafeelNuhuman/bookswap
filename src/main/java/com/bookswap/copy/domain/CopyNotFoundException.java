package com.bookswap.copy.domain;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class CopyNotFoundException extends ApiException {
    public CopyNotFoundException() {
        super(HttpStatus.NOT_FOUND, "COPY_NOT_FOUND", "Copy not found");
    }
}
