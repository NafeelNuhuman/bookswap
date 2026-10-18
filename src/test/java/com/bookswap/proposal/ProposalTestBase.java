package com.bookswap.proposal;

import com.bookswap.support.MarketplaceTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Alice and bob (Berlin) and carol (Munich) from the marketplace base, plus helpers to drive proposals over HTTP. */
abstract class ProposalTestBase extends MarketplaceTestBase {

    ResultActions propose(JsonNode caller, List<String> requested, List<String> offered, String message) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("requestedCopyIds", requested);
        body.put("offeredCopyIds", offered);
        if (message != null) {
            body.put("message", message);
        }
        return proposeRaw(caller, objectMapper.writeValueAsString(body));
    }

    ResultActions proposeRaw(JsonNode caller, String json) throws Exception {
        return mvc.perform(post("/api/proposals").header("Authorization", bearer(caller))
                .contentType(APPLICATION_JSON).content(json));
    }

    /** Proposes without a message, asserts 201 and returns the new proposal's id. */
    String proposeOk(JsonNode caller, List<String> requested, List<String> offered) throws Exception {
        return read(propose(caller, requested, offered, null).andExpect(status().isCreated())).get("id").asText();
    }

    ResultActions counter(JsonNode caller, String id, List<String> requested, List<String> offered) throws Exception {
        return mvc.perform(post("/api/proposals/" + id + "/counter").header("Authorization", bearer(caller))
                .contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(
                        Map.of("requestedCopyIds", requested, "offeredCopyIds", offered))));
    }

    ResultActions action(JsonNode caller, String id, String verb) throws Exception {
        return mvc.perform(post("/api/proposals/" + id + "/" + verb).header("Authorization", bearer(caller)));
    }

    ResultActions detail(JsonNode caller, String id) throws Exception {
        return mvc.perform(get("/api/proposals/" + id).header("Authorization", bearer(caller)));
    }

    String userId(JsonNode tokens) throws Exception {
        return read(mvc.perform(get("/api/users/me").header("Authorization", bearer(tokens)))
                .andExpect(status().isOk())).get("id").asText();
    }
}
