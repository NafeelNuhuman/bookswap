package com.bookswap.proposal.service;

import com.bookswap.book.domain.Book;
import com.bookswap.copy.domain.ConditionGrade;
import com.bookswap.copy.domain.Copy;
import com.bookswap.copy.domain.CopyNotAvailableException;
import com.bookswap.copy.repository.CopyRepository;
import com.bookswap.proposal.domain.InvalidProposalException;
import com.bookswap.proposal.domain.Proposal;
import com.bookswap.proposal.domain.ProposalItem;
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
        when(copies.findAllByIdIn(anyCollection())).thenReturn(List.of(all));
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

    @Test
    void requestingYourOwnCopiesIsSelfProposal() {
        Copy mine = available(me, ConditionGrade.GOOD);
        Copy alsoMine = available(me, ConditionGrade.GOOD);
        loads(mine, alsoMine);
        assertThatThrownBy(() -> service.propose(me, request(List.of(mine), List.of(alsoMine), null)))
                .isInstanceOf(SelfProposalException.class);
    }
}
