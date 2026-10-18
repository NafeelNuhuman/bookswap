package com.bookswap.copy.service;

import com.bookswap.book.domain.Book;
import com.bookswap.book.domain.InvalidIsbnException;
import com.bookswap.book.domain.Isbn;
import com.bookswap.book.service.BookService;
import com.bookswap.common.web.PageResponse;
import com.bookswap.copy.domain.ConditionGrade;
import com.bookswap.copy.domain.Copy;
import com.bookswap.copy.domain.CopyNotFoundException;
import com.bookswap.copy.domain.CopyStatus;
import com.bookswap.copy.domain.ProfileIncompleteException;
import com.bookswap.copy.repository.CopyRepository;
import com.bookswap.copy.web.AddCopyRequest;
import com.bookswap.copy.web.CopyResponse;
import com.bookswap.proposal.service.ProposalService;
import com.bookswap.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CopyServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock CopyRepository copies;
    @Mock BookService bookService;
    @Mock UserService userService;
    @Mock ProposalService proposals;
    CopyService service;

    UUID owner = UUID.randomUUID();
    Book book = new Book("9780140328721", "Fantastic Mr Fox", "Roald Dahl", null, NOW);

    @BeforeEach
    void setUp() {
        service = new CopyService(copies, bookService, userService, proposals, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Copy ownedCopy() {
        return new Copy(owner, book, ConditionGrade.GOOD, NOW);
    }

    @Test
    void addResolvesTheBookAndSavesAnUnlistedCopyForTheCaller() {
        when(bookService.resolveForCopy(any(Isbn.class), eq("T"), eq("A")))
                .thenReturn(new BookService.BookResolution(book, book.getIsbn13(), null, null));
        when(copies.save(any(Copy.class))).thenAnswer(i -> i.getArgument(0));

        CopyResponse r = service.add(owner, new AddCopyRequest("978-0-14-032872-1", ConditionGrade.NEW, "T", "A"));

        ArgumentCaptor<Copy> saved = ArgumentCaptor.forClass(Copy.class);
        verify(copies).save(saved.capture());
        assertThat(saved.getValue().getOwnerId()).isEqualTo(owner);
        assertThat(saved.getValue().getStatus()).isEqualTo(CopyStatus.UNLISTED);
        assertThat(r.isbn()).isEqualTo("9780140328721");
        assertThat(r.grade()).isEqualTo(ConditionGrade.NEW);
    }

    @Test
    void addWithManualDetailsSavesACopyCarryingThemAndNoBook() {
        when(bookService.resolveForCopy(any(Isbn.class), eq("T"), eq("A")))
                .thenReturn(new BookService.BookResolution(null, "9780140328721", "T", "A"));
        when(copies.save(any(Copy.class))).thenAnswer(i -> i.getArgument(0));

        CopyResponse r = service.add(owner, new AddCopyRequest("9780140328721", ConditionGrade.GOOD, "T", "A"));

        assertThat(r.title()).isEqualTo("T");
        assertThat(r.author()).isEqualTo("A");
        assertThat(r.detailsSource()).isEqualTo(Copy.DetailsSource.MANUAL);
        assertThat(r.isbn()).isEqualTo("9780140328721");
    }

    @Test
    void addWithInvalidIsbnNeverReachesTheBookService() {
        assertThatThrownBy(() -> service.add(owner, new AddCopyRequest("nope", ConditionGrade.NEW, null, null)))
                .isInstanceOf(InvalidIsbnException.class);
        verify(copies, never()).save(any());
    }

    @Test
    void anotherUsersOrMissingCopyIsNotFoundForEveryOperation() {
        UUID id = UUID.randomUUID();
        when(copies.findByIdAndOwnerId(id, owner)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(owner, id)).isInstanceOf(CopyNotFoundException.class);
        assertThatThrownBy(() -> service.changeGrade(owner, id, ConditionGrade.POOR)).isInstanceOf(CopyNotFoundException.class);
        assertThatThrownBy(() -> service.listCopy(owner, id)).isInstanceOf(CopyNotFoundException.class);
        assertThatThrownBy(() -> service.unlistCopy(owner, id)).isInstanceOf(CopyNotFoundException.class);
        assertThatThrownBy(() -> service.remove(owner, id)).isInstanceOf(CopyNotFoundException.class);
    }

    @Test
    void listCopyAsksTheProfileAndAppliesTheRule() {
        Copy c = ownedCopy();
        when(copies.findByIdAndOwnerId(c.getId(), owner)).thenReturn(Optional.of(c));
        when(userService.isProfileComplete(owner)).thenReturn(false);
        assertThatThrownBy(() -> service.listCopy(owner, c.getId())).isInstanceOf(ProfileIncompleteException.class);

        when(userService.isProfileComplete(owner)).thenReturn(true);
        assertThat(service.listCopy(owner, c.getId()).status()).isEqualTo(CopyStatus.AVAILABLE);
    }

    @Test
    void removeDeletesOnlyRemovableCopies() {
        Copy c = ownedCopy();
        when(copies.findByIdAndOwnerId(c.getId(), owner)).thenReturn(Optional.of(c));
        service.remove(owner, c.getId());
        verify(copies).delete(c);
    }

    @Test
    void listClampsPageAndSizeAndFiltersByStatus() {
        when(copies.findByOwnerIdAndStatus(eq(owner), eq(CopyStatus.AVAILABLE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ownedCopy())));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        service.list(owner, CopyStatus.AVAILABLE, -5, 100_000);

        verify(copies).findByOwnerIdAndStatus(eq(owner), eq(CopyStatus.AVAILABLE), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void listWithoutStatusUsesTheUnfilteredQueryAndZeroSizeBecomesOne() {
        when(copies.findByOwnerId(eq(owner), any(Pageable.class))).thenReturn(Page.empty());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        PageResponse<CopyResponse> r = service.list(owner, null, 0, 0);

        verify(copies).findByOwnerId(eq(owner), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(1);
        assertThat(r.content()).isEmpty();
    }

    @Test
    void changingTheGradeCancelsOpenProposalsForTheCopy() {
        Copy c = ownedCopy();
        when(copies.findByIdAndOwnerId(c.getId(), owner)).thenReturn(Optional.of(c));
        service.changeGrade(owner, c.getId(), ConditionGrade.POOR);
        verify(proposals).cancelOpenForCopy(c.getId(), NOW);
    }

    @Test
    void settingTheSameGradeDoesNotCancelAnything() {
        Copy c = ownedCopy();
        when(copies.findByIdAndOwnerId(c.getId(), owner)).thenReturn(Optional.of(c));
        service.changeGrade(owner, c.getId(), ConditionGrade.GOOD);
        verify(proposals, never()).cancelOpenForCopy(any(), any());
    }

    @Test
    void unlistingCancelsOpenProposalsForTheCopy() {
        Copy c = ownedCopy();
        when(copies.findByIdAndOwnerId(c.getId(), owner)).thenReturn(Optional.of(c));
        service.unlistCopy(owner, c.getId());
        verify(proposals).cancelOpenForCopy(c.getId(), NOW);
    }

    @Test
    void removingCancelsOpenProposalsBeforeDeleting() {
        Copy c = ownedCopy();
        when(copies.findByIdAndOwnerId(c.getId(), owner)).thenReturn(Optional.of(c));
        service.remove(owner, c.getId());
        InOrder order = inOrder(proposals, copies);
        order.verify(proposals).cancelOpenForCopy(c.getId(), NOW);
        order.verify(copies).delete(c);
    }

    @Test
    void listingDoesNotCancelAnything() {
        Copy c = ownedCopy();
        when(copies.findByIdAndOwnerId(c.getId(), owner)).thenReturn(Optional.of(c));
        when(userService.isProfileComplete(owner)).thenReturn(true);
        service.listCopy(owner, c.getId());
        verify(proposals, never()).cancelOpenForCopy(any(), any());
    }
}
