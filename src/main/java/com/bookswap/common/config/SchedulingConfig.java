package com.bookswap.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Switches on {@code @Scheduled} jobs (see {@code ProposalHousekeeping}). */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
