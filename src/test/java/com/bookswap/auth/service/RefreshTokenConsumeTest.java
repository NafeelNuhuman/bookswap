package com.bookswap.auth.service;

import com.bookswap.auth.config.JwtProperties;
import com.bookswap.auth.domain.RefreshToken;
import com.bookswap.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenConsumeTest {

    @Mock RefreshTokenRepository repository;

    private final Instant now = Instant.parse("2026-10-16T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final UUID userId = UUID.randomUUID();
    private RefreshTokenService service() {
        return new RefreshTokenService(repository,
                new JwtProperties("x".repeat(32), Duration.ofMinutes(15), Duration.ofDays(14)), clock);
    }

    private RefreshToken stored(Instant expiresAt, Instant usedAt, Instant revokedAt) {
        RefreshToken token = new RefreshToken(userId, RefreshTokenService.hash("raw"), expiresAt);
        ReflectionTestUtils.setField(token, "usedAt", usedAt);
        ReflectionTestUtils.setField(token, "revokedAt", revokedAt);
        return token;
    }

    @Test
    void unknownTokenIsRejectedWithoutRevokingAnything() {
        when(repository.findByTokenHash(RefreshTokenService.hash("raw"))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().consume("raw")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository, never()).revokeAllForUser(any(), any());
    }

    @Test
    void expiredTokenIsRejectedWithoutRevokingAnything() {
        when(repository.findByTokenHash(RefreshTokenService.hash("raw")))
                .thenReturn(Optional.of(stored(now.minusSeconds(1), null, null)));
        assertThatThrownBy(() -> service().consume("raw")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository, never()).revokeAllForUser(any(), any());
    }

    @Test
    void revokedTokenIsRejected() {
        when(repository.findByTokenHash(RefreshTokenService.hash("raw")))
                .thenReturn(Optional.of(stored(now.plusSeconds(60), null, now.minusSeconds(5))));
        assertThatThrownBy(() -> service().consume("raw")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository, never()).markUsed(any(), any());
    }

    @Test
    void reusedTokenRevokesAllTokensOfTheUser() {
        when(repository.findByTokenHash(RefreshTokenService.hash("raw")))
                .thenReturn(Optional.of(stored(now.plusSeconds(60), now.minusSeconds(5), null)));
        assertThatThrownBy(() -> service().consume("raw")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository).revokeAllForUser(userId, now);
    }

    @Test
    void losingTheRaceToMarkUsedIsTreatedAsReuse() {
        RefreshToken token = stored(now.plusSeconds(60), null, null);
        when(repository.findByTokenHash(RefreshTokenService.hash("raw"))).thenReturn(Optional.of(token));
        when(repository.markUsed(token.getId(), now)).thenReturn(0);
        assertThatThrownBy(() -> service().consume("raw")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository).revokeAllForUser(userId, now);
    }

    @Test
    void validTokenIsMarkedUsedAndReturnsTheUserId() {
        RefreshToken token = stored(now.plusSeconds(60), null, null);
        when(repository.findByTokenHash(RefreshTokenService.hash("raw"))).thenReturn(Optional.of(token));
        when(repository.markUsed(token.getId(), now)).thenReturn(1);
        assertThat(service().consume("raw")).isEqualTo(userId);
        verify(repository, never()).revokeAllForUser(any(), any());
    }
}
