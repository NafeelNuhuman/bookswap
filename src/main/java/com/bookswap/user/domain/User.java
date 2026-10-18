package com.bookswap.user.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    public static final String DELETED_DISPLAY_NAME = "Deleted user";

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    private String city;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "contact_note")
    private String contactNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    protected User() {}

    public User(String email, String passwordHash, Instant now) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateProfile(String displayName, String city, String postalCode, String contactNote, Instant now) {
        this.displayName = displayName;
        this.city = city;
        this.postalCode = postalCode;
        this.contactNote = contactNote;
        this.updatedAt = now;
    }

    /**
     * Account deletion keeps the row (other people's history points at it) but erases everything personal: the email
     * becomes a placeholder nobody can use to log in, and the password hash can match nothing.
     */
    public void anonymize(Instant now) {
        this.email = "deleted-" + id + "@deleted.invalid";
        this.passwordHash = "!deleted";
        this.displayName = DELETED_DISPLAY_NAME;
        this.city = null;
        this.postalCode = null;
        this.contactNote = null;
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isProfileComplete() {
        return deletedAt == null && notBlank(displayName) && notBlank(city) && notBlank(postalCode);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getCity() { return city; }
    public String getPostalCode() { return postalCode; }
    public String getContactNote() { return contactNote; }
}
