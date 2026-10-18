package com.bookswap.proposal;

import com.bookswap.book.catalog.BookDetails;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.bookswap.support.Isbns.valid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProposalHardeningIT extends ProposalTestBase {

    @Autowired EntityManagerFactory emf;

    /** A listed copy for ISBN number n: catalog-backed when {@code manual} is false, otherwise with typed details. */
    private String listedCopy(JsonNode owner, int n, boolean manual) throws Exception {
        catalog.down(manual);
        if (!manual) {
            catalog.put(valid(n), new BookDetails("Sample " + n, "Author " + n, null));
        }
        Map<String, Object> body = manual
                ? Map.of("isbn", valid(n), "grade", "GOOD", "title", "Sample " + n, "author", "Author " + n)
                : Map.of("isbn", valid(n), "grade", "GOOD");
        String id = addCopy(owner, body);
        catalog.down(false);
        mvc.perform(post("/api/copies/" + id + "/list").header("Authorization", bearer(owner))).andExpect(status().isOk());
        return id;
    }

    private ResultActions inbox(JsonNode who, int size) throws Exception {
        return mvc.perform(get("/api/proposals").header("Authorization", bearer(who))
                .param("role", "incoming").param("size", String.valueOf(size)));
    }

    private long statements(Statistics stats, ResultActions call, int size) throws Exception {
        call.andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(size))
                .andExpect(jsonPath("$.content[0].proposer.displayName").value("Alice"));
        return stats.getPrepareStatementCount();
    }

    @Test
    void inboxStatementCountDoesNotGrowWithPageSize() throws Exception {
        for (int i = 1; i <= 12; i++) {
            boolean manual = i % 2 == 1;
            proposeOk(alice, List.of(listedCopy(bob, i, manual)), List.of(listedCopy(alice, 100 + i, !manual)));
        }
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();

        stats.clear();
        long small = statements(stats, inbox(bob, 5), 5);
        stats.clear();
        long large = statements(stats, inbox(bob, 10), 10);

        assertThat(large).as("size 10 vs size 5").isEqualTo(small);
        assertThat(small).as("proposals + count + items + users").isLessThanOrEqualTo(5);
    }

    @Test
    void openApiListsTheProposalAndShelfPaths() throws Exception {
        var docs = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        for (String path : List.of("/api/proposals", "/api/proposals/{id}", "/api/proposals/{id}/accept",
                "/api/proposals/{id}/reject", "/api/proposals/{id}/cancel", "/api/proposals/{id}/counter",
                "/api/users/{id}/copies")) {
            docs.andExpect(jsonPath("$.paths['" + path + "']").exists());
        }
    }

    @Test
    void everyProposalEndpointRequiresAuthentication() throws Exception {
        String id = UUID.randomUUID().toString();
        mvc.perform(get("/api/proposals").param("role", "incoming")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/proposals/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/proposals").contentType(APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        for (String verb : List.of("accept", "reject", "cancel", "counter")) {
            mvc.perform(post("/api/proposals/" + id + "/" + verb).contentType(APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void malformedBodiesAre400NotA500() throws Exception {
        String id = proposeOk(alice, List.of(addListed(bob, 1, "Book 1")), List.of(addListed(alice, 3, "Book 3")));
        proposeRaw(alice, "{not json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        proposeRaw(alice, "").andExpect(status().isBadRequest());
        proposeRaw(alice, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PROPOSAL"));
        proposeRaw(alice, "{\"requestedCopyIds\":\"x\",\"offeredCopyIds\":1}").andExpect(status().isBadRequest());
        mvc.perform(post("/api/proposals/" + id + "/counter").header("Authorization", bearer(bob))
                        .contentType(APPLICATION_JSON).content("[]"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/proposals/" + id + "/reject").header("Authorization", bearer(bob))
                        .contentType(APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
        detail(bob, id).andExpect(jsonPath("$.status").value("PROPOSED"));
    }

    @Test
    void aHugeMessageIs400NotA500() throws Exception {
        String bobBook = addListed(bob, 1, "Book 1");
        String aliceBook = addListed(alice, 3, "Book 3");
        propose(alice, List.of(bobBook), List.of(aliceBook), "x".repeat(200_000)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROPOSAL"));
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            many.add(UUID.randomUUID().toString());
        }
        propose(alice, many, List.of(aliceBook), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROPOSAL"));
    }
}
