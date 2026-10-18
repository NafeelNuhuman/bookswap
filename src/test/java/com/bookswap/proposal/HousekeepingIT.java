package com.bookswap.proposal;

import com.bookswap.proposal.service.ProposalHousekeeping;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HousekeepingIT extends ProposalTestBase {

    @Autowired ProposalHousekeeping housekeeping;
    @Autowired ApplicationContext context;

    private String b1;
    private String a1;

    private String acceptedSwap(int n) throws Exception {
        b1 = addListed(bob, n, "Book " + n);
        a1 = addListed(alice, n + 100, "Book " + (n + 100));
        String id = proposeOk(alice, List.of(b1), List.of(a1));
        action(bob, id, "accept").andExpect(status().isOk());
        return id;
    }

    private void makeOverdue(String id) {
        jdbc.update("update proposal set confirm_by = now() - interval '1 minute' where id = ?::uuid", id);
    }

    private String proposalStatus(String id) {
        return jdbc.queryForObject("select status from proposal where id = ?::uuid", String.class, id);
    }

    private String copyStatus(String id) {
        return jdbc.queryForObject("select status from copy where id = ?::uuid", String.class, id);
    }

    private String copyOwner(String id) {
        return jdbc.queryForObject("select owner_id::text from copy where id = ?::uuid", String.class, id);
    }

    @Test
    void theJobIsScheduled() {
        assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).isNotEmpty();
    }

    @Test
    void lapsedProposedBecomeExpiredAndOpenOnesAreUntouched() throws Exception {
        String lapsed = proposeOk(alice, List.of(addListed(bob, 1, "Book 1")), List.of(addListed(alice, 3, "Book 3")));
        String open = proposeOk(alice, List.of(addListed(bob, 2, "Book 2")), List.of(addListed(alice, 4, "Book 4")));
        jdbc.update("update proposal set expires_at = now() - interval '1 minute' where id = ?::uuid", lapsed);

        housekeeping.run();

        assertThat(proposalStatus(lapsed)).isEqualTo("EXPIRED");
        assertThat(proposalStatus(open)).isEqualTo("PROPOSED");
        action(bob, lapsed, "accept").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_OPEN"));
        detail(bob, lapsed).andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    void runningTwiceChangesNothingTheSecondTime() throws Exception {
        String lapsed = proposeOk(alice, List.of(addListed(bob, 1, "Book 1")), List.of(addListed(alice, 3, "Book 3")));
        String overdue = acceptedSwap(5);
        jdbc.update("update proposal set expires_at = now() - interval '1 minute' where id = ?::uuid", lapsed);
        makeOverdue(overdue);

        housekeeping.run();
        List<?> first = jdbc.queryForList("select id, status, version, updated_at from proposal order by id");
        housekeeping.run();

        assertThat(jdbc.queryForList("select id, status, version, updated_at from proposal order by id")).isEqualTo(first);
    }

    @Test
    void overdueAcceptedWithNoConfirmationIsCancelledAsNoShowAndCopiesAreReleased() throws Exception {
        String id = acceptedSwap(1);
        makeOverdue(id);

        housekeeping.run();

        assertThat(proposalStatus(id)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select cancel_cause from proposal where id = ?::uuid", String.class, id))
                .isEqualTo("NO_SHOW");
        assertThat(copyStatus(b1)).isEqualTo("AVAILABLE");
        assertThat(copyStatus(a1)).isEqualTo("AVAILABLE");
        feed(carol).andExpect(jsonPath("$.totalElements").value(0));
        feed(alice).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void overdueAcceptedWithOneConfirmationCompletesAndTransfers() throws Exception {
        String id = acceptedSwap(1);
        action(alice, id, "confirm").andExpect(status().isOk());
        makeOverdue(id);

        housekeeping.run();

        assertThat(proposalStatus(id)).isEqualTo("COMPLETED");
        assertThat(copyOwner(b1)).isEqualTo(userId(alice));
        assertThat(copyOwner(a1)).isEqualTo(userId(bob));
        assertThat(copyStatus(b1)).isEqualTo("UNLISTED");
        assertThat(copyStatus(a1)).isEqualTo("UNLISTED");
    }

    @Test
    void acceptedExchangesNotYetOverdueAreUntouched() throws Exception {
        String id = acceptedSwap(1);
        housekeeping.run();
        assertThat(proposalStatus(id)).isEqualTo("ACCEPTED");
        assertThat(copyStatus(b1)).isEqualTo("RESERVED");
    }

    @Test
    void oneBrokenExchangeDoesNotStopTheOthers() throws Exception {
        String broken = acceptedSwap(1);
        String brokenCopy = b1;
        String fine = acceptedSwap(2);
        String fineCopy = b1;
        jdbc.update("update copy set status = 'UNLISTED' where id = ?::uuid", brokenCopy);   // cannot happen through the API
        makeOverdue(broken);
        makeOverdue(fine);

        housekeeping.run();

        assertThat(proposalStatus(fine)).isEqualTo("CANCELLED");
        assertThat(copyStatus(fineCopy)).isEqualTo("AVAILABLE");
        assertThat(proposalStatus(broken)).isEqualTo("ACCEPTED");
    }
}
