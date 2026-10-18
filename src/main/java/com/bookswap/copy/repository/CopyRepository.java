package com.bookswap.copy.repository;

import com.bookswap.copy.domain.Copy;
import com.bookswap.copy.domain.CopyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CopyRepository extends JpaRepository<Copy, UUID> {

    @EntityGraph(attributePaths = "book")
    Page<Copy> findByOwnerId(UUID ownerId, Pageable pageable);

    @EntityGraph(attributePaths = "book")
    Page<Copy> findByOwnerIdAndStatus(UUID ownerId, CopyStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "book")
    Optional<Copy> findByIdAndOwnerId(UUID id, UUID ownerId);

    /**
     * Reads the copies and holds a shared row lock until the transaction ends, in ascending id order. A concurrent
     * regrade, unlist or reservation of one of them waits for us, and so sees what we wrote before it cancels.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from Copy c where c.id in :ids order by c.id")
    List<Copy> lockForShare(@Param("ids") Collection<UUID> ids);

    /** Reads the copies and takes exclusive row locks, in ascending id order, so competing accepts cannot deadlock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Copy c where c.id in :ids order by c.id")
    List<Copy> lockForUpdate(@Param("ids") Collection<UUID> ids);
}
