# BookSwap Step 1: Foundation Design

## Context
BookSwap (see `D:\Projects\bookswap\BookSwapRequirementsSpec.md`) is a local-pickup book swap app. It's a portfolio project where the Java backend is the showcase. This design covers delivery step 1 only: project skeleton, PostgreSQL + Flyway, Docker Compose, CI, and US-01 (register), US-02 (login), US-03 (profile). Stack: Java 21, Spring Boot 3, Maven, PostgreSQL. Open-question defaults from the spec apply. Project directory is empty and not a git repo.

Status: design approved in conversation; written spec awaiting review before the implementation plan.

## Structure
Single Maven module, base package `com.bookswap`, package-by-feature with layers inside each feature.
- `common`: `@RestControllerAdvice`, error body, OpenAPI config, shared exceptions, `Clock` bean.
- `auth`: `/api/auth/register|login|refresh`, JWT service, security filter chain.
- `user`: `User` entity, `GET/PUT /api/users/me`.
- Each feature has `web` (controllers, record DTOs), `service`, `domain` (entities), `repository`. Entities never leave the service layer. Hand-written mappers, no MapStruct.

## Auth
- BCrypt (strength 12), password min 8. Email trimmed, lower-cased, unique (DB unique index on `lower(email)` plus service check). Duplicate -> 409 `EMAIL_TAKEN`. Per-field validation errors via Bean Validation.
- Access token: JWT HS256, 15 min, `sub` = user id, secret from config. Validated by Spring Security resource-server support, no custom filter.
- Refresh token: opaque random, 14 days, only SHA-256 hash stored. Rotated on use. **Reuse-detection:** presenting an already-used token returns 401 and revokes all of that user's refresh tokens.
- Login failure: always 401, generic message; dummy hash comparison for unknown emails to even out timing.
- Authorization in the service layer: `UserService.updateProfile` takes the principal's id, never a path id. No `/users/{id}` write endpoint.

## Profile
- `GET`/`PUT /api/users/me`: display name, city, postal code. Email read-only.
- Computed `profileComplete` (all three set), used by step 2 to gate listing copies.
- Public views of other users arrive with discovery (step 3).

## Data model (`V1__users_and_refresh_tokens.sql`, `ddl-auto=validate`)
`users`: id uuid PK; email varchar(255) NOT NULL, unique on `lower(email)`; password_hash varchar(100) NOT NULL; display_name varchar(60), city varchar(100), postal_code varchar(20) (nullable; CHECK: if any set, all non-blank); created_at, updated_at timestamptz NOT NULL; version bigint NOT NULL (`@Version`).

`refresh_token`: id uuid PK; user_id uuid FK -> users ON DELETE CASCADE, indexed; token_hash char(64) NOT NULL unique; expires_at timestamptz NOT NULL; used_at, revoked_at timestamptz nullable.

Refresh flow (one transaction): look up by hash; unknown/expired/revoked -> 401; `used_at` set -> revoke all user's tokens, 401; else atomic `UPDATE ... SET used_at WHERE used_at IS NULL` (so concurrent refreshes can't both win), issue new pair, insert new token. Time from an injected `Clock`.

## Errors
One advice -> `{status, code, message, fieldErrors[], timestamp, path}`. 400 validation (per field), 409 `EMAIL_TAKEN`, 401 bad credentials / bad refresh token (generic), 409 `CONCURRENT_UPDATE` (optimistic lock), 500 with no internals. Security entry point and access-denied handler use the same body.

## Testing
- Unit (JUnit 5 + Mockito): AuthService, UserService, rotation + reuse-detection, profile-complete rule, fixed `Clock`.
- Integration (Testcontainers PostgreSQL, `@SpringBootTest` + MockMvc): register -> login -> me -> update; duplicate email 409; field errors; identical 401 for wrong password vs unknown email; refresh rotation; reuse -> 401 and new token revoked; concurrent refresh with same token -> exactly one wins; missing/expired access token 401; no endpoint to touch another user's profile. Flyway runs on the container; `validate` checks entities against the real schema.

## Docker Compose, CI, OpenAPI
- `docker-compose.yml`: PostgreSQL 16, named volume, healthcheck; optional `app` service via multi-stage `Dockerfile`. Env-var config with local defaults; only a marked dev `JWT_SECRET` default committed.
- GitHub Actions on push/PR: checkout, Temurin 21 with Maven cache, `mvn verify`, upload test reports.
- springdoc-openapi, Swagger UI at `/swagger-ui.html` with bearer-auth scheme (interim client).

## Out of step 1
ISBN catalog client, scheduled jobs, rate limiting, email verification, copy/book tables.

## Verification
- `mvn verify` passes locally and in CI (unit + Testcontainers tests).
- `docker compose up -d`, run the app, use Swagger UI to register, log in, authorize with the token, edit the profile, refresh twice with the same refresh token and confirm the second is rejected.
