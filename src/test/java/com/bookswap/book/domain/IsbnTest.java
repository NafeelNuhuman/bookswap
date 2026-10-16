package com.bookswap.book.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IsbnTest {

    @Test
    void validIsbn13IsKept() {
        assertThat(Isbn.parse("9780140328721").value()).isEqualTo("9780140328721");
    }

    @Test
    void hyphensAndSurroundingWhitespaceAreIgnored() {
        assertThat(Isbn.parse("  978-0-14-032872-1 ").value()).isEqualTo("9780140328721");
        assertThat(Isbn.parse("978 0 14 032872 1").value()).isEqualTo("9780140328721");
    }

    @Test
    void isbn10IsConvertedToIsbn13() {
        assertThat(Isbn.parse("0306406152").value()).isEqualTo("9780306406157");
        assertThat(Isbn.parse("0-306-40615-2").value()).isEqualTo("9780306406157");
    }

    @Test
    void isbn10WithXCheckDigitAcceptsEitherCase() {
        assertThat(Isbn.parse("080442957X").value()).isEqualTo("9780804429573");
        assertThat(Isbn.parse("080442957x").value()).isEqualTo("9780804429573");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            "   ",
            "9780306406158",            // bad ISBN-13 check digit
            "0306406153",               // bad ISBN-10 check digit
            "abcdefghij",
            "978030640615",             // 12 digits
            "97803064061577",           // 14 digits
            "1234567890123",            // valid-looking length, not 978/979 (and bad check digit)
            "9770306406158",            // 977 prefix with a correct check digit: still not an ISBN
            "97801403\u000028721",      // NUL inside
            "٩٧٨٠١٤٠٣٢٨٧٢١", // Arabic-Indic digits
            "12345678X0",               // X only allowed as the last ISBN-10 character
            "9780140328721xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" // over-long
    })
    void invalidInputsAreRejected(String raw) {
        assertThatThrownBy(() -> Isbn.parse(raw))
                .isInstanceOf(InvalidIsbnException.class)
                .hasMessageContaining("ISBN");
    }

    @Test
    void equalIsbnsAreEqualWhateverTheirInputFormat() {
        assertThat(Isbn.parse("0306406152")).isEqualTo(Isbn.parse("978-0-306-40615-7"));
    }
}
