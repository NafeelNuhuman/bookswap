package com.bookswap.proposal.service;

import com.bookswap.book.domain.Book;
import com.bookswap.copy.domain.ConditionGrade;
import com.bookswap.copy.domain.Copy;
import com.bookswap.copy.domain.CopyNotAvailableException;
import com.bookswap.copy.domain.CopyStatus;
import com.bookswap.copy.repository.CopyRepository;
import com.bookswap.proposal.domain.InvalidProposalException;
import com.bookswap.proposal.domain.Proposal;
import com.bookswap.proposal.domain.ProposalItem;
import com.bookswap.proposal.domain.ProposalNotFoundException;
import com.bookswap.proposal.domain.ProposalStatus;
import com.bookswap.proposal.domain.SelfProposalException;
import com.bookswap.proposal.domain.Side;
import com.bookswap.proposal.repository.ProposalItemRepository;
import com.bookswap.proposal.repository.ProposalRepository;
import com.bookswap.proposal.web.ProposalRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProposalServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock ProposalRepository proposals;
    @Mock ProposalItemRepository items;
    @Mock CopyRepository copies;
    @Mock ProposalViewAssembler assembler;
    ProposalService service;

    UUID me = UUID.randomUUID();
    UUID other = UUID.randomUUID();
    Book book = new Book("9780140328721", "Fantastic Mr Fox", "Roald Dahl", null, NOW);

    @BeforeEach
    void setUp() {
        service = new ProposalService(proposals, items, copies, assembler,
                new ProposalProperties(Duration.ofDays(14)), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Copy available(UUID owner, ConditionGrade grade) {
        Copy c = new Copy(owner, book, grade, NOW);
        c.list(true, NOW);
        return c;
    }

    private Copy unlisted(UUID owner) {
        return new Copy(owner, book, ConditionGrade.GOOD, NOW);
    }

    private ProposalRequest request(List<Copy> requested, List<Copy> offered, String message) {
        return new ProposalRequest(requested.stream().map(Copy::getId).toList(),
                offered.stream().map(Copy::getId).toList(), message);
    }

    private void loads(Copy... all) {
        when(copies.lockForShare(anyCollection())).thenReturn(List.of(all));
    }

    @Test
    void proposeSavesProposalAndItemsWithGradesAtProposalTime() {
        Copy wanted = available(other, ConditionGrade.LIKE_NEW);
        Copy mine = available(me, ConditionGrade.POOR);
        loads(wanted, mine);
        when(proposals.save(any(Proposal.class))).thenAnswer(i -> i.getArgument(0));

        service.propose(me, request(List.of(wanted), List.of(mine), "  Swap?  "));

        ArgumentCaptor<Proposal> saved = ArgumentCaptor.forClass(Proposal.class);
        verify(proposals).save(saved.capture());
        Proposal p = saved.getValue();
        assertThat(p.getProposerId()).isEqualTo(me);
        assertThat(p.getRecipientId()).isEqualTo(other);
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.PROPOSED);
        assertThat(p.getMessage()).isEqualTo("Swap?");
        assertThat(p.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ProposalItem>> savedItems = ArgumentCaptor.forClass(Iterable.class);
        verify(items).saveAll(savedItems.capture());
        List<ProposalItem> list = new ArrayList<>();
        savedItems.getValue().forEach(list::add);
        assertThat(list).hasSize(2);
        assertThat(list).anySatisfy(i -> {
            assertThat(i.getCopyId()).isEqualTo(wanted.getId());
            assertThat(i.getSide()).isEqualTo(Side.REQUESTED);
            assertThat(i.getGradeAtProposal()).isEqualTo(ConditionGrade.LIKE_NEW);
            assertThat(i.getProposalId()).isEqualTo(p.getId());
        });
        assertThat(list).anySatisfy(i -> {
            assertThat(i.getCopyId()).isEqualTo(mine.getId());
            assertThat(i.getSide()).isEqualTo(Side.OFFERED);
            assertThat(i.getGradeAtProposal()).isEqualTo(ConditionGrade.POOR);
        });
    }

    @Test
    void blankMessageIsStoredAsNull() {
        Copy wanted = available(other, ConditionGrade.GOOD);
        Copy mine = available(me, ConditionGrade.GOOD);
        loads(wanted, mine);
        when(proposals.save(any(Proposal.class))).thenAnswer(i -> i.getArgument(0));

        service.propose(me, request(List.of(wanted), List.of(mine), "   "));

        ArgumentCaptor<Proposal> saved = ArgumentCaptor.forClass(Proposal.class);
        verify(proposals).save(saved.capture());
        assertThat(saved.getValue().getMessage()).isNull();
    }

    @Test
    void emptyOrNullListsAreInvalid() {
        UUID id = UUID.randomUUID();
        assertInvalid(new ProposalRequest(List.of(), List.of(id), null));
        assertInvalid(new ProposalRequest(List.of(id), List.of(), null));
        assertInvalid(new ProposalRequest(null, List.of(id), null));
        assertInvalid(new ProposalRequest(List.of(id), null, null));
    }

    @Test
    void moreThanFiveOnEitherSideIsInvalid() {
        List<UUID> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            six.add(UUID.randomUUID());
        }
        List<UUID> one = List.of(UUID.randomUUID());
        assertInvalid(new ProposalRequest(six, one, null));
        assertInvalid(new ProposalRequest(one, six, null));
    }

    @Test
    void duplicateIdsAreInvalid() {
        UUID id = UUID.randomUUID();
        assertInvalid(new ProposalRequest(List.of(id, id), List.of(UUID.randomUUID()), null));
        assertInvalid(new ProposalRequest(List.of(UUID.randomUUID()), List.of(id, id), null));
        assertInvalid(new ProposalRequest(List.of(id), List.of(id), null));
    }

    @Test
    void nullElementIsInvalid() {
        List<UUID> withNull = new ArrayList<>();
        withNull.add(null);
        assertInvalid(new ProposalRequest(withNull, List.of(UUID.randomUUID()), null));
        assertInvalid(new ProposalRequest(List.of(UUID.randomUUID()), withNull, null));
    }

    @Test
    void messageOver500OrWithNulIsInvalid() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertInvalid(new ProposalRequest(List.of(a), List.of(b), "x".repeat(501)));
        assertInvalid(new ProposalRequest(List.of(a), List.of(b), "a\u0000b"));
    }

    private void assertInvalid(ProposalRequest request) {
        assertThatThrownBy(() -> service.propose(me, request)).isInstanceOf(InvalidProposalException.class);
        verify(proposals, never()).save(any());
    }

    @Test
    void unknownOrUnavailableOrForeignOfferedCopyIsCopyNotAvailable() {
        Copy wanted = available(other, ConditionGrade.GOOD);
        Copy someoneElses = available(UUID.randomUUID(), ConditionGrade.GOOD);
        loads(wanted, someoneElses);
        assertThatThrownBy(() -> service.propose(me, request(List.of(wanted), List.of(someoneElses), null)))
                .isInstanceOf(CopyNotAvailableException.class);

        Copy notListed = unlisted(me);
        loads(wanted, notListed);
        assertThatThrownBy(() -> service.propose(me, request(List.of(wanted), List.of(notListed), null)))
                .isInstanceOf(CopyNotAvailableException.class);

        Copy missing = available(me, ConditionGrade.GOOD);
        loads(wanted);
        assertThatThrownBy(() -> service.propose(me, request(List.of(wanted), List.of(missing), null)))
                .isInstanceOf(CopyNotAvailableException.class);
        verify(proposals, never()).save(any());
    }

    @Test
    void requestedCopiesWithDifferentOwnersAreInvalid() {
        Copy first = available(other, ConditionGrade.GOOD);
        Copy second = available(UUID.randomUUID(), ConditionGrade.GOOD);
        Copy mine = available(me, ConditionGrade.GOOD);
        loads(first, second, mine);
        assertThatThrownBy(() -> service.propose(me, request(List.of(first, second), List.of(mine), null)))
                .isInstanceOf(InvalidProposalException.class);
    }

    @Test
    void listClampsPagingAndSortsNewestFirst() {
        when(proposals.findIncoming(eq(me), any(Pageable.class))).thenReturn(Page.empty());

        service.list(me, "incoming", null, -5, 100000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(proposals).findIncoming(eq(me), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort()).isEqualTo(
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
    }

    @Test
    void listUsesTheStatusQueryWhenAStatusIsGiven() {
        when(proposals.findOutgoingByStatus(eq(me), eq(ProposalStatus.EXPIRED), eq(NOW), any(Pageable.class)))
                .thenReturn(Page.empty());
        service.list(me, "OUTGOING", ProposalStatus.EXPIRED, 0, 20);
        verify(proposals).findOutgoingByStatus(eq(me), eq(ProposalStatus.EXPIRED), eq(NOW), any(Pageable.class));
    }

    private Proposal proposalFromMeTo(UUID recipient) {
        return Proposal.create(me, recipient, null, null, NOW, Duration.ofDays(14));
    }

    @Test
    void acceptReservesEveryLockedCopyAndSavesThemTogether() {
        Copy requested = available(other, ConditionGrade.GOOD);
        Copy mine1 = available(me, ConditionGrade.GOOD);
        Copy mine2 = available(me, ConditionGrade.GOOD);
        Proposal p = proposalFromMeTo(other);
        when(proposals.findById(p.getId())).thenReturn(java.util.Optional.of(p));
        when(items.findByIdProposalId(p.getId())).thenReturn(List.of(
                new ProposalItem(p.getId(), requested.getId(), Side.REQUESTED, ConditionGrade.GOOD),
                new ProposalItem(p.getId(), mine1.getId(), Side.OFFERED, ConditionGrade.GOOD),
                new ProposalItem(p.getId(), mine2.getId(), Side.OFFERED, ConditionGrade.GOOD)));
        when(copies.lockForUpdate(anyCollection())).thenReturn(List.of(requested, mine1, mine2));   // the query orders by id

        service.accept(other, p.getId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Copy>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(copies).saveAllAndFlush(saved.capture());
        List<UUID> ids = new ArrayList<>();
        saved.getValue().forEach(c -> ids.add(c.getId()));
        assertThat(ids).containsExactlyInAnyOrder(requested.getId(), mine1.getId(), mine2.getId());
        assertThat(List.of(requested, mine1, mine2)).allSatisfy(c -> assertThat(c.getStatus()).isEqualTo(CopyStatus.RESERVED));
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
        verify(proposals).cancelOpenContaining(anyCollection(), eq(p.getId()), eq(NOW));
    }

    @Test
    void acceptWithAForeignOwnedCopyIsCopyNotAvailableAndReservesNothing() {
        Copy requested = available(other, ConditionGrade.GOOD);
        Copy notMineAnymore = available(UUID.randomUUID(), ConditionGrade.GOOD);   // offered, but owned by a third user
        Proposal p = proposalFromMeTo(other);
        when(proposals.findById(p.getId())).thenReturn(java.util.Optional.of(p));
        when(items.findByIdProposalId(p.getId())).thenReturn(List.of(
                new ProposalItem(p.getId(), requested.getId(), Side.REQUESTED, ConditionGrade.GOOD),
                new ProposalItem(p.getId(), notMineAnymore.getId(), Side.OFFERED, ConditionGrade.GOOD)));
        when(copies.lockForUpdate(anyCollection())).thenReturn(List.of(requested, notMineAnymore));

        assertThatThrownBy(() -> service.accept(other, p.getId())).isInstanceOf(CopyNotAvailableException.class);
        assertThat(requested.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
        verify(copies, never()).saveAllAndFlush(any());
    }

    @Test
    void acceptWithAChangedGradeIsCopyNotAvailable() {
        Copy requested = available(other, ConditionGrade.GOOD);
        Copy regraded = available(me, ConditionGrade.POOR);       // was GOOD when the proposal was made
        Proposal p = proposalFromMeTo(other);
        when(proposals.findById(p.getId())).thenReturn(java.util.Optional.of(p));
        when(items.findByIdProposalId(p.getId())).thenReturn(List.of(
                new ProposalItem(p.getId(), requested.getId(), Side.REQUESTED, ConditionGrade.GOOD),
                new ProposalItem(p.getId(), regraded.getId(), Side.OFFERED, ConditionGrade.GOOD)));
        when(copies.lockForUpdate(anyCollection())).thenReturn(List.of(requested, regraded));

        assertThatThrownBy(() -> service.accept(other, p.getId())).isInstanceOf(CopyNotAvailableException.class);
        assertThat(requested.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
    }

    @Test
    void acceptLocksTheCopiesBeforeItChangesTheProposal() {
        Copy requested = available(other, ConditionGrade.GOOD);
        Copy mine = available(me, ConditionGrade.GOOD);
        Proposal p = proposalFromMeTo(other);
        when(proposals.findById(p.getId())).thenReturn(java.util.Optional.of(p));
        when(items.findByIdProposalId(p.getId())).thenReturn(List.of(
                new ProposalItem(p.getId(), requested.getId(), Side.REQUESTED, ConditionGrade.GOOD),
                new ProposalItem(p.getId(), mine.getId(), Side.OFFERED, ConditionGrade.GOOD)));
        when(copies.lockForUpdate(anyCollection())).thenAnswer(i -> {
            assertThat(p.getStatus()).as("the proposal is untouched while the copies are being locked")
                    .isEqualTo(ProposalStatus.PROPOSED);
            return List.of(requested, mine);
        });

        service.accept(other, p.getId());

        assertThat(p.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
    }

    @Test
    void acceptWithAMissingCopyIsCopyNotAvailable() {
        Copy requested = available(other, ConditionGrade.GOOD);
        Proposal p = proposalFromMeTo(other);
        when(proposals.findById(p.getId())).thenReturn(java.util.Optional.of(p));
        when(items.findByIdProposalId(p.getId())).thenReturn(List.of(
                new ProposalItem(p.getId(), requested.getId(), Side.REQUESTED, ConditionGrade.GOOD),
                new ProposalItem(p.getId(), UUID.randomUUID(), Side.OFFERED, ConditionGrade.GOOD)));
        when(copies.lockForUpdate(anyCollection())).thenReturn(List.of(requested));
        assertThatThrownBy(() -> service.accept(other, p.getId())).isInstanceOf(CopyNotAvailableException.class);
    }

    @Test
    void cancelOpenForCopyCancelsOpenProposalsContainingIt() {
        UUID copyId = UUID.randomUUID();
        service.cancelOpenForCopy(copyId, NOW);
        verify(proposals).cancelOpenContaining(List.of(copyId), new UUID(0L, 0L), NOW);
    }

    @Test
    void rejectStoresTheTrimmedReason() {
        Proposal p = proposalFromMeTo(other);
        when(proposals.findById(p.getId())).thenReturn(java.util.Optional.of(p));
        service.reject(other, p.getId(), "  no thanks ");
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(p.getRejectReason()).isEqualTo("no thanks");
    }

    @Test
    void strangerCannotActOnAProposal() {
        Proposal p = proposalFromMeTo(other);
        when(proposals.findById(p.getId())).thenReturn(java.util.Optional.of(p));
        UUID stranger = UUID.randomUUID();
        assertThatThrownBy(() -> service.accept(stranger, p.getId())).isInstanceOf(ProposalNotFoundException.class);
        assertThatThrownBy(() -> service.reject(stranger, p.getId(), null)).isInstanceOf(ProposalNotFoundException.class);
        assertThatThrownBy(() -> service.cancel(stranger, p.getId())).isInstanceOf(ProposalNotFoundException.class);
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.PROPOSED);
    }

    @Test
    void counterMarksOriginalAndSavesLinkedProposalWithRolesSwapped() {
        Copy theirs = available(other, ConditionGrade.GOOD);
        Copy mine = available(me, ConditionGrade.GOOD);
        Proposal original = Proposal.create(other, me, null, null, NOW, Duration.ofDays(14));
        when(proposals.findById(original.getId())).thenReturn(java.util.Optional.of(original));
        loads(theirs, mine);
        when(proposals.save(any(Proposal.class))).thenAnswer(i -> i.getArgument(0));

        service.counter(me, original.getId(), request(List.of(theirs), List.of(mine), "how about this"));

        assertThat(original.getStatus()).isEqualTo(ProposalStatus.COUNTERED);
        ArgumentCaptor<Proposal> saved = ArgumentCaptor.forClass(Proposal.class);
        verify(proposals).save(saved.capture());
        assertThat(saved.getValue().getProposerId()).isEqualTo(me);
        assertThat(saved.getValue().getRecipientId()).isEqualTo(other);
        assertThat(saved.getValue().getParentId()).isEqualTo(original.getId());
        assertThat(saved.getValue().getStatus()).isEqualTo(ProposalStatus.PROPOSED);
    }

    @Test
    void counterRequestingCopiesOfSomeoneOtherThanTheOriginalProposerIsCopyNotAvailable() {
        Copy third = available(UUID.randomUUID(), ConditionGrade.GOOD);
        Copy mine = available(me, ConditionGrade.GOOD);
        Proposal original = Proposal.create(other, me, null, null, NOW, Duration.ofDays(14));
        when(proposals.findById(original.getId())).thenReturn(java.util.Optional.of(original));
        loads(third, mine);
        assertThatThrownBy(() -> service.counter(me, original.getId(), request(List.of(third), List.of(mine), null)))
                .isInstanceOf(CopyNotAvailableException.class);
        verify(proposals, never()).save(any());
    }

    @Test
    void requestingYourOwnCopiesIsSelfProposal() {
        Copy mine = available(me, ConditionGrade.GOOD);
        Copy alsoMine = available(me, ConditionGrade.GOOD);
        loads(mine, alsoMine);
        assertThatThrownBy(() -> service.propose(me, request(List.of(mine), List.of(alsoMine), null)))
                .isInstanceOf(SelfProposalException.class);
    }
}
