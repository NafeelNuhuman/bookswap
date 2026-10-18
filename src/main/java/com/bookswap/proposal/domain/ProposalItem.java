package com.bookswap.proposal.domain;

import com.bookswap.copy.domain.ConditionGrade;
import com.bookswap.copy.domain.Copy;
import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

/**
 * One copy on one side of a proposal. It keeps its own snapshot of the grade and book details as they were when the
 * proposal was made, so a proposal's history survives the copy being removed ({@code copy_id} then becomes null).
 */
@Entity
@Table(name = "proposal_item")
public class ProposalItem implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "proposal_id", nullable = false, updatable = false)
    private UUID proposalId;

    @Column(name = "copy_id", updatable = false)
    private UUID copyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Side side;

    @Column(nullable = false, updatable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade_at_proposal", nullable = false, updatable = false)
    private ConditionGrade gradeAtProposal;

    @Column(name = "isbn13", nullable = false, updatable = false)
    private String isbn13;

    @Column(nullable = false, updatable = false)
    private String title;

    @Column(nullable = false, updatable = false)
    private String author;

    @Column(name = "cover_url", updatable = false)
    private String coverUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "details_source", nullable = false, updatable = false)
    private Copy.DetailsSource detailsSource;

    @Transient
    private boolean isNew = true;

    protected ProposalItem() {}

    public ProposalItem(UUID proposalId, Copy copy, Side side, int position) {
        this.id = UUID.randomUUID();
        this.proposalId = proposalId;
        this.copyId = copy.getId();
        this.side = side;
        this.position = position;
        this.gradeAtProposal = copy.getGrade();
        this.isbn13 = copy.getIsbn13();
        this.title = copy.getTitle();
        this.author = copy.getAuthor();
        this.coverUrl = copy.getCoverUrl();
        this.detailsSource = copy.getDetailsSource();
    }

    /** Assigned id + no @Version null check: tell Spring Data this is an INSERT so save() does not SELECT first. */
    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() { this.isNew = false; }

    @Override
    public UUID getId() { return id; }
    public UUID getProposalId() { return proposalId; }
    public UUID getCopyId() { return copyId; }
    public Side getSide() { return side; }
    public int getPosition() { return position; }
    public ConditionGrade getGradeAtProposal() { return gradeAtProposal; }
    public String getIsbn13() { return isbn13; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getCoverUrl() { return coverUrl; }
    public Copy.DetailsSource getDetailsSource() { return detailsSource; }
}
