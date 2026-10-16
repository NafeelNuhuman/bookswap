package com.bookswap.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        // PostgreSQL text cannot contain NUL; reject it here rather than failing in the query.
        @NotBlank @Size(max = 255) @Pattern(regexp = "[^\\x00]*", message = "must not contain NUL characters") String email,
        @NotBlank String password) {

    public LoginRequest {
        email = email == null ? null : email.trim();
    }
}
