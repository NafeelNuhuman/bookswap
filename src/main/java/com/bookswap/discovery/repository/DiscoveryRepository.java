package com.bookswap.discovery.repository;

import com.bookswap.copy.domain.Copy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/** Read-only queries over other users' available copies. Everything here excludes the caller's own copies. */
public interface DiscoveryRepository extends Repository<Copy, UUID> {

    @Query(value = """
            select new com.bookswap.discovery.repository.DiscoveryRow(
                c.id, c.isbn13, coalesce(b.title, c.manualTitle), coalesce(b.author, c.manualAuthor), b.coverUrl,
                c.grade, case when b.isbn13 is null then false else true end, u.displayName, u.city)
            from Copy c left join c.book b join User u on u.id = c.ownerId
            where c.status = com.bookswap.copy.domain.CopyStatus.AVAILABLE
              and c.ownerId <> :me and lower(u.city) = :cityLower
            """,
            countQuery = """
            select count(c) from Copy c join User u on u.id = c.ownerId
            where c.status = com.bookswap.copy.domain.CopyStatus.AVAILABLE
              and c.ownerId <> :me and lower(u.city) = :cityLower
            """)
    Page<DiscoveryRow> feed(@Param("me") UUID me, @Param("cityLower") String cityLower, Pageable pageable);
}
