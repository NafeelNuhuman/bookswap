package com.bookswap.auth;

import com.bookswap.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefreshIT extends AbstractIntegrationTest {

    private JsonNode refresh(String token) throws Exception {
        return read(postJson("/api/auth/refresh", Map.of("refreshToken", token)));
    }

    @Test
    void refreshRotatesTheTokenPair() throws Exception {
        JsonNode login = registerAndLogin("rot@example.com", "password123");
        String first = login.get("refreshToken").asText();

        JsonNode second = read(postJson("/api/auth/refresh", Map.of("refreshToken", first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists()));

        assertThat(second.get("refreshToken").asText()).isNotEqualTo(first);
        // the new token works too
        postJson("/api/auth/refresh", Map.of("refreshToken", second.get("refreshToken").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void reusingAnOldTokenIsRejectedAndRevokesTheWholeFamily() throws Exception {
        String t1 = registerAndLogin("reuse@example.com", "password123").get("refreshToken").asText();
        String t2 = refresh(t1).get("refreshToken").asText();

        postJson("/api/auth/refresh", Map.of("refreshToken", t1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // t2 was valid and unused, but reuse-detection must have revoked it (proves no rollback)
        postJson("/api/auth/refresh", Map.of("refreshToken", t2))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where revoked_at is null", Integer.class)).isZero();
    }

    @Test
    void garbageTokenIsRejectedAndDoesNotRevokeRealTokens() throws Exception {
        String real = registerAndLogin("safe@example.com", "password123").get("refreshToken").asText();

        postJson("/api/auth/refresh", Map.of("refreshToken", "garbage-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        postJson("/api/auth/refresh", Map.of("refreshToken", real)).andExpect(status().isOk());
    }

    @Test
    void blankTokenIsA400() throws Exception {
        postJson("/api/auth/refresh", Map.of("refreshToken", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='refreshToken')]").exists());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String token = registerAndLogin("old@example.com", "password123").get("refreshToken").asText();
        jdbc.update("update refresh_token set expires_at = now() - interval '1 hour'");
        postJson("/api/auth/refresh", Map.of("refreshToken", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentRefreshWithTheSameTokenSucceedsExactlyOnce() throws Exception {
        for (int round = 0; round < 5; round++) {
            String token = registerAndLogin("race" + round + "@example.com", "password123").get("refreshToken").asText();
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<Integer> call = () -> {
                start.await();
                return postJson("/api/auth/refresh", Map.of("refreshToken", token))
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> a = pool.submit(call);
            Future<Integer> b = pool.submit(call);
            start.countDown();
            List<Integer> statuses = List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            pool.shutdown();
            assertThat(statuses).containsExactlyInAnyOrder(200, 401);
        }
    }
}
