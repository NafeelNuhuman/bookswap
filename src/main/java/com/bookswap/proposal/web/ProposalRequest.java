package com.bookswap.proposal.web;

import java.util.List;
import java.util.UUID;

/** The shape is validated by the service so that every problem is the same 400 {@code INVALID_PROPOSAL}. */
public record ProposalRequest(List<UUID> requestedCopyIds, List<UUID> offeredCopyIds, String message) {}
