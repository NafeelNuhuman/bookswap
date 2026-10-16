package com.bookswap.user.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        // PostgreSQL text cannot contain NUL; reject it here rather than failing on save.
        @NotBlank @Size(max = 60) @Pattern(regexp = "[^\\x00]*", message = "must not contain NUL characters") String displayName,
        @NotBlank @Size(max = 100) @Pattern(regexp = "[^\\x00]*", message = "must not contain NUL characters") String city,
        @NotBlank @Size(max = 20) @Pattern(regexp = "[^\\x00]*", message = "must not contain NUL characters") String postalCode) {

    public UpdateProfileRequest {
        displayName = displayName == null ? null : displayName.trim();
        city = city == null ? null : city.trim();
        postalCode = postalCode == null ? null : postalCode.trim();
    }
}
