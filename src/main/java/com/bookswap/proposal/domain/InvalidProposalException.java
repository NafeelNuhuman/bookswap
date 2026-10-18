package com.bookswap.proposal.domain;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidProposalException extends ApiException {
    public InvalidProposalException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_PROPOSAL", message);
    }
}
