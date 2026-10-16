package com.bookswap.book.web;

import com.bookswap.book.domain.Book;

public record LookupResponse(String isbn, String title, String author, String coverUrl, Source source) {

    public enum Source { LOCAL, CATALOG }

    public static LookupResponse from(Book book) {
        return new LookupResponse(book.getIsbn13(), book.getTitle(), book.getAuthor(), book.getCoverUrl(), Source.LOCAL);
    }
}
