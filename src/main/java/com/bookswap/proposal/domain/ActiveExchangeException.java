package com.bookswap.proposal.domain;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class ActiveExchangeException extends ApiException {
    public ActiveExchangeException() {
        super(HttpStatus.CONFLICT, "ACCOUNT_HAS_ACTIVE_EXCHANGE",
                "The account has an accepted exchange that is not finished yet");
    }
}
