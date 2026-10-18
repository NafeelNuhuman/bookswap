package com.bookswap.proposal.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProposalTest {
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-02T00:00:00Z");
    private static final Duration TTL = Duration.ofDays(14);
    private static final Duration WINDOW = Duration.ofDays(14);

    private final UUID proposer = UUID.randomUUID();
    private final UUID recipient = UUID.randomUUID();

    private Proposal proposal() {
        return Proposal.create(proposer, recipient, "hello", null, T0, TTL);
    }

    private static Proposal withStatus(Proposal p, ProposalStatus status) throws Exception {
        Field f = Proposal.class.getDeclaredField("status");
        f.setAccessible(true);
        f.set(p, status);
        return p;
    }

    @Test
    void createStartsProposedWithExpiryFourteenDaysOut() {
        Proposal p = Proposal.create(proposer, recipient, "hi", null, T0, TTL);
        assertThat(p.getId()).isNotNull();
        assertThat(p.isNew()).isTrue();
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.PROPOSED);
        assertThat(p.getProposerId()).isEqualTo(proposer);
        assertThat(p.getRecipientId()).isEqualTo(recipient);
        assertThat(p.getMessage()).isEqualTo("hi");
        assertThat(p.getParentId()).isNull();
        assertThat(p.getCancelCause()).isNull();
        assertThat(p.getCreatedAt()).isEqualTo(T0);
        assertThat(p.getExpiresAt()).isEqualTo(T0.plus(TTL));
    }

    @Test
    void createKeepsTheParentLink() {
        UUID parent = UUID.randomUUID();
        assertThat(Proposal.create(recipient, proposer, null, parent, T0, TTL).getParentId()).isEqualTo(parent);
    }

    @Test
    void acceptSetsAccepted() {
        Proposal p = proposal();
        p.accept(recipient, T1, WINDOW);
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(p.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void rejectStoresReasonAndSetsRejected() {
        Proposal p = proposal();
        p.reject(recipient, "not my taste", T1);
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(p.getRejectReason()).isEqualTo("not my taste");
        assertThat(p.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void rejectWithoutReasonIsFine() {
        Proposal p = proposal();
        p.reject(recipient, null, T1);
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(p.getRejectReason()).isNull();
    }

    @Test
    void cancelSetsCancelledWithdrawn() {
        Proposal p = proposal();
        p.cancel(proposer, T1);
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.CANCELLED);
        assertThat(p.getCancelCause()).isEqualTo(CancelCause.WITHDRAWN);
    }

    @Test
    void markCounteredSetsCountered() {
        Proposal p = proposal();
        p.markCountered(recipient, T1);
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.COUNTERED);
    }

    @ParameterizedTest
    @EnumSource(value = ProposalStatus.class, names = "PROPOSED", mode = EnumSource.Mode.EXCLUDE)
    void everyActionFromEveryNonProposedStatusIsNotOpen(ProposalStatus status) throws Exception {
        record Action(String name, BiConsumer<Proposal, UUID> run, boolean byRecipient) {}
        Action[] actions = {
                new Action("accept", (p, who) -> p.accept(who, T1, WINDOW), true),
                new Action("reject", (p, who) -> p.reject(who, "no", T1), true),
                new Action("counter", (p, who) -> p.markCountered(who, T1), true),
                new Action("cancel", (p, who) -> p.cancel(who, T1), false)};
        for (Action action : actions) {
            if (status == ProposalStatus.ACCEPTED && action.name().equals("cancel")) {
                continue;   // an accepted exchange can be called off: see the completion tests below
            }
            Proposal p = withStatus(proposal(), status);
            UUID actor = action.byRecipient() ? recipient : proposer;
            assertThatThrownBy(() -> action.run().accept(p, actor))
                    .as("%s from %s", action.name(), status).isInstanceOf(ProposalNotOpenException.class);
            assertThat(p.getStatus()).isEqualTo(status);
            assertThat(p.getUpdatedAt()).isEqualTo(T0);
        }
    }

    @Test
    void wrongRoleIsForbiddenAndUnchanged() {
        Proposal p = proposal();
        assertThatThrownBy(() -> p.accept(proposer, T1, WINDOW)).isInstanceOf(ProposalActionNotAllowedException.class);
        assertThatThrownBy(() -> p.reject(proposer, "x", T1)).isInstanceOf(ProposalActionNotAllowedException.class);
        assertThatThrownBy(() -> p.markCountered(proposer, T1)).isInstanceOf(ProposalActionNotAllowedException.class);
        assertThatThrownBy(() -> p.cancel(recipient, T1)).isInstanceOf(ProposalActionNotAllowedException.class);
        UUID stranger = UUID.randomUUID();
        assertThatThrownBy(() -> p.accept(stranger, T1, WINDOW)).isInstanceOf(ProposalActionNotAllowedException.class);
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.PROPOSED);
        assertThat(p.getUpdatedAt()).isEqualTo(T0);
    }

    @Test
    void lapsedProposalIsNotOpenAtTheExpiryInstant() {
        Proposal p = proposal();
        Instant expiry = p.getExpiresAt();
        assertThat(p.isOpen(expiry.minusMillis(1))).isTrue();
        assertThat(p.isOpen(expiry)).isFalse();
        assertThatThrownBy(() -> p.accept(recipient, expiry, WINDOW)).isInstanceOf(ProposalNotOpenException.class);
        assertThatThrownBy(() -> p.cancel(proposer, expiry.plusSeconds(1))).isInstanceOf(ProposalNotOpenException.class);
        p.accept(recipient, expiry.minusMillis(1), WINDOW);
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
    }

    @Test
    void effectiveStatusReportsExpiredWhenLapsed() throws Exception {
        Proposal p = proposal();
        assertThat(p.effectiveStatus(T1)).isEqualTo(ProposalStatus.PROPOSED);
        assertThat(p.effectiveStatus(p.getExpiresAt())).isEqualTo(ProposalStatus.EXPIRED);
        Proposal accepted = withStatus(proposal(), ProposalStatus.ACCEPTED);
        assertThat(accepted.effectiveStatus(accepted.getExpiresAt().plusSeconds(1))).isEqualTo(ProposalStatus.ACCEPTED);
    }

    @Test
    void involvesOnlyTheTwoParties() {
        Proposal p = proposal();
        assertThat(p.involves(proposer)).isTrue();
        assertThat(p.involves(recipient)).isTrue();
        assertThat(p.involves(UUID.randomUUID())).isFalse();
    }

    // ---- completion: confirm, cancel after accept, pickup note, settle ----

    private static final Instant DEADLINE = T1.plus(WINDOW);

    private Proposal accepted() {
        Proposal p = proposal();
        p.accept(recipient, T1, WINDOW);
        return p;
    }

    @Test
    void acceptRecordsWhenAndTheConfirmDeadline() {
        Proposal p = accepted();
        assertThat(p.getAcceptedAt()).isEqualTo(T1);
        assertThat(p.getConfirmBy()).isEqualTo(DEADLINE);
    }

    @Test
    void firstConfirmationIsRecordedAndTheExchangeStaysAccepted() {
        Proposal p = accepted();
        assertThat(p.confirm(proposer, T1.plusSeconds(5))).isEqualTo(Proposal.ConfirmOutcome.RECORDED);
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(p.hasConfirmed(proposer)).isTrue();
        assertThat(p.hasConfirmed(recipient)).isFalse();
        assertThat(p.getProposerConfirmedAt()).isEqualTo(T1.plusSeconds(5));
        assertThat(p.getCompletedAt()).isNull();
    }

    @Test
    void secondConfirmationCompletesTheExchange() {
        Proposal p = accepted();
        p.confirm(recipient, T1.plusSeconds(5));
        Instant later = T1.plusSeconds(60);
        assertThat(p.confirm(proposer, later)).isEqualTo(Proposal.ConfirmOutcome.COMPLETED);
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.COMPLETED);
        assertThat(p.getCompletedAt()).isEqualTo(later);
        assertThat(p.getRecipientConfirmedAt()).isEqualTo(T1.plusSeconds(5));
        assertThat(p.getProposerConfirmedAt()).isEqualTo(later);
    }

    @Test
    void confirmingTwiceIsAlreadyConfirmed() {
        Proposal p = accepted();
        p.confirm(proposer, T1);
        assertThatThrownBy(() -> p.confirm(proposer, T1.plusSeconds(1))).isInstanceOf(AlreadyConfirmedException.class);
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
    }

    @Test
    void aStrangerCannotConfirmOrWriteTheNoteOrCancelAnAcceptedExchange() {
        Proposal p = accepted();
        UUID stranger = UUID.randomUUID();
        assertThatThrownBy(() -> p.confirm(stranger, T1)).isInstanceOf(ProposalActionNotAllowedException.class);
        assertThatThrownBy(() -> p.setPickupNote(stranger, "x", T1)).isInstanceOf(ProposalActionNotAllowedException.class);
        assertThatThrownBy(() -> p.cancel(stranger, T1)).isInstanceOf(ProposalActionNotAllowedException.class);
    }

    @Test
    void confirmIsOnlyOpenWhileAcceptedAndBeforeTheDeadline() throws Exception {
        for (ProposalStatus status : ProposalStatus.values()) {
            if (status == ProposalStatus.ACCEPTED) {
                continue;
            }
            Proposal other = withStatus(proposal(), status);
            assertThatThrownBy(() -> other.confirm(proposer, T1)).as("confirm from %s", status)
                    .isInstanceOf(ProposalNotOpenException.class);
        }
        Proposal p = accepted();
        assertThatThrownBy(() -> p.confirm(proposer, DEADLINE)).isInstanceOf(ProposalNotOpenException.class);
        assertThat(p.confirm(proposer, DEADLINE.minusMillis(1))).isEqualTo(Proposal.ConfirmOutcome.RECORDED);
    }

    @Test
    void cancellingAnAcceptedExchangeNobodyConfirmedIsOpenToEitherPartyAndReportsTheRelease() {
        for (UUID who : new UUID[]{proposer, recipient}) {
            Proposal p = accepted();
            assertThat(p.cancel(who, T1.plusSeconds(1))).isTrue();
            assertThat(p.getStatus()).isEqualTo(ProposalStatus.CANCELLED);
            assertThat(p.getCancelCause()).isEqualTo(CancelCause.WITHDRAWN);
        }
    }

    @Test
    void cancellingAProposedProposalReportsNoRelease() {
        assertThat(proposal().cancel(proposer, T1)).isFalse();
    }

    @Test
    void afterOneConfirmationOnlyTheOtherPartyMayCancel() {
        Proposal p = accepted();
        p.confirm(proposer, T1.plusSeconds(1));
        assertThatThrownBy(() -> p.cancel(proposer, T1.plusSeconds(2))).isInstanceOf(ProposalActionNotAllowedException.class);
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(p.cancel(recipient, T1.plusSeconds(2))).isTrue();
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.CANCELLED);
    }

    @Test
    void cancelOfAnAcceptedExchangeIsNotOpenAtTheDeadlineAndForClosedStates() throws Exception {
        Proposal p = accepted();
        assertThatThrownBy(() -> p.cancel(proposer, DEADLINE)).isInstanceOf(ProposalNotOpenException.class);
        for (ProposalStatus status : new ProposalStatus[]{ProposalStatus.COMPLETED, ProposalStatus.CANCELLED,
                ProposalStatus.REJECTED, ProposalStatus.COUNTERED, ProposalStatus.EXPIRED}) {
            Proposal closed = withStatus(proposal(), status);
            assertThatThrownBy(() -> closed.cancel(proposer, T1)).as("cancel from %s", status)
                    .isInstanceOf(ProposalNotOpenException.class);
        }
    }

    @Test
    void eitherPartyCanSetAndClearThePickupNoteWhileAccepted() {
        Proposal p = accepted();
        p.setPickupNote(proposer, "Cafe at 5", T1.plusSeconds(1));
        assertThat(p.getPickupNote()).isEqualTo("Cafe at 5");
        p.setPickupNote(recipient, null, T1.plusSeconds(2));
        assertThat(p.getPickupNote()).isNull();
        assertThat(p.getUpdatedAt()).isEqualTo(T1.plusSeconds(2));
    }

    @Test
    void pickupNoteIsNotOpenOutsideTheAcceptedWindow() throws Exception {
        assertThatThrownBy(() -> proposal().setPickupNote(proposer, "x", T1)).isInstanceOf(ProposalNotOpenException.class);
        assertThatThrownBy(() -> accepted().setPickupNote(proposer, "x", DEADLINE)).isInstanceOf(ProposalNotOpenException.class);
        Proposal done = withStatus(accepted(), ProposalStatus.COMPLETED);
        assertThatThrownBy(() -> done.setPickupNote(proposer, "x", T1)).isInstanceOf(ProposalNotOpenException.class);
    }

    @Test
    void settleDoesNothingBeforeTheDeadlineOrOutsideAccepted() throws Exception {
        Proposal p = accepted();
        assertThat(p.settle(DEADLINE.minusMillis(1))).isEqualTo(Proposal.Settlement.NONE);
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
        Proposal proposed = proposal();
        assertThat(proposed.settle(T0.plus(TTL).plusSeconds(1))).isEqualTo(Proposal.Settlement.NONE);
        assertThat(proposed.getStatus()).isEqualTo(ProposalStatus.PROPOSED);
        Proposal done = withStatus(accepted(), ProposalStatus.COMPLETED);
        assertThat(done.settle(DEADLINE.plusSeconds(1))).isEqualTo(Proposal.Settlement.NONE);
    }

    @Test
    void settleCompletesAnExchangeWithOneConfirmation() {
        Proposal p = accepted();
        p.confirm(recipient, T1.plusSeconds(1));
        assertThat(p.settle(DEADLINE)).isEqualTo(Proposal.Settlement.COMPLETED);
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.COMPLETED);
        assertThat(p.getCompletedAt()).isEqualTo(DEADLINE);
        assertThat(p.settle(DEADLINE.plusSeconds(1))).isEqualTo(Proposal.Settlement.NONE);
    }

    @Test
    void settleCancelsAnExchangeNobodyConfirmedAsNoShow() {
        Proposal p = accepted();
        assertThat(p.settle(DEADLINE)).isEqualTo(Proposal.Settlement.CANCELLED);
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.CANCELLED);
        assertThat(p.getCancelCause()).isEqualTo(CancelCause.NO_SHOW);
        assertThat(p.settle(DEADLINE.plusSeconds(1))).isEqualTo(Proposal.Settlement.NONE);
    }
}
