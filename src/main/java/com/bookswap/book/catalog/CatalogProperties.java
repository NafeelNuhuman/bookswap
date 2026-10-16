package com.bookswap.book.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("bookswap.catalog")
public record CatalogProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {}
