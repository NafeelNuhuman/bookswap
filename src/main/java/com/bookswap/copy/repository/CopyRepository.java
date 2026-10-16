package com.bookswap.copy.repository;

import com.bookswap.copy.domain.Copy;
import com.bookswap.copy.domain.CopyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CopyRepository extends JpaRepository<Copy, UUID> {

    @EntityGraph(attributePaths = "book")
    Page<Copy> findByOwnerId(UUID ownerId, Pageable pageable);

    @EntityGraph(attributePaths = "book")
    Page<Copy> findByOwnerIdAndStatus(UUID ownerId, CopyStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "book")
    Optional<Copy> findByIdAndOwnerId(UUID id, UUID ownerId);
}
