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

## Discovery API

Read-only, bearer token required. Both return other people's *available* copies (never your own) in one city, newest first, in the same paginated `PageResponse`.

| Endpoint | Purpose |
| --- | --- |
| `GET /api/feed?city=&page=&size=` | What is available near you |
| `GET /api/search?q=&city=&page=&size=` | The same, narrowed by `q`: a valid ISBN (10 or 13, hyphens ok) is an exact match, anything else is a partial, case-insensitive match on title or author (`%` and `_` are literal; 1-100 characters) |

`city` defaults to your profile city, is matched case-insensitively, and is required if your profile has none (400 `LOCATION_REQUIRED`; a NUL or over-100-character city is 400 `INVALID_LOCATION`). A card is `{copyId, isbn, title, author, coverUrl, grade, detailsSource, owner: {displayName, city}}`; the owner's email and postal code are never exposed.

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
