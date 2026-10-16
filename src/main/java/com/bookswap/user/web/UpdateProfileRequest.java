package com.bookswap.user.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 60) String displayName,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 20) String postalCode) {

    public UpdateProfileRequest {
        displayName = displayName == null ? null : displayName.trim();
        city = city == null ? null : city.trim();
        postalCode = postalCode == null ? null : postalCode.trim();
    }
}
