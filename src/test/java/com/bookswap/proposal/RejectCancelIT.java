package com.bookswap.proposal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RejectCancelIT extends ProposalTestBase {

    private String b1;
    private String a1;

    private String propose() throws Exception {
        b1 = addListed(bob, 1, "Book 1");
        a1 = addListed(alice, 3, "Book 3");
        return proposeOk(alice, List.of(b1), List.of(a1));
    }

    private org.springframework.test.web.servlet.ResultActions reject(com.fasterxml.jackson.databind.JsonNode who,
                                                                     String id, String body) throws Exception {
        var request = post("/api/proposals/" + id + "/reject").header("Authorization", bearer(who));
        return mvc.perform(body == null ? request : request.contentType(APPLICATION_JSON).content(body));
    }

    private String copyStatus(String copyId) {
        return jdbc.queryForObject("select status from copy where id = ?::uuid", String.class, copyId);
    }

    @Test
    void recipientRejectsWithAReasonTheProposerCanSee() throws Exception {
        String id = propose();
        reject(bob, id, objectMapper.writeValueAsString(Map.of("reason", "Not my taste"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        detail(alice, id).andExpect(jsonPath("$.rejectReason").value("Not my taste"));
        assertThat(copyStatus(b1)).isEqualTo("AVAILABLE");
        assertThat(copyStatus(a1)).isEqualTo("AVAILABLE");
    }

    @Test
    void rejectWithoutABodyWorks() throws Exception {
        String id = propose();
        reject(bob, id, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").doesNotExist());
    }

    @Test
    void rejectReasonOver500OrWithNulIs400AndChangesNothing() throws Exception {
        String id = propose();
        reject(bob, id, objectMapper.writeValueAsString(Map.of("reason", "x".repeat(501)))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROPOSAL"));
        reject(bob, id, objectMapper.writeValueAsString(Map.of("reason", "a\u0000b"))).andExpect(status().isBadRequest());
        detail(bob, id).andExpect(jsonPath("$.status").value("PROPOSED"));
        reject(bob, id, objectMapper.writeValueAsString(Map.of("reason", "x".repeat(500)))).andExpect(status().isOk());
    }

    @Test
    void proposerCancelsAndNothingElseChanges() throws Exception {
        String id = propose();
        action(alice, id, "cancel").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelCause").value("WITHDRAWN"));
        assertThat(copyStatus(b1)).isEqualTo("AVAILABLE");
        assertThat(copyStatus(a1)).isEqualTo("AVAILABLE");
    }

    @Test
    void recipientCannotCancelAndProposerCannotReject() throws Exception {
        String id = propose();
        action(bob, id, "cancel").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROPOSAL_ACTION_NOT_ALLOWED"));
        reject(alice, id, null).andExpect(status().isForbidden());
        detail(alice, id).andExpect(jsonPath("$.status").value("PROPOSED"));
    }

    @Test
    void strangersGet404OnRejectAndCancel() throws Exception {
        String id = propose();
        reject(carol, id, null).andExpect(status().isNotFound());
        action(carol, id, "cancel").andExpect(status().isNotFound());
    }

    @Test
    void cancellingAfterAcceptanceIsNotOpenUntilStep5() throws Exception {
        String id = propose();
        action(bob, id, "accept").andExpect(status().isOk());
        action(alice, id, "cancel").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_OPEN"));
        assertThat(copyStatus(b1)).isEqualTo("RESERVED");
    }

    @Test
    void actionsOnClosedProposalsAreNotOpen() throws Exception {
        String id = propose();
        action(alice, id, "cancel").andExpect(status().isOk());
        action(alice, id, "cancel").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_OPEN"));
        reject(bob, id, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROPOSAL_NOT_OPEN"));
        action(bob, id, "accept").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROPOSAL_NOT_OPEN"));
    }
}
