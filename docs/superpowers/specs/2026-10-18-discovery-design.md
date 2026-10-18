# BookSwap Step 3: Discovery Design (feed and search)

## Why
Users have libraries (step 2) but cannot yet see anyone else's. Discovery lets a user find books other people have available near them, so that step 4 can turn "I want that copy" into an exchange proposal. Requirements: `BookSwapRequirementsSpec.md` (Epic C: US-11 browse, US-12 search). This step is deliberately minimal: two read-only endpoints.

## Decisions from the brainstorm (the owner's words)
- Search and browse are **separate**: browse is a **feed of copies**; search is for finding a specific book.
- A search result shows the copy with the book's details including grade, the owner's details, and a **general area** (city).
- **Keep it simple and minimal.**
- The per-user **shelf** (US-14) is **left for step 4**, when exchange proposals need it.

## Goals
- `GET /api/feed`: other people's available copies in one city, newest first.
- `GET /api/search?q=`: the same, narrowed by title/author text or an exact ISBN.
- One shared result card, shared visibility and location rules, paginated, no N+1 queries.

## Non-goals (this step)
- The shelf / "everything one user has" view (US-14) and any owner-id filter. Deferred to step 4.
- The minimum-grade filter (US-13, a "Should" in the requirements).
- Postal-code prefix matching, distance, relevance ranking, grouping several copies of one book into one result, saved searches, sorting options.

## Anti-goals (would make this a failure even if it technically works)
- Exposing another user's email or postal code anywhere in a response.
- Showing the caller's own copies, or copies that are `UNLISTED` or `RESERVED`.
- A request that returns 500 for any user-supplied query text or paging value.

## API (both endpoints: bearer token required)

### Common parameters and rules
| Parameter | Behaviour |
| --- | --- |
| `city` (optional) | Matches the owner's profile city, case-insensitive after trimming. If absent or blank, the caller's own profile city is used. If the caller has no profile city and sent none: 400 `LOCATION_REQUIRED`. |
| `page`, `size` | Same clamping as the library list: page >= 0 (and never so large that `page * size` overflows), size 1..100, default 20. |

Visibility (always): `status = AVAILABLE` and `owner != caller`.
Order: newest first (`created_at` descending, then `id` for a stable order).
Response: the existing `PageResponse` wrapper (`content`, `page`, `size`, `totalElements`, `totalPages`).

### The result card
```
{ "copyId", "isbn", "title", "author", "coverUrl", "grade", "detailsSource",
  "owner": { "displayName", "city" } }
```
- `title`/`author`/`coverUrl`/`detailsSource` follow the step 2 display rule: the shared catalog `book` row if one exists for the ISBN, otherwise the details the owner typed on the copy.
- The owner object contains exactly `displayName` and `city`. No id, email, or postal code.

### `GET /api/feed`
Only the common parameters.

### `GET /api/search?q=`
- `q` is required. Blank, longer than 100 characters, or containing NUL: 400 `INVALID_QUERY`.
- If `q` is a valid ISBN (ISBN-10 or ISBN-13, hyphens allowed, via the existing `Isbn.parse`) it is an **exact** ISBN match.
- Otherwise it is a **partial, case-insensitive** match on the displayed title or author. `%` and `_` in `q` are matched literally, not as wildcards.
- The same visibility, location and paging rules apply (US-12).

## Data
- No new tables. Reads `copy`, `book` (left join) and `users`.
- Migration `V4__discovery_indexes.sql`: a partial index on `copy (created_at DESC) WHERE status = 'AVAILABLE'` and an index on `users (lower(city))`.

## Structure
New feature package `discovery` with `web` / `service` / `repository` layers, like the other features. The repository returns a flat projection straight from one query (copy + optional book + owner), so listing a page issues a fixed number of statements regardless of page size. The page-size/page-number clamping used by `CopyService` is moved to one shared helper and reused. Owner city for the default location comes from the existing `UserService` profile lookup.

## Testing
- Feed: shows only other people's available copies in the city; excludes own, unlisted, reserved and other-city copies; newest first.
- Card: shape; privacy (the raw JSON contains no email or postal code); mixed catalog and manual-detail copies show the right title and `detailsSource`.
- Location: defaults to own city; `city` override is case-insensitive; `LOCATION_REQUIRED` when neither exists.
- Search: partial title, partial author, case-insensitive, ISBN-13 and ISBN-10 spellings, manual-detail titles are searchable, `%` and `_` are literal, blank/over-long/NUL `q` is a 400, other city is excluded.
- Paging: clamped, huge page number does not 500.
- Performance: statement count is the same for page sizes 5 and 10 with mixed copies.
- Auth: 401 without a token on both endpoints. OpenAPI lists both.

## Calls I made (for you to check)
- Endpoint names `/api/feed` and `/api/search`.
- City matching is exact (case-insensitive), not "contains", and no postal-code matching. This follows the requirements' v1 default.
- A caller with no profile city must pass `city`; there is no "all cities" mode.
- ISBN detection: if `q` parses as an ISBN it is treated only as an ISBN, never also as text.
- No owner id in the card, since there is no shelf yet. Step 4 will add what proposals need.
- Search text matches the displayed title/author, so typed titles on manual copies are searchable.
- A `city` parameter that contains a NUL character or is longer than 100 characters is 400 `INVALID_LOCATION` (added in the plan; PostgreSQL cannot take NUL in a text parameter, and the anti-goals forbid a 500).
- A small, deliberate edge: a title that happens to be a valid ISBN number would be searched as an ISBN.

## Open and deferred
- The shelf (US-14) and the grade filter (US-13): next steps' concerns.
- Open-question defaults from the requirements still apply (city/postal-prefix "near me" in v1; here only city).
