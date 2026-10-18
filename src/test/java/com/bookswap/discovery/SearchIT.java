package com.bookswap.discovery;

import com.bookswap.support.MarketplaceTestBase;
import com.bookswap.book.catalog.BookDetails;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.bookswap.support.Isbns.FOX;
import static com.bookswap.support.Isbns.valid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SearchIT extends MarketplaceTestBase {

    /** Lists a catalog copy of an arbitrary ISBN with the given title and author. */
    private String listedWith(JsonNode owner, String isbn, String title, String author) throws Exception {
        catalog.put(isbn, new BookDetails(title, author, null));
        String id = addCopy(owner, Map.of("isbn", isbn, "grade", "GOOD"));
        mvc.perform(post("/api/copies/" + id + "/list").header("Authorization", bearer(owner))).andExpect(status().isOk());
        return id;
    }

    @Test
    void partialTitleAndAuthorMatchIsCaseInsensitive() throws Exception {
        String fox = listedWith(bob, FOX, "Fantastic Mr Fox", "Roald Dahl");
        listedWith(bob, valid(2), "Matilda", "Roald Dahl");

        search(alice, "q", "fox").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].copyId").value(fox));
        search(alice, "q", "ANTASTIC mr").andExpect(jsonPath("$.totalElements").value(1));
        search(alice, "q", "dahl").andExpect(jsonPath("$.totalElements").value(2));
        search(alice, "q", "  DAHL  ").andExpect(jsonPath("$.totalElements").value(2));
        search(alice, "q", "tolkien").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void isbn13AndIsbn10SpellingsFindTheSameCopyExactly() throws Exception {
        String fox = listedWith(bob, FOX, "Fantastic Mr Fox", "Roald Dahl");

        for (String q : new String[]{"9780140328721", "978-0-14-032872-1", "0140328726", "0-14-032872-6"}) {
            search(alice, "q", q).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].copyId").value(fox));
        }
        search(alice, "q", "97801").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void typedTitlesOnManualCopiesAreSearchableUntilTheCatalogTakesOver() throws Exception {
        catalog.down(true);
        String manual = addCopy(bob, Map.of("isbn", FOX, "grade", "GOOD", "title", "fantsastic mr fox", "author", "roald dal"));
        catalog.down(false);
        mvc.perform(post("/api/copies/" + manual + "/list").header("Authorization", bearer(bob))).andExpect(status().isOk());

        search(alice, "q", "fantsastic").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].detailsSource").value("MANUAL"));

        // carol's catalog-backed add of the same ISBN creates the shared book, which now wins for bob's copy too
        catalog.put(FOX, new BookDetails("Fantastic Mr Fox", "Roald Dahl", null));
        addCopy(carol, Map.of("isbn", FOX, "grade", "GOOD"));
        search(alice, "q", "fantsastic").andExpect(jsonPath("$.totalElements").value(0));
        search(alice, "q", "fantastic").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].copyId").value(manual))
                .andExpect(jsonPath("$.content[0].detailsSource").value("CATALOG"));
    }

    @Test
    void caseInsensitiveSearchWorksForNonAsciiLetters() throws Exception {
        listedWith(bob, valid(1), "İstanbul Guide", "A");                  // capital dotted I
        listedWith(bob, valid(2), "ΟΔΥΣ ΣΟΦΟΣ", "B"); // upper-case Greek, ends in capital sigma

        search(alice, "q", "İSTANBUL").andExpect(jsonPath("$.totalElements").value(1));
        search(alice, "q", "istanbul").andExpect(jsonPath("$.totalElements").value(1));
        // the query ends in a capital sigma: Java would fold it to a final sigma, PostgreSQL to a plain one
        search(alice, "q", "ΟΔΥΣ").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void likeWildcardsAreLiteral() throws Exception {
        listedWith(bob, valid(1), "100% Sure", "A");
        listedWith(bob, valid(2), "Fooxbar", "B");
        listedWith(bob, valid(3), "Foo_bar", "C");

        search(alice, "q", "%").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("100% Sure"));
        search(alice, "q", "foo_bar").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Foo_bar"));
        search(alice, "q", "\\").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void invalidQueriesAre400() throws Exception {
        mvc.perform(get("/api/search").header("Authorization", bearer(alice))).andExpect(status().isBadRequest());
        for (String bad : new String[]{"", "   ", "x".repeat(101), "a\u0000b"}) {
            search(alice, "q", bad).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        }
        search(alice, "q", "x".repeat(100)).andExpect(status().isOk());
    }

    @Test
    void searchAppliesTheSameVisibilityAndLocationRulesAsTheFeed() throws Exception {
        listedWith(bob, valid(1), "Visible Book", "A");
        listedWith(carol, valid(2), "Munich Book", "A");
        listedWith(alice, valid(3), "Own Book", "A");
        catalog.put(valid(4), new BookDetails("Unlisted Book", "A", null));
        addCopy(bob, Map.of("isbn", valid(4), "grade", "GOOD"));
        String reserved = listedWith(bob, valid(5), "Reserved Book", "A");
        jdbc.update("update copy set status = 'RESERVED' where id = ?::uuid", reserved);

        search(alice, "q", "book").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Visible Book"));
        search(alice, "q", "book", "city", "munich").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Munich Book"));

        JsonNode newcomer = registerAndLogin("new@example.com", "password123");
        search(newcomer, "q", "book").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LOCATION_REQUIRED"));
        search(newcomer, "q", "book", "city", "Berlin").andExpect(status().isOk());
        search(alice, "q", "book", "city", "x".repeat(101)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LOCATION"));
    }

    @Test
    void searchCardsNeverContainPrivateFields() throws Exception {
        listedWith(bob, FOX, "Fantastic Mr Fox", "Roald Dahl");
        String body = search(alice, "q", "fox").andExpect(jsonPath("$.content[0].owner.displayName").value("Bob"))
                .andExpect(jsonPath("$.content[0].owner.city").value("Berlin"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("@example.com", "postalCode", "email", "10115");
    }

    @Test
    void pagingIsClampedAndHugePageNumbersAreFine() throws Exception {
        listedWith(bob, FOX, "Fantastic Mr Fox", "Roald Dahl");
        search(alice, "q", "fox", "size", "100000").andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
        search(alice, "q", "fox", "page", String.valueOf(Integer.MAX_VALUE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void searchRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/search").param("q", "fox")).andExpect(status().isUnauthorized());
    }
}
