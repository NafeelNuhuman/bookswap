package com.bookswap.copy.web;

import com.bookswap.copy.domain.ConditionGrade;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/condition-grades")
public class ConditionGradeController {

    @GetMapping
    public List<GradeResponse> grades() {
        return Arrays.stream(ConditionGrade.values()).map(GradeResponse::from).toList();
    }
}
