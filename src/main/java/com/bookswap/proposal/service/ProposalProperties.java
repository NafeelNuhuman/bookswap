package com.bookswap.proposal.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code expiry}: how long a proposal stays open after it is made. {@code confirmWindow}: how long after acceptance
 * the parties have to confirm the swap before it settles by itself.
 */
@ConfigurationProperties("bookswap.proposals")
public record ProposalProperties(Duration expiry, Duration confirmWindow) {}
