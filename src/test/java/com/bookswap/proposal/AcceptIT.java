package com.bookswap.proposal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AcceptIT extends ProposalTestBase {

    private String copyStatus(String copyId) {
        return jdbc.queryForObject("select status from copy where id = ?::uuid", String.class, copyId);
    }

    private String proposalStatus(String id) {
        return jdbc.queryForObject("select status from proposal where id = ?::uuid", String.class, id);
    }

    @Test
    void acceptReservesEveryCopyOnBothSides() throws Exception {
        jdbc.update("update users set contact_note = 'note of ' || split_part(email, '@', 1)");
        String b1 = addListed(bob, 1, "Book 1");
        String b2 = addListed(bob, 2, "Book 2");
        String a1 = addListed(alice, 3, "Book 3");
        String a2 = addListed(alice, 4, "Book 4");
        String id = proposeOk(alice, List.of(b1, b2), List.of(a1, a2));

        action(bob, id, "accept").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.proposer.email").value("alice@example.com"))
                .andExpect(jsonPath("$.proposer.contactNote").value("note of alice"))
                .andExpect(jsonPath("$.recipient.email").doesNotExist());

        for (String copy : List.of(b1, b2, a1, a2)) {
            assertThat(copyStatus(copy)).isEqualTo("RESERVED");
        }
        assertThat(proposalStatus(id)).isEqualTo("ACCEPTED");
        detail(alice, id).andExpect(jsonPath("$.recipient.email").value("bob@example.com"));
    }

    @Test
    void acceptCancelsEveryCompetingOpenProposalContainingThoseCopies() throws Exception {
        String b1 = addListed(bob, 1, "Book 1");
        String b2 = addListed(bob, 2, "Book 2");
        String a1 = addListed(alice, 3, "Book 3");
        String a2 = addListed(alice, 4, "Book 4");
        String c1 = addListed(carol, 5, "Book 5");
        String winner = proposeOk(alice, List.of(b1), List.of(a1));
        String sharesTheirCopy = proposeOk(carol, List.of(b1), List.of(c1));      // shares bob's b1
        String sharesAlicesCopy = proposeOk(alice, List.of(b2), List.of(a1));     // shares alice's a1
        String unrelated = proposeOk(alice, List.of(b2), List.of(a2));

        action(bob, winner, "accept").andExpect(status().isOk());

        for (String cancelled : List.of(sharesTheirCopy, sharesAlicesCopy)) {
            detail(bob, cancelled).andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.cancelCause").value("COPY_UNAVAILABLE"));
        }
        detail(bob, unrelated).andExpect(jsonPath("$.status").value("PROPOSED"));
        assertThat(proposalStatus(winner)).isEqualTo("ACCEPTED");
    }

    @Test
    void onlyTheRecipientCanAccept() throws Exception {
        String b1 = addListed(bob, 1, "Book 1");
        String a1 = addListed(alice, 3, "Book 3");
        String id = proposeOk(alice, List.of(b1), List.of(a1));

        action(alice, id, "accept").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROPOSAL_ACTION_NOT_ALLOWED"));
        assertThat(copyStatus(b1)).isEqualTo("AVAILABLE");
        assertThat(copyStatus(a1)).isEqualTo("AVAILABLE");
        assertThat(proposalStatus(id)).isEqualTo("PROPOSED");
    }

    @Test
    void aStrangerGets404() throws Exception {
        String id = proposeOk(alice, List.of(addListed(bob, 1, "Book 1")), List.of(addListed(alice, 3, "Book 3")));
        action(carol, id, "accept").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_FOUND"));
        action(bob, java.util.UUID.randomUUID().toString(), "accept").andExpect(status().isNotFound());
    }

    @Test
    void acceptingTwiceIsNotOpen() throws Exception {
        String id = proposeOk(alice, List.of(addListed(bob, 1, "Book 1")), List.of(addListed(alice, 3, "Book 3")));
        action(bob, id, "accept").andExpect(status().isOk());
        action(bob, id, "accept").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_OPEN"));
    }

    @Test
    void aLapsedProposalCannotBeAccepted() throws Exception {
        String b1 = addListed(bob, 1, "Book 1");
        String a1 = addListed(alice, 3, "Book 3");
        String id = proposeOk(alice, List.of(b1), List.of(a1));
        jdbc.update("update proposal set expires_at = now() - interval '1 minute' where id = ?::uuid", id);

        action(bob, id, "accept").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_OPEN"));
        assertThat(copyStatus(b1)).isEqualTo("AVAILABLE");
        assertThat(copyStatus(a1)).isEqualTo("AVAILABLE");
    }

    @Test
    void acceptFailsAtomicallyWhenOneCopyIsNoLongerAvailable() throws Exception {
        String b1 = addListed(bob, 1, "Book 1");
        String a1 = addListed(alice, 3, "Book 3");
        String a2 = addListed(alice, 4, "Book 4");
        String id = proposeOk(alice, List.of(b1), List.of(a1, a2));
        jdbc.update("update copy set status = 'UNLISTED' where id = ?::uuid", a2);

        action(bob, id, "accept").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COPY_NOT_AVAILABLE"));
        assertThat(copyStatus(b1)).isEqualTo("AVAILABLE");
        assertThat(copyStatus(a1)).isEqualTo("AVAILABLE");
        assertThat(copyStatus(a2)).isEqualTo("UNLISTED");
        assertThat(proposalStatus(id)).isEqualTo("PROPOSED");
    }

    @Test
    void acceptedCopiesDisappearFromFeedSearchAndTheShelf() throws Exception {
        String b1 = addListed(bob, 1, "Book 1");
        String a1 = addListed(alice, 3, "Book 3");
        String id = proposeOk(alice, List.of(b1), List.of(a1));
        feed(alice).andExpect(jsonPath("$.totalElements").value(1));
        action(bob, id, "accept").andExpect(status().isOk());

        feed(alice).andExpect(jsonPath("$.totalElements").value(0));
        feed(bob).andExpect(jsonPath("$.totalElements").value(0));
        search(alice, "q", "Book").andExpect(jsonPath("$.totalElements").value(0));
    }
}
