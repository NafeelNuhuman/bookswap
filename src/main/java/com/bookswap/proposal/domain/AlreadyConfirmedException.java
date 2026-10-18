package com.bookswap.proposal.domain;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class AlreadyConfirmedException extends ApiException {
    public AlreadyConfirmedException() {
        super(HttpStatus.CONFLICT, "ALREADY_CONFIRMED", "You have already confirmed this exchange");
    }
}
