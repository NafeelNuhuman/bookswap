package com.bookswap;

import com.bookswap.support.StubBookCatalog;
import com.bookswap.support.StubCatalogConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "bookswap.security.bcrypt-strength=4",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
@Import(StubCatalogConfig.class)
public abstract class AbstractIntegrationTest {

    // Singleton container shared by every test class in the JVM (one Spring context, one DB).
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected StubBookCatalog catalog;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE copy, book, refresh_token, users CASCADE");
        catalog.reset();
    }

    protected ResultActions postJson(String url, Object body) throws Exception {
        return mvc.perform(post(url).contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    protected JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** Registers a user, logs in, returns the token response JSON (accessToken, refreshToken, ...). */
    protected JsonNode registerAndLogin(String email, String password) throws Exception {
        postJson("/api/auth/register", Map.of("email", email, "password", password))
                .andExpect(status().isCreated());
        return read(postJson("/api/auth/login", Map.of("email", email, "password", password))
                .andExpect(status().isOk()));
    }

    protected void completeProfile(JsonNode tokens) throws Exception {
        completeProfile(tokens, "Pat", "Berlin");
    }

    protected void completeProfile(JsonNode tokens, String displayName, String city) throws Exception {
        mvc.perform(put("/api/users/me").header("Authorization", bearer(tokens))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "displayName", displayName, "city", city, "postalCode", "10115"))))
                .andExpect(status().isOk());
    }

    protected String bearer(JsonNode tokens) {
        return "Bearer " + tokens.get("accessToken").asText();
    }
}
