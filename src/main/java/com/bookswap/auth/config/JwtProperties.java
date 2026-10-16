package com.bookswap.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("bookswap.jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl) {}
