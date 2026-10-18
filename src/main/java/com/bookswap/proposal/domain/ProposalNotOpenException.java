package com.bookswap.proposal.domain;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class ProposalNotOpenException extends ApiException {
    public ProposalNotOpenException() {
        super(HttpStatus.CONFLICT, "PROPOSAL_NOT_OPEN", "The proposal is no longer open");
    }
}
