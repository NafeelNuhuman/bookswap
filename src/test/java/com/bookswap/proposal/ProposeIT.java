package com.bookswap.proposal;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProposeIT extends ProposalTestBase {

    private String copyStatus(String copyId) {
        return jdbc.queryForObject("select status from copy where id = ?::uuid", String.class, copyId);
    }

    @Test
    void proposeReturns201WithBothSidesAndTheGradesSeen() throws Exception {
        String bobBook = addListed(bob, 1, "Book 1");
        String a1 = addListed(alice, 3, "Book 3");
        String a2 = addListed(alice, 4, "Book 4");

        JsonNode view = read(propose(alice, List.of(bobBook), List.of(a1, a2), "Swap?")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROPOSED"))
                .andExpect(jsonPath("$.message").value("Swap?"))
                .andExpect(jsonPath("$.proposer.id").value(userId(alice)))
                .andExpect(jsonPath("$.proposer.displayName").value("Alice"))
                .andExpect(jsonPath("$.recipient.id").value(userId(bob)))
                .andExpect(jsonPath("$.recipient.city").value("Berlin"))
                .andExpect(jsonPath("$.requested.length()").value(1))
                .andExpect(jsonPath("$.offered.length()").value(2))
                .andExpect(jsonPath("$.requested[0].copyId").value(bobBook))
                .andExpect(jsonPath("$.requested[0].title").value("Book 1"))
                .andExpect(jsonPath("$.requested[0].gradeAtProposal").value("GOOD"))
                .andExpect(jsonPath("$.requested[0].detailsSource").value("CATALOG"))
                .andExpect(jsonPath("$.parentId").doesNotExist()));

        Duration untilExpiry = Duration.between(Instant.now(), Instant.parse(view.get("expiresAt").asText()));
        assertThat(untilExpiry).isBetween(Duration.ofDays(14).minusMinutes(5), Duration.ofDays(14).plusMinutes(5));
    }

    @Test
    void sameCopyCanBeInSeveralOpenProposals() throws Exception {
        String bobBook = addListed(bob, 1, "Book 1");
        String a1 = addListed(alice, 3, "Book 3");
        String a2 = addListed(alice, 4, "Book 4");
        String c1 = addListed(carol, 5, "Book 5");

        proposeOk(alice, List.of(bobBook), List.of(a1));
        proposeOk(alice, List.of(bobBook), List.of(a2));
        proposeOk(carol, List.of(bobBook), List.of(c1));
        assertThat(jdbc.queryForObject("select count(*) from proposal where status = 'PROPOSED'", Integer.class)).isEqualTo(3);
    }

    @Test
    void proposalDoesNotReserveAnyCopy() throws Exception {
        String bobBook = addListed(bob, 1, "Book 1");
        String a1 = addListed(alice, 3, "Book 3");
        proposeOk(alice, List.of(bobBook), List.of(a1));
        assertThat(copyStatus(bobBook)).isEqualTo("AVAILABLE");
        assertThat(copyStatus(a1)).isEqualTo("AVAILABLE");
    }

    @Test
    void unlistedAndReservedCopiesCannotBeProposed() throws Exception {
        String bobBook = addListed(bob, 1, "Book 1");
        String a1 = addListed(alice, 3, "Book 3");

        jdbc.update("update copy set status = 'UNLISTED' where id = ?::uuid", bobBook);
        propose(alice, List.of(bobBook), List.of(a1), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COPY_NOT_AVAILABLE"));

        jdbc.update("update copy set status = 'AVAILABLE' where id = ?::uuid", bobBook);
        jdbc.update("update copy set status = 'RESERVED' where id = ?::uuid", a1);
        propose(alice, List.of(bobBook), List.of(a1), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COPY_NOT_AVAILABLE"));
        assertThat(jdbc.queryForObject("select count(*) from proposal", Integer.class)).isZero();
    }

    @Test
    void cannotOfferSomeoneElsesCopyOrAnUnknownOne() throws Exception {
        String bobBook = addListed(bob, 1, "Book 1");
        String c1 = addListed(carol, 5, "Book 5");
        propose(alice, List.of(bobBook), List.of(c1), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COPY_NOT_AVAILABLE"));
        propose(alice, List.of(bobBook), List.of(UUID.randomUUID().toString()), null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COPY_NOT_AVAILABLE"));
    }

    @Test
    void requestedCopiesFromTwoOwnersOrYourselfAreRejected() throws Exception {
        String bobBook = addListed(bob, 1, "Book 1");
        String c1 = addListed(carol, 5, "Book 5");
        String a1 = addListed(alice, 3, "Book 3");
        String a2 = addListed(alice, 4, "Book 4");
        propose(alice, List.of(bobBook, c1), List.of(a1), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROPOSAL"));
        propose(alice, List.of(a1), List.of(a2), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_PROPOSAL"));
    }

    @Test
    void badShapesAre400InvalidProposalNeverA500() throws Exception {
        String bobBook = addListed(bob, 1, "Book 1");
        String a1 = addListed(alice, 3, "Book 3");
        List<String> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            six.add(UUID.randomUUID().toString());
        }
        propose(alice, List.of(), List.of(a1), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROPOSAL"));
        propose(alice, List.of(bobBook), List.of(), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROPOSAL"));
        propose(alice, List.of(bobBook), six, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROPOSAL"));
        propose(alice, List.of(bobBook), List.of(a1, a1), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROPOSAL"));
        propose(alice, List.of(bobBook), List.of(a1), "x".repeat(501)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROPOSAL"));
        propose(alice, List.of(bobBook), List.of(a1), "a\u0000b").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROPOSAL"));
        proposeRaw(alice, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROPOSAL"));
        proposeRaw(alice, "{\"requestedCopyIds\":[null],\"offeredCopyIds\":[\"" + a1 + "\"]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PROPOSAL"));
        proposeRaw(alice, "{\"requestedCopyIds\":[\"not-a-uuid\"],\"offeredCopyIds\":[]}")
                .andExpect(status().isBadRequest());
        propose(alice, List.of(bobBook), List.of(a1), "x".repeat(500)).andExpect(status().isCreated());
    }

    @Test
    void detailIsVisibleToBothPartiesButNotToAnyoneElse() throws Exception {
        String id = proposeOk(alice, List.of(addListed(bob, 1, "Book 1")), List.of(addListed(alice, 3, "Book 3")));
        detail(alice, id).andExpect(status().isOk());
        detail(bob, id).andExpect(status().isOk());
        detail(carol, id).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROPOSAL_NOT_FOUND"));
        detail(alice, UUID.randomUUID().toString()).andExpect(status().isNotFound());
    }

    @Test
    void noEmailOrContactNoteBeforeAcceptance() throws Exception {
        jdbc.update("update users set contact_note = 'secret-note'");
        String id = proposeOk(alice, List.of(addListed(bob, 1, "Book 1")), List.of(addListed(alice, 3, "Book 3")));
        for (JsonNode who : List.of(alice, bob)) {
            String body = detail(who, id).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("@example.com", "email", "contactNote", "secret-note", "postalCode", "10115");
        }
        String created = propose(alice, List.of(addListed(bob, 7, "Book 7")), List.of(addListed(alice, 8, "Book 8")), null)
                .andReturn().getResponse().getContentAsString();
        assertThat(created).doesNotContain("@example.com", "contactNote", "secret-note");
    }

    @Test
    void acceptedDetailRevealsOnlyTheOtherPartysContact() throws Exception {
        jdbc.update("update users set contact_note = 'note of ' || split_part(email, '@', 1)");
        String id = proposeOk(alice, List.of(addListed(bob, 1, "Book 1")), List.of(addListed(alice, 3, "Book 3")));
        jdbc.update("update proposal set status = 'ACCEPTED' where id = ?::uuid", id);

        detail(bob, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.proposer.email").value("alice@example.com"))
                .andExpect(jsonPath("$.proposer.contactNote").value("note of alice"))
                .andExpect(jsonPath("$.recipient.email").doesNotExist())
                .andExpect(jsonPath("$.recipient.contactNote").doesNotExist());
        detail(alice, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.recipient.email").value("bob@example.com"))
                .andExpect(jsonPath("$.recipient.contactNote").value("note of bob"))
                .andExpect(jsonPath("$.proposer.email").doesNotExist());
        detail(carol, id).andExpect(status().isNotFound());
    }

    @Test
    void lapsedProposalReadsAsExpired() throws Exception {
        String id = proposeOk(alice, List.of(addListed(bob, 1, "Book 1")), List.of(addListed(alice, 3, "Book 3")));
        jdbc.update("update proposal set expires_at = now() - interval '1 minute' where id = ?::uuid", id);
        detail(bob, id).andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    void malformedProposalIdIs400() throws Exception {
        mvc.perform(get("/api/proposals/not-a-uuid").header("Authorization", bearer(alice)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void proposeAndDetailRequireAuthentication() throws Exception {
        mvc.perform(post("/api/proposals").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/proposals/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }
}
