package com.bookswap.proposal.service;

import com.bookswap.common.web.PageResponse;
import com.bookswap.common.web.Paging;
import com.bookswap.copy.domain.Copy;
import com.bookswap.copy.domain.CopyNotAvailableException;
import com.bookswap.copy.domain.CopyStatus;
import com.bookswap.copy.repository.CopyRepository;
import com.bookswap.proposal.domain.InvalidProposalException;
import com.bookswap.proposal.domain.Proposal;
import com.bookswap.proposal.domain.ProposalItem;
import com.bookswap.proposal.domain.ProposalNotFoundException;
import com.bookswap.proposal.domain.ProposalRole;
import com.bookswap.proposal.domain.ProposalStatus;
import com.bookswap.proposal.domain.SelfProposalException;
import com.bookswap.proposal.domain.Side;
import com.bookswap.proposal.repository.ProposalItemRepository;
import com.bookswap.proposal.repository.ProposalRepository;
import com.bookswap.proposal.web.ProposalRequest;
import com.bookswap.proposal.web.ProposalView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    /** Passed as the proposal to leave out when no proposal is being accepted. */
    private static final UUID NO_PROPOSAL = new UUID(0L, 0L);

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
     * Reserves every copy on both sides in one transaction. The copies are locked first, in ascending id order,
     * before anything else is written; every writer (this, the copy hooks, propose) takes copy locks before
     * proposal locks, so competing accepts and accept-versus-unlist cannot deadlock. The loser sees the copies
     * already reserved (or fails its version check) and gets a 409 having changed nothing.
     */
    @Transactional
    public ProposalView accept(UUID me, UUID id) {
        Instant now = clock.instant();
        Proposal proposal = load(me, id);
        proposal.requireAcceptableBy(me, now);

        Map<UUID, ProposalItem> itemsByCopy = new HashMap<>();
        for (ProposalItem item : items.findByProposalId(id)) {
            if (item.getCopyId() == null) {
                throw new CopyNotAvailableException();
            }
            itemsByCopy.put(item.getCopyId(), item);
        }
        List<Copy> toReserve = copies.lockForUpdate(itemsByCopy.keySet());
        if (toReserve.size() != itemsByCopy.size()) {
            throw new CopyNotAvailableException();
        }
        for (Copy copy : toReserve) {
            ProposalItem item = itemsByCopy.get(copy.getId());
            UUID expectedOwner = item.getSide() == Side.OFFERED ? proposal.getProposerId() : proposal.getRecipientId();
            if (!copy.getOwnerId().equals(expectedOwner) || copy.getStatus() != CopyStatus.AVAILABLE
                    || copy.getGrade() != item.getGradeAtProposal()) {
                throw new CopyNotAvailableException();
            }
        }
        proposal.accept(me, now);
        toReserve.forEach(copy -> copy.reserve(now));
        copies.saveAllAndFlush(toReserve);
        proposals.cancelOpenContaining(itemsByCopy.keySet(), id, now);
        return assembler.toView(proposal, me, now);
    }

    /**
     * The recipient answers with a changed offer: the original becomes {@code COUNTERED} and a new proposal goes the
     * other way, linked to it. One transaction, so an invalid counter leaves the original open.
     */
    @Transactional
    public ProposalView counter(UUID me, UUID id, ProposalRequest request) {
        Proposal original = load(me, id);
        original.markCountered(me, clock.instant());
        return createProposal(me, original.getProposerId(), original.getId(), request);
    }

    @Transactional
    public ProposalView reject(UUID me, UUID id, String reason) {
        Instant now = clock.instant();
        Proposal proposal = load(me, id);
        proposal.reject(me, checkedText(reason, "reason"), now);
        return assembler.toView(proposal, me, now);
    }

    @Transactional
    public ProposalView cancel(UUID me, UUID id) {
        Instant now = clock.instant();
        Proposal proposal = load(me, id);
        proposal.cancel(me, now);
        return assembler.toView(proposal, me, now);
    }

    /** Called by the copy service, in its transaction, when a copy is regraded, unlisted or removed. */
    @Transactional
    public void cancelOpenForCopy(UUID copyId, Instant now) {
        proposals.cancelOpenContaining(List.of(copyId), NO_PROPOSAL, now);
    }

    /** The caller's incoming or outgoing proposals, newest first; list cards never carry contact details. */
    @Transactional(readOnly = true)
    public PageResponse<ProposalView> list(UUID me, String role, ProposalStatus status, int page, int size) {
        ProposalRole inbox = ProposalRole.parse(role);
        Instant now = clock.instant();
        Pageable pageable = Paging.pageable(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        Page<Proposal> result;
        if (inbox == ProposalRole.INCOMING) {
            result = status == null ? proposals.findIncoming(me, pageable)
                    : proposals.findIncomingByStatus(me, status, now, pageable);
        } else {
            result = status == null ? proposals.findOutgoing(me, pageable)
                    : proposals.findOutgoingByStatus(me, status, now, pageable);
        }
        List<ProposalView> views = assembler.toViews(result.getContent(), null, now);
        return PageResponse.from(new PageImpl<>(views, result.getPageable(), result.getTotalElements()));
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
        copies.lockForShare(all).forEach(c -> found.put(c.getId(), c));
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
        for (int i = 0; i < requested.size(); i++) {
            newItems.add(new ProposalItem(proposal.getId(), requested.get(i), Side.REQUESTED, i));
        }
        for (int i = 0; i < offered.size(); i++) {
            newItems.add(new ProposalItem(proposal.getId(), offered.get(i), Side.OFFERED, i));
        }
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
        return checkedText(message, "message");
    }

    /** Optional free text: blank means none; at most 500 characters and no NUL (PostgreSQL cannot store NUL). */
    private static String checkedText(String value, String what) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.strip();
        if (text.length() > MAX_MESSAGE_LENGTH || text.indexOf('\0') >= 0) {
            throw new InvalidProposalException(
                    "The " + what + " must be at most " + MAX_MESSAGE_LENGTH + " characters and contain no NUL");
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
