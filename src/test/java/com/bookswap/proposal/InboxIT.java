package com.bookswap.proposal;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InboxIT extends ProposalTestBase {

    private ResultActions inbox(JsonNode caller, String... params) throws Exception {
        var request = get("/api/proposals").header("Authorization", bearer(caller));
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request);
    }

    /** Alice proposes to bob: bob's copy n for alice's copy n + 100. Returns the proposal id. */
    private String aliceProposesToBob(int n) throws Exception {
        return proposeOk(alice, List.of(addListed(bob, n, "Bob " + n)), List.of(addListed(alice, n + 100, "Alice " + n)));
    }

    @Test
    void incomingAndOutgoingAreSeparate() throws Exception {
        String toBob = aliceProposesToBob(1);
        String toAlice = proposeOk(bob, List.of(addListed(alice, 2, "Alice 2")), List.of(addListed(bob, 3, "Bob 3")));

        inbox(bob, "role", "incoming").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(toBob));
        inbox(bob, "role", "outgoing").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(toAlice));
        inbox(alice, "role", "incoming").andExpect(jsonPath("$.content[0].id").value(toAlice));
        inbox(alice, "role", "outgoing").andExpect(jsonPath("$.content[0].id").value(toBob));
    }

    @Test
    void cardsCarryBothSidesButAreOtherwiseTheDetailShape() throws Exception {
        aliceProposesToBob(1);
        inbox(bob, "role", "incoming").andExpect(jsonPath("$.content[0].status").value("PROPOSED"))
                .andExpect(jsonPath("$.content[0].proposer.displayName").value("Alice"))
                .andExpect(jsonPath("$.content[0].requested[0].title").value("Bob 1"))
                .andExpect(jsonPath("$.content[0].offered[0].title").value("Alice 1"));
    }

    @Test
    void newestFirst() throws Exception {
        String first = aliceProposesToBob(1);
        String second = aliceProposesToBob(2);
        String third = aliceProposesToBob(3);
        jdbc.update("update proposal set created_at = now() - interval '3 hours' where id = ?::uuid", first);
        jdbc.update("update proposal set created_at = now() - interval '2 hours' where id = ?::uuid", second);
        jdbc.update("update proposal set created_at = now() - interval '1 hour' where id = ?::uuid", third);

        inbox(bob, "role", "incoming").andExpect(jsonPath("$.content[0].id").value(third))
                .andExpect(jsonPath("$.content[1].id").value(second))
                .andExpect(jsonPath("$.content[2].id").value(first));
    }

    @Test
    void statusFilterTreatsALapsedProposedAsExpired() throws Exception {
        String open = aliceProposesToBob(1);
        String lapsed = aliceProposesToBob(2);
        String rejected = aliceProposesToBob(3);
        jdbc.update("update proposal set expires_at = now() - interval '1 minute' where id = ?::uuid", lapsed);
        jdbc.update("update proposal set status = 'REJECTED' where id = ?::uuid", rejected);

        inbox(bob, "role", "incoming").andExpect(jsonPath("$.totalElements").value(3));
        inbox(bob, "role", "incoming", "status", "PROPOSED").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(open));
        inbox(bob, "role", "incoming", "status", "EXPIRED").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(lapsed))
                .andExpect(jsonPath("$.content[0].status").value("EXPIRED"));
        inbox(bob, "role", "incoming", "status", "REJECTED").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(rejected));
        inbox(bob, "role", "incoming", "status", "ACCEPTED").andExpect(jsonPath("$.totalElements").value(0));
        inbox(alice, "role", "outgoing", "status", "PROPOSED").andExpect(jsonPath("$.totalElements").value(1));
        inbox(alice, "role", "outgoing", "status", "EXPIRED").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void storedExpiredProposalsAreFoundByTheExpiredFilter() throws Exception {
        String id = aliceProposesToBob(1);
        jdbc.update("update proposal set status = 'EXPIRED' where id = ?::uuid", id);
        inbox(bob, "role", "incoming", "status", "EXPIRED").andExpect(jsonPath("$.totalElements").value(1));
        inbox(bob, "role", "incoming", "status", "PROPOSED").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void pagingIsClampedAndAHugePageIsEmpty() throws Exception {
        aliceProposesToBob(1);
        inbox(bob, "role", "incoming", "size", "100000").andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
        inbox(bob, "role", "incoming", "page", String.valueOf(Integer.MAX_VALUE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        inbox(bob, "role", "incoming", "size", "1").andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void roleIsRequiredAndValidated() throws Exception {
        aliceProposesToBob(1);
        inbox(bob).andExpect(status().isBadRequest());
        inbox(bob, "role", "sideways").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ROLE"));
        inbox(bob, "role", "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ROLE"));
        inbox(bob, "role", "INCOMING").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void unknownStatusValueIs400() throws Exception {
        inbox(bob, "role", "incoming", "status", "BOGUS").andExpect(status().isBadRequest());
    }

    @Test
    void listCardsNeverContainContactDetailsEvenWhenAccepted() throws Exception {
        jdbc.update("update users set contact_note = 'secret-note'");
        String id = aliceProposesToBob(1);
        jdbc.update("update proposal set status = 'ACCEPTED' where id = ?::uuid", id);
        for (JsonNode who : List.of(alice, bob)) {
            for (String role : List.of("incoming", "outgoing")) {
                String body = inbox(who, "role", role).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString();
                assertThat(body).doesNotContain("@example.com", "email", "contactNote", "secret-note", "postalCode");
            }
        }
    }

    @Test
    void otherUsersProposalsNeverAppear() throws Exception {
        aliceProposesToBob(1);
        inbox(carol, "role", "incoming").andExpect(jsonPath("$.totalElements").value(0));
        inbox(carol, "role", "outgoing").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void inboxRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/proposals").param("role", "incoming")).andExpect(status().isUnauthorized());
    }
}
