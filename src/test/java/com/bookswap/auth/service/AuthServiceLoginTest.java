package com.bookswap.auth.service;

import com.bookswap.auth.web.LoginRequest;
import com.bookswap.auth.web.TokenResponse;
import com.bookswap.user.domain.User;
import com.bookswap.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock RefreshTokenService refreshTokens;
    @Mock JwtService jwt;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-16T10:00:00Z"), ZoneOffset.UTC);
    private AuthService service;

    @BeforeEach
    void setUp() {
        when(encoder.encode(any())).thenReturn("dummy-hash");
        service = new AuthService(users, encoder, refreshTokens, jwt, clock);
    }

    @Test
    void unknownEmailStillPerformsAPasswordComparisonAndFailsGenerically() {
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("ghost@example.com", "pw")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(encoder).matches("pw", "dummy-hash");
    }

    @Test
    void wrongPasswordFails() {
        User user = new User("a@example.com", "stored-hash", clock.instant());
        when(users.findByEmail("a@example.com")).thenReturn(Optional.of(user));
        when(encoder.matches("pw", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("a@example.com", "pw")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void successIssuesBothTokensAndNormalizesTheEmail() {
        User user = new User("a@example.com", "stored-hash", clock.instant());
        when(users.findByEmail("a@example.com")).thenReturn(Optional.of(user));
        when(encoder.matches("pw", "stored-hash")).thenReturn(true);
        when(jwt.createAccessToken(user.getId())).thenReturn("access");
        when(jwt.accessTtlSeconds()).thenReturn(900L);
        when(refreshTokens.issue(user.getId())).thenReturn("refresh");

        TokenResponse response = service.login(new LoginRequest("  A@Example.com ", "pw"));

        assertThat(response).isEqualTo(new TokenResponse("access", "refresh", "Bearer", 900L));
    }
}
