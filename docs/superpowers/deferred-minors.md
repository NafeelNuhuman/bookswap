# Deferred items from the Step 1 (Foundation) review

Found by the whole-branch review of commits `81f4d0c..e3af5a3`. The two Important findings were fixed in `fdfa7db`. These minors were deliberately deferred.

## Minors

1. **Container-level rejections return 401 instead of 400.** `/error` is not permitted in `SecurityConfig`, so requests rejected by the firewall or Tomcat (`POST /api/auth//login`, `/api/auth/login;x=1`) return `401 UNAUTHORIZED` with `"path":"/error"`. Fix: permit the ERROR dispatch (`.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()`) and render the standard error body for `/error`. (`auth/config/SecurityConfig.java`)
2. **405 responses have no `Allow` header.** `handleExceptionInternal` in `GlobalExceptionHandler` drops the headers prepared by `ResponseEntityExceptionHandler`. Fix: pass `headers` through in `respond(...)`. (`common/error/GlobalExceptionHandler.java`)
3. ~~**A profile can be saved but reported incomplete.**~~ **Fixed in step 2** (`UpdateProfileRequest` now uses `strip()`, test in `ProfileIT`). Original note: A value such as `" "` (em space) passes `@NotBlank` (trim) and the DB `btrim` check, but `User.isProfileComplete()` uses `isBlank()`. Result: `PUT` returns 200 with `profileComplete: false`. Fix: use one definition of blank everywhere (e.g. `strip()` in the DTO). Matters once step 2 gates listing on `profileComplete`. (`user/domain/User.java`, `user/web/UpdateProfileRequest.java`)
4. **Every refresh-token insert does an extra SELECT.** (`Copy` in step 2 already uses the `Persistable` pattern; apply the same to `RefreshToken`.) The entity assigns its UUID in the constructor and has no `@Version`, so Spring Data treats it as not-new and `save()` merges. Fix: implement `Persistable<UUID>` or use `EntityManager.persist`. (`auth/domain/RefreshToken.java`, `auth/service/RefreshTokenService.java`)
5. **Any constraint violation at registration is reported as `EMAIL_TAKEN`.** The catch in `AuthService.register` maps every `DataIntegrityViolationException`. Fix: only map the violation of `users_email_lower_uq`, rethrow others. (`auth/service/AuthService.java`)
6. **The Docker runtime image runs as root.** Fix: add a non-root `USER` in the runtime stage. (`Dockerfile`)

## Follow-ups from the fix pass

- ~~**Upgrade Spring Boot**~~ **Done in step 2: Boot 3.5.16, springdoc 2.8.17.** Original note: (3.3.5 ships Spring Security 6.3.4, affected by CVE-2025-22228, silent BCrypt truncation). The `@MaxUtf8Bytes(72)` check prevents it for our inputs. After upgrading, confirm over-long passwords stay a 400 (patched `encode` throws `IllegalArgumentException`). Suggested at the start of step 2.

## Not verified yet

- The `app` Docker image has not been built (`docker compose --profile app up --build`).
- The GitHub Actions workflow has not run.

## Set aside by the reviewer as by design / out of step 1

Concurrent legitimate refreshes log the user out everywhere; access tokens stay valid up to 15 minutes after a revoke; `EMAIL_TAKEN` reveals registered emails; committed dev `JWT_SECRET` default; no rate limiting or request-size limits; no cleanup of used/expired refresh tokens; account deletion (US-04, step 5); Swagger UI is public; Postgres published on 0.0.0.0:5432 in compose.

# Deferred items from the Step 2 (Library) review

Whole-branch review of `f784054..62902b6`. The one Important finding that was a plain bug (a huge `page` value gave a 500) was fixed with a test. Open items:

## Resolved after the review

