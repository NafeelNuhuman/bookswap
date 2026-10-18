package com.bookswap.proposal.repository;

import com.bookswap.proposal.domain.ProposalItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProposalItemRepository extends JpaRepository<ProposalItem, ProposalItem.Key> {

    List<ProposalItem> findByIdProposalId(UUID proposalId);

    /** The displayed title/author is the shared book's if one exists, otherwise the details typed on the copy. */
    @Query("""
            select new com.bookswap.proposal.repository.ProposalItemRow(
                i.id.proposalId, i.side, i.id.copyId, i.gradeAtProposal, c.isbn13,
                coalesce(b.title, c.manualTitle), coalesce(b.author, c.manualAuthor), b.coverUrl,
                case when b.isbn13 is null then false else true end)
            from ProposalItem i join Copy c on c.id = i.id.copyId left join c.book b
            where i.id.proposalId in :proposalIds
            order by c.createdAt, c.id
            """)
    List<ProposalItemRow> findRows(@Param("proposalIds") Collection<UUID> proposalIds);
}
