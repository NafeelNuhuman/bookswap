package com.bookswap.discovery.service;

import com.bookswap.copy.domain.ConditionGrade;
import com.bookswap.copy.domain.Copy;
import com.bookswap.discovery.repository.DiscoveryRepository;
import com.bookswap.discovery.repository.DiscoveryRow;
import com.bookswap.discovery.web.CopyCard;
import com.bookswap.common.web.PageResponse;
import com.bookswap.user.service.UserService;
import com.bookswap.user.web.ProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscoveryServiceTest {

    @Mock DiscoveryRepository repository;
    @Mock UserService userService;
    DiscoveryService service;
    UUID me = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DiscoveryService(repository, userService);
    }

    private void profileCity(String city) {
        when(userService.getProfile(me)).thenReturn(new ProfileResponse(me, "me@example.com", "Me", city, "10115", city != null));
    }

    @Test
    void explicitCityWinsAndIsTrimmedAndLowerCased() {
        assertThat(service.resolveCity(me, "  BeRLin ")).isEqualTo("berlin");
        verifyNoInteractions(userService);
    }

    @Test
    void blankOrMissingCityFallsBackToTheProfileCity() {
        profileCity("Munich");
        assertThat(service.resolveCity(me, null)).isEqualTo("munich");
        assertThat(service.resolveCity(me, "   ")).isEqualTo("munich");
    }

    @Test
    void noCityAnywhereIsLocationRequired() {
        profileCity(null);
        assertThatThrownBy(() -> service.resolveCity(me, null)).isInstanceOf(LocationRequiredException.class);
        assertThatThrownBy(() -> service.resolveCity(me, " ")).isInstanceOf(LocationRequiredException.class);
    }

    @Test
    void nulOrOverLongCityIsInvalidLocation() {
        assertThatThrownBy(() -> service.resolveCity(me, "Ber\u0000lin")).isInstanceOf(InvalidLocationException.class);
        assertThatThrownBy(() -> service.resolveCity(me, "x".repeat(101))).isInstanceOf(InvalidLocationException.class);
        assertThat(service.resolveCity(me, "x".repeat(100))).hasSize(100);
    }

    @Test
    void feedMapsRowsToCardsWithDetailsSourceAndNoOwnerId() {
        UUID copyId = UUID.randomUUID();
        DiscoveryRow typed = new DiscoveryRow(copyId, "9780140328721", "Typed", "Me", null,
                ConditionGrade.GOOD, false, "Bob", "Berlin");
        DiscoveryRow shared = new DiscoveryRow(UUID.randomUUID(), "9780306406157", "Shared", "Them", "https://c/m.jpg",
                ConditionGrade.NEW, true, "Carol", "Berlin");
        when(repository.feed(eq(me), eq("berlin"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(typed, shared)));

        PageResponse<CopyCard> page = service.feed(me, "Berlin", 0, 20);

        assertThat(page.content()).hasSize(2);
        CopyCard first = page.content().get(0);
        assertThat(first.copyId()).isEqualTo(copyId);
        assertThat(first.detailsSource()).isEqualTo(Copy.DetailsSource.MANUAL);
        assertThat(first.owner()).isEqualTo(new CopyCard.Owner("Bob", "Berlin"));
        assertThat(page.content().get(1).detailsSource()).isEqualTo(Copy.DetailsSource.CATALOG);
    }

    @Test
    void feedClampsPagingBeforeQuerying() {
        when(repository.feed(eq(me), eq("berlin"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        service.feed(me, "berlin", -2, 100_000);
        verify(repository).feed(eq(me), eq("berlin"), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void invalidLocationNeverReachesTheRepository() {
        assertThatThrownBy(() -> service.feed(me, "a\u0000b", 0, 20)).isInstanceOf(InvalidLocationException.class);
        verify(repository, never()).feed(any(), any(), any());
    }

    // --- search ---

    private String capturedPattern() {
        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(repository).searchByText(eq(me), eq("berlin"), pattern.capture(), any(Pageable.class));
        return pattern.getValue();
    }

    @Test
    void isbnLookingQueryUsesTheExactIsbnSearchWithTheNormalizedIsbn13() {
        when(repository.searchByIsbn(eq(me), eq("berlin"), eq("9780306406157"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        service.search(me, "0-306-40615-2", "Berlin", 0, 20);
        verify(repository).searchByIsbn(eq(me), eq("berlin"), eq("9780306406157"), any(Pageable.class));
        verify(repository, never()).searchByText(any(), any(), any(), any());
    }

    @Test
    void otherQueriesUseACaseInsensitiveContainsPattern() {
        when(repository.searchByText(eq(me), eq("berlin"), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        service.search(me, "  Fox ", "Berlin", 0, 20);
        assertThat(capturedPattern()).isEqualTo("%fox%");
    }

    @Test
    void likeWildcardsAndBackslashAreEscaped() {
        when(repository.searchByText(eq(me), eq("berlin"), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        service.search(me, "100%_\\", "Berlin", 0, 20);
        assertThat(capturedPattern()).isEqualTo("%100\\%\\_\\\\%");
    }

    @Test
    void blankOverLongOrNulQueryIsInvalidQuery() {
        for (String bad : new String[]{null, "   ", "x".repeat(101), "a\u0000b"}) {
            assertThatThrownBy(() -> service.search(me, bad, "Berlin", 0, 20))
                    .as("query %s", bad == null ? "null" : bad.length() + " chars").isInstanceOf(InvalidQueryException.class);
        }
        verify(repository, never()).searchByText(any(), any(), any(), any());
        verify(repository, never()).searchByIsbn(any(), any(), any(), any());
    }

    @Test
    void aQueryOfExactlyOneHundredCharactersIsAccepted() {
        when(repository.searchByText(eq(me), eq("berlin"), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        service.search(me, "x".repeat(100), "Berlin", 0, 20);
    }
}
