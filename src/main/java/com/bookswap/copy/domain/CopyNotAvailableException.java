package com.bookswap.copy.domain;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class CopyNotAvailableException extends ApiException {
    public CopyNotAvailableException() {
        super(HttpStatus.CONFLICT, "COPY_NOT_AVAILABLE", "A copy in this proposal is not available");
    }
}
