package com.bookswap;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaIT extends AbstractIntegrationTest {

    private static final String INSERT = """
            insert into users(id, email, password_hash, display_name, city, postal_code, created_at, updated_at, version)
            values (?, ?, 'hash', ?, ?, ?, now(), now(), 0)""";

    @Test
    void migrationCreatesTables() {
        Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name in ('users','refresh_token')",
                Integer.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void emailIsUniqueIgnoringCase() {
        jdbc.update(INSERT, UUID.randomUUID(), "a@example.com", null, null, null);
        assertThatThrownBy(() -> jdbc.update(INSERT, UUID.randomUUID(), "A@Example.com", null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void profileFieldsAreAllOrNone() {
        assertThatThrownBy(() -> jdbc.update(INSERT, UUID.randomUUID(), "b@example.com", "Bo", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(INSERT, UUID.randomUUID(), "c@example.com", "Bo", "  ", "12345"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(INSERT, UUID.randomUUID(), "d@example.com", "Bo", "Berlin", "10115");
    }
}
