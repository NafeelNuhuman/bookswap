package com.bookswap.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class PagingTest {
    private static final Sort SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    @Test
    void negativePageBecomesZero() {
        assertThat(Paging.pageable(-5, 20, SORT).getPageNumber()).isZero();
    }

    @Test
    void sizeIsClampedToOneThroughOneHundred() {
        assertThat(Paging.pageable(0, 0, SORT).getPageSize()).isEqualTo(1);
        assertThat(Paging.pageable(0, -3, SORT).getPageSize()).isEqualTo(1);
        assertThat(Paging.pageable(0, 100_000, SORT).getPageSize()).isEqualTo(100);
        assertThat(Paging.pageable(0, 37, SORT).getPageSize()).isEqualTo(37);
    }

    @Test
    void hugePageNumberCannotOverflowTheOffset() {
        for (int size : new int[]{1, 20, 100}) {
            Pageable p = Paging.pageable(Integer.MAX_VALUE, size, SORT);
            assertThat((long) p.getPageNumber() * p.getPageSize())
                    .as("offset for size %d", size).isLessThanOrEqualTo(Integer.MAX_VALUE);
        }
    }

    @Test
    void sortIsKept() {
        assertThat(Paging.pageable(0, 20, SORT).getSort()).isEqualTo(SORT);
    }
}
