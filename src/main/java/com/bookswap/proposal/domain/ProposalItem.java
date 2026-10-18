package com.bookswap.proposal.domain;

import com.bookswap.copy.domain.ConditionGrade;
import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.util.UUID;

/** One copy on one side of a proposal, with the grade it had when the proposal was made. */
@Entity
@Table(name = "proposal_item")
public class ProposalItem implements Persistable<ProposalItem.Key> {

    @EmbeddedId
    private Key id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Side side;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade_at_proposal", nullable = false, updatable = false)
    private ConditionGrade gradeAtProposal;

    @Transient
    private boolean isNew = true;

    protected ProposalItem() {}

    public ProposalItem(UUID proposalId, UUID copyId, Side side, ConditionGrade gradeAtProposal) {
        this.id = new Key(proposalId, copyId);
        this.side = side;
        this.gradeAtProposal = gradeAtProposal;
    }

    @Embeddable
    public record Key(@Column(name = "proposal_id") UUID proposalId, @Column(name = "copy_id") UUID copyId)
            implements Serializable {}

    /** Composite assigned key: tell Spring Data this is an INSERT so save() does not SELECT first. */
    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() { this.isNew = false; }

    @Override
    public Key getId() { return id; }
    public UUID getProposalId() { return id.proposalId(); }
    public UUID getCopyId() { return id.copyId(); }
    public Side getSide() { return side; }
    public ConditionGrade getGradeAtProposal() { return gradeAtProposal; }
}
