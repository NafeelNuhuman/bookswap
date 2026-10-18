package com.bookswap.discovery;

import com.bookswap.AbstractIntegrationTest;
import com.bookswap.book.catalog.BookDetails;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static com.bookswap.support.Isbns.valid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeedIT extends DiscoveryTestBase {

    @Test
    void feedShowsOnlyOtherPeoplesAvailableCopiesInTheCallersCity() throws Exception {
        addListed(bob, 1, "Book 1");
        addListed(bob, 2, "Book 2");
        catalog.put(valid(3), new BookDetails("Book 3", "Author 3", null));
        addCopy(bob, Map.of("isbn", valid(3), "grade", "GOOD"));                    // stays UNLISTED
        String reserved = addListed(bob, 4, "Book 4");
        jdbc.update("update copy set status = 'RESERVED' where id = ?::uuid", reserved);
        addListed(carol, 5, "Book 5");                                              // Munich
        addListed(alice, 6, "Book 6");                                              // her own

        feed(alice).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Book 2"))          // newest first
                .andExpect(jsonPath("$.content[1].title").value("Book 1"));
    }

    @Test
    void cardShowsBookGradeAndOwnerAreaButNothingPrivate() throws Exception {
        String id = addListed(bob, 1, "Book 1");

        String body = feed(alice).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].copyId").value(id))
                .andExpect(jsonPath("$.content[0].isbn").value(valid(1)))
                .andExpect(jsonPath("$.content[0].title").value("Book 1"))
                .andExpect(jsonPath("$.content[0].author").value("Author 1"))
                .andExpect(jsonPath("$.content[0].coverUrl").value("https://covers.example/1.jpg"))
                .andExpect(jsonPath("$.content[0].grade").value("GOOD"))
                .andExpect(jsonPath("$.content[0].detailsSource").value("CATALOG"))
                .andExpect(jsonPath("$.content[0].owner.displayName").value("Bob"))
                .andExpect(jsonPath("$.content[0].owner.city").value("Berlin"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("@example.com", "postalCode", "email", "10115");
        assertThat(read(feed(alice)).get("content").get(0).get("owner").size()).as("owner has exactly two fields").isEqualTo(2);
    }

    @Test
    void manualAndCatalogCopiesShowTheirOwnTitlesAndSource() throws Exception {
        addListed(bob, 1, "Catalog Title");
        catalog.down(true);
        String manual = addCopy(bob, Map.of("isbn", valid(2), "grade", "POOR", "title", "Typed Title", "author", "Typist"));
        catalog.down(false);
        mvc.perform(post("/api/copies/" + manual + "/list").header("Authorization", bearer(bob))).andExpect(status().isOk());

        feed(alice).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Typed Title"))
                .andExpect(jsonPath("$.content[0].detailsSource").value("MANUAL"))
                .andExpect(jsonPath("$.content[0].coverUrl").doesNotExist())
                .andExpect(jsonPath("$.content[1].title").value("Catalog Title"))
                .andExpect(jsonPath("$.content[1].detailsSource").value("CATALOG"));
    }

    @Test
    void cityDefaultsToTheCallersAndOverrideIsCaseAndWhitespaceInsensitive() throws Exception {
        addListed(bob, 1, "Berlin Book");
        addListed(carol, 2, "Munich Book");

        feed(alice).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Berlin Book"));
        feed(alice, "city", " mUNICH ").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Munich Book"));
        feed(alice, "city", "Hamburg").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void citiesWithNonAsciiCaseFoldingStillMatch() throws Exception {
        // Java lower-cases U+0130 (capital dotted I) to "i" + a combining dot, PostgreSQL to a plain "i".
        completeProfile(alice, "Alice", "İstanbul");
        completeProfile(bob, "Bob", "İstanbul");
        addListed(bob, 1, "Istanbul Book");

        feed(alice).andExpect(jsonPath("$.totalElements").value(1));                       // her own stored city
        feed(alice, "city", "İSTANBUL").andExpect(jsonPath("$.totalElements").value(1));
        feed(alice, "city", "istanbul").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void locationIsRequiredWhenTheCallerHasNoCityAndSendsNone() throws Exception {
        JsonNode newcomer = registerAndLogin("new@example.com", "password123");
        addListed(bob, 1, "Berlin Book");

        feed(newcomer).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LOCATION_REQUIRED"));
        feed(newcomer, "city", "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LOCATION_REQUIRED"));
        feed(newcomer, "city", "Berlin").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void blankCityParameterFallsBackToTheProfileCity() throws Exception {
        addListed(bob, 1, "Berlin Book");
        feed(alice, "city", "   ").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void nulOrOverLongCityIsA400NotA500() throws Exception {
        feed(alice, "city", "Ber\u0000lin").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LOCATION"));
        feed(alice, "city", "x".repeat(101)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LOCATION"));
    }

    @Test
    void pagingIsClamped() throws Exception {
        addListed(bob, 1, "Book 1");
        feed(alice, "size", "100000").andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
        feed(alice, "page", String.valueOf(Integer.MAX_VALUE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        feed(alice, "size", "1").andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void feedRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/feed")).andExpect(status().isUnauthorized());
    }
}
