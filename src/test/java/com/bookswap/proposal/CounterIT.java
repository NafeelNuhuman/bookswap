package com.bookswap.proposal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CounterIT extends ProposalTestBase {

    private String b1;
    private String b2;
    private String a1;
    private String a2;

    /** Alice offers a1 for bob's b1; returns the proposal id. Also lists a second copy for each. */
    private String aliceProposes() throws Exception {
        b1 = addListed(bob, 1, "Book 1");
        b2 = addListed(bob, 2, "Book 2");
        a1 = addListed(alice, 3, "Book 3");
        a2 = addListed(alice, 4, "Book 4");
        return proposeOk(alice, List.of(b1), List.of(a1));
    }

    private String proposalStatus(String id) {
        return jdbc.queryForObject("select status from proposal where id = ?::uuid", String.class, id);
    }

    @Test
    void counterCreatesALinkedProposalWithTheRolesSwapped() throws Exception {
        String original = aliceProposes();

        String counterId = read(counter(bob, original, List.of(a1, a2), List.of(b1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROPOSED"))
                .andExpect(jsonPath("$.parentId").value(original))
                .andExpect(jsonPath("$.proposer.id").value(userId(bob)))
                .andExpect(jsonPath("$.recipient.id").value(userId(alice)))
                .andExpect(jsonPath("$.requested.length()").value(2))
                .andExpect(jsonPath("$.offered.length()").value(1))).get("id").asText();

        assertThat(proposalStatus(original)).isEqualTo("COUNTERED");
        assertThat(proposalStatus(counterId)).isEqualTo("PROPOSED");
        detail(alice, counterId).andExpect(status().isOk());
        action(alice, counterId, "accept").andExpect(status().isOk());
    }

    @Test
    void counterMayAddAndRemoveCopiesOnBothSides() throws Exception {
        String original = aliceProposes();
        counter(bob, original, List.of(a2), List.of(b1, b2)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.requested[0].copyId").value(a2))
                .andExpect(jsonPath("$.offered.length()").value(2));
    }

    @Test
    void aCounterCanBeCounteredAgainAndTheChainEndsInAnAcceptance() throws Exception {
        String p1 = aliceProposes();
        String p2 = read(counter(bob, p1, List.of(a1, a2), List.of(b1)).andExpect(status().isCreated())).get("id").asText();
        String p3 = read(counter(alice, p2, List.of(b1), List.of(a1, a2)).andExpect(status().isCreated())).get("id").asText();

        action(bob, p3, "accept").andExpect(status().isOk());

        assertThat(proposalStatus(p1)).isEqualTo("COUNTERED");
        assertThat(proposalStatus(p2)).isEqualTo("COUNTERED");
        assertThat(proposalStatus(p3)).isEqualTo("ACCEPTED");
        detail(alice, p3).andExpect(jsonPath("$.parentId").value(p2));
    }

    @Test
    void aCounterCanBeRejected() throws Exception {
        String p1 = aliceProposes();
        String p2 = read(counter(bob, p1, List.of(a1, a2), List.of(b1)).andExpect(status().isCreated())).get("id").asText();
        action(alice, p2, "reject").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void onlyTheRecipientCanCounter() throws Exception {
        String original = aliceProposes();
        counter(alice, original, List.of(b1), List.of(a2)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROPOSAL_ACTION_NOT_ALLOWED"));
        counter(carol, original, List.of(b1), List.of(a2)).andExpect(status().isNotFound());
        assertThat(proposalStatus(original)).isEqualTo("PROPOSED");
    }

    @Test
    void counteringAClosedOrLapsedProposalIsNotOpen() throws Exception {
        String original = aliceProposes();
        jdbc.update("update proposal set expires_at = now() - interval '1 minute' where id = ?::uuid", original);
        counter(bob, original, List.of(a1), List.of(b1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_OPEN"));

        String second = proposeOk(alice, List.of(b2), List.of(a2));
        action(alice, second, "cancel").andExpect(status().isOk());
        counter(bob, second, List.of(a2), List.of(b2)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_OPEN"));
        assertThat(jdbc.queryForObject("select count(*) from proposal", Integer.class)).isEqualTo(2);
    }

    @Test
    void anInvalidCounterLeavesTheOriginalProposedAndCreatesNothing() throws Exception {
        String original = aliceProposes();
        jdbc.update("update copy set status = 'UNLISTED' where id = ?::uuid", b2);
        counter(bob, original, List.of(a1), List.of(b2)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COPY_NOT_AVAILABLE"));
        counter(bob, original, List.of(), List.of(b1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROPOSAL"));

        assertThat(proposalStatus(original)).isEqualTo("PROPOSED");
        assertThat(jdbc.queryForObject("select count(*) from proposal", Integer.class)).isEqualTo(1);
    }

    @Test
    void aCountersRequestedCopiesMustBelongToTheOriginalProposer() throws Exception {
        String original = aliceProposes();
        String c1 = addListed(carol, 5, "Book 5");
        counter(bob, original, List.of(c1), List.of(b1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COPY_NOT_AVAILABLE"));
        assertThat(proposalStatus(original)).isEqualTo("PROPOSED");
    }

    @Test
    void aCounterCannotRequestTheCountererOwnCopies() throws Exception {
        String original = aliceProposes();
        counter(bob, original, List.of(b2), List.of(b1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_PROPOSAL"));
        assertThat(proposalStatus(original)).isEqualTo("PROPOSED");
    }

    @Test
    void counteringAnUnknownProposalIs404() throws Exception {
        aliceProposes();
        counter(bob, UUID.randomUUID().toString(), List.of(a1), List.of(b1)).andExpect(status().isNotFound());
    }
}
