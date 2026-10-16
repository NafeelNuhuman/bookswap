# BookSwap Foundation (Step 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A runnable Spring Boot 3 / Java 21 backend skeleton on PostgreSQL + Flyway with Docker Compose and CI, delivering register (US-01), login with refresh-token rotation (US-02) and profile (US-03).

**Architecture:** Single Maven module, package-by-feature (`auth`, `user`, `common`), layers (`web`/`service`/`domain`/`repository`) inside each feature. Stateless JWT (HS256) access tokens validated by Spring Security's resource server; opaque rotating refresh tokens stored hashed, with reuse-detection. One global error format. Authorization lives in services (principal id, never a path id).

**Tech Stack:** Java 21, Spring Boot 3.3.5, Maven, PostgreSQL 16, Flyway, Spring Data JPA, Spring Security (oauth2-resource-server), springdoc-openapi 2.6.0, JUnit 5, Mockito, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-10-16-foundation-design.md` (requirements: `BookSwapRequirementsSpec.md`)

## Global Constraints

- Java 21, Spring Boot 3, Maven. PostgreSQL only; schema changes only via Flyway; `spring.jpa.hibernate.ddl-auto=validate`.
- Passwords: BCrypt strength 12 (configurable, tests use 4), min 8 chars, never stored in clear.
- Email: trimmed, lower-cased, unique; duplicate -> 409 `EMAIL_TAKEN`.
- Access token: JWT HS256, 15 min, `sub` = user id. Refresh token: opaque, 14 days, only SHA-256 hash stored, single use, reuse revokes all of the user's refresh tokens.
- Login failures: always 401 with a generic message, identical for wrong password and unknown email.
- Error body everywhere (incl. 401/403): `{status, code, message, fieldErrors[], timestamp, path}`.
- Other users must never see email or postal code. No endpoint writes a user by path id.
- Entities never leave the service layer; DTOs are Java records.
- `refresh_token.token_hash` is `varchar(64)` (spec said `char(64)`; `char` trips Hibernate schema validation against a Java `String`).
- The project folder is not yet a git repo; Task 1 runs `git init`.

## Review Focus

Inputs the spec implies but its stories don't spell out; each has a test in the named task.

1. Email with mixed case and surrounding whitespace must collide with the same email in another form (Task 3: `RegistrationIT`).
2. A password over BCrypt's 72-char limit must give a 400 field error, not a 500 (Task 3).
3. Malformed or missing JSON body must give the standard 400 error body, not a 500 or an HTML page (Task 2/3).
4. A garbage or blank refresh token must give 401/400 and must not revoke anyone's real tokens (Task 5).
5. A valid access token for a user who no longer exists must give a clean 404, not a 500 (Task 6).

---

## File Structure

```
pom.xml
.gitignore
docker-compose.yml
Dockerfile
README.md
.github/workflows/ci.yml
src/main/resources/application.yml
src/main/resources/db/migration/V1__users_and_refresh_tokens.sql
src/main/java/com/bookswap/BookSwapApplication.java
src/main/java/com/bookswap/common/config/AppConfig.java            (Clock bean)
src/main/java/com/bookswap/common/config/OpenApiConfig.java
src/main/java/com/bookswap/common/error/ErrorResponse.java
src/main/java/com/bookswap/common/error/ApiException.java
src/main/java/com/bookswap/common/error/ErrorResponseWriter.java   (JSON for security handlers)
src/main/java/com/bookswap/common/error/GlobalExceptionHandler.java
src/main/java/com/bookswap/auth/config/JwtProperties.java
src/main/java/com/bookswap/auth/config/JwtConfig.java
src/main/java/com/bookswap/auth/config/SecurityConfig.java
src/main/java/com/bookswap/auth/config/PasswordConfig.java
src/main/java/com/bookswap/auth/domain/RefreshToken.java
src/main/java/com/bookswap/auth/repository/RefreshTokenRepository.java
src/main/java/com/bookswap/auth/service/JwtService.java
src/main/java/com/bookswap/auth/service/RefreshTokenService.java
src/main/java/com/bookswap/auth/service/AuthService.java
src/main/java/com/bookswap/auth/service/EmailTakenException.java
src/main/java/com/bookswap/auth/service/InvalidCredentialsException.java
src/main/java/com/bookswap/auth/service/InvalidRefreshTokenException.java
src/main/java/com/bookswap/auth/web/{AuthController,RegisterRequest,LoginRequest,RefreshRequest,TokenResponse}.java
src/main/java/com/bookswap/user/domain/User.java
src/main/java/com/bookswap/user/repository/UserRepository.java
src/main/java/com/bookswap/user/service/{UserService,UserNotFoundException}.java
src/main/java/com/bookswap/user/web/{UserController,ProfileResponse,UpdateProfileRequest}.java
src/test/java/com/bookswap/AbstractIntegrationTest.java
src/test/java/com/bookswap/SchemaIT.java
src/test/java/com/bookswap/common/error/{ErrorProbeController,GlobalExceptionHandlerIT}.java
src/test/java/com/bookswap/auth/{SecurityIT,RegistrationIT,LoginIT,RefreshIT}.java
src/test/java/com/bookswap/auth/service/{JwtServiceTest,RefreshTokenServiceTest,RefreshTokenConsumeTest,AuthServiceLoginTest}.java
src/test/java/com/bookswap/user/{ProfileIT}.java and user/service/UserServiceTest.java
src/test/java/com/bookswap/common/OpenApiIT.java
```

Prerequisites on the machine (check in Task 1): JDK 21, Maven 3.9+, Docker running (Testcontainers needs it).

---

### Task 1: Skeleton, database schema, Docker Compose, test base

**Files:**
- Create: `pom.xml`, `.gitignore`, `docker-compose.yml`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/db/migration/V1__users_and_refresh_tokens.sql`
- Create: `src/main/java/com/bookswap/BookSwapApplication.java`
- Create: `src/test/java/com/bookswap/AbstractIntegrationTest.java`
- Test: `src/test/java/com/bookswap/SchemaIT.java`

**Interfaces:**
- Produces: `AbstractIntegrationTest` (protected `mvc`, `objectMapper`, `jdbc`; helpers `postJson(url, body)`, `read(ResultActions)`, `registerAndLogin(email, password)`, `bearer(JsonNode tokens)`; truncates `refresh_token, users` before each test). Tables `users`, `refresh_token`.

- [ ] **Step 1: Verify tooling**

Run: `java -version`, `mvn -v`, `docker info`
Expected: Java 21, Maven 3.9+, Docker reachable. Stop and tell the user if any is missing.

- [ ] **Step 2: Init repo and write `.gitignore`**

```bash
cd /d/Projects/bookswap && git init
```

`.gitignore`:
```
target/
.idea/
*.iml
.vscode/
.env
```

- [ ] **Step 3: Write `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.5</version>
    <relativePath/>
  </parent>

  <groupId>com.bookswap</groupId>
  <artifactId>bookswap-api</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <name>bookswap-api</name>

  <properties>
    <java.version>21</java.version>
  </properties>

  <dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-oauth2-resource-server</artifactId></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
    <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
    <dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>2.6.0</version></dependency>

    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>postgresql</artifactId><scope>test</scope></dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <configuration>
          <includes>
            <include>**/*Test.java</include>
            <include>**/*IT.java</include>
          </includes>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 4: Write `BookSwapApplication`, `application.yml`, `docker-compose.yml`**

`src/main/java/com/bookswap/BookSwapApplication.java`:
```java
package com.bookswap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BookSwapApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookSwapApplication.class, args);
    }
}
```

`src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: bookswap
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/bookswap}
    username: ${DB_USER:bookswap}
    password: ${DB_PASSWORD:bookswap}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true

