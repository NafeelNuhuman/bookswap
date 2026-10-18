package com.bookswap.proposal.service;

import com.bookswap.copy.domain.Copy;
import com.bookswap.copy.domain.CopyNotAvailableException;
import com.bookswap.copy.domain.CopyStatus;
import com.bookswap.copy.repository.CopyRepository;
import com.bookswap.proposal.domain.InvalidProposalException;
import com.bookswap.proposal.domain.Proposal;
import com.bookswap.proposal.domain.ProposalItem;
import com.bookswap.proposal.domain.ProposalNotFoundException;
import com.bookswap.proposal.domain.SelfProposalException;
import com.bookswap.proposal.domain.Side;
import com.bookswap.proposal.repository.ProposalItemRepository;
import com.bookswap.proposal.repository.ProposalRepository;
import com.bookswap.proposal.web.ProposalRequest;
import com.bookswap.proposal.web.ProposalView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Every method takes the authenticated caller's id. A proposal the caller is not a party to is indistinguishable
 * from a missing one.
 */
@Service
public class ProposalService {
    static final int MAX_COPIES_PER_SIDE = 5;
    static final int MAX_MESSAGE_LENGTH = 500;

    private final ProposalRepository proposals;
    private final ProposalItemRepository items;
    private final CopyRepository copies;
    private final ProposalViewAssembler assembler;
    private final ProposalProperties properties;
    private final Clock clock;

    ProposalService(ProposalRepository proposals, ProposalItemRepository items, CopyRepository copies,
                    ProposalViewAssembler assembler, ProposalProperties properties, Clock clock) {
        this.proposals = proposals;
        this.items = items;
        this.copies = copies;
        this.assembler = assembler;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ProposalView propose(UUID me, ProposalRequest request) {
        return createProposal(me, null, null, request);
    }

    @Transactional(readOnly = true)
    public ProposalView get(UUID me, UUID id) {
        return assembler.toView(load(me, id), me, clock.instant());
    }

    /**
     * Validates and saves a new proposal from {@code proposerId}. {@code fixedRecipientId} is null for a first
     * proposal (the recipient is whoever owns the requested copies) and set for a counter (it must be them).
     */
    ProposalView createProposal(UUID proposerId, UUID fixedRecipientId, UUID parentId, ProposalRequest request) {
        List<UUID> requestedIds = checkedIds(request.requestedCopyIds());
        List<UUID> offeredIds = checkedIds(request.offeredCopyIds());
        Set<UUID> all = new HashSet<>(requestedIds);
        all.addAll(offeredIds);
        if (all.size() != requestedIds.size() + offeredIds.size()) {
            throw new InvalidProposalException("A copy can only appear once in a proposal");
        }
        String message = checkedMessage(request.message());

        Map<UUID, Copy> found = new HashMap<>();
        copies.findAllByIdIn(all).forEach(c -> found.put(c.getId(), c));
        List<Copy> requested = requireAvailable(requestedIds, found);
        List<Copy> offered = requireAvailable(offeredIds, found);
        for (Copy c : offered) {
            if (!c.getOwnerId().equals(proposerId)) {
                throw new CopyNotAvailableException();
            }
        }
        UUID recipientId = requested.get(0).getOwnerId();
        for (Copy c : requested) {
            if (!c.getOwnerId().equals(recipientId)) {
                throw new InvalidProposalException("All requested copies must belong to the same user");
            }
        }
        if (recipientId.equals(proposerId)) {
            throw new SelfProposalException();
        }
        if (fixedRecipientId != null && !fixedRecipientId.equals(recipientId)) {
            throw new CopyNotAvailableException();
        }

        Instant now = clock.instant();
        Proposal proposal = proposals.save(
                Proposal.create(proposerId, recipientId, message, parentId, now, properties.expiry()));
        List<ProposalItem> newItems = new ArrayList<>();
        requested.forEach(c -> newItems.add(new ProposalItem(proposal.getId(), c.getId(), Side.REQUESTED, c.getGrade())));
        offered.forEach(c -> newItems.add(new ProposalItem(proposal.getId(), c.getId(), Side.OFFERED, c.getGrade())));
        items.saveAll(newItems);
        return assembler.toView(proposal, proposerId, now);
    }

    Proposal load(UUID me, UUID id) {
        return proposals.findById(id).filter(p -> p.involves(me)).orElseThrow(ProposalNotFoundException::new);
    }

    private static List<UUID> checkedIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > MAX_COPIES_PER_SIDE
                || ids.stream().anyMatch(Objects::isNull)) {
            throw new InvalidProposalException(
                    "Each side needs between 1 and " + MAX_COPIES_PER_SIDE + " copies");
        }
        return ids;
    }

    private static String checkedMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String text = message.strip();
        if (text.length() > MAX_MESSAGE_LENGTH || text.indexOf('\u0000') >= 0) {
            throw new InvalidProposalException(
                    "The message must be at most " + MAX_MESSAGE_LENGTH + " characters and contain no NUL");
        }
        return text;
    }

    private static List<Copy> requireAvailable(List<UUID> ids, Map<UUID, Copy> found) {
        List<Copy> result = new ArrayList<>();
        for (UUID id : ids) {
            Copy c = found.get(id);
            if (c == null || c.getStatus() != CopyStatus.AVAILABLE) {
                throw new CopyNotAvailableException();
            }
            result.add(c);
        }
        return result;
    }
}
