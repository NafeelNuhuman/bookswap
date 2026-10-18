package com.bookswap.proposal;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountDeletionIT extends ProposalTestBase {

    private ResultActions deleteAccount(JsonNode who, String json) throws Exception {
        return mvc.perform(delete("/api/users/me").header("Authorization", bearer(who))
                .contentType(APPLICATION_JSON).content(json));
    }

    private ResultActions deleteAccount(JsonNode who, String password, boolean unused) throws Exception {
        return deleteAccount(who, objectMapper.writeValueAsString(Map.of("password", password)));
    }

    private ResultActions deleteWithPassword(JsonNode who) throws Exception {
        return deleteAccount(who, "password123", true);
    }

    private String proposalStatus(String id) {
        return jdbc.queryForObject("select status from proposal where id = ?::uuid", String.class, id);
    }

    private int copiesOf(String userId) {
        return jdbc.queryForObject("select count(*) from copy where owner_id = ?::uuid", Integer.class, userId);
    }

    @Test
    void aWrongPasswordIs401AndNothingChanges() throws Exception {
        addListed(bob, 1, "Book 1");
        deleteAccount(bob, "wrong-password", true).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        assertThat(copiesOf(userId(bob))).isEqualTo(1);
        mvc.perform(get("/api/users/me").header("Authorization", bearer(bob))).andExpect(status().isOk());
    }

    @Test
    void aMissingOrBlankPasswordIs400() throws Exception {
        deleteAccount(bob, "{}").andExpect(status().isBadRequest());
        deleteAccount(bob, "{\"password\":\"  \"}").andExpect(status().isBadRequest());
        mvc.perform(delete("/api/users/me").header("Authorization", bearer(bob))).andExpect(status().is4xxClientError());
    }

    @Test
    void requiresAToken() throws Exception {
        mvc.perform(delete("/api/users/me").contentType(APPLICATION_JSON).content("{\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusedWhileAnAcceptedExchangeExistsForEitherParty() throws Exception {
        String b1 = addListed(bob, 1, "Book 1");
        String a1 = addListed(alice, 3, "Book 3");
        String id = proposeOk(alice, List.of(b1), List.of(a1));
        action(bob, id, "accept").andExpect(status().isOk());

        for (JsonNode who : List.of(alice, bob)) {
            deleteWithPassword(who).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_HAS_ACTIVE_EXCHANGE"));
        }
        assertThat(copiesOf(userId(alice))).isEqualTo(1);
        assertThat(copiesOf(userId(bob))).isEqualTo(1);
        assertThat(proposalStatus(id)).isEqualTo("ACCEPTED");
        mvc.perform(get("/api/users/me").header("Authorization", bearer(alice))).andExpect(status().isOk());
    }

    @Test
    void allowedOnceTheExchangeIsCalledOffOrCompleted() throws Exception {
        String b1 = addListed(bob, 1, "Book 1");
        String a1 = addListed(alice, 3, "Book 3");
        String id = proposeOk(alice, List.of(b1), List.of(a1));
        action(bob, id, "accept").andExpect(status().isOk());
        action(bob, id, "cancel").andExpect(status().isOk());
        deleteWithPassword(bob).andExpect(status().isNoContent());
        assertThat(copiesOf(userId(alice))).isEqualTo(1);
    }

    @Test
    void openProposalsInvolvingTheUserAreCancelledForTheOtherParty() throws Exception {
        String b1 = addListed(bob, 1, "Book 1");
        String b2 = addListed(bob, 2, "Book 2");
        String a1 = addListed(alice, 3, "Book 3");
        String c1 = addListed(carol, 4, "Book 4");
        String incoming = proposeOk(alice, List.of(b1), List.of(a1));       // to bob
        String outgoing = proposeOk(bob, List.of(c1), List.of(b2));         // from bob

        deleteWithPassword(bob).andExpect(status().isNoContent());

        for (String id : List.of(incoming, outgoing)) {
            assertThat(proposalStatus(id)).isEqualTo("CANCELLED");
        }
        detail(alice, incoming).andExpect(jsonPath("$.cancelCause").value("COPY_UNAVAILABLE"))
                .andExpect(jsonPath("$.recipient.displayName").value("Deleted user"))
                .andExpect(jsonPath("$.requested[0].title").value("Book 1"));
        detail(carol, outgoing).andExpect(jsonPath("$.proposer.displayName").value("Deleted user"));
    }

    @Test
    void copiesAreRemovedAndPersonalDataIsErased() throws Exception {
        jdbc.update("update users set contact_note = 'call me' where email = 'bob@example.com'");
        addListed(bob, 1, "Book 1");
        addListed(bob, 2, "Book 2");
        String bobId = userId(bob);

        deleteWithPassword(bob).andExpect(status().isNoContent());

        assertThat(copiesOf(bobId)).isZero();
        Map<String, Object> row = jdbc.queryForMap("select * from users where id = ?::uuid", bobId);
        assertThat(row.get("email")).isEqualTo("deleted-" + bobId + "@deleted.invalid");
        assertThat(row.get("display_name")).isEqualTo("Deleted user");
        assertThat(row.get("city")).isNull();
        assertThat(row.get("postal_code")).isNull();
        assertThat(row.get("contact_note")).isNull();
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat((String) row.get("password_hash")).doesNotStartWith("$2");
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where user_id = ?::uuid", Integer.class, bobId)).isZero();
        carolSeesNothingOfBob();
    }

    private void carolSeesNothingOfBob() throws Exception {
        feed(carol).andExpect(jsonPath("$.totalElements").value(0));
        search(carol, "q", "Book").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void theShelfOfADeletedUserIs404() throws Exception {
        String bobId = userId(bob);
        addListed(bob, 1, "Book 1");
        deleteWithPassword(bob).andExpect(status().isNoContent());
        mvc.perform(get("/api/users/" + bobId + "/copies").header("Authorization", bearer(carol)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void aDeletedUserCannotLogInOrRefreshAndTheEmailCanBeReused() throws Exception {
        String refresh = bob.get("refreshToken").asText();
        deleteWithPassword(bob).andExpect(status().isNoContent());

        postJson("/api/auth/login", Map.of("email", "bob@example.com", "password", "password123"))
                .andExpect(status().isUnauthorized());
        postJson("/api/auth/refresh", Map.of("refreshToken", refresh)).andExpect(status().isUnauthorized());
        postJson("/api/auth/register", Map.of("email", "bob@example.com", "password", "password123"))
                .andExpect(status().isCreated());
    }

    @Test
    void aStillValidAccessTokenGetsNotFoundOnTheProfile() throws Exception {
        deleteWithPassword(bob).andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me").header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        deleteWithPassword(bob).andExpect(status().isNotFound());
    }

    @Test
    void theOtherPartysHistoryOutlivesTheDeletion() throws Exception {
        String b1 = addListed(bob, 1, "Book 1");
        String a1 = addListed(alice, 3, "Book 3");
        String id = proposeOk(alice, List.of(b1), List.of(a1));
        action(bob, id, "accept").andExpect(status().isOk());
        action(alice, id, "confirm").andExpect(status().isOk());
        action(bob, id, "confirm").andExpect(status().isOk());

        deleteWithPassword(bob).andExpect(status().isNoContent());

        mvc.perform(get("/api/exchanges").header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].with.displayName").value("Deleted user"))
                .andExpect(jsonPath("$.content[0].with.city").doesNotExist())
                .andExpect(jsonPath("$.content[0].with.email").doesNotExist())
                .andExpect(jsonPath("$.content[0].received[0].title").value("Book 1"));
        // alice keeps the book she got; the book bob received went with his account
        assertThat(copiesOf(userId(alice))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select owner_id::text from copy where id = ?::uuid", String.class, b1))
                .isEqualTo(userId(alice));
        detail(alice, id).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.recipient.displayName").value("Deleted user"));
    }
}
