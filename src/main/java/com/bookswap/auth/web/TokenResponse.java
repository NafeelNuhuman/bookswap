package com.bookswap.auth.web;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {}
