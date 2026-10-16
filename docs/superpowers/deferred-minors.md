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

## Needs a decision before step 3

- **I-2. Any user can permanently set the shared `book` row.** When the catalog is down (or has no entry), the first user's manual title/author becomes the record every user sees and every later add reuses; no code path repairs it (books are never updated). Also stores the placeholder author "Unknown author" permanently. Options: (a) add a `source` column (`CATALOG`/`MANUAL`) and retry the catalog on later lookups/adds for `MANUAL` books, upgrading the row if it answers; (b) return 503 when the catalog is unavailable and manual details were sent, so manual data is only accepted after a definite "not found". (b) conflicts with US-05's "if the lookup fails ... I can enter title and author manually".

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
