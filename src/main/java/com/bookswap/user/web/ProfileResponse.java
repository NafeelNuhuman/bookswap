package com.bookswap.user.web;

import com.bookswap.user.domain.User;

import java.util.UUID;

public record ProfileResponse(UUID id, String email, String displayName, String city,
                              String postalCode, boolean profileComplete) {

    public static ProfileResponse from(User user) {
        return new ProfileResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getCity(), user.getPostalCode(), user.isProfileComplete());
    }
}
