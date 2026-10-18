package com.bookswap.user.service;

import com.bookswap.auth.service.InvalidCredentialsException;
import com.bookswap.auth.service.RefreshTokenService;
import com.bookswap.copy.service.CopyService;
import com.bookswap.user.domain.User;
import com.bookswap.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** Account deletion: orchestrates the copies, the login tokens and the user row in one transaction. */
@Service
public class AccountService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final CopyService copyService;
    private final RefreshTokenService refreshTokens;
    private final Clock clock;

    public AccountService(UserRepository users, PasswordEncoder passwordEncoder, CopyService copyService,
                          RefreshTokenService refreshTokens, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.copyService = copyService;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    /**
     * The password is checked before anything is locked. Refused while the user is party to an accepted exchange;
     * otherwise their copies are removed (cancelling the open proposals that contain them), their login tokens are
     * deleted and the user row is anonymized, all or nothing.
     */
    @Transactional
    public void delete(UUID me, String password) {
        User user = users.findById(me).filter(u -> !u.isDeleted()).orElseThrow(UserNotFoundException::new);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        copyService.removeAll(me);
        refreshTokens.deleteAll(me);   // a bulk delete that clears the persistence context: reload the user after it
        users.findById(me).orElseThrow(UserNotFoundException::new).anonymize(clock.instant());
    }
}
