package com.bookswap.copy.web;

import com.bookswap.copy.domain.ConditionGrade;
import com.bookswap.copy.domain.Copy;
import com.bookswap.copy.domain.CopyStatus;

import java.time.Instant;
import java.util.UUID;

public record CopyResponse(UUID id, String isbn, String title, String author, String coverUrl,
                           ConditionGrade grade, CopyStatus status, Instant createdAt, Instant updatedAt) {

    public static CopyResponse from(Copy copy) {
        return new CopyResponse(copy.getId(), copy.getBook().getIsbn13(), copy.getBook().getTitle(),
                copy.getBook().getAuthor(), copy.getBook().getCoverUrl(), copy.getGrade(), copy.getStatus(),
                copy.getCreatedAt(), copy.getUpdatedAt());
    }
}
