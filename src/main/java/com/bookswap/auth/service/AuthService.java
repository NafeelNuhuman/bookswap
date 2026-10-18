package com.bookswap.auth.service;

import com.bookswap.auth.web.LoginRequest;
import com.bookswap.auth.web.RegisterRequest;
import com.bookswap.auth.web.TokenResponse;
import com.bookswap.user.domain.User;
import com.bookswap.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final JwtService jwtService;
    private final Clock clock;
    /** Compared against when the email is unknown, so timing does not reveal whether it exists. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, RefreshTokenService refreshTokens,
                       JwtService jwtService, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.jwtService = jwtService;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public User register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailTakenException();
        }
        User user = new User(email, passwordEncoder.encode(request.password()), clock.instant());
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent registration; the unique index on lower(email) decided.
            throw new EmailTakenException();
        }
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Optional<User> user = users.findByEmail(normalizeEmail(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !matches) {
            throw new InvalidCredentialsException();
        }
        return issueTokens(user.get().getId());
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenResponse refresh(String rawRefreshToken) {
        UUID userId = refreshTokens.consume(rawRefreshToken);
        if (users.findById(userId).filter(u -> !u.isDeleted()).isEmpty()) {
            // e.g. a refresh that raced the account deletion left a live token behind
            refreshTokens.deleteAll(userId);
            throw new InvalidRefreshTokenException();
        }
        return issueTokens(userId);
    }

    private TokenResponse issueTokens(UUID userId) {
        return new TokenResponse(jwtService.createAccessToken(userId), refreshTokens.issue(userId),
                "Bearer", jwtService.accessTtlSeconds());
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
