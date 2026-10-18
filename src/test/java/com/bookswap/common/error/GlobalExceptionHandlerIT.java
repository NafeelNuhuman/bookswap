package com.bookswap.common.error;

import com.bookswap.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerIT extends AbstractIntegrationTest {

    @Test
    void validationErrorsAreReportedPerField() throws Exception {
        postJson("/api/auth/_probe/validate", Map.of("name", " ", "count", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.path").value("/api/auth/_probe/validate"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='name')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='count')]").exists());
    }

    @Test
    void malformedJsonGivesStandardBadRequest() throws Exception {
        mvc.perform(post("/api/auth/_probe/validate").contentType(APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void missingBodyGivesStandardBadRequest() throws Exception {
        mvc.perform(post("/api/auth/_probe/validate").contentType(APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void apiExceptionKeepsItsStatusAndCode() throws Exception {
        mvc.perform(get("/api/auth/_probe/api-error"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROBE_CONFLICT"))
                .andExpect(jsonPath("$.message").value("probe conflict"));
    }

    @Test
    void optimisticLockFailureIsAConflict() throws Exception {
        mvc.perform(get("/api/auth/_probe/lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
    }

    @Test
    void databaseLockFailureIsAConflictToo() throws Exception {
        mvc.perform(get("/api/auth/_probe/deadlock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
    }

    @Test
    void unexpectedErrorsDoNotLeakInternals() throws Exception {
        mvc.perform(get("/api/auth/_probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(not(containsString("secret"))));
    }

    @Test
    void unknownPathGivesStandardNotFound() throws Exception {
        mvc.perform(get("/api/auth/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
