package com.bookswap.discovery.web;

import com.bookswap.copy.domain.ConditionGrade;
import com.bookswap.copy.domain.Copy;

import java.util.UUID;

/** What another user sees of an available copy. The owner is only ever a display name and a city. */
public record CopyCard(UUID copyId, String isbn, String title, String author, String coverUrl,
                       ConditionGrade grade, Copy.DetailsSource detailsSource, Owner owner) {

    public record Owner(String displayName, String city) {}
}
