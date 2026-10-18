package com.bookswap.copy.domain;

import com.bookswap.book.domain.Book;
import jakarta.persistence.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/** A physical copy of a book. All grade/status rules live here, in one place. */
@Entity
@Table(name = "copy")
public class Copy implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "isbn13", nullable = false, updatable = false)
    private String isbn13;

    /**
     * The shared catalog book, if we have one. There is deliberately no foreign key: a copy may exist for an ISBN
     * with no book row, carrying the owner's typed title/author instead (see {@link #getTitle()}).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "isbn13", insertable = false, updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @NotFound(action = NotFoundAction.IGNORE)
    private Book book;

    /** The owner's own title/author, only set when neither we nor the catalog knew the book. */
    @Column(name = "title", updatable = false)
    private String manualTitle;

    @Column(name = "author", updatable = false)
    private String manualAuthor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConditionGrade grade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CopyStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @Transient
    private boolean isNew = true;

    protected Copy() {}

    public Copy(UUID ownerId, Book book, ConditionGrade grade, Instant now) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.isbn13 = book.getIsbn13();
        this.book = book;
        this.grade = grade;
        this.status = CopyStatus.UNLISTED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** A copy of a book the catalog does not (yet) know: the owner's typed details live on the copy only. */
    public static Copy withManualDetails(UUID ownerId, String isbn13, String title, String author,
                                         ConditionGrade grade, Instant now) {
        Copy copy = new Copy();
        copy.id = UUID.randomUUID();
        copy.ownerId = ownerId;
        copy.isbn13 = isbn13;
        copy.manualTitle = title;
        copy.manualAuthor = author;
        copy.grade = grade;
        copy.status = CopyStatus.UNLISTED;
        copy.createdAt = now;
        copy.updatedAt = now;
        return copy;
    }

    public void changeGrade(ConditionGrade newGrade, Instant now) {
        requireNotReserved();
        this.grade = newGrade;
        this.updatedAt = now;
    }

    public void list(boolean profileComplete, Instant now) {
        requireNotReserved();
        if (!profileComplete) {
            throw new ProfileIncompleteException();
        }
        this.status = CopyStatus.AVAILABLE;
        this.updatedAt = now;
    }

    public void unlist(Instant now) {
        requireNotReserved();
        this.status = CopyStatus.UNLISTED;
        this.updatedAt = now;
    }

    /** Taken out of circulation by an accepted proposal. Releasing it again arrives with step 5. */
    public void reserve(Instant now) {
        if (status != CopyStatus.AVAILABLE) {
            throw new CopyNotAvailableException();
        }
        this.status = CopyStatus.RESERVED;
        this.updatedAt = now;
    }

    public void requireRemovable() {
        requireNotReserved();
    }

    private void requireNotReserved() {
        if (status == CopyStatus.RESERVED) {
            throw new CopyReservedException();
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
    public UUID getOwnerId() { return ownerId; }
    public String getIsbn13() { return isbn13; }

    /** Catalog details win over the owner's typed ones as soon as a shared book row exists for this ISBN. */
    public String getTitle() { return book != null ? book.getTitle() : manualTitle; }
    public String getAuthor() { return book != null ? book.getAuthor() : manualAuthor; }
    public String getCoverUrl() { return book != null ? book.getCoverUrl() : null; }
    public DetailsSource getDetailsSource() { return book != null ? DetailsSource.CATALOG : DetailsSource.MANUAL; }

    public enum DetailsSource { CATALOG, MANUAL }
    public ConditionGrade getGrade() { return grade; }
    public CopyStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
