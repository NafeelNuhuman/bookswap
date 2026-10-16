package com.bookswap.copy.domain;

import com.bookswap.book.domain.Book;
import jakarta.persistence.*;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "isbn13", nullable = false, updatable = false)
    private Book book;

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
        this.book = book;
        this.grade = grade;
        this.status = CopyStatus.UNLISTED;
        this.createdAt = now;
        this.updatedAt = now;
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
    public Book getBook() { return book; }
    public ConditionGrade getGrade() { return grade; }
    public CopyStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
