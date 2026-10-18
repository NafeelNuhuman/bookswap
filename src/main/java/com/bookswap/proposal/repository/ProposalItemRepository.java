package com.bookswap.proposal.repository;

import com.bookswap.proposal.domain.ProposalItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProposalItemRepository extends JpaRepository<ProposalItem, UUID> {

    List<ProposalItem> findByProposalId(UUID proposalId);

    /** The displayed details are the snapshot taken when the proposal was made, not the copy's current ones. */
    @Query("""
            select new com.bookswap.proposal.repository.ProposalItemRow(
                i.proposalId, i.side, i.copyId, i.gradeAtProposal, i.isbn13, i.title, i.author, i.coverUrl,
                i.detailsSource)
            from ProposalItem i
            where i.proposalId in :proposalIds
            order by i.proposalId, i.side, i.position
            """)
    List<ProposalItemRow> findRows(@Param("proposalIds") Collection<UUID> proposalIds);
}
