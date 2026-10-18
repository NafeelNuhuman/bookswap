package com.bookswap.proposal;

import org.junit.jupiter.api.Test;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Real threads against PostgreSQL. Every scenario repeats for several rounds with fresh copies and asserts the
 * final database state, not only the HTTP statuses: whatever interleaving happens, no copy may end up reserved
 * twice, an accepted proposal must hold only reserved copies, and nobody may get a 500.
 */
class ConcurrencyIT extends ProposalTestBase {
    private static final int ROUNDS = 5;

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
    }

    @Test
    void competingAcceptsOfOverlappingProposalsLeaveExactlyOneAccepted() throws Exception {
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

            assertThat(successes(statuses)).as("round %d: %s", round, statuses).isEqualTo(1);
            if (proposalStatus(id).equals("ACCEPTED")) {
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
