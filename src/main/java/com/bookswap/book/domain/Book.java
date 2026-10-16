package com.bookswap.book.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "book")
public class Book {

    @Id
    @Column(name = "isbn13")
    private String isbn13;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column(name = "cover_url")
    private String coverUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Book() {}

    public Book(String isbn13, String title, String author, String coverUrl, Instant now) {
        this.isbn13 = isbn13;
        this.title = title;
        this.author = author;
        this.coverUrl = coverUrl;
        this.createdAt = now;
    }

    public String getIsbn13() { return isbn13; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getCoverUrl() { return coverUrl; }
}
