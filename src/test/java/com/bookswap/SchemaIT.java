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

    @Test
    void completionSchemaAllowsCompletedStatusNoShowCauseAndAnonymizedUsers() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        jdbc.update(INSERT, a, "p@example.com", null, null, null);
        jdbc.update(INSERT, b, "q@example.com", null, null, null);
        jdbc.update("""
                insert into proposal(id, proposer_id, recipient_id, status, cancel_cause, created_at, updated_at, expires_at,
                    version, accepted_at, confirm_by, proposer_confirmed_at, recipient_confirmed_at, completed_at, pickup_note)
                values (?, ?, ?, 'COMPLETED', null, now(), now(), now(), 0, now(), now(), now(), now(), now(), 'note')""",
                UUID.randomUUID(), a, b);
        jdbc.update("""
                insert into proposal(id, proposer_id, recipient_id, status, cancel_cause, created_at, updated_at, expires_at, version)
                values (?, ?, ?, 'CANCELLED', 'NO_SHOW', now(), now(), now(), 0)""", UUID.randomUUID(), a, b);

        UUID gone = UUID.randomUUID();
        jdbc.update("""
                insert into users(id, email, password_hash, display_name, created_at, updated_at, version, deleted_at)
                values (?, 'gone@example.com', 'x', 'Deleted user', now(), now(), 0, now())""", gone);
        assertThatThrownBy(() -> jdbc.update("""
                insert into users(id, email, password_hash, display_name, created_at, updated_at, version)
                values (?, 'half@example.com', 'x', 'Name only', now(), now(), 0)""", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("""
                select is_nullable from information_schema.columns
                where table_name = 'proposal_item' and column_name = 'copy_id'""", String.class)).isEqualTo("YES");
    }

    @Test
    void proposalTablesExistAndAProposalCannotBeToYourself() {
        Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name in ('proposal','proposal_item')",
                Integer.class);
        assertThat(count).isEqualTo(2);

        UUID user = UUID.randomUUID();
        jdbc.update(INSERT, user, "self@example.com", null, null, null);
        assertThatThrownBy(() -> jdbc.update("""
                insert into proposal(id, proposer_id, recipient_id, status, created_at, updated_at, expires_at, version)
                values (?, ?, ?, 'PROPOSED', now(), now(), now() + interval '1 day', 0)""",
                UUID.randomUUID(), user, user)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
