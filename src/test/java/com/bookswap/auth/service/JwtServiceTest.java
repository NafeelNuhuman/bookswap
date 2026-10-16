package com.bookswap.auth.service;

import com.bookswap.auth.config.JwtConfig;
import com.bookswap.auth.config.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final JwtProperties props =
            new JwtProperties("0123456789abcdef0123456789abcdef", Duration.ofMinutes(15), Duration.ofDays(14));
    private final JwtConfig config = new JwtConfig();
    private final SecretKey key = config.jwtSecretKey(props);
    private final JwtService service = new JwtService(config.jwtEncoder(key), props, Clock.systemUTC());

    @Test
    void tokenCarriesUserIdAndFifteenMinuteLifetime() {
        UUID id = UUID.randomUUID();
        Jwt jwt = config.jwtDecoder(key).decode(service.createAccessToken(id));
        assertThat(jwt.getSubject()).isEqualTo(id.toString());
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(service.accessTtlSeconds()).isEqualTo(900);
    }

    @Test
    void tokenIsRejectedByDecoderWithDifferentKey() {
        JwtDecoder other = NimbusJwtDecoder.withSecretKey(
                new SecretKeySpec("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz".getBytes(StandardCharsets.UTF_8), "HmacSHA256")).build();
        String token = service.createAccessToken(UUID.randomUUID());
        assertThatThrownBy(() -> other.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretIsRejectedAtStartup() {
        JwtProperties weak = new JwtProperties("short", Duration.ofMinutes(15), Duration.ofDays(14));
        assertThatThrownBy(() -> config.jwtSecretKey(weak)).isInstanceOf(IllegalStateException.class);
    }
}