- **I-2. Any user could permanently set the shared `book` row.** Fixed by making manual title/author per-copy: migration `V3__copy_manual_details.sql` drops the `copy -> book` foreign key and adds optional `title`/`author` to `copy`; manual details never enter the shared `book` table; a catalog-backed book row wins over typed details at display time (`Copy.getTitle()`), so the catalog takes over automatically. Tests: `CopyLibraryIT.manualDetailsStayOnTheCopyAndNeverEnterTheSharedBook`, `CopyHardeningIT` (mixed N+1, constraints).
- Known limitation that remains: a user can still type nonsense or offensive text for an ISBN the catalog never knows, visible on their own copies (with their display name once step 3 shows owners). The spec has no moderation role in v1.

## Future feature: manual book entry polish

Ideas discussed and parked: a small-model check of manually entered text before saving (behind an interface like `BookCatalog`; decide fail-open vs fail-closed; prompt-injection hardening; it catches inappropriate text, not wrong text), a report/hide mechanism, or hiding manual-details copies from browse until the catalog confirms.

## Minors

1. `POST /api/copies` with a blank/over-long ISBN returns `VALIDATION_FAILED`; the lookup endpoint returns `INVALID_ISBN` for the same input. Drop `@Size` on the request's `isbn` (`Isbn.parse` caps at 32) or document it.
2. A blank manual title/author is turned into null and ends as `BOOK_DETAILS_REQUIRED` without a per-field error.
3. NUL/length/https protection of catalog data lives only in `OpenLibraryCatalog`; `BookService` trusts any `BookCatalog`, and no IT inserts real adapter output. Normalize at the `BookService` boundary or add an IT.
4. Truncating catalog text at 500 chars can split a surrogate pair.
5. A catalog entry with a blank or non-text title returns 503 on lookup instead of 404.
6. The stale-version test bypasses HTTP; the exception-to-409 mapping for copies is not tested end to end.
7. The unfiltered library list ordering cannot use `copy_owner_status_created_idx` (`status` sits between the columns).
8. Listing an already-`AVAILABLE` copy with an incomplete profile returns 409 instead of a no-op (unreachable today).
9. The design spec still says `jscmdata=data`; the code correctly uses `jscmd=data`.

## Set aside by the reviewer as by design / out of step 2

No catalog caching or rate limiting (each lookup can hold a thread for several seconds); no response size cap on the trusted catalog host; copy insert after the owner is deleted mid-session (account deletion is step 5); catalog-down + no manual details is 400 `BOOK_DETAILS_REQUIRED` (per spec); manual details silently ignored when a book already exists; orphan `book` rows are kept by design; `RESERVED` is only set via SQL in tests until step 4.

# Deferred items from the Step 3 (Discovery) review

Whole-branch review of `0483854..b6ea022`. The one Important finding (Java and PostgreSQL case-fold some non-ASCII letters differently, so cities like "İstanbul" and some search text matched nothing) was fixed by folding case in SQL on both sides, with tests. Open items:

## Minors

1. `GET /api/search` without `q` returns 400 with code `BAD_REQUEST`, while a blank `q` returns `INVALID_QUERY`. Make `q` optional in the controller so the service maps null to `INVALID_QUERY`, and assert the code.
2. The backslash case in `SearchIT.likeWildcardsAreLiteral` cannot fail (no title contains a backslash). Add a title such as `Foo\bar` and assert `q=\` finds exactly it.
3. One `FeedIT` ordering assertion relies on two copies having different `created_at` values (random UUID tie-break). Very unlikely to collide; make it deterministic if it ever flakes.
4. The partial index `copy_available_created_idx` may do little for city-filtered queries; the planner will probably use `users_city_lower_idx` plus the V2 `copy_owner_status_created_idx`. Check with EXPLAIN on realistic data. Substring text search scans the city's available copies; a `pg_trgm` GIN index is the later fix.
5. Postgres treats backslash as the default LIKE escape, so the explicit `ESCAPE '\'` is redundant there and the ITs cannot detect a mis-rendered ESCAPE clause. Only matters if the database changes.

## Set aside by the reviewer as by design / out of scope

A title that is itself a valid ISBN is searched only as an ISBN (spec); a partial ISBN never matches as part of an ISBN; "newest first" means when the copy was added, not when it was listed (a product question worth revisiting); exact city match only, no aliases (München/Munich) and no Unicode normalization (NFC/NFD); `ownerCity` is shown as typed; `copyId` is exposed and the owner id is not (step 4 will need more); a deleted principal with an explicit `city` still gets results (pre-existing token-lifetime behaviour); no relevance ranking and no grade filter (US-13).

