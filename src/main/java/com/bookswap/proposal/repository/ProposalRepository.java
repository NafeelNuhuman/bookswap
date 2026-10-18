package com.bookswap.proposal.repository;

import com.bookswap.proposal.domain.Proposal;
import com.bookswap.proposal.domain.ProposalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The inbox queries. A status filter is evaluated against {@code now}: {@code PROPOSED} means proposed and not yet
 * lapsed, {@code EXPIRED} means stored as expired or proposed and lapsed, anything else is an exact match.
 */
public interface ProposalRepository extends JpaRepository<Proposal, UUID> {

    /**
     * Cancels every still-proposed proposal (other than {@code exceptProposalId}) that contains any of the copies.
     * A bulk update, so it bumps {@code version} itself: a concurrent action on one of these proposals then fails
     * its own version check instead of silently overwriting the cancellation.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Proposal p set p.status = com.bookswap.proposal.domain.ProposalStatus.CANCELLED,
                p.cancelCause = com.bookswap.proposal.domain.CancelCause.COPY_UNAVAILABLE,
                p.updatedAt = :now, p.version = p.version + 1
            where p.status = com.bookswap.proposal.domain.ProposalStatus.PROPOSED and p.id <> :exceptProposalId
              and p.id in (select i.proposalId from ProposalItem i where i.copyId in :copyIds)
            """)
    int cancelOpenContaining(@Param("copyIds") Collection<UUID> copyIds,
                             @Param("exceptProposalId") UUID exceptProposalId, @Param("now") Instant now);

    /** Housekeeping: proposed proposals past their expiry become expired (bumps the version like any other writer). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Proposal p set p.status = com.bookswap.proposal.domain.ProposalStatus.EXPIRED,
                p.updatedAt = :now, p.version = p.version + 1
            where p.status = com.bookswap.proposal.domain.ProposalStatus.PROPOSED and p.expiresAt <= :now
            """)
    int expireLapsed(@Param("now") Instant now);

    /** Housekeeping: accepted exchanges past their confirm deadline, oldest first. */
    @Query("""
            select p.id from Proposal p
            where p.status = com.bookswap.proposal.domain.ProposalStatus.ACCEPTED and p.confirmBy <= :now
              and p.id not in :skip
            order by p.confirmBy, p.id
            """)
    List<UUID> findOverdueAccepted(@Param("now") Instant now, @Param("skip") Collection<UUID> skip, Pageable limit);

    @Query("""
            select count(p) > 0 from Proposal p
            where p.status = com.bookswap.proposal.domain.ProposalStatus.ACCEPTED
              and (p.proposerId = :userId or p.recipientId = :userId)
            """)
    boolean existsAcceptedFor(@Param("userId") UUID userId);

    /** The user's completed exchanges, as either party. */
    @Query("""
            select p from Proposal p
            where p.status = com.bookswap.proposal.domain.ProposalStatus.COMPLETED
              and (p.proposerId = :me or p.recipientId = :me)
            """)
    Page<Proposal> findCompleted(@Param("me") UUID me, Pageable pageable);

    @Query("select p from Proposal p where p.recipientId = :me")
    Page<Proposal> findIncoming(@Param("me") UUID me, Pageable pageable);

    @Query("select p from Proposal p where p.proposerId = :me")
    Page<Proposal> findOutgoing(@Param("me") UUID me, Pageable pageable);

    @Query("""
            select p from Proposal p where p.recipientId = :me and (
                (:status = com.bookswap.proposal.domain.ProposalStatus.PROPOSED
                    and p.status = :status and p.expiresAt > :now)
                or (:status = com.bookswap.proposal.domain.ProposalStatus.EXPIRED
                    and (p.status = :status or (p.status = com.bookswap.proposal.domain.ProposalStatus.PROPOSED
                                                and p.expiresAt <= :now)))
                or (:status <> com.bookswap.proposal.domain.ProposalStatus.PROPOSED
                    and :status <> com.bookswap.proposal.domain.ProposalStatus.EXPIRED and p.status = :status))
            """)
    Page<Proposal> findIncomingByStatus(@Param("me") UUID me, @Param("status") ProposalStatus status,
                                        @Param("now") Instant now, Pageable pageable);

    @Query("""
            select p from Proposal p where p.proposerId = :me and (
                (:status = com.bookswap.proposal.domain.ProposalStatus.PROPOSED
                    and p.status = :status and p.expiresAt > :now)
                or (:status = com.bookswap.proposal.domain.ProposalStatus.EXPIRED
                    and (p.status = :status or (p.status = com.bookswap.proposal.domain.ProposalStatus.PROPOSED
                                                and p.expiresAt <= :now)))
                or (:status <> com.bookswap.proposal.domain.ProposalStatus.PROPOSED
                    and :status <> com.bookswap.proposal.domain.ProposalStatus.EXPIRED and p.status = :status))
            """)
    Page<Proposal> findOutgoingByStatus(@Param("me") UUID me, @Param("status") ProposalStatus status,
                                        @Param("now") Instant now, Pageable pageable);
}
