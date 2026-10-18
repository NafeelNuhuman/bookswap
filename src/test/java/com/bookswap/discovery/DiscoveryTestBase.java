package com.bookswap.discovery;

import com.bookswap.AbstractIntegrationTest;
import com.bookswap.book.catalog.BookDetails;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static com.bookswap.support.Isbns.valid;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shared setup for the discovery ITs: alice and bob in Berlin, carol in Munich, and helpers to list copies. */
abstract class DiscoveryTestBase extends AbstractIntegrationTest {

    JsonNode alice;   // Berlin, the caller in most tests
    JsonNode bob;     // Berlin
    JsonNode carol;   // Munich

    @BeforeEach
    void users() throws Exception {
        alice = registerAndLogin("alice@example.com", "password123");
        bob = registerAndLogin("bob@example.com", "password123");
        carol = registerAndLogin("carol@example.com", "password123");
        completeProfile(alice, "Alice", "Berlin");
        completeProfile(bob, "Bob", "Berlin");
        completeProfile(carol, "Carol", "Munich");
    }

    /** Adds a catalog-backed copy of ISBN number n and lists it; returns the copy id. */
    String addListed(JsonNode owner, int n, String title) throws Exception {
        catalog.put(valid(n), new BookDetails(title, "Author " + n, "https://covers.example/" + n + ".jpg"));
        String id = addCopy(owner, Map.of("isbn", valid(n), "grade", "GOOD"));
        mvc.perform(post("/api/copies/" + id + "/list").header("Authorization", bearer(owner))).andExpect(status().isOk());
        return id;
    }

    String addCopy(JsonNode owner, Map<String, Object> body) throws Exception {
        return read(mvc.perform(post("/api/copies").header("Authorization", bearer(owner))
                        .contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())).get("id").asText();
    }

    ResultActions feed(JsonNode caller, String... params) throws Exception {
        var request = get("/api/feed").header("Authorization", bearer(caller));
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request);
    }

    ResultActions search(JsonNode caller, String... params) throws Exception {
        var request = get("/api/search").header("Authorization", bearer(caller));
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request);
    }
}