bookswap:
  jwt:
    # DEV DEFAULT ONLY. Override JWT_SECRET (>= 32 bytes) in any real deployment.
    secret: ${JWT_SECRET:dev-only-secret-change-me-32-bytes-minimum!!}
    access-ttl: 15m
    refresh-ttl: 14d
  security:
    bcrypt-strength: 12
```

`docker-compose.yml`:
```yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: bookswap
      POSTGRES_USER: bookswap
      POSTGRES_PASSWORD: bookswap
    ports:
      - "5432:5432"
    volumes:
      - bookswap-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U bookswap -d bookswap"]
      interval: 5s
      timeout: 3s
      retries: 10

volumes:
  bookswap-data:
```

- [ ] **Step 5: Write the failing schema test and test base**

`src/test/java/com/bookswap/AbstractIntegrationTest.java`:
```java
package com.bookswap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "bookswap.security.bcrypt-strength=4")
@AutoConfigureMockMvc
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

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE refresh_token, users CASCADE");
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

    protected String bearer(JsonNode tokens) {
        return "Bearer " + tokens.get("accessToken").asText();
    }
}
```

`src/test/java/com/bookswap/SchemaIT.java`:
```java
package com.bookswap;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaIT extends AbstractIntegrationTest {

    private static final String INSERT = """
            insert into users(id, email, password_hash, display_name, city, postal_code, created_at, updated_at, version)
            values (?, ?, 'hash', ?, ?, ?, now(), now(), 0)""";

    @Test
    void migrationCreatesTables() {
        Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name in ('users','refresh_token')",
                Integer.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void emailIsUniqueIgnoringCase() {
        jdbc.update(INSERT, UUID.randomUUID(), "a@example.com", null, null, null);
        assertThatThrownBy(() -> jdbc.update(INSERT, UUID.randomUUID(), "A@Example.com", null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void profileFieldsAreAllOrNone() {
        assertThatThrownBy(() -> jdbc.update(INSERT, UUID.randomUUID(), "b@example.com", "Bo", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(INSERT, UUID.randomUUID(), "c@example.com", "Bo", "  ", "12345"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(INSERT, UUID.randomUUID(), "d@example.com", "Bo", "Berlin", "10115");
    }
}
```

- [ ] **Step 6: Run to verify it fails**

Run: `mvn -B -q test -Dtest=SchemaIT`
Expected: FAIL (Flyway has no migration; `users` table missing / relation does not exist).

- [ ] **Step 7: Write the migration**

`src/main/resources/db/migration/V1__users_and_refresh_tokens.sql`:
```sql
CREATE TABLE users (
    id            uuid         PRIMARY KEY,
    email         varchar(255) NOT NULL,
    password_hash varchar(100) NOT NULL,
    display_name  varchar(60),
    city          varchar(100),
    postal_code   varchar(20),
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    version       bigint       NOT NULL,
    CONSTRAINT users_profile_all_or_none CHECK (
        (display_name IS NULL AND city IS NULL AND postal_code IS NULL)
        OR (coalesce(btrim(display_name), '') <> ''
            AND coalesce(btrim(city), '') <> ''
            AND coalesce(btrim(postal_code), '') <> '')
    )
);

CREATE UNIQUE INDEX users_email_lower_uq ON users (lower(email));

CREATE TABLE refresh_token (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    revoked_at timestamptz
);

CREATE INDEX refresh_token_user_idx ON refresh_token (user_id);
```

- [ ] **Step 8: Run to verify it passes**

Run: `mvn -B -q test -Dtest=SchemaIT`
Expected: PASS (3 tests). The first run pulls the postgres image.

- [ ] **Step 9: Commit**

```bash
git add -A && git commit -m "chore: project skeleton, Flyway schema, Docker Compose, Testcontainers base"
```

---

### Task 2: Error model, security and JWT infrastructure

**Files:**
- Create: `common/config/AppConfig.java`, `common/error/{ErrorResponse,ApiException,ErrorResponseWriter,GlobalExceptionHandler}.java`
- Create: `auth/config/{JwtProperties,JwtConfig,SecurityConfig}.java`, `auth/service/JwtService.java`
- Test: `common/error/ErrorProbeController.java` (test-only), `common/error/GlobalExceptionHandlerIT.java`, `auth/SecurityIT.java`, `auth/service/JwtServiceTest.java`

All main paths are under `src/main/java/com/bookswap/`, tests under `src/test/java/com/bookswap/`.

**Interfaces:**
- Produces:
  - `ErrorResponse(int status, String code, String message, List<FieldError> fieldErrors, Instant timestamp, String path)` with nested `FieldError(String field, String message)`.
  - `ApiException(HttpStatus status, String code, String message)` with `getStatus()`, `getCode()`.
  - `JwtService.createAccessToken(UUID userId): String`, `JwtService.accessTtlSeconds(): long`.
  - `JwtProperties(String secret, Duration accessTtl, Duration refreshTtl)`.
  - Beans: `Clock`, `SecretKey`, `JwtEncoder`, `JwtDecoder`, `SecurityFilterChain` (permits `/api/auth/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`; everything else authenticated).

- [ ] **Step 1: Write the failing tests**

`common/error/ErrorProbeController.java` (test source; reachable unauthenticated under `/api/auth/_probe`):
```java
package com.bookswap.common.error;

import com.bookswap.common.error.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/_probe")
public class ErrorProbeController {

    public record Body(@NotBlank String name, @Min(1) int count) {}

    @PostMapping("/validate")
    public void validate(@Valid @RequestBody Body body) {}

    @GetMapping("/api-error")
    public void apiError() {
        throw new ApiException(HttpStatus.CONFLICT, "PROBE_CONFLICT", "probe conflict");
    }

    @GetMapping("/lock")
    public void lock() {
        throw new ObjectOptimisticLockingFailureException(Object.class, 1L);
    }

    @GetMapping("/boom")
    public void boom() {
        throw new IllegalStateException("secret internals");
    }
}
```

`common/error/GlobalExceptionHandlerIT.java`:
```java
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
```

`auth/SecurityIT.java`:
```java
package com.bookswap.auth;

import com.bookswap.AbstractIntegrationTest;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityIT extends AbstractIntegrationTest {

    @Autowired JwtEncoder encoder;

    private String token(JwtEncoder enc, Instant issuedAt, Duration ttl) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString()).issuedAt(issuedAt).expiresAt(issuedAt.plus(ttl)).build();
        return enc.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @Test
    void missingTokenGivesStandard401() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/users/me"));
    }

    @Test
    void garbageTokenGivesStandard401() throws Exception {
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void expiredTokenGivesStandard401() throws Exception {
        String expired = token(encoder, Instant.now().minus(Duration.ofHours(2)), Duration.ofMinutes(15));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void tokenSignedWithAnotherKeyGivesStandard401() throws Exception {
        var otherKey = new SecretKeySpec("another-secret-another-secret-123456".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        String forged = token(new NimbusJwtEncoder(new ImmutableSecret<>(otherKey)), Instant.now(), Duration.ofMinutes(15));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenPassesSecurity() throws Exception {
        String valid = token(encoder, Instant.now(), Duration.ofMinutes(15));
        // No handler mapped for this path: authenticated request reaches MVC and gets the standard 404.
        mvc.perform(get("/api/nothing-here").header("Authorization", "Bearer " + valid))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
```

`auth/service/JwtServiceTest.java`:
```java
package com.bookswap.auth.service;

import com.bookswap.auth.config.JwtConfig;
import com.bookswap.auth.config.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final JwtProperties props =
            new JwtProperties("0123456789abcdef0123456789abcdef", Duration.ofMinutes(15), Duration.ofDays(14));
    private final JwtConfig config = new JwtConfig();
    private final SecretKey key = config.jwtSecretKey(props);
    private final JwtService service = new JwtService(config.jwtEncoder(key), props, Clock.systemUTC());

    @Test
    void tokenCarriesUserIdAndFifteenMinuteLifetime() {
        UUID id = UUID.randomUUID();
        Jwt jwt = config.jwtDecoder(key).decode(service.createAccessToken(id));
        assertThat(jwt.getSubject()).isEqualTo(id.toString());
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(service.accessTtlSeconds()).isEqualTo(900);
    }

    @Test
    void tokenIsRejectedByDecoderWithDifferentKey() {
        JwtDecoder other = NimbusJwtDecoder.withSecretKey(
                new SecretKeySpec("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz".getBytes(StandardCharsets.UTF_8), "HmacSHA256")).build();
        String token = service.createAccessToken(UUID.randomUUID());
        assertThatThrownBy(() -> other.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretIsRejectedAtStartup() {
        JwtProperties weak = new JwtProperties("short", Duration.ofMinutes(15), Duration.ofDays(14));
        assertThatThrownBy(() -> config.jwtSecretKey(weak)).isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `mvn -B -q test -Dtest='GlobalExceptionHandlerIT,SecurityIT,JwtServiceTest'`
Expected: COMPILATION FAILURE (`ApiException`, `JwtService`, etc. do not exist).

- [ ] **Step 3: Implement the error model**

`common/config/AppConfig.java`:
```java
package com.bookswap.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
```

`common/error/ErrorResponse.java`:
```java
package com.bookswap.common.error;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(int status, String code, String message, List<FieldError> fieldErrors,
                            Instant timestamp, String path) {

    public record FieldError(String field, String message) {}
}
```

`common/error/ApiException.java`:
```java
package com.bookswap.common.error;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
```

`common/error/ErrorResponseWriter.java`:
```java
package com.bookswap.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

/** Writes the standard error body from servlet-level handlers (security entry point, access denied). */
@Component
public class ErrorResponseWriter {
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ErrorResponseWriter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                new ErrorResponse(status.value(), code, message, List.of(), clock.instant(), request.getRequestURI()));
    }
}
```

`common/error/GlobalExceptionHandler.java`:
```java
package com.bookswap.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.Clock;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApi(ApiException ex, HttpServletRequest request) {
        return respond(ex.getStatus(), ex.getCode(), ex.getMessage(), List.of(), request.getRequestURI());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Object> handleOptimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
                "The resource was modified concurrently, please retry", List.of(), request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Unexpected error", List.of(), request.getRequestURI());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorResponse.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErrorResponse.FieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Validation failed", fields, path(request));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Malformed or missing request body", List.of(), path(request));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        return respond(status, status.name(), status.getReasonPhrase(), List.of(), path(request));
    }

    private ResponseEntity<Object> respond(HttpStatus status, String code, String message,
                                           List<ErrorResponse.FieldError> fieldErrors, String path) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(status.value(), code, message, fieldErrors, clock.instant(), path));
    }

    private static String path(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }
}
```

- [ ] **Step 4: Implement JWT and security**

`auth/config/JwtProperties.java`:
```java
package com.bookswap.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("bookswap.jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl) {}
```

`auth/config/JwtConfig.java`:
```java
package com.bookswap.auth.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
public class JwtConfig {

    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        byte[] bytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("bookswap.jwt.secret must be at least 32 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
```

`auth/service/JwtService.java`:
```java
package com.bookswap.auth.service;

import com.bookswap.auth.config.JwtProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public String createAccessToken(UUID userId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTtl()))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    public long accessTtlSeconds() {
        return properties.accessTtl().toSeconds();
    }
}
```

`auth/config/SecurityConfig.java`:
```java
package com.bookswap.auth.config;

import com.bookswap.common.error.ErrorResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ErrorResponseWriter writer) throws Exception {
        AuthenticationEntryPoint entryPoint = (request, response, ex) ->
                writer.write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
        AccessDeniedHandler denied = (request, response, ex) ->
                writer.write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");

        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(denied))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied));
        return http.build();
    }
}
```

- [ ] **Step 5: Run to verify they pass**

Run: `mvn -B -q test -Dtest='GlobalExceptionHandlerIT,SecurityIT,JwtServiceTest,SchemaIT'`
Expected: PASS. If `unknownPathGivesStandardNotFound` fails because the body code is not `NOT_FOUND`, check that `handleExceptionInternal` is being reached for `NoResourceFoundException` (Spring 6.1 routes it through the base class).

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: global error model, JWT infrastructure and security chain"
```

---

### Task 3: Registration (US-01)

**Files:**
- Create: `user/domain/User.java`, `user/repository/UserRepository.java`, `user/web/ProfileResponse.java`
- Create: `auth/config/PasswordConfig.java`, `auth/service/{AuthService,EmailTakenException}.java`
- Create: `auth/web/{AuthController,RegisterRequest}.java`
- Test: `auth/RegistrationIT.java`

**Interfaces:**
- Consumes: `ApiException`, `Clock`, `AbstractIntegrationTest`.
- Produces:
  - `User(String email, String passwordHash, Instant now)`; getters `getId(): UUID`, `getEmail()`, `getPasswordHash()`, `getDisplayName()`, `getCity()`, `getPostalCode()`; `updateProfile(String displayName, String city, String postalCode, Instant now)`; `isProfileComplete(): boolean`.
  - `UserRepository extends JpaRepository<User, UUID>`: `Optional<User> findByEmail(String)`, `boolean existsByEmail(String)`.
  - `ProfileResponse(UUID id, String email, String displayName, String city, String postalCode, boolean profileComplete)` with `static from(User)`.
  - `AuthService.register(RegisterRequest): User`; `EmailTakenException` (409 `EMAIL_TAKEN`).
  - Bean `PasswordEncoder` (BCrypt, strength from `bookswap.security.bcrypt-strength`).

- [ ] **Step 1: Write the failing test**

`auth/RegistrationIT.java`:
```java
package com.bookswap.auth;

import com.bookswap.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistrationIT extends AbstractIntegrationTest {

    @Test
    void registersUserAndStoresOnlyAHash() throws Exception {
        postJson("/api/auth/register", Map.of("email", "  Alice@Example.com ", "password", "password123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.profileComplete").value(false))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String stored = jdbc.queryForObject("select password_hash from users where email = 'alice@example.com'", String.class);
        assertThat(stored).startsWith("$2").isNotEqualTo("password123");
    }

    @Test
    void duplicateEmailIsRejectedWhateverTheCaseOrWhitespace() throws Exception {
        postJson("/api/auth/register", Map.of("email", "bob@example.com", "password", "password123"))
                .andExpect(status().isCreated());
        postJson("/api/auth/register", Map.of("email", " BOB@Example.COM ", "password", "password123"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(1);
    }

    @Test
    void invalidEmailAndShortPasswordGivePerFieldErrors() throws Exception {
        postJson("/api/auth/register", Map.of("email", "not-an-email", "password", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='email')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='password')]").exists());
    }

    @Test
    void passwordLongerThanBcryptLimitIsA400NotA500() throws Exception {
        postJson("/api/auth/register", Map.of("email", "long@example.com", "password", "x".repeat(73)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='password')]").exists());
    }

    @Test
    void missingFieldsGivePerFieldErrors() throws Exception {
        postJson("/api/auth/register", Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='email')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='password')]").exists());
    }

    @Test
    void malformedOrMissingBodyIsAStandard400() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -B -q test -Dtest=RegistrationIT`
Expected: FAIL (404 for `/api/auth/register`; no controller).

- [ ] **Step 3: Implement user domain and repository**

`user/domain/User.java`:
```java
package com.bookswap.user.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    private String city;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected User() {}

    public User(String email, String passwordHash, Instant now) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateProfile(String displayName, String city, String postalCode, Instant now) {
        this.displayName = displayName;
        this.city = city;
        this.postalCode = postalCode;
        this.updatedAt = now;
    }

    public boolean isProfileComplete() {
        return notBlank(displayName) && notBlank(city) && notBlank(postalCode);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getCity() { return city; }
    public String getPostalCode() { return postalCode; }
}
```

`user/repository/UserRepository.java`:
```java
package com.bookswap.user.repository;

import com.bookswap.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

`user/web/ProfileResponse.java`:
```java
package com.bookswap.user.web;

import com.bookswap.user.domain.User;

import java.util.UUID;

public record ProfileResponse(UUID id, String email, String displayName, String city,
                              String postalCode, boolean profileComplete) {

    public static ProfileResponse from(User user) {
        return new ProfileResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getCity(), user.getPostalCode(), user.isProfileComplete());
    }
}
```

- [ ] **Step 4: Implement auth registration**

`auth/config/PasswordConfig.java`:
```java
package com.bookswap.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {
    @Bean
    PasswordEncoder passwordEncoder(@Value("${bookswap.security.bcrypt-strength:12}") int strength) {
        return new BCryptPasswordEncoder(strength);
    }
}
```

`auth/service/EmailTakenException.java`:
```java
package com.bookswap.auth.service;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class EmailTakenException extends ApiException {
    public EmailTakenException() {
        super(HttpStatus.CONFLICT, "EMAIL_TAKEN", "An account with this email already exists");
    }
}
```

`auth/web/RegisterRequest.java`:
```java
package com.bookswap.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password) {

    public RegisterRequest {
        email = email == null ? null : email.trim();
    }
}
```

`auth/service/AuthService.java` (login/refresh are added in Tasks 4 and 5):
```java
package com.bookswap.auth.service;

import com.bookswap.auth.web.RegisterRequest;
import com.bookswap.user.domain.User;
import com.bookswap.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public User register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailTakenException();
        }
        User user = new User(email, passwordEncoder.encode(request.password()), clock.instant());
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent registration; the unique index on lower(email) decided.
            throw new EmailTakenException();
        }
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
```

`auth/web/AuthController.java`:
```java
package com.bookswap.auth.web;

import com.bookswap.auth.service.AuthService;
import com.bookswap.user.web.ProfileResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileResponse register(@Valid @RequestBody RegisterRequest request) {
        return ProfileResponse.from(authService.register(request));
    }
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `mvn -B -q test -Dtest=RegistrationIT`
Expected: PASS (6 tests).

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: user registration (US-01)"
```

---

### Task 4: Login and refresh-token issuing (US-02, part 1)

**Files:**
- Create: `auth/domain/RefreshToken.java`, `auth/repository/RefreshTokenRepository.java`
- Create: `auth/service/{RefreshTokenService,InvalidCredentialsException}.java`
- Create: `auth/web/{LoginRequest,TokenResponse}.java`
- Modify: `auth/service/AuthService.java` (replace with the full version below), `auth/web/AuthController.java` (replace)
- Test: `auth/LoginIT.java`, `auth/service/RefreshTokenServiceTest.java`, `auth/service/AuthServiceLoginTest.java`

**Interfaces:**
- Consumes: `User`, `UserRepository`, `JwtService`, `JwtProperties`, `Clock`, `PasswordEncoder`.
- Produces:
  - `RefreshToken(UUID userId, String tokenHash, Instant expiresAt)`; getters `getId()`, `getUserId()`, `getTokenHash()`, `getExpiresAt()`, `getUsedAt()`, `getRevokedAt()`.
  - `RefreshTokenRepository`: `Optional<RefreshToken> findByTokenHash(String)`, `int markUsed(UUID id, Instant now)`, `int revokeAllForUser(UUID userId, Instant now)`.
  - `RefreshTokenService.issue(UUID userId): String` (raw token), `static RefreshTokenService.hash(String raw): String`.
  - `TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds)`.
  - `AuthService(UserRepository, PasswordEncoder, RefreshTokenService, JwtService, Clock)`; `login(LoginRequest): TokenResponse`.
  - `InvalidCredentialsException` (401 `INVALID_CREDENTIALS`, message "Invalid email or password").

- [ ] **Step 1: Write the failing tests**

`auth/LoginIT.java`:
```java
package com.bookswap.auth;

import com.bookswap.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginIT extends AbstractIntegrationTest {

    @Autowired JwtDecoder decoder;

    @Test
    void validLoginReturnsTokensAndStoresOnlyTheRefreshHash() throws Exception {
        postJson("/api/auth/register", Map.of("email", "amy@example.com", "password", "password123"))
                .andExpect(status().isCreated());
        JsonNode body = read(postJson("/api/auth/login", Map.of("email", " AMY@example.com", "password", "password123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900)));

        String userId = jdbc.queryForObject("select id::text from users where email='amy@example.com'", String.class);
        assertThat(decoder.decode(body.get("accessToken").asText()).getSubject()).isEqualTo(userId);

        String raw = body.get("refreshToken").asText();
        assertThat(raw).isNotBlank();
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where token_hash = ?", Integer.class, raw)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where user_id = ?::uuid", Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void wrongPasswordAndUnknownEmailAreIndistinguishable() throws Exception {
        postJson("/api/auth/register", Map.of("email", "ann@example.com", "password", "password123"))
                .andExpect(status().isCreated());

        JsonNode wrongPassword = read(postJson("/api/auth/login", Map.of("email", "ann@example.com", "password", "nope-nope"))
                .andExpect(status().isUnauthorized()));
        JsonNode unknownEmail = read(postJson("/api/auth/login", Map.of("email", "ghost@example.com", "password", "nope-nope"))
                .andExpect(status().isUnauthorized()));

        assertThat(wrongPassword.get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(wrongPassword.get("code")).isEqualTo(unknownEmail.get("code"));
        assertThat(wrongPassword.get("message")).isEqualTo(unknownEmail.get("message"));
        assertThat(wrongPassword.get("status")).isEqualTo(unknownEmail.get("status"));
    }

    @Test
    void blankFieldsGivePerFieldErrors() throws Exception {
        postJson("/api/auth/login", Map.of("email", "", "password", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='email')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='password')]").exists());
    }
}
```

`auth/service/RefreshTokenServiceTest.java`:
```java
package com.bookswap.auth.service;

import com.bookswap.auth.config.JwtProperties;
import com.bookswap.auth.domain.RefreshToken;
import com.bookswap.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock RefreshTokenRepository repository;

    private final Instant now = Instant.parse("2026-10-16T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final JwtProperties props = new JwtProperties("x".repeat(32), Duration.ofMinutes(15), Duration.ofDays(14));

    @Test
    void issueStoresOnlyTheHashAndExpiresInFourteenDays() {
        RefreshTokenService service = new RefreshTokenService(repository, props, clock);
        UUID userId = UUID.randomUUID();

        String raw = service.issue(userId);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(RefreshTokenService.hash(raw)).isNotEqualTo(raw).hasSize(64);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(now.plus(Duration.ofDays(14)));
        assertThat(raw.length()).isGreaterThanOrEqualTo(43);
    }

    @Test
    void issuedTokensAreUnique() {
        RefreshTokenService service = new RefreshTokenService(repository, props, clock);
        assertThat(service.issue(UUID.randomUUID())).isNotEqualTo(service.issue(UUID.randomUUID()));
    }
}
```

`auth/service/AuthServiceLoginTest.java`:
```java
package com.bookswap.auth.service;

import com.bookswap.auth.web.LoginRequest;
import com.bookswap.auth.web.TokenResponse;
import com.bookswap.user.domain.User;
import com.bookswap.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock RefreshTokenService refreshTokens;
    @Mock JwtService jwt;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-16T10:00:00Z"), ZoneOffset.UTC);
    private AuthService service;

    @BeforeEach
    void setUp() {
        when(encoder.encode(any())).thenReturn("dummy-hash");
        service = new AuthService(users, encoder, refreshTokens, jwt, clock);
    }

    @Test
    void unknownEmailStillPerformsAPasswordComparisonAndFailsGenerically() {
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("ghost@example.com", "pw")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(encoder).matches("pw", "dummy-hash");
    }

    @Test
    void wrongPasswordFails() {
        User user = new User("a@example.com", "stored-hash", clock.instant());
        when(users.findByEmail("a@example.com")).thenReturn(Optional.of(user));
        when(encoder.matches("pw", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("a@example.com", "pw")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void successIssuesBothTokensAndNormalizesTheEmail() {
        User user = new User("a@example.com", "stored-hash", clock.instant());
        when(users.findByEmail("a@example.com")).thenReturn(Optional.of(user));
        when(encoder.matches("pw", "stored-hash")).thenReturn(true);
        when(jwt.createAccessToken(user.getId())).thenReturn("access");
        when(jwt.accessTtlSeconds()).thenReturn(900L);
        when(refreshTokens.issue(user.getId())).thenReturn("refresh");

        TokenResponse response = service.login(new LoginRequest("  A@Example.com ", "pw"));

        assertThat(response).isEqualTo(new TokenResponse("access", "refresh", "Bearer", 900L));
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `mvn -B -q test -Dtest='LoginIT,RefreshTokenServiceTest,AuthServiceLoginTest'`
Expected: COMPILATION FAILURE (`RefreshTokenService`, `LoginRequest`, etc. missing).

- [ ] **Step 3: Implement refresh-token persistence and issuing**

`auth/domain/RefreshToken.java`:
```java
package com.bookswap.auth.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshToken() {}

    public RefreshToken(UUID userId, String tokenHash, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
```

Note: `RefreshToken` has no `@Version`, so `save()` of a new instance with an assigned id performs a merge (select, then insert). That is acceptable here; do not "fix" it by adding a version column.

`auth/repository/RefreshTokenRepository.java`:
```java
package com.bookswap.auth.repository;

import com.bookswap.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Atomically marks a token used; returns 0 if it was already used or revoked (lost a race). */
    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.usedAt = :now where t.id = :id and t.usedAt is null and t.revokedAt is null")
    int markUsed(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
```

`auth/service/RefreshTokenService.java` (`consume` is added in Task 5):
```java
package com.bookswap.auth.service;

import com.bookswap.auth.config.JwtProperties;
import com.bookswap.auth.domain.RefreshToken;
import com.bookswap.auth.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository repository;
    private final JwtProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Creates a refresh token for the user and returns the raw value; only its hash is stored. */
    @Transactional
    public String issue(UUID userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new RefreshToken(userId, hash(raw), clock.instant().plus(properties.refreshTtl())));
        return raw;
    }

    public static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
```

- [ ] **Step 4: Implement login**

`auth/service/InvalidCredentialsException.java`:
```java
package com.bookswap.auth.service;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends ApiException {
    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
    }
}
```

`auth/web/LoginRequest.java`:
```java
package com.bookswap.auth.web;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String email, @NotBlank String password) {

    public LoginRequest {
        email = email == null ? null : email.trim();
    }
}
```

`auth/web/TokenResponse.java`:
```java
package com.bookswap.auth.web;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {}
```

Replace `auth/service/AuthService.java` with:
```java
package com.bookswap.auth.service;

import com.bookswap.auth.web.LoginRequest;
import com.bookswap.auth.web.RegisterRequest;
import com.bookswap.auth.web.TokenResponse;
import com.bookswap.user.domain.User;
import com.bookswap.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final JwtService jwtService;
    private final Clock clock;
    /** Compared against when the email is unknown, so timing does not reveal whether it exists. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, RefreshTokenService refreshTokens,
                       JwtService jwtService, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.jwtService = jwtService;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public User register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailTakenException();
        }
        User user = new User(email, passwordEncoder.encode(request.password()), clock.instant());
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent registration; the unique index on lower(email) decided.
            throw new EmailTakenException();
        }
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Optional<User> user = users.findByEmail(normalizeEmail(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !matches) {
            throw new InvalidCredentialsException();
        }
        return issueTokens(user.get().getId());
    }

    private TokenResponse issueTokens(UUID userId) {
        return new TokenResponse(jwtService.createAccessToken(userId), refreshTokens.issue(userId),
                "Bearer", jwtService.accessTtlSeconds());
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
```

Replace `auth/web/AuthController.java` with:
```java
package com.bookswap.auth.web;

import com.bookswap.auth.service.AuthService;
import com.bookswap.user.web.ProfileResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileResponse register(@Valid @RequestBody RegisterRequest request) {
        return ProfileResponse.from(authService.register(request));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
```

- [ ] **Step 5: Run to verify they pass**

Run: `mvn -B -q test -Dtest='LoginIT,RefreshTokenServiceTest,AuthServiceLoginTest,RegistrationIT'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: login and refresh-token issuing (US-02 part 1)"
```

---

### Task 5: Refresh rotation, reuse-detection, concurrency (US-02, part 2)

**Files:**
- Create: `auth/service/InvalidRefreshTokenException.java`, `auth/web/RefreshRequest.java`
- Modify: `auth/service/RefreshTokenService.java` (add `consume`), `auth/service/AuthService.java` (add `refresh`), `auth/web/AuthController.java` (add endpoint)
- Test: `auth/service/RefreshTokenConsumeTest.java`, `auth/RefreshIT.java`

**Interfaces:**
- Consumes: `RefreshTokenRepository.findByTokenHash/markUsed/revokeAllForUser`, `RefreshTokenService.hash`, `Clock`.
- Produces: `RefreshTokenService.consume(String raw): UUID` (userId; throws `InvalidRefreshTokenException`), `AuthService.refresh(String raw): TokenResponse`, `RefreshRequest(@NotBlank String refreshToken)`, `POST /api/auth/refresh`. `InvalidRefreshTokenException` -> 401 `INVALID_REFRESH_TOKEN`.

Critical: reuse-detection revokes tokens and then throws. A normal `@Transactional` would roll the revocation back. Both `consume` and `AuthService.refresh` must use `noRollbackFor = InvalidRefreshTokenException.class`, and `RefreshIT` proves the revocation persists.

- [ ] **Step 1: Write the failing tests**

`auth/service/RefreshTokenConsumeTest.java`:
```java
package com.bookswap.auth.service;

import com.bookswap.auth.config.JwtProperties;
import com.bookswap.auth.domain.RefreshToken;
import com.bookswap.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenConsumeTest {

    @Mock RefreshTokenRepository repository;

    private final Instant now = Instant.parse("2026-10-16T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final UUID userId = UUID.randomUUID();
    private RefreshTokenService service() {
        return new RefreshTokenService(repository,
                new JwtProperties("x".repeat(32), Duration.ofMinutes(15), Duration.ofDays(14)), clock);
    }

    private RefreshToken stored(Instant expiresAt, Instant usedAt, Instant revokedAt) {
        RefreshToken token = new RefreshToken(userId, RefreshTokenService.hash("raw"), expiresAt);
        ReflectionTestUtils.setField(token, "usedAt", usedAt);
        ReflectionTestUtils.setField(token, "revokedAt", revokedAt);
        return token;
    }

    @Test
    void unknownTokenIsRejectedWithoutRevokingAnything() {
        when(repository.findByTokenHash(RefreshTokenService.hash("raw"))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().consume("raw")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository, never()).revokeAllForUser(any(), any());
    }

    @Test
    void expiredTokenIsRejectedWithoutRevokingAnything() {
        when(repository.findByTokenHash(RefreshTokenService.hash("raw")))
                .thenReturn(Optional.of(stored(now.minusSeconds(1), null, null)));
        assertThatThrownBy(() -> service().consume("raw")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository, never()).revokeAllForUser(any(), any());
    }

    @Test
    void revokedTokenIsRejected() {
        when(repository.findByTokenHash(RefreshTokenService.hash("raw")))
                .thenReturn(Optional.of(stored(now.plusSeconds(60), null, now.minusSeconds(5))));
        assertThatThrownBy(() -> service().consume("raw")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository, never()).markUsed(any(), any());
    }

    @Test
    void reusedTokenRevokesAllTokensOfTheUser() {
        when(repository.findByTokenHash(RefreshTokenService.hash("raw")))
                .thenReturn(Optional.of(stored(now.plusSeconds(60), now.minusSeconds(5), null)));
        assertThatThrownBy(() -> service().consume("raw")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository).revokeAllForUser(userId, now);
    }

    @Test
    void losingTheRaceToMarkUsedIsTreatedAsReuse() {
        RefreshToken token = stored(now.plusSeconds(60), null, null);
        when(repository.findByTokenHash(RefreshTokenService.hash("raw"))).thenReturn(Optional.of(token));
        when(repository.markUsed(token.getId(), now)).thenReturn(0);
        assertThatThrownBy(() -> service().consume("raw")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository).revokeAllForUser(userId, now);
    }

    @Test
    void validTokenIsMarkedUsedAndReturnsTheUserId() {
        RefreshToken token = stored(now.plusSeconds(60), null, null);
        when(repository.findByTokenHash(RefreshTokenService.hash("raw"))).thenReturn(Optional.of(token));
        when(repository.markUsed(token.getId(), now)).thenReturn(1);
        assertThat(service().consume("raw")).isEqualTo(userId);
        verify(repository, never()).revokeAllForUser(any(), any());
    }
}
```

`auth/RefreshIT.java`:
```java
package com.bookswap.auth;

import com.bookswap.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefreshIT extends AbstractIntegrationTest {

    private JsonNode refresh(String token) throws Exception {
        return read(postJson("/api/auth/refresh", Map.of("refreshToken", token)));
    }

    @Test
    void refreshRotatesTheTokenPair() throws Exception {
        JsonNode login = registerAndLogin("rot@example.com", "password123");
        String first = login.get("refreshToken").asText();

        JsonNode second = read(postJson("/api/auth/refresh", Map.of("refreshToken", first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists()));

        assertThat(second.get("refreshToken").asText()).isNotEqualTo(first);
        // the new token works too
        postJson("/api/auth/refresh", Map.of("refreshToken", second.get("refreshToken").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void reusingAnOldTokenIsRejectedAndRevokesTheWholeFamily() throws Exception {
        String t1 = registerAndLogin("reuse@example.com", "password123").get("refreshToken").asText();
        String t2 = refresh(t1).get("refreshToken").asText();

        postJson("/api/auth/refresh", Map.of("refreshToken", t1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // t2 was valid and unused, but reuse-detection must have revoked it (proves no rollback)
        postJson("/api/auth/refresh", Map.of("refreshToken", t2))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where revoked_at is null", Integer.class)).isZero();
    }

    @Test
    void garbageTokenIsRejectedAndDoesNotRevokeRealTokens() throws Exception {
        String real = registerAndLogin("safe@example.com", "password123").get("refreshToken").asText();

        postJson("/api/auth/refresh", Map.of("refreshToken", "garbage-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        postJson("/api/auth/refresh", Map.of("refreshToken", real)).andExpect(status().isOk());
    }

    @Test
    void blankTokenIsA400() throws Exception {
        postJson("/api/auth/refresh", Map.of("refreshToken", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='refreshToken')]").exists());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String token = registerAndLogin("old@example.com", "password123").get("refreshToken").asText();
        jdbc.update("update refresh_token set expires_at = now() - interval '1 hour'");
        postJson("/api/auth/refresh", Map.of("refreshToken", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentRefreshWithTheSameTokenSucceedsExactlyOnce() throws Exception {
        for (int round = 0; round < 5; round++) {
            String token = registerAndLogin("race" + round + "@example.com", "password123").get("refreshToken").asText();
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<Integer> call = () -> {
                start.await();
                return postJson("/api/auth/refresh", Map.of("refreshToken", token))
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> a = pool.submit(call);
            Future<Integer> b = pool.submit(call);
            start.countDown();
            List<Integer> statuses = List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            pool.shutdown();
            assertThat(statuses).containsExactlyInAnyOrder(200, 401);
        }
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `mvn -B -q test -Dtest='RefreshTokenConsumeTest,RefreshIT'`
Expected: COMPILATION FAILURE (`consume`, `InvalidRefreshTokenException` missing).

- [ ] **Step 3: Implement**

`auth/service/InvalidRefreshTokenException.java`:
```java
package com.bookswap.auth.service;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends ApiException {
    public InvalidRefreshTokenException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Invalid or expired refresh token");
    }
}
```

`auth/web/RefreshRequest.java`:
```java
package com.bookswap.auth.web;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank String refreshToken) {}
```

Add to `RefreshTokenService` (and import `java.time.Instant`):
```java
    /**
     * Validates and single-use-consumes a refresh token, returning the owning user id.
     * Reuse of an already-used token revokes all of that user's tokens. The revocation must be
     * committed even though we throw, hence noRollbackFor.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public UUID consume(String raw) {
        Instant now = clock.instant();
        RefreshToken token = repository.findByTokenHash(hash(raw))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (token.getRevokedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException();
        }
        if (token.getUsedAt() != null || repository.markUsed(token.getId(), now) == 0) {
            repository.revokeAllForUser(token.getUserId(), now);
            throw new InvalidRefreshTokenException();
        }
        return token.getUserId();
    }
```

Add to `AuthService`:
```java
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenResponse refresh(String rawRefreshToken) {
        UUID userId = refreshTokens.consume(rawRefreshToken);
        return issueTokens(userId);
    }
```
(import `com.bookswap.auth.service.InvalidRefreshTokenException` is same package; none needed.)

Add to `AuthController` (and `RefreshRequest` is in the same package):
```java
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }
```

- [ ] **Step 4: Run to verify they pass**

Run: `mvn -B -q test -Dtest='RefreshTokenConsumeTest,RefreshIT,LoginIT'`
Expected: PASS. If the reuse test fails at the "t2 is revoked" assertion, the revocation was rolled back: re-check `noRollbackFor` on both `consume` and `AuthService.refresh`.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: refresh-token rotation with reuse-detection (US-02 part 2)"
```

---

### Task 6: Profile (US-03)

**Files:**
- Create: `user/service/{UserService,UserNotFoundException}.java`, `user/web/{UserController,UpdateProfileRequest}.java`
- Test: `user/service/UserServiceTest.java`, `user/ProfileIT.java`

**Interfaces:**
- Consumes: `User`, `UserRepository`, `ProfileResponse.from`, `ApiException`, `Clock`.
- Produces: `UserService.getProfile(UUID principalId): ProfileResponse`, `UserService.updateProfile(UUID principalId, UpdateProfileRequest): ProfileResponse`, `UpdateProfileRequest(String displayName, String city, String postalCode)`, `GET/PUT /api/users/me`, `UserNotFoundException` (404 `USER_NOT_FOUND`).

- [ ] **Step 1: Write the failing tests**

`user/service/UserServiceTest.java`:
```java
package com.bookswap.user.service;

import com.bookswap.user.domain.User;
import com.bookswap.user.repository.UserRepository;
import com.bookswap.user.web.ProfileResponse;
import com.bookswap.user.web.UpdateProfileRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository users;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-16T10:00:00Z"), ZoneOffset.UTC);

    private UserService service() {
        return new UserService(users, clock);
    }

    @Test
    void newUserHasAnIncompleteProfile() {
        User user = new User("a@example.com", "hash", clock.instant());
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        ProfileResponse profile = service().getProfile(user.getId());

        assertThat(profile.profileComplete()).isFalse();
        assertThat(profile.email()).isEqualTo("a@example.com");
    }

    @Test
    void updateAppliesToThePrincipalAndCompletesTheProfile() {
        User user = new User("a@example.com", "hash", clock.instant());
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        ProfileResponse profile = service().updateProfile(user.getId(),
                new UpdateProfileRequest("Amy", "Berlin", "10115"));

        assertThat(profile.profileComplete()).isTrue();
        assertThat(user.getDisplayName()).isEqualTo("Amy");
        assertThat(user.getCity()).isEqualTo("Berlin");
        assertThat(user.getPostalCode()).isEqualTo("10115");
    }

    @Test
    void unknownPrincipalGivesNotFound() {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().getProfile(id)).isInstanceOf(UserNotFoundException.class);
    }
}
```

`user/ProfileIT.java`:
```java
package com.bookswap.user;

import com.bookswap.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileIT extends AbstractIntegrationTest {

    private org.springframework.test.web.servlet.ResultActions putMe(JsonNode tokens, Object body) throws Exception {
        return mvc.perform(put("/api/users/me").header("Authorization", bearer(tokens))
                .contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    @Test
    void profileStartsIncompleteThenBecomesCompleteAfterUpdate() throws Exception {
        JsonNode tokens = registerAndLogin("pat@example.com", "password123");

        mvc.perform(get("/api/users/me").header("Authorization", bearer(tokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("pat@example.com"))
                .andExpect(jsonPath("$.profileComplete").value(false));

        putMe(tokens, Map.of("displayName", " Pat ", "city", "Berlin", "postalCode", "10115"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Pat"))
                .andExpect(jsonPath("$.profileComplete").value(true));

        mvc.perform(get("/api/users/me").header("Authorization", bearer(tokens)))
                .andExpect(jsonPath("$.city").value("Berlin"))
                .andExpect(jsonPath("$.postalCode").value("10115"))
                .andExpect(jsonPath("$.profileComplete").value(true));
    }

    @Test
    void eachUserOnlySeesAndChangesTheirOwnProfile() throws Exception {
        JsonNode a = registerAndLogin("a@example.com", "password123");
        JsonNode b = registerAndLogin("b@example.com", "password123");
        putMe(a, Map.of("displayName", "Alice", "city", "Berlin", "postalCode", "10115")).andExpect(status().isOk());

        mvc.perform(get("/api/users/me").header("Authorization", bearer(b)))
                .andExpect(jsonPath("$.email").value("b@example.com"))
                .andExpect(jsonPath("$.displayName").doesNotExist());

        // there is no way to address another user by id
        String aId = jdbc.queryForObject("select id::text from users where email='a@example.com'", String.class);
        mvc.perform(put("/api/users/" + aId).header("Authorization", bearer(b))
                        .contentType(APPLICATION_JSON).content("{\"displayName\":\"x\",\"city\":\"y\",\"postalCode\":\"z\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void emailCannotBeChangedThroughTheProfile() throws Exception {
        JsonNode tokens = registerAndLogin("keep@example.com", "password123");
        putMe(tokens, Map.of("email", "evil@example.com", "displayName", "K", "city", "Bonn", "postalCode", "53111"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("keep@example.com"));
    }

    @Test
    void blankOrOversizedFieldsAreRejectedPerField() throws Exception {
        JsonNode tokens = registerAndLogin("val@example.com", "password123");
        putMe(tokens, Map.of("displayName", "   ", "city", "", "postalCode", "1".repeat(21)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='displayName')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='city')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='postalCode')]").exists());
        putMe(tokens, Map.of("displayName", "n".repeat(61), "city", "Berlin", "postalCode", "10115"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='displayName')]").exists());
    }

    @Test
    void validTokenOfADeletedUserIsAClean404() throws Exception {
        JsonNode tokens = registerAndLogin("gone@example.com", "password123");
        jdbc.update("delete from users where email = 'gone@example.com'");
        mvc.perform(get("/api/users/me").header("Authorization", bearer(tokens)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void profileEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/users/me").contentType(APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `mvn -B -q test -Dtest='UserServiceTest,ProfileIT'`
Expected: COMPILATION FAILURE (`UserService` etc. missing).

- [ ] **Step 3: Implement**

`user/service/UserNotFoundException.java`:
```java
package com.bookswap.user.service;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ApiException {
    public UserNotFoundException() {
        super(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
    }
}
```

`user/web/UpdateProfileRequest.java`:
```java
package com.bookswap.user.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 60) String displayName,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 20) String postalCode) {

    public UpdateProfileRequest {
        displayName = displayName == null ? null : displayName.trim();
        city = city == null ? null : city.trim();
        postalCode = postalCode == null ? null : postalCode.trim();
    }
}
```

`user/service/UserService.java`:
```java
package com.bookswap.user.service;

import com.bookswap.user.domain.User;
import com.bookswap.user.repository.UserRepository;
import com.bookswap.user.web.ProfileResponse;
import com.bookswap.user.web.UpdateProfileRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class UserService {
    private final UserRepository users;
    private final Clock clock;

    public UserService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    /** Authorization is by construction: callers pass the authenticated principal's id, never a request-supplied id. */
    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID principalId) {
        return ProfileResponse.from(load(principalId));
    }

    @Transactional
    public ProfileResponse updateProfile(UUID principalId, UpdateProfileRequest request) {
        User user = load(principalId);
        user.updateProfile(request.displayName(), request.city(), request.postalCode(), clock.instant());
        return ProfileResponse.from(user);
    }

    private User load(UUID id) {
        return users.findById(id).orElseThrow(UserNotFoundException::new);
    }
}
```

`user/web/UserController.java`:
```java
package com.bookswap.user.web;

import com.bookswap.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/me")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ProfileResponse me(Principal principal) {
        return userService.getProfile(UUID.fromString(principal.getName()));
    }

    @PutMapping
    public ProfileResponse update(Principal principal, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(UUID.fromString(principal.getName()), request);
    }
}
```

- [ ] **Step 4: Run to verify they pass**

Run: `mvn -B -q test -Dtest='UserServiceTest,ProfileIT'`
Expected: PASS. If `eachUserOnlySeesAndChangesTheirOwnProfile` returns 405 instead of 404 for `PUT /api/users/{id}`, accept 405 only if Spring maps it that way; the requirement is "no write endpoint by id", so adjust the assertion to `is4xxClientError()` and note why.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: user profile (US-03)"
```

---

### Task 7: OpenAPI, Dockerfile, CI, README

**Files:**
- Create: `common/config/OpenApiConfig.java`, `Dockerfile`, `.github/workflows/ci.yml`, `README.md`
- Modify: `user/web/UserController.java` (add `@SecurityRequirement`), `docker-compose.yml` (add `app` service)
- Test: `common/OpenApiIT.java`

**Interfaces:**
- Consumes: all controllers.
- Produces: OpenAPI doc at `/v3/api-docs` with a `bearerAuth` scheme; Swagger UI at `/swagger-ui.html`.

- [ ] **Step 1: Write the failing test**

`common/OpenApiIT.java`:
```java
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
    void swaggerUiIsNotBehindAuthentication() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -B -q test -Dtest=OpenApiIT`
Expected: FAIL on `components.securitySchemes.bearerAuth` (scheme not declared yet).

- [ ] **Step 3: Implement OpenAPI config**

`common/config/OpenApiConfig.java`:
```java
package com.bookswap.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "BookSwap API", version = "v1",
        description = "Local book swapping. Log in via /api/auth/login, then use Authorize with the access token."))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {}
```

In `user/web/UserController.java` add the import `io.swagger.v3.oas.annotations.security.SecurityRequirement` and annotate the class with `@SecurityRequirement(name = "bearerAuth")` (above `@RestController`).

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -B -q test -Dtest=OpenApiIT`
Expected: PASS.

- [ ] **Step 5: Dockerfile, compose app service, CI, README**

`Dockerfile`:
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Append to `docker-compose.yml` under `services:` (keep `volumes:` as is):
```yaml
  app:
    build: .
    profiles: ["app"]
    depends_on:
      db:
        condition: service_healthy
    environment:
      DB_URL: jdbc:postgresql://db:5432/bookswap
      DB_USER: bookswap
      DB_PASSWORD: bookswap
      JWT_SECRET: ${JWT_SECRET:-dev-only-secret-change-me-32-bytes-minimum!!}
    ports:
      - "8080:8080"
```

`.github/workflows/ci.yml`:
```yaml
name: CI

on:
  push:
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
          cache: maven
      - name: Build and test (Testcontainers uses the runner's Docker)
        run: mvn -B verify
      - name: Upload test reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: target/surefire-reports
```

`README.md`:
```markdown
# BookSwap API

Spring Boot 3 / Java 21 backend for local book swapping. See `BookSwapRequirementsSpec.md`;
design and plan live in `docs/superpowers/`.

## Run locally
```bash
docker compose up -d db          # PostgreSQL 16
mvn spring-boot:run              # app on :8080
```
Or everything in containers: `docker compose --profile app up --build`.

Swagger UI: http://localhost:8080/swagger-ui.html
Register, log in, click **Authorize**, paste the access token.

## Test
```bash
mvn verify    # unit + Testcontainers integration tests (Docker required)
```

## Configuration
| Env var | Default | Notes |
| --- | --- | --- |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | local compose values | |
| `JWT_SECRET` | dev-only default | **Override in any real deployment**, at least 32 bytes |
```

- [ ] **Step 6: Full verification**

Run: `mvn -B verify`
Expected: BUILD SUCCESS, all unit and integration tests pass.

Then manually: `docker compose up -d db`, `mvn spring-boot:run`, open Swagger UI, register, log in, Authorize, `PUT /api/users/me`, `GET /api/users/me`; call `/api/auth/refresh` twice with the same refresh token and confirm the second is 401 (and that the newest token from the first call no longer works either).

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat: OpenAPI docs, Dockerfile, CI, README"
```

---

## Self-Review

**Spec coverage:** Structure/auth/profile/data model/errors/testing/Docker/CI/OpenAPI sections of the design map to Tasks 1-7. Reuse-detection with revoke-all: Task 5 (with `noRollbackFor` called out and tested). Public profile views, ISBN, scheduling are explicitly out of scope per the spec.

**Placeholder scan:** none; all steps contain code or exact commands.

**Type consistency:** `AuthService` constructor changes between Task 3 (3 args) and Task 4 (5 args); Task 4 supplies the full replacement and `AuthServiceLoginTest` uses the 5-arg form. `RefreshTokenService(repository, properties, clock)` is the same in Tasks 4 and 5. `TokenResponse` field names (`accessToken`, `refreshToken`, `tokenType`, `expiresInSeconds`) match the ITs. `ProfileResponse` is defined in Task 3 and reused in Task 6.

**Known judgment calls:** (1) `refresh_token.token_hash` is `varchar(64)`, not `char(64)`. (2) `ProfileIT` accepts 404 or 405 for `PUT /api/users/{id}` if Spring maps it differently; the requirement is only that no write-by-id endpoint exists.
