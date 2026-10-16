# Deferred items from the Step 1 (Foundation) review

Found by the whole-branch review of commits `81f4d0c..e3af5a3`. The two Important findings were fixed in `fdfa7db`. These minors were deliberately deferred.

## Minors

1. **Container-level rejections return 401 instead of 400.** `/error` is not permitted in `SecurityConfig`, so requests rejected by the firewall or Tomcat (`POST /api/auth//login`, `/api/auth/login;x=1`) return `401 UNAUTHORIZED` with `"path":"/error"`. Fix: permit the ERROR dispatch (`.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()`) and render the standard error body for `/error`. (`auth/config/SecurityConfig.java`)
2. **405 responses have no `Allow` header.** `handleExceptionInternal` in `GlobalExceptionHandler` drops the headers prepared by `ResponseEntityExceptionHandler`. Fix: pass `headers` through in `respond(...)`. (`common/error/GlobalExceptionHandler.java`)
3. **A profile can be saved but reported incomplete.** A value such as `" "` (em space) passes `@NotBlank` (trim) and the DB `btrim` check, but `User.isProfileComplete()` uses `isBlank()`. Result: `PUT` returns 200 with `profileComplete: false`. Fix: use one definition of blank everywhere (e.g. `strip()` in the DTO). Matters once step 2 gates listing on `profileComplete`. (`user/domain/User.java`, `user/web/UpdateProfileRequest.java`)
4. **Every refresh-token insert does an extra SELECT.** The entity assigns its UUID in the constructor and has no `@Version`, so Spring Data treats it as not-new and `save()` merges. Fix: implement `Persistable<UUID>` or use `EntityManager.persist`. (`auth/domain/RefreshToken.java`, `auth/service/RefreshTokenService.java`)
5. **Any constraint violation at registration is reported as `EMAIL_TAKEN`.** The catch in `AuthService.register` maps every `DataIntegrityViolationException`. Fix: only map the violation of `users_email_lower_uq`, rethrow others. (`auth/service/AuthService.java`)
6. **The Docker runtime image runs as root.** Fix: add a non-root `USER` in the runtime stage. (`Dockerfile`)

## Follow-ups from the fix pass

- **Upgrade Spring Boot** (3.3.5 ships Spring Security 6.3.4, affected by CVE-2025-22228, silent BCrypt truncation). The `@MaxUtf8Bytes(72)` check prevents it for our inputs. After upgrading, confirm over-long passwords stay a 400 (patched `encode` throws `IllegalArgumentException`). Suggested at the start of step 2.

## Not verified yet

- The `app` Docker image has not been built (`docker compose --profile app up --build`).
- The GitHub Actions workflow has not run.

## Set aside by the reviewer as by design / out of step 1

Concurrent legitimate refreshes log the user out everywhere; access tokens stay valid up to 15 minutes after a revoke; `EMAIL_TAKEN` reveals registered emails; committed dev `JWT_SECRET` default; no rate limiting or request-size limits; no cleanup of used/expired refresh tokens; account deletion (US-04, step 5); Swagger UI is public; Postgres published on 0.0.0.0:5432 in compose.
