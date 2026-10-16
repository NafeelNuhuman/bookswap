package com.bookswap.book;

import com.bookswap.AbstractIntegrationTest;
import com.bookswap.book.catalog.BookDetails;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static com.bookswap.support.Isbns.FOX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookLookupIT extends AbstractIntegrationTest {

    private static final BookDetails FOX_DETAILS =
            new BookDetails("Fantastic Mr Fox", "Roald Dahl", "https://covers.example/fox.jpg");

    @Test
    void lookupReturnsCatalogDetailsAndSavesNothing() throws Exception {
        JsonNode tokens = registerAndLogin("l1@example.com", "password123");
        catalog.put(FOX, FOX_DETAILS);

        mvc.perform(get("/api/books/lookup").param("isbn", "978-0-14-032872-1").header("Authorization", bearer(tokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isbn").value(FOX))
                .andExpect(jsonPath("$.title").value("Fantastic Mr Fox"))
                .andExpect(jsonPath("$.author").value("Roald Dahl"))
                .andExpect(jsonPath("$.coverUrl").value("https://covers.example/fox.jpg"))
                .andExpect(jsonPath("$.source").value("CATALOG"));

        assertThat(jdbc.queryForObject("select count(*) from book", Integer.class)).isZero();
    }

    @Test
    void isbn10WithLowercaseXIsNormalized() throws Exception {
        JsonNode tokens = registerAndLogin("l2@example.com", "password123");
        catalog.put("9780804429573", new BookDetails("Some Title", "Some Author", null));

        mvc.perform(get("/api/books/lookup").param("isbn", "080442957x").header("Authorization", bearer(tokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isbn").value("9780804429573"));
    }

    @Test
    void invalidIsbnsAreA400() throws Exception {
        JsonNode tokens = registerAndLogin("l3@example.com", "password123");
        for (String bad : new String[]{"9780306406158", "abc", " ", "97801\u00003287", "9".repeat(200)}) {
            mvc.perform(get("/api/books/lookup").param("isbn", bad).header("Authorization", bearer(tokens)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_ISBN"));
        }
        mvc.perform(get("/api/books/lookup").header("Authorization", bearer(tokens)))
                .andExpect(status().isBadRequest());
        assertThat(catalog.calls()).isZero();
    }

    @Test
    void unknownBookIs404AndUnavailableCatalogIs503() throws Exception {
        JsonNode tokens = registerAndLogin("l4@example.com", "password123");
        mvc.perform(get("/api/books/lookup").param("isbn", FOX).header("Authorization", bearer(tokens)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));

        catalog.down(true);
        mvc.perform(get("/api/books/lookup").param("isbn", FOX).header("Authorization", bearer(tokens)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
    }

    @Test
    void lookupRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/books/lookup").param("isbn", FOX)).andExpect(status().isUnauthorized());
    }
}
