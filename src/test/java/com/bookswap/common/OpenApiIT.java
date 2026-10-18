package com.bookswap.common;

import com.bookswap.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiIT extends AbstractIntegrationTest {

    @Test
    void apiDocsArePublicAndDescribeTheEndpointsAndBearerAuth() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/register']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/refresh']").exists())
                .andExpect(jsonPath("$.paths['/api/users/me']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    @Test
    void apiDocsListTheCompletionEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/proposals/{id}/confirm'].post").exists())
                .andExpect(jsonPath("$.paths['/api/proposals/{id}/cancel'].post").exists())
                .andExpect(jsonPath("$.paths['/api/proposals/{id}/pickup-note'].put").exists())
                .andExpect(jsonPath("$.paths['/api/exchanges'].get").exists())
                .andExpect(jsonPath("$.paths['/api/users/me'].delete").exists());
    }

    @Test
    void swaggerUiIsNotBehindAuthentication() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
    }
}
