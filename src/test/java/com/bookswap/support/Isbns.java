package com.bookswap.support;

/** Generates distinct, valid ISBN-13 values for tests. */
public final class Isbns {
    public static final String FOX = "9780140328721";

    private Isbns() {}

    public static String valid(int n) {
        String body = "978" + String.format("%09d", n);
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += (body.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return body + (10 - sum % 10) % 10;
    }
}
