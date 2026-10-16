package com.bookswap.copy.domain;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class CopyReservedException extends ApiException {
    public CopyReservedException() {
        super(HttpStatus.CONFLICT, "COPY_RESERVED", "A reserved copy cannot be changed");
    }
}
