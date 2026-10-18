package com.bookswap.proposal;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CopyChangeIT extends ProposalTestBase {

    private String b1;
    private String b2;
    private String a1;
    private String a2;

    @org.junit.jupiter.api.BeforeEach
    void copies() throws Exception {
        b1 = addListed(bob, 1, "Book 1");
        b2 = addListed(bob, 2, "Book 2");
        a1 = addListed(alice, 3, "Book 3");
        a2 = addListed(alice, 4, "Book 4");
    }

    private void regrade(JsonNode owner, String copyId, String grade) throws Exception {
        mvc.perform(put("/api/copies/" + copyId).header("Authorization", bearer(owner))
                .contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("grade", grade))))
                .andExpect(status().isOk());
    }

    private String proposalStatus(String id) {
        return jdbc.queryForObject("select status from proposal where id = ?::uuid", String.class, id);
    }

    @Test
    void unlistingAnOfferedCopyCancelsItsOpenProposalsOnly() throws Exception {
        String affected = proposeOk(alice, List.of(b1), List.of(a1));
        String unaffected = proposeOk(alice, List.of(b2), List.of(a2));

        mvc.perform(post("/api/copies/" + a1 + "/unlist").header("Authorization", bearer(alice)))
                .andExpect(status().isOk());

        detail(bob, affected).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelCause").value("COPY_UNAVAILABLE"));
        assertThat(proposalStatus(unaffected)).isEqualTo("PROPOSED");
    }

    @Test
    void regradingARequestedCopyCancelsItsOpenProposals() throws Exception {
        String id = proposeOk(alice, List.of(b1), List.of(a1));
        regrade(bob, b1, "POOR");
        detail(alice, id).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelCause").value("COPY_UNAVAILABLE"));
    }

    @Test
    void settingTheSameGradeKeepsTheProposalOpen() throws Exception {
        String id = proposeOk(alice, List.of(b1), List.of(a1));
        regrade(bob, b1, "GOOD");
        assertThat(proposalStatus(id)).isEqualTo("PROPOSED");
    }

    @Test
    void removingACopyCancelsProposalsAndDropsItsItemsOnly() throws Exception {
        String id = proposeOk(alice, List.of(b1, b2), List.of(a1));

        mvc.perform(delete("/api/copies/" + b1).header("Authorization", bearer(bob))).andExpect(status().isNoContent());

        detail(alice, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.requested.length()").value(1))
                .andExpect(jsonPath("$.requested[0].copyId").value(b2))
                .andExpect(jsonPath("$.offered.length()").value(1));
        assertThat(jdbc.queryForObject("select count(*) from proposal_item where copy_id = ?::uuid", Integer.class, b1)).isZero();
    }

    @Test
    void reservedCopiesOfAnAcceptedProposalCannotBeChangedSoTheProposalStays() throws Exception {
        String id = proposeOk(alice, List.of(b1), List.of(a1));
        action(bob, id, "accept").andExpect(status().isOk());

        mvc.perform(put("/api/copies/" + b1).header("Authorization", bearer(bob))
                        .contentType(APPLICATION_JSON).content("{\"grade\":\"POOR\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COPY_RESERVED"));
        mvc.perform(post("/api/copies/" + a1 + "/unlist").header("Authorization", bearer(alice)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COPY_RESERVED"));
        mvc.perform(delete("/api/copies/" + b1).header("Authorization", bearer(bob)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COPY_RESERVED"));
        assertThat(proposalStatus(id)).isEqualTo("ACCEPTED");
    }

    @Test
    void closedProposalsStayClosed() throws Exception {
        String rejected = proposeOk(alice, List.of(b1), List.of(a1));
        action(bob, rejected, "reject").andExpect(status().isOk());

        mvc.perform(post("/api/copies/" + a1 + "/unlist").header("Authorization", bearer(alice)))
                .andExpect(status().isOk());

        detail(bob, rejected).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.cancelCause").doesNotExist());
    }
}
