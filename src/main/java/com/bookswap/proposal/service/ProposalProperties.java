package com.bookswap.proposal.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** {@code expiry}: how long a proposal stays open after it is made. */
@ConfigurationProperties("bookswap.proposals")
public record ProposalProperties(Duration expiry) {}