---

# Deferred items from the Step 4 (Exchange core) review

Whole-branch review of `367df92..4f33b9d` by a fresh most-capable-model reviewer. Two Important findings were fixed with tests: (1) accept took row locks in proposal-then-copy order, so competing accepts and accept-versus-unlist deadlocked (PostgreSQL broke them after about a second, answering 409; now copies are locked first, in id order, and a deadlock-counter assertion guards it); (2) a propose racing a regrade, unlist or accept could leave an open proposal at a stale grade or on a reserved copy (propose now reads copies `FOR SHARE`; accept also re-checks the grade seen at proposal time). Open items:

## Ruling (not fixed)

- Removing a copy deletes its `proposal_item` rows by cascade, so a closed proposal's history loses that book (the cancellation cause stays, the book is gone). This was a call listed in the spec that was reviewed and accepted. Fix when wanted: snapshot isbn/title/author/cover on `proposal_item` and drop the cascade, or soft-delete copies.

## Minors

1. `cancelOpenContaining` also cancels proposals that are stored `PROPOSED` but already lapsed, so one that read as `EXPIRED` can become `CANCELLED`/`COPY_UNAVAILABLE`. Add `and p.expiresAt > :now`.
2. `proposal.parent_id` FK has no index; cascaded proposal deletes (account deletion, step 5) scan for children. Add one.
3. `role` is case-insensitive but `status` is case-sensitive (`status=proposed` is a 400). Make them consistent.
4. `@NotFound(IGNORE)` on `Copy.book` makes Hibernate load the book eagerly, so the copy lookups in propose/accept issue one extra SELECT per distinct ISBN (bounded at 10).
5. `PUT /api/users/me` clears `contactNote` when the field is omitted (PUT semantics); document it for clients.
6. `ProposalViewAssembler.party` would throw (500) if a user row vanished between loading a proposal and its parties, which can only happen in a race with account deletion.
7. The inbox status filter is a parameter-OR query; with generic prepared plans the index may only help on the user column. Fine at this scale.

## Set aside by the reviewer as by design / out of scope

No rate limiting or request-size limit on propose (app-wide); malformed JSON gives `MALFORMED_REQUEST` rather than `INVALID_PROPOSAL` (existing handler); a counter requesting the counterer's own copies is `SELF_PROPOSAL` (author's ruling, agreed); "requested copies must share an owner" is a 400 rather than 409 (nothing probeable: those copies are public in the feed); message length counts UTF-16 units (strict side).


---

# Step 5 (Completion and housekeeping): status of earlier items and new open items

Closed by step 5: the step 4 ruling about removed copies (items now keep their own snapshot of the book, so a closed
proposal or a completed exchange keeps what changed hands); step 4 minor 2 (`proposal.parent_id` index, V6); step 4
minor 6 (a user row is never removed, only anonymized, so the assembler never meets a vanished user).

Open:

1. `cancelOpenContaining` still also cancels proposals stored `PROPOSED` but already lapsed (step 4 minor 1); the hourly
   job now persists `EXPIRED` quickly, so the window is short.
2. A deleted user's access token stays valid for the rest of its 15 minutes on every endpoint that does not load the
   user (the profile and shelf calls answer 404, but for example adding a copy would still work for that time).
3. Account deletion anonymizes instead of deleting the `users` row; this is deliberate (other people's history points at
   it) but means "personal data erased" holds for the profile fields, email and password hash, not for the row's id.
4. A confirm or cancel on a proposal id the caller is not a party to takes (and immediately releases) row locks on the
   proposal's copies before answering 404.
5. The housekeeping job is a plain `@Scheduled` method: with more than one application instance it would run on each
   (harmless, it is idempotent and per-proposal transactional, but wasteful); add a lock if the app is ever scaled out.
