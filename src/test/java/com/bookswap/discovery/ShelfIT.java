package com.bookswap.discovery;

import com.bookswap.support.MarketplaceTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShelfIT extends MarketplaceTestBase {

    private String idOf(JsonNode tokens) throws Exception {
        return read(mvc.perform(get("/api/users/me").header("Authorization", bearer(tokens)))).get("id").asText();
    }

    private ResultActions shelf(JsonNode caller, String userId, String... params) throws Exception {
        var request = get("/api/users/" + userId + "/copies").header("Authorization", bearer(caller));
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request);
    }

    @Test
    void shelfShowsOnlyThatUsersAvailableCopiesNewestFirst() throws Exception {
        String first = addListed(bob, 1, "Book 1");
        String second = addListed(bob, 2, "Book 2");
        addListed(bob, 3, "Book 3");
        String unlisted = addListed(bob, 4, "Book 4");
        String reserved = addListed(bob, 5, "Book 5");
        addListed(carol, 6, "Not Bob's");
        jdbc.update("update copy set status = 'UNLISTED' where id = ?::uuid", unlisted);
        jdbc.update("update copy set status = 'RESERVED' where id = ?::uuid", reserved);
        jdbc.update("update copy set created_at = now() - interval '3 hours' where id = ?::uuid", first);
        jdbc.update("update copy set created_at = now() - interval '2 hours' where id = ?::uuid", second);

        shelf(alice, idOf(bob)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].title").value("Book 3"))
                .andExpect(jsonPath("$.content[1].copyId").value(second))
                .andExpect(jsonPath("$.content[2].copyId").value(first))
                .andExpect(jsonPath("$.content[0].owner.displayName").value("Bob"));
    }

    @Test
    void shelfWorksAcrossCities() throws Exception {
        addListed(carol, 6, "Munich Book");
        shelf(alice, idOf(carol)).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].owner.city").value("Munich"));
    }

    @Test
    void shelfOfAnUnknownUserIs404UserNotFound() throws Exception {
        shelf(alice, UUID.randomUUID().toString()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void malformedUserIdIs400() throws Exception {
        shelf(alice, "not-a-uuid").andExpect(status().isBadRequest());
    }

    @Test
    void ownShelfShowsOwnAvailableCopies() throws Exception {
        addListed(alice, 1, "Mine");
        shelf(alice, idOf(alice)).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shelfCardsCarryTheOwnerIdButNoEmailOrPostalCode() throws Exception {
        addListed(bob, 1, "Book 1");
        String bobId = idOf(bob);
        String body = shelf(alice, bobId).andExpect(jsonPath("$.content[0].owner.id").value(bobId))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("@example.com", "postalCode", "email", "10115", "contactNote");
    }

    @Test
    void shelfPagingIsClamped() throws Exception {
        addListed(bob, 1, "Book 1");
        String bobId = idOf(bob);
        shelf(alice, bobId, "size", "100000").andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
        shelf(alice, bobId, "page", String.valueOf(Integer.MAX_VALUE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void shelfRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/users/" + UUID.randomUUID() + "/copies")).andExpect(status().isUnauthorized());
    }
}
