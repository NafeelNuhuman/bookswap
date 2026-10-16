package com.bookswap.auth.service;

import com.bookswap.auth.config.JwtProperties;
import com.bookswap.auth.domain.RefreshToken;
import com.bookswap.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock RefreshTokenRepository repository;

    private final Instant now = Instant.parse("2026-10-16T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final JwtProperties props = new JwtProperties("x".repeat(32), Duration.ofMinutes(15), Duration.ofDays(14));

    @Test
    void issueStoresOnlyTheHashAndExpiresInFourteenDays() {
        RefreshTokenService service = new RefreshTokenService(repository, props, clock);
        UUID userId = UUID.randomUUID();

        String raw = service.issue(userId);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(RefreshTokenService.hash(raw)).isNotEqualTo(raw).hasSize(64);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(now.plus(Duration.ofDays(14)));
        assertThat(raw.length()).isGreaterThanOrEqualTo(43);
    }

    @Test
    void issuedTokensAreUnique() {
        RefreshTokenService service = new RefreshTokenService(repository, props, clock);
        assertThat(service.issue(UUID.randomUUID())).isNotEqualTo(service.issue(UUID.randomUUID()));
    }
}
