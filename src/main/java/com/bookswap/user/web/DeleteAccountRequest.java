package com.bookswap.user.web;

import jakarta.validation.constraints.NotBlank;

/** Deleting an account is irreversible, so the current password is required as well as the token. */
public record DeleteAccountRequest(@NotBlank String password) {}
