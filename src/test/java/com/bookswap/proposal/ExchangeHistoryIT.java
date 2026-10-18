package com.bookswap.proposal;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExchangeHistoryIT extends ProposalTestBase {

    @Autowired EntityManagerFactory emf;

    private int next = 1;

    /** Alice gives one book and gets one from bob, both confirm. Returns {aliceGave, aliceGot, proposalId}. */
    private String[] completedSwap() throws Exception {
        int n = next++;
        String mine = addListed(alice, n, "Alice book " + n);
        String theirs = addListed(bob, n + 500, "Bob book " + n);
        String id = proposeOk(alice, List.of(theirs), List.of(mine));
        action(bob, id, "accept").andExpect(status().isOk());
        action(alice, id, "confirm").andExpect(status().isOk());
        action(bob, id, "confirm").andExpect(status().isOk());
        return new String[]{mine, theirs, id};
    }

    private ResultActions history(com.fasterxml.jackson.databind.JsonNode who, String... params) throws Exception {
        var request = get("/api/exchanges").header("Authorization", bearer(who));
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request);
    }

    @Test
    void completedExchangesAreListedNewestFirstForBothParties() throws Exception {
        String[] first = completedSwap();
        String[] second = completedSwap();
        jdbc.update("update proposal set completed_at = now() - interval '1 day' where id = ?::uuid", first[2]);

        for (var who : List.of(alice, bob)) {
            history(who).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content[0].proposalId").value(second[2]))
                    .andExpect(jsonPath("$.content[1].proposalId").value(first[2]))
                    .andExpect(jsonPath("$.content[0].completedAt").isNotEmpty());
        }
        history(carol).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void gaveAndReceivedAreFromTheCallersPointOfView() throws Exception {
        String[] swap = completedSwap();
        history(alice).andExpect(jsonPath("$.content[0].with.displayName").value("Bob"))
                .andExpect(jsonPath("$.content[0].gave[0].title").value("Alice book 1"))
                .andExpect(jsonPath("$.content[0].received[0].title").value("Bob book 1"));
        history(bob).andExpect(jsonPath("$.content[0].with.displayName").value("Alice"))
                .andExpect(jsonPath("$.content[0].gave[0].title").value("Bob book 1"))
                .andExpect(jsonPath("$.content[0].received[0].title").value("Alice book 1"));
        assertThat(swap).hasSize(3);
    }

    @Test
    void onlyCompletedExchangesAppear() throws Exception {
        completedSwap();
        String b = addListed(bob, 900, "Open");
        String a = addListed(alice, 901, "Open too");
        String proposed = proposeOk(alice, List.of(b), List.of(a));
        String b2 = addListed(bob, 902, "Accepted");
        String a2 = addListed(alice, 903, "Accepted too");
        String accepted = proposeOk(alice, List.of(b2), List.of(a2));
        action(bob, accepted, "accept").andExpect(status().isOk());
        action(alice, proposed, "cancel").andExpect(status().isOk());

        history(alice).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void historyCarriesNoContactDetails() throws Exception {
        completedSwap();
        String body = history(alice).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("bob@example.com").doesNotContain("email").doesNotContain("contactNote");
    }

    @Test
    void historyKeepsItsItemsAfterTheNewOwnerRemovesABook() throws Exception {
        String[] swap = completedSwap();
        mvc.perform(delete("/api/copies/" + swap[1]).header("Authorization", bearer(alice))).andExpect(status().isNoContent());

        history(alice).andExpect(jsonPath("$.content[0].received[0].title").value("Bob book 1"))
                .andExpect(jsonPath("$.content[0].received[0].author").value("Author 501"));
        history(bob).andExpect(jsonPath("$.content[0].gave[0].title").value("Bob book 1"));
    }

    @Test
    void pagingIsClampedAndWorks() throws Exception {
        for (int i = 0; i < 3; i++) {
            completedSwap();
        }
        history(alice, "size", "2").andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        history(alice, "size", "2", "page", "1").andExpect(jsonPath("$.content.length()").value(1));
        history(alice, "size", "100000").andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
        history(alice, "page", "-4").andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void requiresAToken() throws Exception {
        mvc.perform(get("/api/exchanges")).andExpect(status().isUnauthorized());
    }

    @Test
    void statementCountDoesNotGrowWithPageSize() throws Exception {
        for (int i = 0; i < 8; i++) {
            completedSwap();
        }
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        history(alice, "size", "3").andExpect(jsonPath("$.content.length()").value(3));
        long small = stats.getPrepareStatementCount();
        stats.clear();
        history(alice, "size", "8").andExpect(jsonPath("$.content.length()").value(8));
        long large = stats.getPrepareStatementCount();
        assertThat(large).isEqualTo(small);
    }
}
