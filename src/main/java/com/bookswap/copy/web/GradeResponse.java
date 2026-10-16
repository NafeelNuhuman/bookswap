package com.bookswap.copy.web;

import com.bookswap.copy.domain.ConditionGrade;

public record GradeResponse(String code, String label, String criteria) {
    public static GradeResponse from(ConditionGrade g) {
        return new GradeResponse(g.name(), g.label(), g.criteria());
    }
}
