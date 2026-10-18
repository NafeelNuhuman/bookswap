# Discovery (Step 3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Two read-only endpoints, `GET /api/feed` and `GET /api/search?q=`, that list other people's available copies in a city as cards with the book, grade and the owner's name and city.

**Architecture:** New `discovery` feature package (`web` / `service` / `repository`). One JPQL constructor-expression query (copy, optional book, owner) per endpoint variant returns a flat projection, so a page costs a fixed number of statements. Paging clamps move into one shared helper used by both the library list and discovery.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA (JPQL), Flyway, PostgreSQL 16, JUnit 5, Mockito, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-10-18-discovery-design.md`

## Global Constraints

- Only another user's copies with `status = AVAILABLE`; never the caller's own; newest first (`created_at` desc, then `id`).
- The owner object in a card contains exactly `displayName` and `city`: no id, email or postal code, anywhere in any response.
- Paging: page >= 0 and `page * size` never overflows; size 1..100, default 20. Response wrapper: existing `PageResponse`.
- Location: `city` parameter, else the caller's profile city; case-insensitive after trimming; neither -> 400 `LOCATION_REQUIRED`.
- Title/author shown follow the step 2 display rule (catalog `book` row wins over typed copy details; `detailsSource` is `CATALOG` / `MANUAL`).
- Errors use the existing `ApiException` / `ErrorResponse`; no user input may produce a 500.
- Schema changes only via Flyway (`V4__discovery_indexes.sql`), `ddl-auto=validate`.
- Commit messages end with a second `-m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"`. Whole suite: `mvn -B test`; one class: `mvn -B -q test -Dtest=ClassName`.

## Review Focus

