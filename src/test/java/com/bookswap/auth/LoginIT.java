package com.bookswap.auth;

import com.bookswap.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginIT extends AbstractIntegrationTest {

    @Autowired JwtDecoder decoder;

    @Test
    void validLoginReturnsTokensAndStoresOnlyTheRefreshHash() throws Exception {
        postJson("/api/auth/register", Map.of("email", "amy@example.com", "password", "password123"))
                .andExpect(status().isCreated());
        JsonNode body = read(postJson("/api/auth/login", Map.of("email", " AMY@example.com", "password", "password123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900)));

        String userId = jdbc.queryForObject("select id::text from users where email='amy@example.com'", String.class);
        assertThat(decoder.decode(body.get("accessToken").asText()).getSubject()).isEqualTo(userId);

        String raw = body.get("refreshToken").asText();
        assertThat(raw).isNotBlank();
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where token_hash = ?", Integer.class, raw)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where user_id = ?::uuid", Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void wrongPasswordAndUnknownEmailAreIndistinguishable() throws Exception {
        postJson("/api/auth/register", Map.of("email", "ann@example.com", "password", "password123"))
                .andExpect(status().isCreated());

        JsonNode wrongPassword = read(postJson("/api/auth/login", Map.of("email", "ann@example.com", "password", "nope-nope"))
                .andExpect(status().isUnauthorized()));
        JsonNode unknownEmail = read(postJson("/api/auth/login", Map.of("email", "ghost@example.com", "password", "nope-nope"))
                .andExpect(status().isUnauthorized()));

        assertThat(wrongPassword.get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(wrongPassword.get("code")).isEqualTo(unknownEmail.get("code"));
        assertThat(wrongPassword.get("message")).isEqualTo(unknownEmail.get("message"));
        assertThat(wrongPassword.get("status")).isEqualTo(unknownEmail.get("status"));
    }

    @Test
    void blankFieldsGivePerFieldErrors() throws Exception {
        postJson("/api/auth/login", Map.of("email", "", "password", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='email')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='password')]").exists());
    }
}
