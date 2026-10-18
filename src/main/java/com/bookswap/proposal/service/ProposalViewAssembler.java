package com.bookswap.proposal.service;

import com.bookswap.proposal.domain.Proposal;
import com.bookswap.proposal.domain.ProposalStatus;
import com.bookswap.proposal.domain.Side;
import com.bookswap.proposal.repository.ProposalItemRepository;
import com.bookswap.proposal.repository.ProposalItemRow;
import com.bookswap.proposal.web.ProposalView;
import com.bookswap.user.domain.User;
import com.bookswap.user.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds proposal views for a whole page with a fixed number of queries (items, users). The stored status is shown
 * as {@code EXPIRED} once a proposal has lapsed. Contact details are revealed only in the view of an accepted
 * proposal, and only to the party who is not the viewer; a {@code null} viewer (lists) never sees any.
 */
@Component
class ProposalViewAssembler {
    private final ProposalItemRepository items;
    private final UserRepository users;

    ProposalViewAssembler(ProposalItemRepository items, UserRepository users) {
        this.items = items;
        this.users = users;
    }

    ProposalView toView(Proposal proposal, UUID viewer, Instant now) {
        return toViews(List.of(proposal), viewer, now).get(0);
    }

    List<ProposalView> toViews(List<Proposal> proposals, UUID viewer, Instant now) {
        if (proposals.isEmpty()) {
            return List.of();
        }
        Set<UUID> proposalIds = new HashSet<>();
        Set<UUID> userIds = new HashSet<>();
        for (Proposal p : proposals) {
            proposalIds.add(p.getId());
            userIds.add(p.getProposerId());
            userIds.add(p.getRecipientId());
        }
        Map<UUID, List<ProposalItemRow>> rowsByProposal = new HashMap<>();
        for (ProposalItemRow row : items.findRows(proposalIds)) {
            rowsByProposal.computeIfAbsent(row.proposalId(), k -> new ArrayList<>()).add(row);
        }
        Map<UUID, User> usersById = new HashMap<>();
        users.findAllById(userIds).forEach(u -> usersById.put(u.getId(), u));

        List<ProposalView> views = new ArrayList<>();
        for (Proposal p : proposals) {
            boolean reveal = viewer != null && p.getStatus() == ProposalStatus.ACCEPTED;
            List<ProposalItemRow> rows = rowsByProposal.getOrDefault(p.getId(), List.of());
            boolean showNote = viewer != null
                    && (p.getStatus() == ProposalStatus.ACCEPTED || p.getStatus() == ProposalStatus.COMPLETED);
            UUID other = viewer == null ? null : viewer.equals(p.getProposerId()) ? p.getRecipientId() : p.getProposerId();
            views.add(new ProposalView(p.getId(), p.effectiveStatus(now), p.getCancelCause(), p.getParentId(),
                    p.getMessage(), p.getRejectReason(), p.getCreatedAt(), p.getExpiresAt(), p.getAcceptedAt(),
                    p.getConfirmBy(), p.getCompletedAt(),
                    viewer == null ? null : p.hasConfirmed(viewer), viewer == null ? null : p.hasConfirmed(other),
                    showNote ? p.getPickupNote() : null,
                    party(usersById.get(p.getProposerId()), reveal && !p.getProposerId().equals(viewer)),
                    party(usersById.get(p.getRecipientId()), reveal && !p.getRecipientId().equals(viewer)),
                    itemViews(rows, Side.OFFERED), itemViews(rows, Side.REQUESTED)));
        }
        return views;
    }

    private static ProposalView.Party party(User user, boolean withContact) {
        return new ProposalView.Party(user.getId(), user.getDisplayName(), user.getCity(),
                withContact ? user.getEmail() : null, withContact ? user.getContactNote() : null);
    }

    private static List<ProposalView.Item> itemViews(List<ProposalItemRow> rows, Side side) {
        return rows.stream().filter(r -> r.side() == side)
                .map(r -> new ProposalView.Item(r.copyId(), r.isbn13(), r.title(), r.author(), r.coverUrl(),
                        r.detailsSource(), r.gradeAtProposal()))
                .toList();
    }
}
