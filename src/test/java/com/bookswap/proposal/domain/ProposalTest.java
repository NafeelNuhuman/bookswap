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
        p.accept(recipient, T1);
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
                new Action("accept", (p, who) -> p.accept(who, T1), true),
                new Action("reject", (p, who) -> p.reject(who, "no", T1), true),
                new Action("counter", (p, who) -> p.markCountered(who, T1), true),
                new Action("cancel", (p, who) -> p.cancel(who, T1), false)};
        for (Action action : actions) {
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
        assertThatThrownBy(() -> p.accept(proposer, T1)).isInstanceOf(ProposalActionNotAllowedException.class);
        assertThatThrownBy(() -> p.reject(proposer, "x", T1)).isInstanceOf(ProposalActionNotAllowedException.class);
        assertThatThrownBy(() -> p.markCountered(proposer, T1)).isInstanceOf(ProposalActionNotAllowedException.class);
        assertThatThrownBy(() -> p.cancel(recipient, T1)).isInstanceOf(ProposalActionNotAllowedException.class);
        UUID stranger = UUID.randomUUID();
        assertThatThrownBy(() -> p.accept(stranger, T1)).isInstanceOf(ProposalActionNotAllowedException.class);
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.PROPOSED);
        assertThat(p.getUpdatedAt()).isEqualTo(T0);
    }

    @Test
    void lapsedProposalIsNotOpenAtTheExpiryInstant() {
        Proposal p = proposal();
        Instant expiry = p.getExpiresAt();
        assertThat(p.isOpen(expiry.minusMillis(1))).isTrue();
        assertThat(p.isOpen(expiry)).isFalse();
        assertThatThrownBy(() -> p.accept(recipient, expiry)).isInstanceOf(ProposalNotOpenException.class);
        assertThatThrownBy(() -> p.cancel(proposer, expiry.plusSeconds(1))).isInstanceOf(ProposalNotOpenException.class);
        p.accept(recipient, expiry.minusMillis(1));
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
}
