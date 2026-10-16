package com.bookswap.copy.web;

import com.bookswap.copy.domain.ConditionGrade;
import jakarta.validation.constraints.NotNull;

public record UpdateCopyRequest(@NotNull ConditionGrade grade) {}
