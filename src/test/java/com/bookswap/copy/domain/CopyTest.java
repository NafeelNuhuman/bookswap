package com.bookswap.copy.domain;

import com.bookswap.book.domain.Book;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CopyTest {
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-02T00:00:00Z");

    private final Book book = new Book("9780140328721", "Fantastic Mr Fox", "Roald Dahl", null, T0);

    private Copy copy() {
        return new Copy(UUID.randomUUID(), book, ConditionGrade.GOOD, T0);
    }

    private static Copy reserved(Copy c) throws Exception {
        Field f = Copy.class.getDeclaredField("status");
        f.setAccessible(true);
        f.set(c, CopyStatus.RESERVED);
        return c;
    }

    @Test
    void newCopyIsUnlisted() {
        Copy c = copy();
        assertThat(c.getStatus()).isEqualTo(CopyStatus.UNLISTED);
        assertThat(c.getId()).isNotNull();
        assertThat(c.isNew()).isTrue();
    }

    @Test
    void manualCopyCarriesItsOwnDetailsAndFollowsTheSameRules() {
        Copy c = Copy.withManualDetails(UUID.randomUUID(), "9780140328721", "Typed", "Me", ConditionGrade.GOOD, T0);
        assertThat(c.getTitle()).isEqualTo("Typed");
        assertThat(c.getAuthor()).isEqualTo("Me");
        assertThat(c.getCoverUrl()).isNull();
        assertThat(c.getIsbn13()).isEqualTo("9780140328721");
        assertThat(c.getDetailsSource()).isEqualTo(Copy.DetailsSource.MANUAL);
        assertThat(c.getStatus()).isEqualTo(CopyStatus.UNLISTED);
        assertThatThrownBy(() -> c.list(false, T1)).isInstanceOf(ProfileIncompleteException.class);
        c.list(true, T1);
        assertThat(c.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
    }

    @Test
    void catalogCopyReportsTheBooksDetails() {
        Copy c = copy();
        assertThat(c.getTitle()).isEqualTo("Fantastic Mr Fox");
        assertThat(c.getDetailsSource()).isEqualTo(Copy.DetailsSource.CATALOG);
    }

    @Test
    void listingRequiresACompleteProfile() {
        Copy c = copy();
        assertThatThrownBy(() -> c.list(false, T1)).isInstanceOf(ProfileIncompleteException.class);
        assertThat(c.getStatus()).isEqualTo(CopyStatus.UNLISTED);
        c.list(true, T1);
        assertThat(c.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
        assertThat(c.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void listingAnAvailableCopyAndUnlistingAnUnlistedCopyAreNoOps() {
        Copy c = copy();
        c.list(true, T1);
        c.list(true, T1);
        assertThat(c.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
        c.unlist(T1);
        c.unlist(T1);
        assertThat(c.getStatus()).isEqualTo(CopyStatus.UNLISTED);
    }

    @Test
    void gradeCanBeChanged() {
        Copy c = copy();
        c.changeGrade(ConditionGrade.POOR, T1);
        assertThat(c.getGrade()).isEqualTo(ConditionGrade.POOR);
        assertThat(c.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void reservedCopyCannotBeChangedUnlistedListedOrRemoved() throws Exception {
        Copy c = reserved(copy());
        assertThatThrownBy(() -> c.changeGrade(ConditionGrade.POOR, T1)).isInstanceOf(CopyReservedException.class);
        assertThatThrownBy(() -> c.unlist(T1)).isInstanceOf(CopyReservedException.class);
        assertThatThrownBy(() -> c.list(true, T1)).isInstanceOf(CopyReservedException.class);
        assertThatThrownBy(c::requireRemovable).isInstanceOf(CopyReservedException.class);
        assertThat(c.getStatus()).isEqualTo(CopyStatus.RESERVED);
        assertThat(c.getGrade()).isEqualTo(ConditionGrade.GOOD);
    }

    @Test
    void unreservedCopiesAreRemovable() {
        copy().requireRemovable();
        Copy listed = copy();
        listed.list(true, T1);
        listed.requireRemovable();
    }

    @Test
    void everyGradeHasALabelAndCriteria() {
        for (ConditionGrade g : ConditionGrade.values()) {
            assertThat(g.label()).isNotBlank();
            assertThat(g.criteria()).isNotBlank();
        }
        assertThat(ConditionGrade.values()).hasSize(5);
    }
}
