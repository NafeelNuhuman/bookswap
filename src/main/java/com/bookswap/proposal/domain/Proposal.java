package com.bookswap.proposal.domain;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * An exchange proposal between two users. Every status transition lives here, in one place: the actor must have
 * the right role, and the proposal must still be open (proposed and not past its expiry).
 */
@Entity
@Table(name = "proposal")
public class Proposal implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "proposer_id", nullable = false, updatable = false)
    private UUID proposerId;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProposalStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_cause")
    private CancelCause cancelCause;

    @Column(updatable = false)
    private String message;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "parent_id", updatable = false)
    private UUID parentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Version
    private Long version;

    @Transient
    private boolean isNew = true;

    protected Proposal() {}

    public static Proposal create(UUID proposerId, UUID recipientId, String message, UUID parentId,
                                  Instant now, Duration ttl) {
        Proposal p = new Proposal();
        p.id = UUID.randomUUID();
        p.proposerId = proposerId;
        p.recipientId = recipientId;
        p.status = ProposalStatus.PROPOSED;
        p.message = message;
        p.parentId = parentId;
        p.createdAt = now;
        p.updatedAt = now;
        p.expiresAt = now.plus(ttl);
        return p;
    }

    public boolean involves(UUID userId) {
        return proposerId.equals(userId) || recipientId.equals(userId);
    }

    public boolean isOpen(Instant now) {
        return status == ProposalStatus.PROPOSED && now.isBefore(expiresAt);
    }

    /** A proposed proposal past its expiry reads as expired even before the housekeeping job stores that. */
    public ProposalStatus effectiveStatus(Instant now) {
        return status == ProposalStatus.PROPOSED && !now.isBefore(expiresAt) ? ProposalStatus.EXPIRED : status;
    }

    public void accept(UUID actor, Instant now) {
        requireOpenFor(recipientId, actor, now);
        this.status = ProposalStatus.ACCEPTED;
        this.updatedAt = now;
    }

    public void reject(UUID actor, String reason, Instant now) {
        requireOpenFor(recipientId, actor, now);
        this.status = ProposalStatus.REJECTED;
        this.rejectReason = reason;
        this.updatedAt = now;
    }

    public void cancel(UUID actor, Instant now) {
        requireOpenFor(proposerId, actor, now);
        this.status = ProposalStatus.CANCELLED;
        this.cancelCause = CancelCause.WITHDRAWN;
        this.updatedAt = now;
    }

    public void markCountered(UUID actor, Instant now) {
        requireOpenFor(recipientId, actor, now);
        this.status = ProposalStatus.COUNTERED;
        this.updatedAt = now;
    }

    private void requireOpenFor(UUID allowedActor, UUID actor, Instant now) {
        if (!allowedActor.equals(actor)) {
            throw new ProposalActionNotAllowedException();
        }
        if (!isOpen(now)) {
            throw new ProposalNotOpenException();
        }
    }

    /** Assigned id + no @Version null check: tell Spring Data this is an INSERT so save() does not SELECT first. */
    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() { this.isNew = false; }

    @Override
    public UUID getId() { return id; }
    public UUID getProposerId() { return proposerId; }
    public UUID getRecipientId() { return recipientId; }
    public ProposalStatus getStatus() { return status; }
    public CancelCause getCancelCause() { return cancelCause; }
    public String getMessage() { return message; }
    public String getRejectReason() { return rejectReason; }
    public UUID getParentId() { return parentId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
