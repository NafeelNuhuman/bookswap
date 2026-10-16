package com.bookswap.common.error;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(int status, String code, String message, List<FieldError> fieldErrors,
                            Instant timestamp, String path) {

    public record FieldError(String field, String message) {}
}
