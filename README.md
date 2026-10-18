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

## Library API

All endpoints need a bearer token. New copies start `UNLISTED`; listing one needs a complete profile.

| Endpoint | Purpose |
| --- | --- |
| `GET /api/books/lookup?isbn=` | Look a book up (local first, then Open Library); saves nothing. 404 `BOOK_NOT_FOUND` / 503 `CATALOG_UNAVAILABLE` mean "enter title and author manually" |
| `POST /api/copies` | Add a copy: `{isbn, grade, title?, author?}`. ISBN-10/13 accepted, stored as ISBN-13. `title`/`author` are used only if neither we nor the catalog know the book; they are stored on your copy, never in the shared book record, and the catalog's details replace them automatically once the catalog knows the book (`detailsSource`: `CATALOG` / `MANUAL`) |
| `GET /api/copies?status=&page=&size=` | My library, paginated (size 1-100, default 20) |
| `GET /api/copies/{id}`, `PUT /api/copies/{id}` | View a copy / change its grade |
| `POST /api/copies/{id}/list`, `/unlist` | Make a copy available / unlisted |
| `DELETE /api/copies/{id}` | Remove a copy |
| `GET /api/condition-grades` | The five grades with their criteria |

Someone else's copy is a 404. A reserved copy cannot be edited, unlisted or removed (409 `COPY_RESERVED`).

## Test

```bash
mvn verify    # unit + Testcontainers integration tests (Docker required)
```

## Configuration

| Env var | Default | Notes |
| --- | --- | --- |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | local compose values | |
| `CATALOG_BASE_URL` | `https://openlibrary.org` | Book catalog used by the ISBN lookup |
| `JWT_SECRET` | dev-only default | **Override in any real deployment**, at least 32 bytes |
