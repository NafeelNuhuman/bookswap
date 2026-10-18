package com.bookswap.user.service;

import com.bookswap.user.domain.User;
import com.bookswap.user.repository.UserRepository;
import com.bookswap.user.web.ProfileResponse;
import com.bookswap.user.web.UpdateProfileRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class UserService {
    private final UserRepository users;
    private final Clock clock;

    public UserService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    /** Authorization is by construction: callers pass the authenticated principal's id, never a request-supplied id. */
    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID principalId) {
        return ProfileResponse.from(load(principalId));
    }

    @Transactional(readOnly = true)
    public boolean isProfileComplete(UUID principalId) {
        return load(principalId).isProfileComplete();
    }

    @Transactional
    public ProfileResponse updateProfile(UUID principalId, UpdateProfileRequest request) {
        User user = load(principalId);
        user.updateProfile(request.displayName(), request.city(), request.postalCode(), request.contactNote(),
                clock.instant());
        return ProfileResponse.from(user);
    }

    @Transactional(readOnly = true)
    public void requireExists(UUID id) {
        if (!users.existsById(id)) {
            throw new UserNotFoundException();
        }
    }

    private User load(UUID id) {
        return users.findById(id).orElseThrow(UserNotFoundException::new);
    }
}
