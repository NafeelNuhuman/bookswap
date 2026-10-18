package com.bookswap.user.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-02T00:00:00Z");

    @Test
    void anonymizeErasesPersonalDataAndMarksTheUserDeleted() {
        User user = new User("bob@example.com", "$2a$04$hash", T0);
        user.updateProfile("Bob", "Berlin", "10115", "call me", T0);

        user.anonymize(T1);

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getEmail()).isEqualTo("deleted-" + user.getId() + "@deleted.invalid");
        assertThat(user.getDisplayName()).isEqualTo("Deleted user");
        assertThat(user.getCity()).isNull();
        assertThat(user.getPostalCode()).isNull();
        assertThat(user.getContactNote()).isNull();
        assertThat(user.getPasswordHash()).doesNotStartWith("$2");
        assertThat(user.isProfileComplete()).isFalse();
    }

    @Test
    void aNewUserIsNotDeleted() {
        assertThat(new User("a@example.com", "hash", T0).isDeleted()).isFalse();
    }
}
