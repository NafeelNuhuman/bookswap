package com.bookswap.proposal.domain;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class ProposalActionNotAllowedException extends ApiException {
    public ProposalActionNotAllowedException() {
        super(HttpStatus.FORBIDDEN, "PROPOSAL_ACTION_NOT_ALLOWED", "You cannot do that with this proposal");
    }
}
