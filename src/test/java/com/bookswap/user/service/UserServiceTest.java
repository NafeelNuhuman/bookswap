package com.bookswap.user.service;

import com.bookswap.user.domain.User;
import com.bookswap.user.repository.UserRepository;
import com.bookswap.user.web.ProfileResponse;
import com.bookswap.user.web.UpdateProfileRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository users;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-16T10:00:00Z"), ZoneOffset.UTC);

    private UserService service() {
        return new UserService(users, clock);
    }

    @Test
    void newUserHasAnIncompleteProfile() {
        User user = new User("a@example.com", "hash", clock.instant());
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        ProfileResponse profile = service().getProfile(user.getId());

        assertThat(profile.profileComplete()).isFalse();
        assertThat(profile.email()).isEqualTo("a@example.com");
    }

    @Test
    void updateAppliesToThePrincipalAndCompletesTheProfile() {
        User user = new User("a@example.com", "hash", clock.instant());
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        ProfileResponse profile = service().updateProfile(user.getId(),
                new UpdateProfileRequest("Amy", "Berlin", "10115"));

        assertThat(profile.profileComplete()).isTrue();
        assertThat(user.getDisplayName()).isEqualTo("Amy");
        assertThat(user.getCity()).isEqualTo("Berlin");
        assertThat(user.getPostalCode()).isEqualTo("10115");
    }

    @Test
    void unknownPrincipalGivesNotFound() {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().getProfile(id)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void isProfileCompleteReflectsTheStoredProfile() {
        User user = new User("a@example.com", "hash", clock.instant());
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        assertThat(service().isProfileComplete(user.getId())).isFalse();
        user.updateProfile("Pat", "Berlin", "10115", clock.instant());
        assertThat(service().isProfileComplete(user.getId())).isTrue();
    }

    @Test
    void isProfileCompleteOfUnknownUserIsNotFound() {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().isProfileComplete(id)).isInstanceOf(UserNotFoundException.class);
    }
}
