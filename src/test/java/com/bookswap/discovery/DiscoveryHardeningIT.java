package com.bookswap.discovery;

import com.bookswap.support.MarketplaceTestBase;
import com.bookswap.book.catalog.BookDetails;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static com.bookswap.support.Isbns.valid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DiscoveryHardeningIT extends MarketplaceTestBase {

    @Autowired EntityManagerFactory emf;

    /** Twelve listed copies from bob: even ISBNs are catalog-backed, odd ones are manual (no shared book row). */
    private void twelveMixedCopies() throws Exception {
        for (int i = 1; i <= 12; i++) {
            boolean manual = i % 2 == 1;
            catalog.down(manual);
            if (!manual) {
                catalog.put(valid(i), new BookDetails("Sample " + i, "Author " + i, null));
            }
            Map<String, Object> body = manual
                    ? Map.of("isbn", valid(i), "grade", "GOOD", "title", "Sample " + i, "author", "Author " + i)
                    : Map.of("isbn", valid(i), "grade", "GOOD");
            String id = addCopy(bob, body);
            mvc.perform(post("/api/copies/" + id + "/list").header("Authorization", bearer(bob))).andExpect(status().isOk());
        }
        catalog.down(false);
        assertThat(jdbc.queryForObject("select count(*) from book", Integer.class)).isEqualTo(6);
    }

    private long statements(Statistics stats, ResultActions call, int size) throws Exception {
        call.andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(size))
                .andExpect(jsonPath("$.content[0].owner.displayName").value("Bob"));
        return stats.getPrepareStatementCount();
    }

    @Test
    void statementCountDoesNotGrowWithPageSizeOnFeedAndSearch() throws Exception {
        twelveMixedCopies();
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();

        stats.clear();
        long feedSmall = statements(stats, feed(alice, "size", "5"), 5);
        stats.clear();
        long feedLarge = statements(stats, feed(alice, "size", "10"), 10);
        stats.clear();
        long searchSmall = statements(stats, search(alice, "q", "sample", "size", "5"), 5);
        stats.clear();
        long searchLarge = statements(stats, search(alice, "q", "sample", "size", "10"), 10);

        assertThat(feedLarge).as("feed: size 10 vs size 5").isEqualTo(feedSmall);
        assertThat(searchLarge).as("search: size 10 vs size 5").isEqualTo(searchSmall);
        // alice's city comes from her profile, so one extra read is allowed on top of the page and count queries
        assertThat(feedSmall).as("feed: city lookup + page + count").isLessThanOrEqualTo(3);
        assertThat(searchSmall).as("search: city lookup + page + count").isLessThanOrEqualTo(3);
    }

    @Test
    void openApiListsFeedAndSearch() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/feed']").exists())
                .andExpect(jsonPath("$.paths['/api/search']").exists());
    }
}
