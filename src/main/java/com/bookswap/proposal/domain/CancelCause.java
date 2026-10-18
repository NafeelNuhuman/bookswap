package com.bookswap.proposal.domain;

public enum CancelCause {
    /** The proposer withdrew the proposal. */
    WITHDRAWN,
    /** A copy in the proposal was reserved elsewhere, regraded, unlisted or removed. */
    COPY_UNAVAILABLE
}