- `q` containing `%`, `_` or `\` must match literally, not as LIKE wildcards (Task 3).
- `q` blank, missing, over 100 characters or containing NUL -> 400, never 500 (Task 3).
- `city` containing NUL or longer than 100 characters -> 400 `INVALID_LOCATION`, never 500 (PostgreSQL rejects NUL in text parameters) (Task 2). This is a plan decision: the spec says "never 500" but only names `LOCATION_REQUIRED`.
- `page=2147483647` on both endpoints -> 200 with empty content (Tasks 1, 2, 3).
- Privacy: the raw JSON of both endpoints never contains an email address or postal code (Tasks 2, 3).
- A manual-details copy later covered by a catalog book row shows the catalog title and is searchable by it (Task 3).

---

### Task 1: Shared paging helper

**Files:**
- Create: `src/main/java/com/bookswap/common/web/Paging.java`
- Modify: `src/main/java/com/bookswap/copy/service/CopyService.java` (use the helper; delete its `MAX_PAGE_SIZE`)
- Test: `src/test/java/com/bookswap/common/web/PagingTest.java`

**Interfaces:**
- Produces: `Paging.pageable(int page, int size, Sort sort) -> Pageable`, and `Paging.MAX_PAGE_SIZE = 100`.

- [ ] **Step 1: Write `PagingTest`** with these tests (assertions on `pageable.getPageNumber()`, `getPageSize()`, `getSort()`):
  - `negativePageBecomesZero`: page -5 -> 0.
  - `sizeIsClampedToOneThroughOneHundred`: 0 -> 1, -3 -> 1, 100000 -> 100, 37 -> 37.
  - `hugePageNumberCannotOverflowTheOffset`: page `Integer.MAX_VALUE`, size 100 -> `(long) pageNumber * pageSize <= Integer.MAX_VALUE`; also for size 1 and 20.
  - `sortIsKept`: the given `Sort` comes back unchanged.
- [ ] **Step 2: Run** `mvn -B -q test -Dtest=PagingTest`. Expected: compile FAIL, `Paging` missing.
- [ ] **Step 3: Implement** `Paging.pageable` in `common/web/Paging.java` by moving the clamping logic currently inline in `CopyService.list` (size via min/max with `MAX_PAGE_SIZE`; page via `min(max(page,0), Integer.MAX_VALUE / pageSize)`; `PageRequest.of(page, size, sort)`). Final utility class, private constructor. Update `CopyService.list` to call it with its existing sort.
- [ ] **Step 4: Run** `PagingTest,CopyServiceTest,CopyLibraryIT`. Expected: all PASS (behaviour of the library list is unchanged).
- [ ] **Step 5: Commit** `refactor: share paging clamps between the library list and discovery`.

---

### Task 2: Feed endpoint

**Files:**
- Create: `src/main/resources/db/migration/V4__discovery_indexes.sql`
- Create: `src/main/java/com/bookswap/discovery/repository/{DiscoveryRepository,DiscoveryRow}.java`
- Create: `src/main/java/com/bookswap/discovery/service/{DiscoveryService,InvalidLocationException,LocationRequiredException}.java`
- Create: `src/main/java/com/bookswap/discovery/web/{CopyCard,DiscoveryController}.java`
- Modify: `src/test/java/com/bookswap/AbstractIntegrationTest.java` (add `completeProfile(JsonNode tokens, String displayName, String city)`; the existing one-argument overload calls it with `"Pat"`, `"Berlin"`)
- Test: `src/test/java/com/bookswap/discovery/FeedIT.java`, `src/test/java/com/bookswap/discovery/service/DiscoveryServiceTest.java`

**Interfaces:**
- Consumes: `Paging.pageable` (Task 1); `PageResponse.from(Page)`; `UserService.getProfile(UUID) -> ProfileResponse` (has `city()`); `Copy` (fields `ownerId`, `isbn13`, `book`, `manualTitle`, `manualAuthor`, `grade`, `status`, `createdAt`); `User` (`displayName`, `city`).
- Produces:
  - `DiscoveryRow(UUID copyId, String isbn13, String title, String author, String coverUrl, ConditionGrade grade, boolean fromCatalog, String ownerName, String ownerCity)`.
  - `DiscoveryRepository`: `Page<DiscoveryRow> feed(UUID me, String cityLower, Pageable p)`, `Page<DiscoveryRow> searchByIsbn(UUID me, String cityLower, String isbn13, Pageable p)`, `Page<DiscoveryRow> searchByText(UUID me, String cityLower, String likePattern, Pageable p)`. Only `feed` is built in this task; the search methods are added in Task 3.
  - `CopyCard(UUID copyId, String isbn, String title, String author, String coverUrl, ConditionGrade grade, Copy.DetailsSource detailsSource, Owner owner)` with nested `record Owner(String displayName, String city)`.
  - `DiscoveryService.feed(UUID me, String city, int page, int size) -> PageResponse<CopyCard>`; package-private `String resolveCity(UUID me, String cityParam)` (trim; blank/null -> caller's profile city; still blank -> `LocationRequiredException` 400 `LOCATION_REQUIRED`; longer than 100 or containing NUL -> `InvalidLocationException` 400 `INVALID_LOCATION`; returns the lower-cased trimmed city).

- [ ] **Step 1: Write `DiscoveryServiceTest`** (Mockito, mocks `DiscoveryRepository` and `UserService`):
  - `explicitCityWinsAndIsTrimmedAndLowerCased`: `"  BeRLin "` -> `"berlin"`; the profile is never read.
  - `blankOrMissingCityFallsBackToTheProfileCity`.
  - `noCityAnywhereIsLocationRequired`: profile city null and param null/blank -> `LocationRequiredException`.
  - `nulOrOverLongCityIsInvalidLocation`: `"Ber\u0000lin"` and 101 characters.
  - `feedMapsRowsToCardsWithDetailsSourceAndNoOwnerId`: a `DiscoveryRow` with `fromCatalog=false` becomes a card with `detailsSource == MANUAL`, and `owner` carries only name and city.
- [ ] **Step 2: Write `FeedIT`** (extends `AbstractIntegrationTest`; helper `addListed(owner, n, title)` registers a catalog stub entry for `Isbns.valid(n)`, adds a copy via `POST /api/copies` and lists it via `POST /api/copies/{id}/list`). Users: alice, bob (Berlin), carol (Munich), each with `completeProfile(tokens, name, city)`. Tests:
  - `feedShowsOnlyOtherPeoplesAvailableCopiesInTheCallersCity`: bob lists two copies; bob also has one unlisted copy and one set to `RESERVED` via SQL; carol lists one (Munich); alice lists one of her own. Alice's feed has `totalElements == 2`, the two bob copies, newest first.
  - `cardShowsBookGradeAndOwnerAreaButNothingPrivate`: asserts `copyId`, `isbn`, `title`, `author`, `coverUrl`, `grade`, `detailsSource`, `owner.displayName`, `owner.city`; and the raw response body contains none of `"@example.com"`, `"postalCode"`, `"email"`, `"10115"`.
  - `manualAndCatalogCopiesShowTheirOwnTitlesAndSource`: one catalog copy (`CATALOG`), one manual copy (catalog down at add time, typed title; `MANUAL`).
  - `cityDefaultsToTheCallersAndOverrideIsCaseAndWhitespaceInsensitive`: alice sees Berlin by default; `?city=%20mUNICH%20` shows carol's copy.
  - `locationIsRequiredWhenTheCallerHasNoCityAndSendsNone`: a freshly registered user with no profile -> 400 `LOCATION_REQUIRED`; `?city=Berlin` -> 200; `?city=` (blank) -> 400 `LOCATION_REQUIRED`.
  - `nulOrOverLongCityIsA400NotA500`: `?city=Ber%00lin` and a 101-character city -> 400 `INVALID_LOCATION`.
  - `pagingIsClamped`: `size=100000` -> echoed `size` 100; `page=2147483647` -> 200 with empty content.
  - `feedRequiresAuthentication`: no token -> 401.
- [ ] **Step 3: Run** `mvn -B -q test -Dtest='DiscoveryServiceTest,FeedIT'`. Expected: compile FAIL (classes missing).
- [ ] **Step 4: Migration `V4__discovery_indexes.sql`:** `CREATE INDEX copy_available_created_idx ON copy (created_at DESC) WHERE status = 'AVAILABLE';` and `CREATE INDEX users_city_lower_idx ON users (lower(city));`.
- [ ] **Step 5: Implement the repository.** `DiscoveryRepository` is an interface extending `Repository<Copy, UUID>` with one `@Query` JPQL method `feed` returning `Page<DiscoveryRow>` via a constructor expression (`select new ...DiscoveryRow(...)`), from `Copy c left join c.book b join User u on u.id = c.ownerId`, where `c.status = com.bookswap.copy.domain.CopyStatus.AVAILABLE and c.ownerId <> :me and lower(u.city) = :cityLower`. Title/author are `coalesce(b.title, c.manualTitle)` / `coalesce(b.author, c.manualAuthor)`, cover is `b.coverUrl`, and `fromCatalog` is true when `b.isbn13` is not null. Provide an explicit `countQuery` (same joins and predicates, `select count(c)`). The sort comes from the `Pageable`.
- [ ] **Step 6: Implement the service and controller.** `DiscoveryService` (`@Transactional(readOnly = true)`) resolves the city, calls `Paging.pageable(page, size, Sort.by(desc "createdAt", asc "id"))`, maps rows to `CopyCard` (`fromCatalog` -> `Copy.DetailsSource.CATALOG` / `MANUAL`), wraps in `PageResponse`. `DiscoveryController` (`@SecurityRequirement(name = "bearerAuth")`) maps `GET /api/feed` with `@RequestParam(required = false) String city`, `page` default 0, `size` default 20, and the principal's UUID as in `CopyController`.
- [ ] **Step 7: Run** `mvn -B -q test -Dtest='DiscoveryServiceTest,FeedIT'`, then `mvn -B test`. Expected: PASS; full suite green.
- [ ] **Step 8: Mutation check** (revert after): drop the `c.ownerId <> :me` predicate -> `feedShowsOnlyOtherPeoplesAvailableCopies...` must FAIL; then drop `lower(u.city) = :cityLower` -> the city tests must FAIL.
- [ ] **Step 9: Commit** `feat: discovery feed of other users' available copies` (include the migration).