6. Releasing or transferring a copy that is not reserved throws `IllegalStateException` (an invariant violation, not
   user input); the job logs and skips such an exchange, an interactive call would answer 500.

## Step 5 whole-branch review (fresh fable reviewer): one Important fixed, minors deferred

Fixed: `AuthService.refresh` now refuses (and clears the tokens of) a deleted user, so a refresh racing the account deletion cannot leave a usable token behind (`AccountDeletionIT.aRefreshTokenThatOutlivedTheDeletionIsStillRefused`, RED then GREEN).

Minors:

7. The recipient cancelling a closed (completed, cancelled, rejected...) proposal gets 403 `PROPOSAL_ACTION_NOT_ALLOWED` while the proposer gets 409 `PROPOSAL_NOT_OPEN` (cancel on a non-accepted proposal checks the role first). Pre-existing order; untested for the recipient.
8. A confirmer cancelling at or after `confirm_by` gets 409 instead of 403 (state is checked before the confirmer rule).
9. A pickup-note edit racing a confirm can make the confirm fail its version check with 409 `CONCURRENT_UPDATE`; the end state is consistent and a retry works.
10. `ConcurrencyIT.housekeepingRacingALateConfirmSettlesOnce` sets the deadline in the past first, so the confirm is always 409; it checks no 500, no deadlock and a single settlement, not interleavings.
11. `HousekeepingIT.theJobIsScheduled` only proves some scheduled task exists.
12. The spec names the detail field `confirmDeadline`; the API calls it `confirmBy`.

## Step 6 whole-branch review (fresh fable reviewer): one Critical and two Important fixed, minors deferred

Fixed (each RED then GREEN, mobile suite 123/123):
- Critical: the app had no `/` route, so Expo Go opened on "Unmatched Route". Added `app/index.tsx` (redirects to `/login` or `/library` by session) and extracted `src/RootNavigator.tsx`; `__tests__/routing.test.tsx` renders the real guards and index with `expo-router/testing-library`.
- Important: sending a proposal or counter used `router.replace` from the root modal, stacking a second copy of the tabs; now `router.dismissTo` (`composeScreen.test.tsx` expects it).
- Important: `Screen` is wrapped in `KeyboardAvoidingView behavior="padding"` (edge-to-edge Android does not resize for the keyboard); `screen.test.tsx`. Still to be confirmed on the phone.

Ruling: the Incoming and Outgoing lists keep showing every status (newest first, with a status chip) rather than only `PROPOSED`. A proposer would otherwise never see that their proposal was declined or cancelled. The spec line about a countered original not appearing in Incoming/Outgoing is superseded: it appears with the "Countered" chip. Cost if wrong: the lists are longer than a pure inbox.

Minors:

13. Compose: the tapped book (`wantedCopyId`) is pre-selected but only shown if it is on a loaded shelf page (20 per page); with a long shelf or a stale card the user sees "1 of 5 chosen" with no row to untick.
14. Discover: the feed returns 400 `LOCATION_REQUIRED` for a user with no city; `errors.ts` has no friendly message and "Try again" cannot help (link to Profile would).
15. Active section: `isError` is only true when both queries fail, so one failing query shows "No accepted exchanges right now".
16. A logout during an in-flight token refresh can write tokens back after `clear()`; a request with no stored tokens calls `onSignedOut` again. Needs a session epoch.
17. `SessionProvider`: if `tokenStore.get()` rejects the status stays `loading` (blank screen).
18. `BookGrid`: an error loading the next page shows nothing when items are already loaded.
19. `keyboardType="numbers-and-punctuation"` on the ISBN field is iOS only.
20. The compose modal title is "Propose a swap" even for a counter-offer.
21. `package.json` uses `~`/`^` ranges, the spec says exact pins (the lockfile pins).
22. `useOpenProfileWhenIncomplete` opens Profile on every launch for an incomplete profile, not only right after sign-up.
23. Not judged (needs the phone): serif font rendering, Alert styling, camera preview inside a ScrollView, the bottom inset under the 3-button navigation bar, and the Tasks 9 and 10 tests were written before the code but their RED run was skipped.
