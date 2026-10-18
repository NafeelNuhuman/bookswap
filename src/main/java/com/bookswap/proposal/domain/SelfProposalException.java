package com.bookswap.proposal.domain;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class SelfProposalException extends ApiException {
    public SelfProposalException() {
        super(HttpStatus.BAD_REQUEST, "SELF_PROPOSAL", "You cannot propose an exchange with yourself");
    }
}
