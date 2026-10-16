package com.bookswap.book.domain;

import java.util.Locale;

/** A validated ISBN, always held in its 13-digit form so that ISBN-10 and ISBN-13 spellings compare equal. */
public final class Isbn {
    private static final int MAX_RAW_LENGTH = 32;

    private final String value;

    private Isbn(String value) {
        this.value = value;
    }

    public static Isbn parse(String raw) {
        if (raw == null || raw.length() > MAX_RAW_LENGTH) {
            throw new InvalidIsbnException();
        }
        String s = raw.strip().replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        // Explicit [0-9] on purpose: \d and Character.isDigit also match non-ASCII digits.
        if (s.length() == 10 && s.matches("[0-9]{9}[0-9X]") && validIsbn10(s)) {
            return new Isbn(toIsbn13(s));
        }
        if (s.length() == 13 && s.matches("[0-9]{13}")
                && (s.startsWith("978") || s.startsWith("979")) && validIsbn13(s)) {
            return new Isbn(s);
        }
        throw new InvalidIsbnException();
    }

    public String value() {
        return value;
    }

    private static boolean validIsbn10(String s) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            char c = s.charAt(i);
            int digit = c == 'X' ? 10 : c - '0';
            sum += (10 - i) * digit;
        }
        return sum % 11 == 0;
    }

    private static boolean validIsbn13(String s) {
        return weightedSum13(s, 13) % 10 == 0;
    }

    private static String toIsbn13(String isbn10) {
        String body = "978" + isbn10.substring(0, 9);
        int check = (10 - weightedSum13(body, 12) % 10) % 10;
        return body + check;
    }

    private static int weightedSum13(String s, int digits) {
        int sum = 0;
        for (int i = 0; i < digits; i++) {
            sum += (s.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return sum;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Isbn other && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
