package com.bookswap.book.repository;

import com.bookswap.book.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface BookRepository extends JpaRepository<Book, String> {

    /**
     * Race-safe first insert of a shared book: a concurrent insert of the same ISBN is a no-op, so callers
     * simply re-read afterwards. The CAST keeps PostgreSQL from typing a null cover as bytea.
     */
    @Transactional
    @Modifying
    @Query(value = "INSERT INTO book (isbn13, title, author, cover_url, created_at) "
            + "VALUES (:isbn13, :title, :author, CAST(:coverUrl AS varchar), :createdAt) "
            + "ON CONFLICT (isbn13) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("isbn13") String isbn13, @Param("title") String title,
                       @Param("author") String author, @Param("coverUrl") String coverUrl,
                       @Param("createdAt") Instant createdAt);
}
