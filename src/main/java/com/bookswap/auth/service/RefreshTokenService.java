package com.bookswap.auth.service;

import com.bookswap.auth.config.JwtProperties;
import com.bookswap.auth.domain.RefreshToken;
import com.bookswap.auth.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository repository;
    private final JwtProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Creates a refresh token for the user and returns the raw value; only its hash is stored. */
    @Transactional
    public String issue(UUID userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new RefreshToken(userId, hash(raw), clock.instant().plus(properties.refreshTtl())));
        return raw;
    }

    /**
     * Validates and single-use-consumes a refresh token, returning the owning user id.
     * Reuse of an already-used token revokes all of that user's tokens. The revocation must be
     * committed even though we throw, hence noRollbackFor.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public UUID consume(String raw) {
        Instant now = clock.instant();
        RefreshToken token = repository.findByTokenHash(hash(raw))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (token.getRevokedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException();
        }
        if (token.getUsedAt() != null || repository.markUsed(token.getId(), now) == 0) {
            repository.revokeAllForUser(token.getUserId(), now);
            throw new InvalidRefreshTokenException();
        }
        return token.getUserId();
    }

    /** Account deletion: forgets every login token of the user. */
    @Transactional
    public void deleteAll(UUID userId) {
        repository.deleteAllForUser(userId);
    }

    public static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
