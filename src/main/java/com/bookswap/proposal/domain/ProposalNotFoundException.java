package com.bookswap.proposal.domain;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class ProposalNotFoundException extends ApiException {
    public ProposalNotFoundException() {
        super(HttpStatus.NOT_FOUND, "PROPOSAL_NOT_FOUND", "Proposal not found");
    }
}
