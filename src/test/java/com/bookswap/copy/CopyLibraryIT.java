package com.bookswap.copy;

import com.bookswap.AbstractIntegrationTest;
import com.bookswap.book.catalog.BookDetails;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static com.bookswap.support.Isbns.FOX;
import static com.bookswap.support.Isbns.valid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CopyLibraryIT extends AbstractIntegrationTest {

    private static final BookDetails FOX_DETAILS = new BookDetails("Fantastic Mr Fox", "Roald Dahl", null);

    private ResultActions add(JsonNode tokens, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/copies").header("Authorization", bearer(tokens))
                .contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private String addOk(JsonNode tokens, String isbn, String grade) throws Exception {
        return read(add(tokens, Map.of("isbn", isbn, "grade", grade)).andExpect(status().isCreated())).get("id").asText();
    }

    private ResultActions send(MockHttpServletRequestBuilder b, JsonNode tokens) throws Exception {
        return mvc.perform(b.header("Authorization", bearer(tokens)));
    }

    @Test
    void addingByIsbnUsesCatalogDetailsAndStartsUnlisted() throws Exception {
        JsonNode tokens = registerAndLogin("a1@example.com", "password123");
        catalog.put(FOX, FOX_DETAILS);

        add(tokens, Map.of("isbn", "978-0-14-032872-1", "grade", "LIKE_NEW"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.isbn").value(FOX))
                .andExpect(jsonPath("$.title").value("Fantastic Mr Fox"))
                .andExpect(jsonPath("$.author").value("Roald Dahl"))
                .andExpect(jsonPath("$.grade").value("LIKE_NEW"))
                .andExpect(jsonPath("$.status").value("UNLISTED"));

        assertThat(jdbc.queryForObject("select count(*) from book", Integer.class)).isEqualTo(1);
    }

    @Test
    void localBookIsPreferredOnLaterLookupsAndAdds() throws Exception {
        JsonNode tokens = registerAndLogin("a2@example.com", "password123");
        catalog.put(FOX, FOX_DETAILS);
        addOk(tokens, FOX, "GOOD");
        int callsAfterFirstAdd = catalog.calls();

        mvc.perform(get("/api/books/lookup").param("isbn", FOX).header("Authorization", bearer(tokens)))
                .andExpect(jsonPath("$.source").value("LOCAL"));
        // a different title in the request must not rewrite the shared book
        add(tokens, Map.of("isbn", FOX, "grade", "POOR", "title", "Hacked", "author", "Mallory"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Fantastic Mr Fox"));

        assertThat(catalog.calls()).isEqualTo(callsAfterFirstAdd);
        assertThat(jdbc.queryForObject("select title from book where isbn13 = ?", String.class, FOX))
                .isEqualTo("Fantastic Mr Fox");
    }

    @Test
    void twoCopiesOfOneBookShareOneBookRowButAreSeparateCopies() throws Exception {
        JsonNode tokens = registerAndLogin("a3@example.com", "password123");
        catalog.put(FOX, FOX_DETAILS);
        String first = addOk(tokens, FOX, "GOOD");
        String second = addOk(tokens, FOX, "POOR");

        assertThat(first).isNotEqualTo(second);
        assertThat(jdbc.queryForObject("select count(*) from book", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from copy", Integer.class)).isEqualTo(2);
    }

    @Test
    void manualDetailsAreUsedWhenTheCatalogHasNothingOrIsDown() throws Exception {
        JsonNode tokens = registerAndLogin("a4@example.com", "password123");

        add(tokens, Map.of("isbn", valid(1), "grade", "GOOD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BOOK_DETAILS_REQUIRED"));
        add(tokens, Map.of("isbn", valid(1), "grade", "GOOD", "title", "Only a title"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BOOK_DETAILS_REQUIRED"));

        add(tokens, Map.of("isbn", valid(1), "grade", "GOOD", "title", " Hand Typed ", "author", "Me"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Hand Typed"))
                .andExpect(jsonPath("$.author").value("Me"));

        catalog.down(true);
        add(tokens, Map.of("isbn", valid(2), "grade", "GOOD", "title", "Offline", "author", "Me"))
                .andExpect(status().isCreated());
        add(tokens, Map.of("isbn", valid(3), "grade", "GOOD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BOOK_DETAILS_REQUIRED"));
    }

    @Test
    void manualDetailsStayOnTheCopyAndNeverEnterTheSharedBook() throws Exception {
        JsonNode alice = registerAndLogin("alice-m@example.com", "password123");
        JsonNode bob = registerAndLogin("bob-m@example.com", "password123");

        // The catalog is down, so Alice types the details herself.
        catalog.down(true);
        String aliceCopy = read(add(alice, Map.of("isbn", FOX, "grade", "GOOD", "title", "fantsastic mr fox", "author", "roald dal"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("fantsastic mr fox"))
                .andExpect(jsonPath("$.detailsSource").value("MANUAL"))).get("id").asText();
        assertThat(jdbc.queryForObject("select count(*) from book", Integer.class))
                .as("manual details must not create a shared book row").isZero();

        // Bob is unaffected by Alice's typos.
        send(get("/api/books/lookup").param("isbn", FOX), bob).andExpect(status().isServiceUnavailable());

        // Once the catalog knows the book, the first catalog-backed add creates the shared row ...
        catalog.down(false);
        catalog.put(FOX, FOX_DETAILS);
        add(bob, Map.of("isbn", FOX, "grade", "POOR"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Fantastic Mr Fox"))
                .andExpect(jsonPath("$.detailsSource").value("CATALOG"));

        // ... and Alice's copy now shows the catalog's details instead of her typed ones.
        send(get("/api/copies/" + aliceCopy), alice)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Fantastic Mr Fox"))
                .andExpect(jsonPath("$.author").value("Roald Dahl"))
                .andExpect(jsonPath("$.detailsSource").value("CATALOG"));
        send(get("/api/copies"), alice)
                .andExpect(jsonPath("$.content[0].title").value("Fantastic Mr Fox"));
        assertThat(jdbc.queryForObject("select title from copy where id = ?::uuid", String.class, aliceCopy))
                .as("the typed details are kept on the copy").isEqualTo("fantsastic mr fox");
    }

    @Test
    void invalidInputsGivePerFieldOrIsbnErrors() throws Exception {
        JsonNode tokens = registerAndLogin("a5@example.com", "password123");

        add(tokens, Map.of("isbn", "9780306406158", "grade", "GOOD"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ISBN"));
        add(tokens, Map.of("isbn", FOX))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='grade')]").exists());
        add(tokens, Map.of("grade", "GOOD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='isbn')]").exists());
        add(tokens, Map.of("isbn", FOX, "grade", "MINT"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        add(tokens, Map.of("isbn", valid(1), "grade", "GOOD", "title", "T\u0000x", "author", "A".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='title')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='author')]").exists());
        assertThat(jdbc.queryForObject("select count(*) from copy", Integer.class)).isZero();
    }

    @Test
    void libraryIsPaginatedFilteredByStatusAndShowsOnlyMyCopies() throws Exception {
        JsonNode me = registerAndLogin("me@example.com", "password123");
        JsonNode other = registerAndLogin("other@example.com", "password123");
        completeProfile(me);
        for (int i = 1; i <= 5; i++) {
            catalog.put(valid(i), new BookDetails("Book " + i, "Author", null));
        }
        catalog.put(valid(9), new BookDetails("Not mine", "Author", null));
        String id1 = addOk(me, valid(1), "GOOD");
        addOk(me, valid(2), "GOOD");
        addOk(me, valid(3), "GOOD");
        addOk(me, valid(4), "GOOD");
        addOk(me, valid(5), "GOOD");
        addOk(other, valid(9), "GOOD");
        send(post("/api/copies/" + id1 + "/list"), me).andExpect(status().isOk());

        send(get("/api/copies").param("size", "2"), me)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
        send(get("/api/copies").param("size", "2").param("page", "2"), me)
                .andExpect(jsonPath("$.content.length()").value(1));
        send(get("/api/copies").param("status", "AVAILABLE"), me)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id1));
        send(get("/api/copies").param("status", "UNLISTED"), me)
                .andExpect(jsonPath("$.totalElements").value(4));
        send(get("/api/copies").param("status", "RESERVED"), me)
                .andExpect(jsonPath("$.totalElements").value(0));
        send(get("/api/copies").param("status", "BOGUS"), me).andExpect(status().isBadRequest());
        send(get("/api/copies"), other).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void pageParametersAreClampedNotRejected() throws Exception {
        JsonNode tokens = registerAndLogin("clamp@example.com", "password123");
        catalog.put(FOX, FOX_DETAILS);
        addOk(tokens, FOX, "GOOD");

        send(get("/api/copies").param("size", "100000").param("page", "-3"), tokens)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.page").value(0));
        send(get("/api/copies").param("size", "0"), tokens)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1));
        send(get("/api/copies").param("size", "abc"), tokens).andExpect(status().isBadRequest());
    }

    @Test
    void hugePageNumbersDoNotOverflowTheOffsetIntoA500() throws Exception {
        JsonNode tokens = registerAndLogin("huge@example.com", "password123");
        send(get("/api/copies").param("page", String.valueOf(Integer.MAX_VALUE)), tokens)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        send(get("/api/copies").param("page", "21474837").param("size", "100"), tokens)
                .andExpect(status().isOk());
    }

    @Test
    void gradeCanBeEditedAndCopyViewedAndRemoved() throws Exception {
        JsonNode tokens = registerAndLogin("edit@example.com", "password123");
        catalog.put(FOX, FOX_DETAILS);
        String id = addOk(tokens, FOX, "GOOD");

        send(put("/api/copies/" + id).contentType(APPLICATION_JSON).content("{\"grade\":\"POOR\"}"), tokens)
                .andExpect(status().isOk()).andExpect(jsonPath("$.grade").value("POOR"));
        send(get("/api/copies/" + id), tokens)
                .andExpect(status().isOk()).andExpect(jsonPath("$.grade").value("POOR"));
        send(put("/api/copies/" + id).contentType(APPLICATION_JSON).content("{}"), tokens)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='grade')]").exists());

        send(delete("/api/copies/" + id), tokens).andExpect(status().isNoContent());
        send(get("/api/copies/" + id), tokens)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COPY_NOT_FOUND"));
        send(delete("/api/copies/" + id), tokens).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from book", Integer.class)).isEqualTo(1);
    }

    @Test
    void listingNeedsACompleteProfileAndUnlistingReverses() throws Exception {
        JsonNode tokens = registerAndLogin("gate@example.com", "password123");
        catalog.put(FOX, FOX_DETAILS);
        String id = addOk(tokens, FOX, "GOOD");

        send(post("/api/copies/" + id + "/list"), tokens)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_INCOMPLETE"));
        send(get("/api/copies/" + id), tokens).andExpect(jsonPath("$.status").value("UNLISTED"));

        completeProfile(tokens);
        send(post("/api/copies/" + id + "/list"), tokens)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AVAILABLE"));
        send(post("/api/copies/" + id + "/list"), tokens)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AVAILABLE"));
        send(post("/api/copies/" + id + "/unlist"), tokens)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNLISTED"));
    }

    @Test
    void anotherUsersCopyIsA404ForEveryOperation() throws Exception {
        JsonNode owner = registerAndLogin("owner@example.com", "password123");
        JsonNode intruder = registerAndLogin("intruder@example.com", "password123");
        completeProfile(intruder);
        catalog.put(FOX, FOX_DETAILS);
        String id = addOk(owner, FOX, "GOOD");

        send(get("/api/copies/" + id), intruder).andExpect(status().isNotFound());
        send(put("/api/copies/" + id).contentType(APPLICATION_JSON).content("{\"grade\":\"POOR\"}"), intruder)
                .andExpect(status().isNotFound());
        send(post("/api/copies/" + id + "/list"), intruder).andExpect(status().isNotFound());
        send(post("/api/copies/" + id + "/unlist"), intruder).andExpect(status().isNotFound());
        send(delete("/api/copies/" + id), intruder).andExpect(status().isNotFound());
        send(get("/api/copies/" + UUID.randomUUID()), intruder).andExpect(status().isNotFound());
        send(get("/api/copies/not-a-uuid"), intruder).andExpect(status().isBadRequest());

        send(get("/api/copies/" + id), owner)
                .andExpect(status().isOk()).andExpect(jsonPath("$.grade").value("GOOD"));
    }

    @Test
    void reservedCopyCannotBeEditedUnlistedListedOrRemoved() throws Exception {
        JsonNode tokens = registerAndLogin("res@example.com", "password123");
        completeProfile(tokens);
        catalog.put(FOX, FOX_DETAILS);
        String id = addOk(tokens, FOX, "GOOD");
        // RESERVED is set by the exchange flow in step 4; simulate it directly.
        jdbc.update("update copy set status = 'RESERVED' where id = ?::uuid", id);

        send(put("/api/copies/" + id).contentType(APPLICATION_JSON).content("{\"grade\":\"POOR\"}"), tokens)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COPY_RESERVED"));
        send(post("/api/copies/" + id + "/unlist"), tokens)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COPY_RESERVED"));
        send(post("/api/copies/" + id + "/list"), tokens)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COPY_RESERVED"));
        send(delete("/api/copies/" + id), tokens)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COPY_RESERVED"));

        assertThat(jdbc.queryForObject("select grade || '/' || status from copy where id = ?::uuid", String.class, id))
                .isEqualTo("GOOD/RESERVED");
    }

    @Test
    void conditionGradesExposeTheFiveLevelsWithCriteria() throws Exception {
        JsonNode tokens = registerAndLogin("grades@example.com", "password123");
        send(get("/api/condition-grades"), tokens)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].code").value("NEW"))
                .andExpect(jsonPath("$[0].label").value("New"))
                .andExpect(jsonPath("$[0].criteria").value("Unread, no marks, no wear"))
                .andExpect(jsonPath("$[4].code").value("POOR"));
    }

    @Test
    void everyLibraryEndpointRequiresAuthentication() throws Exception {
        String id = UUID.randomUUID().toString();
        mvc.perform(post("/api/copies").contentType(APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/copies")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/copies/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/copies/" + id).contentType(APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/copies/" + id + "/list")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/copies/" + id + "/unlist")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/copies/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/condition-grades")).andExpect(status().isUnauthorized());
    }
}