---

### Task 3: Search endpoint, hardening and docs

**Files:**
- Modify: `src/main/java/com/bookswap/discovery/repository/DiscoveryRepository.java` (add `searchByIsbn`, `searchByText`)
- Modify: `src/main/java/com/bookswap/discovery/service/DiscoveryService.java`, `.../web/DiscoveryController.java`
- Create: `src/main/java/com/bookswap/discovery/service/InvalidQueryException.java`
- Test: `src/test/java/com/bookswap/discovery/SearchIT.java`, `src/test/java/com/bookswap/discovery/DiscoveryHardeningIT.java`; extend `DiscoveryServiceTest`
- Modify: `README.md`, `docs/superpowers/specs/2026-10-18-discovery-design.md` (only if the build changed a decision)

**Interfaces:**
- Consumes: Task 2's repository, `resolveCity`, `CopyCard`; `Isbn.parse(String) -> Isbn` (throws `InvalidIsbnException`; `.value()` is the ISBN-13).
- Produces: `DiscoveryService.search(UUID me, String q, String city, int page, int size) -> PageResponse<CopyCard>`; `InvalidQueryException` (400 `INVALID_QUERY`).

- [ ] **Step 1: Extend `DiscoveryServiceTest`:**
  - `isbnLookingQueryUsesTheExactIsbnSearchWithTheNormalizedIsbn13`: `"0306406152"` -> `searchByIsbn(..., "9780306406157", ...)`, text search never called.
  - `otherQueriesUseACaseInsensitiveContainsPattern`: `"  Fox "` -> `searchByText(..., "%fox%", ...)`.
  - `likeWildcardsAndBackslashAreEscaped`: `"100%_\\"` -> pattern `"%100\\%\\_\\\\%"` (each of `%`, `_`, `\` prefixed with a backslash, lower-cased with `Locale.ROOT`).
  - `blankOverLongOrNulQueryIsInvalidQuery`: `null`, `"   "`, 101 characters, `"a\u0000b"`.
- [ ] **Step 2: Write `SearchIT`** (same setup helpers as `FeedIT`; copy the helper into a shared test support class `DiscoverySupport` under `src/test/java/com/bookswap/discovery/` if both ITs need it, rather than duplicating):
  - `partialTitleAndAuthorMatchIsCaseInsensitive`: `q=fox` and `q=DAHL` find the right copy; a non-matching `q` returns `totalElements == 0`.
  - `isbn13AndIsbn10SpellingsFindTheSameCopyExactly`: `q=978-0-14-032872-1`, `q=9780140328721` and the ISBN-10 spelling all find it; a partial ISBN such as `q=97801` finds nothing.
  - `typedTitlesOnManualCopiesAreSearchableUntilTheCatalogTakesOver`: a manual copy is found by its typed title; after a catalog-backed copy of the same ISBN creates the shared book, searching the typed title no longer finds it and searching the catalog title does.
  - `likeWildcardsAreLiteral`: copies titled `100% Sure` and `Fooxbar` and `Foo_bar`: `q=%` finds only `100% Sure`; `q=foo_bar` finds only `Foo_bar`; `q=\` finds nothing and returns 200.
  - `invalidQueriesAre400`: missing `q`, blank, 101 characters, NUL -> 400 (`INVALID_QUERY` for the last three).
  - `searchAppliesTheSameVisibilityAndLocationRulesAsTheFeed`: own, unlisted, reserved and other-city copies never appear; `?city=` override works; `LOCATION_REQUIRED` for a cityless caller.
  - `searchCardsNeverContainPrivateFields`: raw body has no email or postal code.
  - `pagingAndHugePageNumbers`: `size=100000` echoes 100; `page=2147483647` -> 200 empty.
  - `searchRequiresAuthentication`: 401.
- [ ] **Step 3: Run** `mvn -B -q test -Dtest='DiscoveryServiceTest,SearchIT'`. Expected: FAIL (search missing).
- [ ] **Step 4: Implement search.** In the repository add `searchByIsbn` (same query as `feed` plus `and c.isbn13 = :isbn13`) and `searchByText` (plus `and (lower(coalesce(b.title, c.manualTitle)) like :pattern escape '\\' or lower(coalesce(b.author, c.manualAuthor)) like :pattern escape '\\')`), each with its count query. In `DiscoveryService.search`: strip `q`; reject null/blank/over 100/NUL with `InvalidQueryException`; try `Isbn.parse(q)` -> `searchByIsbn`, on `InvalidIsbnException` build the pattern `"%" + escape(q.toLowerCase(Locale.ROOT)) + "%"` where `escape` prefixes `\`, `%` and `_` with a backslash (backslash first) -> `searchByText`. Add `GET /api/search` (`@RequestParam String q`, optional `city`, `page`, `size`) to the controller.
- [ ] **Step 5: Run** the same tests. Expected: PASS.
- [ ] **Step 6: Write `DiscoveryHardeningIT`:**
  - `statementCountDoesNotGrowWithPageSizeOnFeedAndSearch`: 12 copies, half manual and half catalog; for feed and for `search?q=...` (matching all 12), `getPrepareStatementCount()` for size 5 equals size 10 and is <= 2 (page + count). Use the Hibernate `Statistics` pattern from `CopyHardeningIT`.
  - `openApiListsFeedAndSearch`: `/v3/api-docs` has `paths['/api/feed']` and `paths['/api/search']`.
- [ ] **Step 7: Run** `mvn -B test -Dtest=DiscoveryHardeningIT`. Expected: PASS. **Mutation checks** (revert after): remove the `escape` call -> `likeWildcardsAreLiteral` must FAIL; replace `join User u on ...` plus projection by loading entities lazily (or remove the `left join c.book b` fetch from the query) so rows trigger extra statements -> the statement-count test must FAIL.
- [ ] **Step 8: Docs.** README: add a "Discovery" section with the two endpoints, the `city` rule and the card shape. If any decision in the spec changed during the build, amend the spec's "Calls I made".
- [ ] **Step 9: Full verification:** `mvn -B verify`. Expected: BUILD SUCCESS, 0 failures; report the count.
- [ ] **Step 10: Manual smoke test** (not on port 8080; an older app instance may still be running there): run `docker compose up -d db` and `SERVER_PORT=8081 mvn -B -q spring-boot:run`; register two users in the same city, complete both profiles, add and list a copy for one (a real ISBN such as 9780140328721), and as the other call `GET /api/feed` and `GET /api/search?q=fox`; confirm the card, that the first user sees nothing from themselves, and that no email or postal code appears. Stop the app afterwards.
- [ ] **Step 11: Commit** `feat: discovery search and hardening tests` (separate docs commit if the spec changed).

---

## Self-review notes

- **Spec coverage:** feed, search, shared rules, card and privacy (Tasks 2, 3); paging (Tasks 1-3); migration/indexes (Task 2); testing list (all three tasks); N+1 and OpenAPI (Task 3). Non-goals (shelf, grade filter, postal prefix) have no task, as intended.
- **Gap found and closed:** the spec does not say what a NUL or over-long `city` parameter does. The plan adds 400 `INVALID_LOCATION` (Review Focus, Task 2) and says so, instead of leaving a 500 to the build.
- **Type consistency:** `DiscoveryRow`, `CopyCard`, `resolveCity` and `Paging.pageable` have the same signatures wherever they are used.
