package com.bookswap.proposal.web;

/** The optional body of a rejection; the service validates the reason so that a bad one is an INVALID_PROPOSAL 400. */
public record RejectRequest(String reason) {}
