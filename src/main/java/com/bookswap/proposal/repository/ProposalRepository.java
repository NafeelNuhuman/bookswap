package com.bookswap.proposal.repository;

import com.bookswap.proposal.domain.Proposal;
import com.bookswap.proposal.domain.ProposalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/**
 * The inbox queries. A status filter is evaluated against {@code now}: {@code PROPOSED} means proposed and not yet
 * lapsed, {@code EXPIRED} means stored as expired or proposed and lapsed, anything else is an exact match.
 */
public interface ProposalRepository extends JpaRepository<Proposal, UUID> {

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
