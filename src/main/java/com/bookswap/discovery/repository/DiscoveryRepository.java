package com.bookswap.discovery.repository;

import com.bookswap.copy.domain.Copy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Read-only queries over other users' available copies. Everything here excludes the caller's own copies.
 * The displayed title/author is the shared book's if one exists, otherwise the details typed on the copy.
 */
public interface DiscoveryRepository extends Repository<Copy, UUID> {

    @Query(value = """
            select new com.bookswap.discovery.repository.DiscoveryRow(
                c.id, c.isbn13, coalesce(b.title, c.manualTitle), coalesce(b.author, c.manualAuthor), b.coverUrl,
                c.grade, case when b.isbn13 is null then false else true end, u.displayName, u.city)
            from Copy c left join c.book b join User u on u.id = c.ownerId
            where c.status = com.bookswap.copy.domain.CopyStatus.AVAILABLE
              and c.ownerId <> :me and lower(u.city) = lower(:city)
            """,
            countQuery = """
            select count(c) from Copy c join User u on u.id = c.ownerId
            where c.status = com.bookswap.copy.domain.CopyStatus.AVAILABLE
              and c.ownerId <> :me and lower(u.city) = lower(:city)
            """)
    Page<DiscoveryRow> feed(@Param("me") UUID me, @Param("city") String city, Pageable pageable);

    @Query(value = """
            select new com.bookswap.discovery.repository.DiscoveryRow(
                c.id, c.isbn13, coalesce(b.title, c.manualTitle), coalesce(b.author, c.manualAuthor), b.coverUrl,
                c.grade, case when b.isbn13 is null then false else true end, u.displayName, u.city)
            from Copy c left join c.book b join User u on u.id = c.ownerId
            where c.status = com.bookswap.copy.domain.CopyStatus.AVAILABLE
              and c.ownerId <> :me and lower(u.city) = lower(:city) and c.isbn13 = :isbn13
            """,
            countQuery = """
            select count(c) from Copy c join User u on u.id = c.ownerId
            where c.status = com.bookswap.copy.domain.CopyStatus.AVAILABLE
              and c.ownerId <> :me and lower(u.city) = lower(:city) and c.isbn13 = :isbn13
            """)
    Page<DiscoveryRow> searchByIsbn(@Param("me") UUID me, @Param("city") String city,
                                    @Param("isbn13") String isbn13, Pageable pageable);

    /** {@code pattern} is a LIKE pattern (case is folded by the query) whose wildcards the caller has escaped with a backslash. */
    @Query(value = """
            select new com.bookswap.discovery.repository.DiscoveryRow(
                c.id, c.isbn13, coalesce(b.title, c.manualTitle), coalesce(b.author, c.manualAuthor), b.coverUrl,
                c.grade, case when b.isbn13 is null then false else true end, u.displayName, u.city)
            from Copy c left join c.book b join User u on u.id = c.ownerId
            where c.status = com.bookswap.copy.domain.CopyStatus.AVAILABLE
              and c.ownerId <> :me and lower(u.city) = lower(:city)
              and (lower(coalesce(b.title, c.manualTitle)) like lower(:pattern) escape '\\'
                or lower(coalesce(b.author, c.manualAuthor)) like lower(:pattern) escape '\\')
            """,
            countQuery = """
            select count(c) from Copy c left join c.book b join User u on u.id = c.ownerId
            where c.status = com.bookswap.copy.domain.CopyStatus.AVAILABLE
              and c.ownerId <> :me and lower(u.city) = lower(:city)
              and (lower(coalesce(b.title, c.manualTitle)) like lower(:pattern) escape '\\'
                or lower(coalesce(b.author, c.manualAuthor)) like lower(:pattern) escape '\\')
            """)
    Page<DiscoveryRow> searchByText(@Param("me") UUID me, @Param("city") String city,
                                    @Param("pattern") String pattern, Pageable pageable);
}
