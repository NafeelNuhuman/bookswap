package com.bookswap.copy.web;

import com.bookswap.copy.domain.ConditionGrade;
import com.bookswap.copy.domain.Copy;
import com.bookswap.copy.domain.CopyStatus;

import java.time.Instant;
import java.util.UUID;

public record CopyResponse(UUID id, String isbn, String title, String author, String coverUrl,
                           ConditionGrade grade, CopyStatus status, Copy.DetailsSource detailsSource,
                           Instant createdAt, Instant updatedAt) {

    public static CopyResponse from(Copy copy) {
        return new CopyResponse(copy.getId(), copy.getIsbn13(), copy.getTitle(), copy.getAuthor(),
                copy.getCoverUrl(), copy.getGrade(), copy.getStatus(), copy.getDetailsSource(),
                copy.getCreatedAt(), copy.getUpdatedAt());
    }
}
