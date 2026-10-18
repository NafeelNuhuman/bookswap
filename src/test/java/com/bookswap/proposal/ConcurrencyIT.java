package com.bookswap.proposal;

import com.bookswap.proposal.service.ProposalHousekeeping;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Real threads against PostgreSQL. Every scenario repeats for several rounds with fresh copies and asserts the
 * final database state, not only the HTTP statuses: whatever interleaving happens, no copy may end up reserved
 * twice, an accepted proposal must hold only reserved copies, and nobody may get a 500.
 */
class ConcurrencyIT extends ProposalTestBase {
    private static final int ROUNDS = 8;

    /** Starts all calls together on their own threads and returns their HTTP statuses in call order. */
    private List<Integer> race(List<Callable<ResultActions>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            CountDownLatch ready = new CountDownLatch(calls.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<ResultActions> call : calls) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return call.call().andReturn().getResponse().getStatus();
                }));
            }
            ready.await();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(60, TimeUnit.SECONDS));
            }
            assertThat(statuses).as("no server errors").allMatch(s -> s < 500);
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private String proposalStatus(String id) {
        return jdbc.queryForObject("select status from proposal where id = ?::uuid", String.class, id);
    }

    private String copyStatus(String id) {
        return jdbc.queryForObject("select status from copy where id = ?::uuid", String.class, id);
    }

    /** PostgreSQL's own count of deadlocks it had to break (statistics are flushed about once a second). */
    private long deadlocks() throws Exception {
        Thread.sleep(1500);
        return jdbc.queryForObject("select deadlocks from pg_stat_database where datname = current_database()", Long.class);
    }

    private static long successes(List<Integer> statuses) {
        return statuses.stream().filter(s -> s == 200 || s == 201).count();
    }

    /** The invariants that must hold after any interleaving. */
    private void assertConsistent() {
        assertThat(jdbc.queryForObject("""
                select count(*) from (
                    select pi.copy_id from proposal_item pi join proposal p on p.id = pi.proposal_id
                    where p.status = 'ACCEPTED' group by pi.copy_id having count(*) > 1) twice""", Integer.class))
                .as("copies in two accepted proposals").isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from proposal_item pi join proposal p on p.id = pi.proposal_id
                join copy c on c.id = pi.copy_id where p.status = 'ACCEPTED' and c.status <> 'RESERVED'""", Integer.class))
                .as("accepted proposals holding a copy that is not reserved").isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from copy c where c.status = 'RESERVED' and not exists (
                    select 1 from proposal_item pi join proposal p on p.id = pi.proposal_id
                    where pi.copy_id = c.id and p.status = 'ACCEPTED')""", Integer.class))
                .as("reserved copies without an accepted proposal").isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from proposal_item pi join proposal p on p.id = pi.proposal_id
                join copy c on c.id = pi.copy_id where p.status = 'COMPLETED' and c.status = 'RESERVED'""", Integer.class))
                .as("completed exchanges holding a reserved copy").isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from proposal_item pi join proposal p on p.id = pi.proposal_id
                join copy c on c.id = pi.copy_id where p.status = 'COMPLETED'
                  and ((pi.side = 'OFFERED' and c.owner_id <> p.recipient_id)
                    or (pi.side = 'REQUESTED' and c.owner_id <> p.proposer_id))""", Integer.class))
                .as("completed exchanges whose copies did not change hands").isZero();
    }

    // ---- completion ----

    @Autowired ProposalHousekeeping housekeeping;

    private String[] acceptedSwap(int n) throws Exception {
        String b = addListed(bob, n, "Book " + n);
        String a = addListed(alice, n + 100, "Book " + (n + 100));
        String id = proposeOk(alice, List.of(b), List.of(a));
        action(bob, id, "accept").andExpect(status().isOk());
        return new String[]{id, b, a};
    }

    @Test
    void bothPartiesConfirmingAtOnceCompleteTheExchangeOnce() throws Exception {
        long deadlocksBefore = deadlocks();
        for (int round = 0; round < ROUNDS; round++) {
            String[] swap = acceptedSwap(round * 2 + 1);

            List<Integer> statuses = race(List.of(() -> action(alice, swap[0], "confirm"), () -> action(bob, swap[0], "confirm")));

            assertThat(statuses).as("round %d", round).containsExactly(200, 200);
            assertThat(proposalStatus(swap[0])).isEqualTo("COMPLETED");
            assertThat(copyStatus(swap[1])).isEqualTo("UNLISTED");
            assertThat(copyStatus(swap[2])).isEqualTo("UNLISTED");
            assertConsistent();
        }
        assertThat(deadlocks()).as("confirm locks copies before the proposal").isEqualTo(deadlocksBefore);
    }

    @Test
    void confirmingRacingTheOtherPartysCancelIsConsistent() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            String[] swap = acceptedSwap(round * 2 + 1);

            List<Integer> statuses = race(List.of(() -> action(alice, swap[0], "confirm"), () -> action(bob, swap[0], "cancel")));

            assertThat(statuses.get(1)).as("the non-confirmer may always cancel: round %d %s", round, statuses).isEqualTo(200);
            assertThat(statuses.get(0)).isIn(200, 409);
            assertThat(proposalStatus(swap[0])).isEqualTo("CANCELLED");
            assertThat(List.of(copyStatus(swap[1]), copyStatus(swap[2]))).containsOnly("AVAILABLE");
            assertConsistent();
        }
    }

    @Test
    void theSecondConfirmationRacingItsOwnersCancelHasExactlyOneWinner() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            String[] swap = acceptedSwap(round * 2 + 1);
            action(alice, swap[0], "confirm").andExpect(status().isOk());

            List<Integer> statuses = race(List.of(() -> action(bob, swap[0], "confirm"), () -> action(bob, swap[0], "cancel")));

            assertThat(successes(statuses)).as("round %d: %s", round, statuses).isEqualTo(1);
            if (proposalStatus(swap[0]).equals("COMPLETED")) {
                assertThat(List.of(copyStatus(swap[1]), copyStatus(swap[2]))).containsOnly("UNLISTED");
            } else {
                assertThat(proposalStatus(swap[0])).isEqualTo("CANCELLED");
                assertThat(List.of(copyStatus(swap[1]), copyStatus(swap[2]))).containsOnly("AVAILABLE");
            }
            assertConsistent();
        }
    }

    @Test
    void theConfirmerCancellingWhileTheOtherPartyCompletesNeverUndoesTheCompletion() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            String[] swap = acceptedSwap(round * 2 + 1);
            action(alice, swap[0], "confirm").andExpect(status().isOk());

            List<Integer> statuses = race(List.of(() -> action(bob, swap[0], "confirm"), () -> action(alice, swap[0], "cancel")));

            assertThat(statuses).as("round %d", round).containsExactly(200, statuses.get(1));
            assertThat(statuses.get(1)).isIn(403, 409);
            assertThat(proposalStatus(swap[0])).isEqualTo("COMPLETED");
            assertConsistent();
        }
    }

    @Test
    void housekeepingRacingALateConfirmSettlesOnce() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            String[] swap = acceptedSwap(round * 2 + 1);
            action(alice, swap[0], "confirm").andExpect(status().isOk());
            jdbc.update("update proposal set confirm_by = now() - interval '1 minute' where id = ?::uuid", swap[0]);

            List<Integer> statuses = race(List.of(() -> action(bob, swap[0], "confirm"), () -> {
                housekeeping.run();
                return mvc.perform(get("/v3/api-docs"));
            }));

            assertThat(statuses.get(0)).as("round %d", round).isEqualTo(409);
            assertThat(proposalStatus(swap[0])).isEqualTo("COMPLETED");
            assertThat(copyStatus(swap[1])).isEqualTo("UNLISTED");
            assertConsistent();
        }
    }

    @Test
    void deletingTheAccountRacingAnAcceptIsConsistent() throws Exception {
        long deadlocksBefore = deadlocks();
        for (int round = 0; round < ROUNDS; round++) {
            jdbc.execute("TRUNCATE copy, book, refresh_token, users CASCADE");
            users();
            String b = addListed(bob, 1, "Book 1");
            String a = addListed(alice, 3, "Book 3");
            String id = proposeOk(alice, List.of(b), List.of(a));

            List<Integer> statuses = race(List.of(() -> action(bob, id, "accept"),
                    () -> mvc.perform(delete("/api/users/me").header("Authorization", bearer(bob))
                            .contentType(APPLICATION_JSON).content("{\"password\":\"password123\"}"))));

            assertThat(successes(statuses) + (statuses.get(1) == 204 ? 1 : 0)).as("round %d: %s", round, statuses).isEqualTo(1);
            boolean deleted = jdbc.queryForObject("select count(*) from users where email like 'deleted-%'", Integer.class) == 1;
            if (deleted) {
                assertThat(statuses.get(1)).isEqualTo(204);
                assertThat(statuses.get(0)).isEqualTo(409);
                assertThat(proposalStatus(id)).isEqualTo("CANCELLED");
                assertThat(jdbc.queryForObject("select count(*) from copy where id = ?::uuid", Integer.class, b)).isZero();
                assertThat(copyStatus(a)).isEqualTo("AVAILABLE");
            } else {
                assertThat(statuses.get(0)).isEqualTo(200);
                assertThat(statuses.get(1)).isEqualTo(409);
                assertThat(proposalStatus(id)).isEqualTo("ACCEPTED");
                assertThat(List.of(copyStatus(a), copyStatus(b))).containsOnly("RESERVED");
            }
            assertConsistent();
        }
        assertThat(deadlocks()).as("deletion locks copies in id order like accept").isEqualTo(deadlocksBefore);
    }

    @Test
    void competingAcceptsOfOverlappingProposalsLeaveExactlyOneAccepted() throws Exception {
        long deadlocksBefore = deadlocks();
        for (int round = 0; round < ROUNDS; round++) {
            String b1 = addListed(bob, 1, "Book 1");
            String b2 = addListed(bob, 2, "Book 2");
            String a1 = addListed(alice, 3, "Book 3");
            String c1 = addListed(carol, 4, "Book 4");
            String forAlice = proposeOk(alice, List.of(b1), List.of(a1));
            String forCarol = proposeOk(carol, List.of(b1, b2), List.of(c1));

            List<Integer> statuses = race(List.of(() -> action(bob, forAlice, "accept"), () -> action(bob, forCarol, "accept")));

            assertThat(statuses).as("round %d", round).containsExactlyInAnyOrder(200, 409);
            assertThat(List.of(proposalStatus(forAlice), proposalStatus(forCarol)))
                    .containsExactlyInAnyOrder("ACCEPTED", "CANCELLED");
            assertThat(copyStatus(b1)).isEqualTo("RESERVED");
            assertConsistent();
        }
        assertThat(deadlocks()).as("accepts take copy locks first, so PostgreSQL never has to break a deadlock")
                .isEqualTo(deadlocksBefore);
    }

    @Test
    void theSameProposalAcceptedTwiceAtOnceSucceedsOnce() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            String id = proposeOk(alice, List.of(addListed(bob, 1, "Book 1")), List.of(addListed(alice, 3, "Book 3")));
            List<Integer> statuses = race(List.of(() -> action(bob, id, "accept"), () -> action(bob, id, "accept")));
            assertThat(statuses).as("round %d", round).containsExactlyInAnyOrder(200, 409);
            assertThat(proposalStatus(id)).isEqualTo("ACCEPTED");
            assertConsistent();
        }
    }

    @Test
    void acceptRacingTheProposersCancelIsConsistent() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            String b1 = addListed(bob, 1, "Book 1");
            String a1 = addListed(alice, 3, "Book 3");
            String id = proposeOk(alice, List.of(b1), List.of(a1));

            List<Integer> statuses = race(List.of(() -> action(bob, id, "accept"), () -> action(alice, id, "cancel")));

            // an accepted exchange can be called off, so "accept, then cancel" (both succeed) is as valid as "cancel first"
            assertThat(successes(statuses)).as("round %d: %s", round, statuses).isBetween(1L, 2L);
            if (successes(statuses) == 1 && proposalStatus(id).equals("ACCEPTED")) {
                assertThat(List.of(copyStatus(b1), copyStatus(a1))).containsOnly("RESERVED");
            } else {
                assertThat(proposalStatus(id)).isEqualTo("CANCELLED");
                assertThat(List.of(copyStatus(b1), copyStatus(a1))).containsOnly("AVAILABLE");
            }
            assertConsistent();
        }
    }

    @Test
    void acceptRacingTheOwnerUnlistingAnIncludedCopyIsConsistent() throws Exception {
        long deadlocksBefore = deadlocks();
        for (int round = 0; round < ROUNDS; round++) {
            String b1 = addListed(bob, 1, "Book 1");
            String a1 = addListed(alice, 3, "Book 3");
            String id = proposeOk(alice, List.of(b1), List.of(a1));

            List<Integer> statuses = race(List.of(() -> action(bob, id, "accept"),
                    () -> mvc.perform(post("/api/copies/" + a1 + "/unlist").header("Authorization", bearer(alice)))));

            assertThat(successes(statuses)).as("round %d: %s", round, statuses).isEqualTo(1);
            if (proposalStatus(id).equals("ACCEPTED")) {
                assertThat(copyStatus(a1)).isEqualTo("RESERVED");
            } else {
                assertThat(proposalStatus(id)).isEqualTo("CANCELLED");
                assertThat(copyStatus(a1)).isEqualTo("UNLISTED");
                assertThat(copyStatus(b1)).isEqualTo("AVAILABLE");
            }
            assertConsistent();
        }
        assertThat(deadlocks()).as("accept and unlist lock copies before proposals").isEqualTo(deadlocksBefore);
    }

    /** Open proposals may only hold copies that are still available at the grade the proposal saw. */
    private void assertNoStaleOpenProposal() {
        assertThat(jdbc.queryForObject("""
                select count(*) from proposal_item pi join proposal p on p.id = pi.proposal_id
                join copy c on c.id = pi.copy_id
                where p.status = 'PROPOSED' and (c.status <> 'AVAILABLE' or c.grade <> pi.grade_at_proposal)""", Integer.class))
                .as("open proposals holding an unavailable or regraded copy").isZero();
    }

    @Test
    void proposingWhileTheOwnerRegradesTheCopyNeverLeavesAStaleOpenProposal() throws Exception {
        for (int round = 0; round < ROUNDS * 3; round++) {
            String b1 = addListed(bob, 1, "Book 1");
            String a1 = addListed(alice, 3, "Book 3");
            List<Integer> statuses = race(List.of(() -> propose(alice, List.of(b1), List.of(a1), null),
                    () -> mvc.perform(put("/api/copies/" + b1).header("Authorization", bearer(bob))
                            .contentType(APPLICATION_JSON).content("{\"grade\":\"POOR\"}"))));
            assertThat(statuses).as("round %d", round).allMatch(s -> s == 200 || s == 201 || s == 409);
            assertNoStaleOpenProposal();
            assertConsistent();
        }
    }

    @Test
    void proposingWhileTheOwnerUnlistsTheCopyNeverLeavesAStaleOpenProposal() throws Exception {
        for (int round = 0; round < ROUNDS * 3; round++) {
            String b1 = addListed(bob, 1, "Book 1");
            String a1 = addListed(alice, 3, "Book 3");
            List<Integer> statuses = race(List.of(() -> propose(alice, List.of(b1), List.of(a1), null),
                    () -> mvc.perform(post("/api/copies/" + b1 + "/unlist").header("Authorization", bearer(bob)))));
            assertThat(statuses).as("round %d", round).allMatch(s -> s == 200 || s == 201 || s == 409);
            mvc.perform(post("/api/copies/" + b1 + "/list").header("Authorization", bearer(bob)));   // relisting must not revive anything
            assertNoStaleOpenProposal();
            assertConsistent();
        }
    }

    @Test
    void proposingWhileAnotherProposalForTheSameCopyIsAcceptedNeverLeavesAnOpenOneOnAReservedCopy() throws Exception {
        for (int round = 0; round < ROUNDS * 3; round++) {
            String b1 = addListed(bob, 1, "Book 1");
            String a1 = addListed(alice, 3, "Book 3");
            String c1 = addListed(carol, 4, "Book 4");
            String first = proposeOk(carol, List.of(b1), List.of(c1));
            List<Integer> statuses = race(List.of(() -> action(bob, first, "accept"),
                    () -> propose(alice, List.of(b1), List.of(a1), null)));
            assertThat(statuses).as("round %d", round).allMatch(s -> s == 200 || s == 201 || s == 409);
            assertNoStaleOpenProposal();
            assertConsistent();
        }
    }

    @Test
    void acceptRacingACounterIsConsistent() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            String b1 = addListed(bob, 1, "Book 1");
            String a1 = addListed(alice, 3, "Book 3");
            String a2 = addListed(alice, 4, "Book 4");
            String id = proposeOk(alice, List.of(b1), List.of(a1));

            List<Integer> statuses = race(List.of(() -> action(bob, id, "accept"), () -> counter(bob, id, List.of(a1, a2), List.of(b1))));

            assertThat(successes(statuses)).as("round %d: %s", round, statuses).isEqualTo(1);
            int children = jdbc.queryForObject("select count(*) from proposal where parent_id = ?::uuid", Integer.class, id);
            if (proposalStatus(id).equals("ACCEPTED")) {
                assertThat(children).isZero();
            } else {
                assertThat(proposalStatus(id)).isEqualTo("COUNTERED");
                assertThat(children).isEqualTo(1);
                assertThat(List.of(copyStatus(b1), copyStatus(a1), copyStatus(a2))).containsOnly("AVAILABLE");
            }
            assertConsistent();
        }
    }
}
