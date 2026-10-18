package com.bookswap.proposal.web;

import com.bookswap.copy.domain.ConditionGrade;
import com.bookswap.copy.domain.Copy;
import com.bookswap.proposal.domain.CancelCause;
import com.bookswap.proposal.domain.ProposalStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A proposal as one of its two parties sees it. {@code email} and {@code contactNote} are only ever present on the
 * other party of an accepted proposal, in the detail view.
 */
public record ProposalView(UUID id, ProposalStatus status, CancelCause cancelCause, UUID parentId, String message,
                           String rejectReason, Instant createdAt, Instant expiresAt, Party proposer, Party recipient,
                           List<Item> offered, List<Item> requested) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Party(UUID id, String displayName, String city, String email, String contactNote) {}

    public record Item(UUID copyId, String isbn, String title, String author, String coverUrl,
                       Copy.DetailsSource detailsSource, ConditionGrade gradeAtProposal) {}
}
