package com.bookswap.discovery.repository;

import com.bookswap.copy.domain.ConditionGrade;

import java.util.UUID;

/** One discovery result, read in a single query: the copy, its displayed book details and its owner's public area (id, name, city). */
public record DiscoveryRow(UUID copyId, String isbn13, String title, String author, String coverUrl,
                           ConditionGrade grade, boolean fromCatalog, UUID ownerId, String ownerName, String ownerCity) {}
