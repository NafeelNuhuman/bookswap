package com.bookswap.proposal.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One completed exchange from the caller's point of view: who they swapped with, what they gave, what they got. */
public record ExchangeView(UUID proposalId, Instant completedAt, ProposalView.Party with,
                           List<ProposalView.Item> gave, List<ProposalView.Item> received) {}
