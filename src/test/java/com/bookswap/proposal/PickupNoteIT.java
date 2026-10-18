package com.bookswap.proposal;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PickupNoteIT extends ProposalTestBase {

    private ResultActions noteJson(JsonNode caller, String id, String json) throws Exception {
        return mvc.perform(put("/api/proposals/" + id + "/pickup-note").header("Authorization", bearer(caller))
                .contentType(APPLICATION_JSON).content(json));
    }

    private ResultActions note(JsonNode caller, String id, String text) throws Exception {
        return noteJson(caller, id, objectMapper.writeValueAsString(Map.of("note", text)));
    }

    private String proposed() throws Exception {
        return proposeOk(alice, List.of(addListed(bob, 1, "Book 1")), List.of(addListed(alice, 3, "Book 3")));
    }

    private String accepted() throws Exception {
        String id = proposed();
        action(bob, id, "accept").andExpect(status().isOk());
        return id;
    }

    @Test
    void eitherPartySetsAndEditsItAndBothSeeIt() throws Exception {
        String id = accepted();
        note(alice, id, "Cafe Einstein, Saturday 3pm").andExpect(status().isOk())
                .andExpect(jsonPath("$.pickupNote").value("Cafe Einstein, Saturday 3pm"));
        note(bob, id, "  Make it 4pm  ").andExpect(status().isOk())
                .andExpect(jsonPath("$.pickupNote").value("Make it 4pm"));
        detail(alice, id).andExpect(jsonPath("$.pickupNote").value("Make it 4pm"));
        detail(bob, id).andExpect(jsonPath("$.pickupNote").value("Make it 4pm"));
    }

    @Test
    void aBlankNoteClears() throws Exception {
        String id = accepted();
        note(alice, id, "x").andExpect(status().isOk());
        note(alice, id, "   ").andExpect(status().isOk()).andExpect(jsonPath("$.pickupNote").doesNotExist());
    }

    @Test
    void aMissingNoteFieldClears() throws Exception {
        String id = accepted();
        note(alice, id, "x").andExpect(status().isOk());
        noteJson(alice, id, "{}").andExpect(status().isOk()).andExpect(jsonPath("$.pickupNote").doesNotExist());
    }

    @Test
    void aStrangerGets404AndNeverSeesTheNote() throws Exception {
        String id = accepted();
        note(alice, id, "secret place").andExpect(status().isOk());
        note(carol, id, "hijack").andExpect(status().isNotFound());
        detail(carol, id).andExpect(status().isNotFound());
        detail(alice, id).andExpect(jsonPath("$.pickupNote").value("secret place"));
    }

    @Test
    void listCardsNeverCarryTheNote() throws Exception {
        String id = accepted();
        note(alice, id, "secret place").andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/proposals")
                        .param("role", "incoming").header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$.content[0].pickupNote").doesNotExist());
    }

    @Test
    void onlyAnAcceptedExchangeTakesANote() throws Exception {
        String open = proposed();
        note(alice, open, "x").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROPOSAL_NOT_OPEN"));

        String id = accepted();
        action(alice, id, "confirm").andExpect(status().isOk());
        action(bob, id, "confirm").andExpect(status().isOk());
        note(alice, id, "x").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROPOSAL_NOT_OPEN"));
    }

    @Test
    void theNoteStaysVisibleAfterCompletion() throws Exception {
        String id = accepted();
        note(alice, id, "Cafe").andExpect(status().isOk());
        action(alice, id, "confirm").andExpect(status().isOk());
        action(bob, id, "confirm").andExpect(status().isOk());
        detail(bob, id).andExpect(jsonPath("$.pickupNote").value("Cafe"));
    }

    @Test
    void tooLongOrNulNotesAreRejected() throws Exception {
        String id = accepted();
        note(alice, id, "x".repeat(501)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROPOSAL"));
        note(alice, id, "bad\0note").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROPOSAL"));
        note(alice, id, "x".repeat(500)).andExpect(status().isOk());
    }

    @Test
    void aNoteAfterTheDeadlineIsNotOpen() throws Exception {
        String id = accepted();
        jdbc.update("update proposal set confirm_by = now() - interval '1 minute' where id = ?::uuid", id);
        note(alice, id, "x").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROPOSAL_NOT_OPEN"));
    }
}
