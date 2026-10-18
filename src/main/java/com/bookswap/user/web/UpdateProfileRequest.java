package com.bookswap.user.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        // PostgreSQL text cannot contain NUL; reject it here rather than failing on save.
        @NotBlank @Size(max = 60) @Pattern(regexp = "[^\\x00]*", message = "must not contain NUL characters") String displayName,
        @NotBlank @Size(max = 100) @Pattern(regexp = "[^\\x00]*", message = "must not contain NUL characters") String city,
        @NotBlank @Size(max = 20) @Pattern(regexp = "[^\\x00]*", message = "must not contain NUL characters") String postalCode,
        // Optional free text shown to the other party of an accepted proposal; blank means none.
        @Size(max = 200) @Pattern(regexp = "[^\\x00]*", message = "must not contain NUL characters") String contactNote) {

    public UpdateProfileRequest {
        displayName = displayName == null ? null : displayName.strip();
        city = city == null ? null : city.strip();
        postalCode = postalCode == null ? null : postalCode.strip();
        contactNote = contactNote == null || contactNote.isBlank() ? null : contactNote.strip();
    }
}
