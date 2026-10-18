package com.bookswap.proposal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The scheduled housekeeping: lapsed proposals become expired, overdue accepted exchanges settle. Idempotent, so it can
 * be run twice or overlap a restart; each exchange settles in its own transaction, and one that cannot (a version
 * clash with a user's action, an inconsistent copy) is skipped and retried by the next run.
 */
@Component
public class ProposalHousekeeping {
    private static final Logger log = LoggerFactory.getLogger(ProposalHousekeeping.class);
    private static final int BATCH = 100;

    private final ProposalService proposals;

    ProposalHousekeeping(ProposalService proposals) {
        this.proposals = proposals;
    }

    /** Not at start-up: the first run is one interval after the application starts. */
    @Scheduled(fixedDelayString = "${bookswap.jobs.interval}", initialDelayString = "${bookswap.jobs.interval}")
    void runScheduled() {
        try {
            run();
        } catch (RuntimeException e) {
            log.error("Housekeeping run failed", e);
        }
    }

    public void run() {
        int expired = proposals.expireLapsed();
        Set<UUID> skipped = new HashSet<>();
        int settled = 0;
        List<UUID> batch;
        do {
            batch = proposals.overdueAccepted(skipped, BATCH);
            for (UUID id : batch) {
                try {
                    proposals.settle(id);
                    settled++;
                } catch (RuntimeException e) {
                    skipped.add(id);
                    log.warn("Could not settle proposal {}; it will be retried by the next run", id, e);
                }
            }
        } while (batch.size() == BATCH);
        if (expired > 0 || settled > 0 || !skipped.isEmpty()) {
            log.info("Housekeeping: {} expired, {} settled, {} skipped", expired, settled, skipped.size());
        }
    }
}
