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

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    /** After this instant an accepted exchange can no longer be confirmed or cancelled; the housekeeping job settles it. */
    @Column(name = "confirm_by")
    private Instant confirmBy;

    @Column(name = "proposer_confirmed_at")
    private Instant proposerConfirmedAt;

    @Column(name = "recipient_confirmed_at")
    private Instant recipientConfirmedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "pickup_note")
    private String pickupNote;

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

    /** The checks of {@link #accept}, without changing anything: so a caller can lock what it needs first. */
    public void requireAcceptableBy(UUID actor, Instant now) {
        requireOpenFor(recipientId, actor, now);
    }

    public void accept(UUID actor, Instant now, Duration confirmWindow) {
        requireOpenFor(recipientId, actor, now);
        this.status = ProposalStatus.ACCEPTED;
        this.acceptedAt = now;
        this.confirmBy = now.plus(confirmWindow);
        this.updatedAt = now;
    }

    public void reject(UUID actor, String reason, Instant now) {
        requireOpenFor(recipientId, actor, now);
        this.status = ProposalStatus.REJECTED;
        this.rejectReason = reason;
        this.updatedAt = now;
    }

    /**
     * Withdraws the proposal. A proposed one can only be withdrawn by the proposer. An accepted one can be called off
     * by either party until someone has confirmed; after that only the party who has not confirmed may (a dispute).
     *
     * @return true when an accepted exchange was cancelled, so the caller must release its reserved copies
     */
    public boolean cancel(UUID actor, Instant now) {
        if (status != ProposalStatus.ACCEPTED) {
            requireOpenFor(proposerId, actor, now);
            cancelWith(CancelCause.WITHDRAWN, now);
            return false;
        }
        requireParty(actor);
        requireAcceptedBeforeDeadline(now);
        if (confirmationCount() > 0 && hasConfirmed(actor)) {
            throw new ProposalActionNotAllowedException();
        }
        cancelWith(CancelCause.WITHDRAWN, now);
        return true;
    }

    public enum ConfirmOutcome { RECORDED, COMPLETED }

    /** Records this party's confirmation; the second party's confirmation completes the exchange. */
    public ConfirmOutcome confirm(UUID actor, Instant now) {
        requireParty(actor);
        requireAcceptedBeforeDeadline(now);
        if (hasConfirmed(actor)) {
            throw new AlreadyConfirmedException();
        }
        if (actor.equals(proposerId)) {
            this.proposerConfirmedAt = now;
        } else {
            this.recipientConfirmedAt = now;
        }
        this.updatedAt = now;
        if (confirmationCount() == 2) {
            complete(now);
            return ConfirmOutcome.COMPLETED;
        }
        return ConfirmOutcome.RECORDED;
    }

    public void setPickupNote(UUID actor, String note, Instant now) {
        requireParty(actor);
        requireAcceptedBeforeDeadline(now);
        this.pickupNote = note;
        this.updatedAt = now;
    }

    public enum Settlement { NONE, COMPLETED, CANCELLED }

    /**
     * Housekeeping, no actor: an accepted exchange past its confirm deadline completes if anyone confirmed (their word
     * counts, the other's silence is consent) and is cancelled as a no-show if nobody did.
     */
    public Settlement settle(Instant now) {
        if (status != ProposalStatus.ACCEPTED || confirmBy == null || now.isBefore(confirmBy)) {
            return Settlement.NONE;
        }
        if (confirmationCount() > 0) {
            complete(now);
            return Settlement.COMPLETED;
        }
        cancelWith(CancelCause.NO_SHOW, now);
        return Settlement.CANCELLED;
    }

    public boolean hasConfirmed(UUID user) {
        return user.equals(proposerId) ? proposerConfirmedAt != null
                : user.equals(recipientId) && recipientConfirmedAt != null;
    }

    private int confirmationCount() {
        return (proposerConfirmedAt != null ? 1 : 0) + (recipientConfirmedAt != null ? 1 : 0);
    }

    private void complete(Instant now) {
        this.status = ProposalStatus.COMPLETED;
        this.completedAt = now;
        this.updatedAt = now;
    }

    private void cancelWith(CancelCause cause, Instant now) {
        this.status = ProposalStatus.CANCELLED;
        this.cancelCause = cause;
        this.updatedAt = now;
    }

    private void requireParty(UUID actor) {
        if (!involves(actor)) {
            throw new ProposalActionNotAllowedException();
        }
    }

    private void requireAcceptedBeforeDeadline(Instant now) {
        if (status != ProposalStatus.ACCEPTED || confirmBy == null || !now.isBefore(confirmBy)) {
            throw new ProposalNotOpenException();
        }
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
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getConfirmBy() { return confirmBy; }
    public Instant getProposerConfirmedAt() { return proposerConfirmedAt; }
    public Instant getRecipientConfirmedAt() { return recipientConfirmedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getPickupNote() { return pickupNote; }
}
