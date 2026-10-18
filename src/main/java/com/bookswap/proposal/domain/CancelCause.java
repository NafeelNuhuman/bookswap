package com.bookswap.proposal.domain;

public enum CancelCause {
    /** The proposer withdrew the proposal, or a party called off an accepted exchange. */
    WITHDRAWN,
    /** A copy in the proposal was reserved elsewhere, regraded, unlisted or removed. */
    COPY_UNAVAILABLE,
    /** Accepted, but nobody confirmed the swap before the deadline. */
    NO_SHOW
}
