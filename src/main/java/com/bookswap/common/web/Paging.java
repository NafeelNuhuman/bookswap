package com.bookswap.common.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** One place for the paging limits every list endpoint shares. */
public final class Paging {
    public static final int MAX_PAGE_SIZE = 100;

    private Paging() {}

    /** Clamps instead of rejecting: page >= 0, size 1..{@value MAX_PAGE_SIZE}, and {@code page * size} never overflows. */
    public static Pageable pageable(int page, int size, Sort sort) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        // Spring Data computes the offset as page * size and rejects it above Integer.MAX_VALUE (a 500 otherwise).
        int pageNumber = Math.min(Math.max(page, 0), Integer.MAX_VALUE / pageSize);
        return PageRequest.of(pageNumber, pageSize, sort);
    }
}
