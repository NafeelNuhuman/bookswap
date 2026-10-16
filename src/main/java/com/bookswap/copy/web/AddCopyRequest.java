package com.bookswap.copy.web;

import com.bookswap.copy.domain.ConditionGrade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddCopyRequest(
        @NotBlank @Size(max = 32) String isbn,
        @NotNull ConditionGrade grade,
        // Only used when neither we nor the catalog know the book. NUL cannot be stored in PostgreSQL text.
        @Size(max = 500) @Pattern(regexp = "[^\\x00]*", message = "must not contain NUL characters") String title,
        @Size(max = 500) @Pattern(regexp = "[^\\x00]*", message = "must not contain NUL characters") String author) {

    public AddCopyRequest {
        title = blankToNull(title);
        author = blankToNull(author);
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String stripped = s.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
