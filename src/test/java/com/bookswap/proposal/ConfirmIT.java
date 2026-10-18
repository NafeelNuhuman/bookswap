package com.bookswap.proposal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConfirmIT extends ProposalTestBase {

    private String b1;
    private String b2;
    private String a1;
    private String a2;

    /** A 2-for-2 swap, accepted: alice offers a1+a2, bob gives b1+b2. */
    private String acceptedSwap() throws Exception {
        b1 = addListed(bob, 1, "Book 1");
        b2 = addListed(bob, 2, "Book 2");
        a1 = addListed(alice, 3, "Book 3");
        a2 = addListed(alice, 4, "Book 4");
        String id = proposeOk(alice, List.of(b1, b2), List.of(a1, a2));
        action(bob, id, "accept").andExpect(status().isOk());
        return id;
    }

    private String copyOwner(String copyId) {
        return jdbc.queryForObject("select owner_id::text from copy where id = ?::uuid", String.class, copyId);
    }

    private String copyStatus(String copyId) {
        return jdbc.queryForObject("select status from copy where id = ?::uuid", String.class, copyId);
    }

    private String proposalStatus(String id) {
        return jdbc.queryForObject("select status from proposal where id = ?::uuid", String.class, id);
    }

    @Test
    void acceptingSetsTheConfirmDeadlineFourteenDaysOut() throws Exception {
        String id = acceptedSwap();
        detail(alice, id).andExpect(jsonPath("$.acceptedAt").isNotEmpty())
                .andExpect(jsonPath("$.confirmBy").isNotEmpty())
                .andExpect(jsonPath("$.confirmedByMe").value(false))
                .andExpect(jsonPath("$.confirmedByOther").value(false))
                .andExpect(jsonPath("$.completedAt").doesNotExist());
        assertThat(jdbc.queryForObject(
                "select extract(epoch from confirm_by - accepted_at) from proposal where id = ?::uuid", Long.class, id))
                .isEqualTo(14L * 24 * 3600);
    }

    @Test
    void firstConfirmationKeepsTheExchangeAccepted() throws Exception {
        String id = acceptedSwap();
        action(alice, id, "confirm").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.confirmedByMe").value(true))
                .andExpect(jsonPath("$.confirmedByOther").value(false));
        detail(bob, id).andExpect(jsonPath("$.confirmedByMe").value(false))
                .andExpect(jsonPath("$.confirmedByOther").value(true));
        for (String copy : List.of(b1, b2, a1, a2)) {
            assertThat(copyStatus(copy)).isEqualTo("RESERVED");
        }
    }

    @Test
    void secondConfirmationCompletesAndTransfersEveryCopy() throws Exception {
        String id = acceptedSwap();
        String aliceId = userId(alice);
        String bobId = userId(bob);
        action(alice, id, "confirm").andExpect(status().isOk());
        action(bob, id, "confirm").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        assertThat(proposalStatus(id)).isEqualTo("COMPLETED");
        for (String copy : List.of(b1, b2)) {
            assertThat(copyOwner(copy)).isEqualTo(aliceId);
        }
        for (String copy : List.of(a1, a2)) {
            assertThat(copyOwner(copy)).isEqualTo(bobId);
        }
        for (String copy : List.of(b1, b2, a1, a2)) {
            assertThat(copyStatus(copy)).isEqualTo("UNLISTED");
        }
        feed(alice).andExpect(jsonPath("$.totalElements").value(0));
        feed(carol).andExpect(jsonPath("$.totalElements").value(0));
        search(carol, "q", "Book").andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/copies").header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/copies/" + a1).header("Authorization", bearer(bob))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNLISTED"));
        mvc.perform(get("/api/copies/" + a1).header("Authorization", bearer(alice))).andExpect(status().isNotFound());
    }

    @Test
    void confirmingTwiceIsAlreadyConfirmed() throws Exception {
        String id = acceptedSwap();
        action(alice, id, "confirm").andExpect(status().isOk());
        action(alice, id, "confirm").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CONFIRMED"));
        assertThat(proposalStatus(id)).isEqualTo("ACCEPTED");
    }

    @Test
    void aStrangerGets404() throws Exception {
        String id = acceptedSwap();
        action(carol, id, "confirm").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_FOUND"));
        action(alice, java.util.UUID.randomUUID().toString(), "confirm").andExpect(status().isNotFound());
    }

    @Test
    void confirmingAProposedOrClosedProposalIsNotOpen() throws Exception {
        b1 = addListed(bob, 1, "Book 1");
        a1 = addListed(alice, 3, "Book 3");
        String id = proposeOk(alice, List.of(b1), List.of(a1));
        action(alice, id, "confirm").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_OPEN"));
        action(alice, id, "cancel").andExpect(status().isOk());
        action(alice, id, "confirm").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_OPEN"));
    }

    @Test
    void confirmingPastTheDeadlineIsNotOpen() throws Exception {
        String id = acceptedSwap();
        jdbc.update("update proposal set confirm_by = now() - interval '1 minute' where id = ?::uuid", id);
        action(alice, id, "confirm").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_OPEN"));
    }

    @Test
    void completionIsAtomicWhenACopyIsNotReservedAnyMore() throws Exception {
        String id = acceptedSwap();
        action(alice, id, "confirm").andExpect(status().isOk());
        jdbc.update("update copy set status = 'UNLISTED' where id = ?::uuid", b2);   // cannot happen through the API

        action(bob, id, "confirm").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COPY_NOT_AVAILABLE"));

        assertThat(proposalStatus(id)).isEqualTo("ACCEPTED");
        assertThat(copyOwner(a1)).isEqualTo(userId(alice));
        assertThat(copyOwner(b1)).isEqualTo(userId(bob));
        assertThat(copyStatus(b1)).isEqualTo("RESERVED");
        assertThat(jdbc.queryForObject("select recipient_confirmed_at is null from proposal where id = ?::uuid",
                Boolean.class, id)).isTrue();
    }

    @Test
    void contactDetailsAreStillOnlyForTheOtherPartyWhileAccepted() throws Exception {
        String id = acceptedSwap();
        action(alice, id, "confirm").andExpect(status().isOk())
                .andExpect(jsonPath("$.recipient.email").value("bob@example.com"))
                .andExpect(jsonPath("$.proposer.email").doesNotExist());
    }

    @Test
    void listCardsDoNotCarryConfirmationState() throws Exception {
        acceptedSwap();
        mvc.perform(get("/api/proposals").param("role", "outgoing").header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$.content[0].confirmedByMe").doesNotExist())
                .andExpect(jsonPath("$.content[0].proposer.email").doesNotExist())
                .andExpect(jsonPath("$.content[0].recipient.email").doesNotExist());
    }
}
