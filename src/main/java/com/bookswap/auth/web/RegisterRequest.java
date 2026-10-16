package com.bookswap.auth.web;

import jakarta.validation.constraints.Email;
import com.bookswap.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        // BCrypt only uses the first 72 bytes; limit bytes, not characters, so non-ASCII can't be silently truncated.
        @NotBlank @Size(min = 8) @MaxUtf8Bytes(72) String password) {

    public RegisterRequest {
        email = email == null ? null : email.trim();
    }
}
