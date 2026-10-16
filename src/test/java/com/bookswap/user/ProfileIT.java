package com.bookswap.user;

import com.bookswap.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileIT extends AbstractIntegrationTest {

    private ResultActions putMe(JsonNode tokens, Object body) throws Exception {
        return mvc.perform(put("/api/users/me").header("Authorization", bearer(tokens))
                .contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    @Test
    void profileStartsIncompleteThenBecomesCompleteAfterUpdate() throws Exception {
        JsonNode tokens = registerAndLogin("pat@example.com", "password123");

        mvc.perform(get("/api/users/me").header("Authorization", bearer(tokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("pat@example.com"))
                .andExpect(jsonPath("$.profileComplete").value(false));

        putMe(tokens, Map.of("displayName", " Pat ", "city", "Berlin", "postalCode", "10115"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Pat"))
                .andExpect(jsonPath("$.profileComplete").value(true));

        mvc.perform(get("/api/users/me").header("Authorization", bearer(tokens)))
                .andExpect(jsonPath("$.city").value("Berlin"))
                .andExpect(jsonPath("$.postalCode").value("10115"))
                .andExpect(jsonPath("$.profileComplete").value(true));
    }

    @Test
    void eachUserOnlySeesAndChangesTheirOwnProfile() throws Exception {
        JsonNode a = registerAndLogin("a@example.com", "password123");
        JsonNode b = registerAndLogin("b@example.com", "password123");
        putMe(a, Map.of("displayName", "Alice", "city", "Berlin", "postalCode", "10115")).andExpect(status().isOk());

        mvc.perform(get("/api/users/me").header("Authorization", bearer(b)))
                .andExpect(jsonPath("$.email").value("b@example.com"))
                .andExpect(jsonPath("$.displayName").doesNotExist());

        // there is no way to address another user by id
        String aId = jdbc.queryForObject("select id::text from users where email='a@example.com'", String.class);
        mvc.perform(put("/api/users/" + aId).header("Authorization", bearer(b))
                        .contentType(APPLICATION_JSON).content("{\"displayName\":\"x\",\"city\":\"y\",\"postalCode\":\"z\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void emailCannotBeChangedThroughTheProfile() throws Exception {
        JsonNode tokens = registerAndLogin("keep@example.com", "password123");
        putMe(tokens, Map.of("email", "evil@example.com", "displayName", "K", "city", "Bonn", "postalCode", "53111"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("keep@example.com"));
    }

    @Test
    void blankOrOversizedFieldsAreRejectedPerField() throws Exception {
        JsonNode tokens = registerAndLogin("val@example.com", "password123");
        putMe(tokens, Map.of("displayName", "   ", "city", "", "postalCode", "1".repeat(21)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='displayName')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='city')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='postalCode')]").exists());
        putMe(tokens, Map.of("displayName", "n".repeat(61), "city", "Berlin", "postalCode", "10115"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='displayName')]").exists());
    }

    @Test
    void nulCharacterInProfileFieldsIsA400NotA500() throws Exception {
        JsonNode tokens = registerAndLogin("nul@example.com", "password123");
        putMe(tokens, Map.of("displayName", "a\u0000b", "city", "Berlin", "postalCode", "10115"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='displayName')]").exists());
        putMe(tokens, Map.of("displayName", "Nul", "city", "Ber\u0000lin", "postalCode", "10\u0000115"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='city')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='postalCode')]").exists());
    }

    @Test
    void validTokenOfADeletedUserIsAClean404() throws Exception {
        JsonNode tokens = registerAndLogin("gone@example.com", "password123");
        jdbc.update("delete from users where email = 'gone@example.com'");
        mvc.perform(get("/api/users/me").header("Authorization", bearer(tokens)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void profileEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/users/me").contentType(APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }
}
