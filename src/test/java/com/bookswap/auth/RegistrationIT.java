package com.bookswap.auth;

import com.bookswap.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistrationIT extends AbstractIntegrationTest {

    @Test
    void registersUserAndStoresOnlyAHash() throws Exception {
        postJson("/api/auth/register", Map.of("email", "  Alice@Example.com ", "password", "password123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.profileComplete").value(false))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String stored = jdbc.queryForObject("select password_hash from users where email = 'alice@example.com'", String.class);
        assertThat(stored).startsWith("$2").isNotEqualTo("password123");
    }

    @Test
    void duplicateEmailIsRejectedWhateverTheCaseOrWhitespace() throws Exception {
        postJson("/api/auth/register", Map.of("email", "bob@example.com", "password", "password123"))
                .andExpect(status().isCreated());
        postJson("/api/auth/register", Map.of("email", " BOB@Example.COM ", "password", "password123"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(1);
    }

    @Test
    void invalidEmailAndShortPasswordGivePerFieldErrors() throws Exception {
        postJson("/api/auth/register", Map.of("email", "not-an-email", "password", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='email')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='password')]").exists());
    }

    @Test
    void passwordLongerThanBcryptLimitIsA400NotA500() throws Exception {
        postJson("/api/auth/register", Map.of("email", "long@example.com", "password", "x".repeat(73)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='password')]").exists());
    }

    @Test
    void nonAsciiPasswordOverBcryptByteLimitIsRejectedNotSilentlyTruncated() throws Exception {
        // 72 characters but 108 UTF-8 bytes: BCrypt would ignore the tail, so two passwords would match.
        postJson("/api/auth/register", Map.of("email", "utf@example.com", "password", "é".repeat(36) + "A".repeat(36)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='password')]").exists());
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isZero();
    }

    @Test
    void nonAsciiPasswordWithinTheByteLimitIsAccepted() throws Exception {
        // 36 two-byte characters = exactly 72 bytes
        postJson("/api/auth/register", Map.of("email", "ok@example.com", "password", "é".repeat(36)))
                .andExpect(status().isCreated());
    }

    @Test
    void missingFieldsGivePerFieldErrors() throws Exception {
        postJson("/api/auth/register", Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='email')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='password')]").exists());
    }

    @Test
    void malformedOrMissingBodyIsAStandard400() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }
}
