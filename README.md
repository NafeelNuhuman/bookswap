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
