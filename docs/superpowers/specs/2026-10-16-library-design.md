# BookSwap Step 2: Library Design (US-05 to US-10)

## Context
Step 1 (auth + profile) is built and pushed. Step 2 adds the user's library: books identified by ISBN, owned as copies with a condition grade and a status, plus the ISBN catalog lookup. Spec: `BookSwapRequirementsSpec.md` (Epic B). User chose **Open Library** as the catalog. Step 2 is a new spec -> plan -> implement cycle; on approval, save this as `docs/superpowers/specs/2026-10-16-library-design.md`, then you review it, then writing-plans (inline execution, check-in after each task, as before).

## Housekeeping first (task 1)
- Upgrade Spring Boot to the latest 3.5.x patch (follow-up from step 1; Spring Security CVE-2025-22228). Confirm over-long passwords still give 400 and the suite is green. If the bump breaks something non-trivial, ruling: stay on 3.3.x patch and ledger it.
- Fix deferred minor 3: one blank definition (`strip()` in `UpdateProfileRequest`, `User.isProfileComplete`, matching the DB check), because listing is now gated on `profileComplete`.

## Structure (same conventions as step 1)
New feature packages, each with `web` / `service` / `domain` / `repository`:
- `book`: `Book` entity (shared), `Isbn` value object (validate, normalize to ISBN-13), `BookCatalog` port + `OpenLibraryCatalog` adapter, `BookService` (lookup + find-or-create).
- `copy`: `Copy` entity, `ConditionGrade` enum (with criteria text), `CopyStatus` enum, `CopyService`, `CopyController`.
- Entities never leave the service layer; record DTOs; hand-written mappers.

## API (all authenticated, bearer)
| Endpoint | Behaviour |
| --- | --- |
| `GET /api/books/lookup?isbn=` | Validates ISBN (10 or 13, hyphens ok, check digit) -> 400 `INVALID_ISBN`. Returns normalized ISBN-13, title, author, coverUrl, source (`LOCAL` if we already hold the book, else `CATALOG`). Nothing is saved. Not found -> 404 `BOOK_NOT_FOUND`; catalog down/timeout -> 503 `CATALOG_UNAVAILABLE`. Both mean "client offers manual entry". |
| `POST /api/copies` | `{isbn, grade, title?, author?}` -> 201. Book resolved in order: existing local book (client title/author ignored) -> catalog lookup -> manual title+author; none available -> 400 `BOOK_DETAILS_REQUIRED`. Always creates a new copy (US-07: duplicates allowed). New copies start `UNLISTED`. |
| `GET /api/copies?status=&page=&size=` | My copies, paginated (default 20, max 100), newest first, filter by status. |
| `GET /api/copies/{id}` | One of my copies. |
| `PUT /api/copies/{id}` | Change grade. |
| `POST /api/copies/{id}/list` / `/unlist` | Status transitions. Listing requires `profileComplete` -> 409 `PROFILE_INCOMPLETE`. |
| `DELETE /api/copies/{id}` | Remove (204). |
| `GET /api/condition-grades` | The five grades with criteria text from the spec. |

Rules: someone else's copy id -> 404 (does not reveal existence); `RESERVED` copies cannot be edited, unlisted or removed -> 409 `COPY_RESERVED`; copy status rules live in `Copy` (one place, unit-tested for every transition, as the spec asks of state logic).

## Data model (`V2__books_and_copies.sql`, `ddl-auto=validate`)
- `book`: `isbn13 varchar(13)` PK (CHECK 13 digits), title varchar(500), author varchar(500), cover_url varchar(1000) nullable (https only), created_at.
- `copy`: id uuid PK, owner_id FK -> users ON DELETE CASCADE, isbn13 FK -> book, grade, status (CHECK constraints for the allowed values), created_at, updated_at, `version bigint` (`@Version`). Indexes: `(owner_id, status, created_at desc)`, `(isbn13)`.
- Books are never updated or deleted by users; concurrent first-add of the same ISBN is handled with `INSERT ... ON CONFLICT DO NOTHING` then re-read.

## Catalog adapter
- `BookCatalog.lookup(Isbn) -> Optional<BookDetails>`; throws `CatalogUnavailableException` on timeout, 5xx or malformed payload.
- `OpenLibraryCatalog` uses Spring `RestClient`, base URL from `bookswap.catalog.base-url` (default `https://openlibrary.org`), connect 2s / read 3s timeouts, `GET /api/books?bibkeys=ISBN:<isbn13>&format=json&jscmdata=data`. First author name, `cover.medium` if present. Only digits reach the URL (normalized ISBN), so no injection; cover URL accepted only if https.
- No new dependency: tests use `MockRestServiceServer` for the adapter, and a `@Primary` stub `BookCatalog` in integration tests so CI never touches the network.

## Out of step 2
Proposal-related cancellations (US-08/09 "cancels proposals") and a real `RESERVED` transition arrive with step 4: `CopyService` is the single place that will gain the cancel call. Browse/search (step 3), account deletion (step 5), caching of catalog responses, a second catalog provider.

## Testing
- Unit: `Isbn` (valid/invalid ISBN-10 and -13, hyphens, bad check digit, normalization), `Copy` status/grade transitions incl. reserved and profile-incomplete, `CopyService` ownership, `BookService` resolution order, `OpenLibraryCatalog` parsing (found, not found, no cover, 500, malformed, timeout).
- Integration (Testcontainers, stub catalog): add by ISBN -> list -> edit grade -> list/unlist -> remove; two copies of one ISBN share one book row; another user gets 404 on my copy; unlisting/listing gate on profile; pagination and status filter; **no N+1 on the library list** (Hibernate statistics statement count independent of page size); concurrent first-add of same ISBN yields one book; stale `@Version` update -> 409 `CONCURRENT_UPDATE`; reserved copy (set via SQL) -> 409 `COPY_RESERVED`; Swagger exposes the new endpoints.
- Review Focus for the final review: ISBN edge inputs (whitespace, lowercase `x`, NUL, over-long), oversized/NUL manual title/author, cover URL scheme, catalog slow/garbage responses, page size abuse.

## Verification
`mvn -B verify` green; `docker compose up -d`, run the app, in Swagger UI: register, log in, complete profile, look up ISBN 9780140328721 (real Open Library once, manually), add a copy, list it, filter the library by status, edit grade, remove.
