# Completion and Housekeeping (Step 5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an accepted exchange finish: both parties confirm, ownership transfers in the same transaction, accepted exchanges can be cancelled or settle by themselves after 14 days, lapsed proposals expire by a scheduled job, completed exchanges form a history, and an account can be deleted (anonymized) safely.

**Architecture:** Same `proposal` feature package. `Proposal` owns every new transition (`confirm`, `cancel` for both states, `setPickupNote`, `settle`); `Copy` gains `release` and `transferTo`. `ProposalService` orchestrates and is the only writer of proposals; `CopyService` gains `removeAll(ownerId)` for account deletion; a new `AccountService` (user package) orchestrates deletion. Lock order everywhere: copies `FOR UPDATE` in ascending id order first, proposal after. `proposal_item` keeps its own snapshot of the book so history outlives copies.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA (JPQL), `@Scheduled`, Flyway, PostgreSQL 16, JUnit 5, Mockito, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-10-18-completion-design.md` (builds on `2026-10-18-exchange-core-design.md`)

## Global Constraints

- Statuses become `PROPOSED, ACCEPTED, REJECTED, COUNTERED, CANCELLED, EXPIRED, COMPLETED`; cancel causes `WITHDRAWN, COPY_UNAVAILABLE, NO_SHOW`.
- Confirm window: property `bookswap.proposals.confirm-window`, default `14d`; job interval `bookswap.jobs.interval`, default `1h`.
- New error codes (via `ApiException`): 409 `ALREADY_CONFIRMED`, 409 `ACCOUNT_HAS_ACTIVE_EXCHANGE`; reused: 404 `PROPOSAL_NOT_FOUND`, 403 `PROPOSAL_ACTION_NOT_ALLOWED`, 409 `PROPOSAL_NOT_OPEN`, 401 `INVALID_CREDENTIALS`, 400 `INVALID_PROPOSAL` (pickup note), 409 `CONCURRENT_UPDATE`. Check order for actions: participant (404) -> role/actor (403) -> state (409).
- An `ACCEPTED` proposal past `confirm_by` is not open: confirm, cancel and pickup-note all give 409 `PROPOSAL_NOT_OPEN` until the job settles it.
- Pickup note: at most 500 characters after trimming, no NUL, blank clears; visible only to the two parties and only while status is `ACCEPTED` or `COMPLETED`.
- Email and `contactNote` stay visible only in the detail view of an `ACCEPTED` proposal, to the party who is not the caller. History and list views never carry them.
- A deleted user is shown as display name `Deleted user`, no city, no contact. Their email becomes `deleted-<id>@deleted.invalid` and can be registered again by someone else.
- Transferred copies become `UNLISTED` and are no longer reserved; released copies become `AVAILABLE`.
- No user input may produce a 500. Schema changes only via Flyway (`V6__completion.sql`), `ddl-auto=validate`.
- Commit messages end with `-m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>" -m "Claude-Session: https://claude.ai/code/session_01A9ZdQrYBVzmxQSkUW2wWSw"`. Whole suite: `mvn -B test`; one class: `mvn -B -q test -Dtest=ClassName`. Write Java with the Write/Edit tools, never with shell or Python heredocs (NUL-byte and backslash corruption seen before); scan `src/` for NUL bytes if in doubt. Do not touch the untracked `.claude/` folder.

## Review Focus

- Deadline boundary: confirm, cancel and pickup-note exactly at `confirm_by` and one tick before; the job settling exactly at the boundary (Tasks 2, 5).
- Both parties confirming at once, a confirm racing a cancel, a confirm racing the job: exactly one consistent end state, no 500, no deadlock (Task 8).
- A removed copy, a deleted user and a very long title/author in a closed proposal: history still renders, snapshot columns never overflow (Tasks 1, 6, 7).
- Deleting an account while one of the user's copies is being accepted, or while an accepted exchange exists: refused or consistent, never a half-deleted state (Tasks 7, 8).
- A deleted user's still-valid access token (15 minutes): profile read is 404; other calls must not 500 (Task 7).
- Pickup note with NUL, 501 characters, or from a stranger/after completion (Task 4).

---

### Task 1: Schema V6 and item snapshots

**Files:**
- Create: `src/main/resources/db/migration/V6__completion.sql`
- Modify: `proposal/domain/ProposalItem.java`, `proposal/repository/ProposalItemRepository.java`, `proposal/repository/ProposalItemRow.java`, `proposal/repository/ProposalRepository.java` (`cancelOpenContaining`), `proposal/service/ProposalService.java` (item creation, accept), `proposal/service/ProposalViewAssembler.java` (item view)
- Modify tests: `SchemaIT.java`, `proposal/CopyChangeIT.java`, any test constructing `ProposalItem` or `ProposalItemRow`

**Interfaces:**
- Produces: `ProposalItem(UUID proposalId, Copy copy, Side side, int position)` (snapshots grade, isbn13, title, author, coverUrl, detailsSource from the copy); getters `getId()` (surrogate `UUID`), `getProposalId()`, `getCopyId()` (nullable), `getSide()`, `getGradeAtProposal()`, `getIsbn13()`, `getTitle()`, `getAuthor()`, `getCoverUrl()`, `getDetailsSource()`, `getPosition()`. `ProposalItemRepository extends JpaRepository<ProposalItem, UUID>` with `List<ProposalItem> findByProposalId(UUID)`, `findRows(Collection<UUID>)` reading only item columns, ordered by `proposalId, side, position`. `ProposalItemRow(UUID proposalId, Side side, UUID copyId, ConditionGrade gradeAtProposal, String isbn13, String title, String author, String coverUrl, Copy.DetailsSource detailsSource)`.
- Also in V6 (used by later tasks): proposal columns `accepted_at`, `confirm_by`, `proposer_confirmed_at`, `recipient_confirmed_at`, `completed_at` (all `timestamptz` null), `pickup_note varchar(500)`; status CHECK adds `COMPLETED`; cancel-cause CHECK adds `NO_SHOW`; `users.deleted_at timestamptz`; users profile CHECK rebuilt as `deleted_at IS NOT NULL OR (original condition)`; indexes `proposal (parent_id)`, partial `proposal (confirm_by) WHERE status = 'ACCEPTED'`, partial `proposal (completed_at DESC) WHERE status = 'COMPLETED'` plus recipient/proposer lookups for history as needed.

- [ ] **Step 1: Write the failing tests.** In `CopyChangeIT`: `removingACopyKeepsItsItemInAClosedProposal` (propose, owner removes an offered copy so the proposal is cancelled, then the detail of the cancelled proposal still lists both items with title/author/isbn, `copyId` of the removed one is `null`). In `SchemaIT`: V6 columns exist, `proposal_item.copy_id` is nullable, inserting status `COMPLETED` and cause `NO_SHOW` is accepted, an anonymized users row (`deleted_at` set, no city/postal code) satisfies the CHECK while a non-deleted row with only a name still fails it.
- [ ] **Step 2: Run them.** `mvn -B -q test -Dtest=CopyChangeIT,SchemaIT`. Expected: FAIL (items vanish / columns missing).
- [ ] **Step 3: Write `V6__completion.sql`.** Items: add `id uuid`, snapshot columns (`isbn13`, `title`, `author`, `cover_url`, `details_source` in `CATALOG|MANUAL`, `position integer`) backfilled from `copy`/`book` (position = `row_number()` per proposal and side in the old order `copy.created_at, copy.id`), then NOT NULL on all but `cover_url`; replace the composite primary key with `PRIMARY KEY (id)`, keep `UNIQUE (proposal_id, copy_id)`, make `copy_id` nullable and its FK `ON DELETE SET NULL`. Everything else listed under Interfaces.
- [ ] **Step 4: Rework the entity and queries.** `ProposalItem` becomes a plain assigned-id entity (keep the `Persistable` insert trick), no embeddable key. `ProposalService.createProposal` builds items with `new ProposalItem(proposal.getId(), copy, side, index)`; `accept` keys its map on `getCopyId()` and treats a null copy id as unavailable. `cancelOpenContaining` subquery uses `i.proposalId`/`i.copyId`. `findRows` reads the snapshot (no join to copy/book); the assembler maps `detailsSource` straight through.
- [ ] **Step 5: Run the whole suite.** `mvn -B test`. Expected: all green (320 existing plus the new ones).
- [ ] **Step 6: Commit** `feat: snapshot book details on proposal items and add the V6 schema`.

---

### Task 2: Domain rules (Proposal, Copy)

**Files:**
- Modify: `proposal/domain/Proposal.java`, `ProposalStatus.java`, `CancelCause.java`; `copy/domain/Copy.java`; `proposal/service/ProposalProperties.java`; `src/main/resources/application.yml`
- Create: `proposal/domain/AlreadyConfirmedException.java`
- Test: `proposal/domain/ProposalTest.java`, `copy/domain/CopyTest.java` (existing copy domain test class)

**Interfaces:**
- Produces on `Proposal`: `accept(UUID actor, Instant now, Duration confirmWindow)` (sets `acceptedAt`, `confirmBy = now + window`; existing callers updated); `ConfirmOutcome confirm(UUID actor, Instant now)` with nested `enum ConfirmOutcome { RECORDED, COMPLETED }`; `boolean cancel(UUID actor, Instant now)` (true when it cancelled an `ACCEPTED` proposal, so the caller releases copies); `void setPickupNote(UUID actor, String note, Instant now)`; `Settlement settle(Instant now)` with nested `enum Settlement { NONE, COMPLETED, CANCELLED }`; `boolean hasConfirmed(UUID user)`; getters `getAcceptedAt()`, `getConfirmBy()`, `getCompletedAt()`, `getPickupNote()`, `getProposerConfirmedAt()`, `getRecipientConfirmedAt()`. `ProposalProperties(Duration expiry, Duration confirmWindow)`.
- Produces on `Copy`: `release(Instant now)` (RESERVED to AVAILABLE, else `IllegalStateException`), `transferTo(UUID newOwner, Instant now)` (RESERVED to UNLISTED with the new owner, else `IllegalStateException`); `owner_id` column becomes updatable.
- `AlreadyConfirmedException`: 409 `ALREADY_CONFIRMED`.

- [ ] **Step 1: Write the failing unit tests** (state x action x actor, using the reflection `withStatus` helper already in `ProposalTest`): `confirm` first call records and stays `ACCEPTED`, second party's call returns `COMPLETED` and sets `completedAt`, same party twice throws `AlreadyConfirmedException`, stranger throws `ProposalActionNotAllowedException`, any non-`ACCEPTED` status or `now >= confirmBy` throws `ProposalNotOpenException` (assert at `confirmBy` and one second before); `cancel` on `PROPOSED` is proposer-only `WITHDRAWN` returning false (existing behaviour), on `ACCEPTED` works for either party when nobody confirmed (returns true, `WITHDRAWN`), after one confirmation only the non-confirmer may (the confirmer gets `ProposalActionNotAllowedException`), `COMPLETED`/`CANCELLED` give not-open; `setPickupNote` either party while `ACCEPTED` and before `confirmBy`, stranger forbidden, other states not-open; `settle`: `NONE` before `confirmBy` and for non-`ACCEPTED`, exactly one confirmation gives `COMPLETED`, none gives `CANCELLED` with cause `NO_SHOW`, calling twice is `NONE` the second time. `Copy`: `release` and `transferTo` from `RESERVED` and from every other status.
- [ ] **Step 2: Run them.** `mvn -B -q test -Dtest=ProposalTest,CopyTest`. Expected: compile errors / FAIL.
- [ ] **Step 3: Implement** the signatures above in `Proposal` and `Copy`. Every transition stays in the entity; `requireOpenFor` is reused where it fits. Add `COMPLETED` and `NO_SHOW`. `application.yml`: `bookswap.proposals.confirm-window: 14d`.
- [ ] **Step 4: Fix callers** (`ProposalService.accept` passes `properties.confirmWindow()`, `ProposalServiceTest` builds `ProposalProperties(Duration.ofDays(14), Duration.ofDays(14))`, `Proposal` entity maps the new columns from Task 1) and run `mvn -B test`. Expected: green.
- [ ] **Step 5: Commit** `feat: confirm, cancel-after-accept, pickup note and settle rules on Proposal`.

---

### Task 3: Confirm and complete

**Files:**
- Modify: `proposal/service/ProposalService.java`, `proposal/web/ProposalController.java`, `proposal/web/ProposalView.java`, `proposal/service/ProposalViewAssembler.java`, `copy/repository/CopyRepository.java` (nothing new if `lockForUpdate` suffices)
- Test: create `proposal/ConfirmIT.java`; modify `proposal/service/ProposalServiceTest.java`, `AcceptIT.java` if the view shape is asserted

**Interfaces:**
- Consumes: Task 1 items, Task 2 `Proposal.confirm`, `Copy.transferTo`.
- Produces: `ProposalService.confirm(UUID me, UUID id)` returning `ProposalView`; private helper `Proposal lockCopiesThenLoad(UUID me, UUID id)` (reads the item copy ids without loading the proposal, locks those copies `lockForUpdate` in id order when any, then `load(me, id)`, so the proposal is read fresh after the locks); private `completeExchange(Proposal, List<Copy> locked, Instant now)` (offered copies go to the recipient, requested copies to the proposer). `ProposalView` gains `acceptedAt`, `confirmBy`, `confirmedByMe`, `confirmedByOther` (both null when the viewer is null), `completedAt`, `pickupNote` (null unless viewer present and status `ACCEPTED`/`COMPLETED`). `POST /api/proposals/{id}/confirm` returns 200 with the detail view.

- [ ] **Step 1: Write failing ITs** in `ConfirmIT` (extends `ProposalTestBase`): `firstConfirmationKeepsTheExchangeAccepted` (view shows `confirmedByMe` true, other party's view `confirmedByOther` true, copies still `RESERVED`); `secondConfirmationCompletesAndTransfersEveryCopy` (2-for-2 swap: status `COMPLETED`, all four copies have swapped owners in the DB, status `UNLISTED`, gone from feed and search of everyone, present in the new owners' `GET /api/copies`); `confirmingTwiceIsAlreadyConfirmed` (409 `ALREADY_CONFIRMED`); `aStrangerGets404`; `confirmingAProposedOrClosedProposalIsNotOpen`; `confirmingPastTheDeadlineIsNotOpen` (update `confirm_by` via jdbc); `completionIsAtomic` (make one copy non-`RESERVED` via jdbc before the second confirm: response 5xx is not acceptable, expect the documented failure and assert no copy moved and the proposal is still `ACCEPTED`; the builder decides the status code and ledgers it as a ruling, preferring 409 `COPY_NOT_AVAILABLE`); contact details still only for the other party while `ACCEPTED`. Also mock-based `ProposalServiceTest` for the orchestration order (copies locked before the proposal is read).
- [ ] **Step 2: Run them.** `mvn -B -q test -Dtest=ConfirmIT`. Expected: FAIL (404 route / missing fields).
- [ ] **Step 3: Implement** `confirm`: `lockCopiesThenLoad`, `proposal.confirm(me, now)`, and on `COMPLETED` re-check each locked copy is `RESERVED` and owned by the expected party (else throw `CopyNotAvailableException`), `transferTo`, `saveAllAndFlush`. Extend the assembler and view; controller maps the new route.
- [ ] **Step 4: Run** `mvn -B test`. Expected: green.
- [ ] **Step 5: Commit** `feat: confirm an exchange and transfer ownership on the second confirmation`.

---

### Task 4: Cancel after acceptance and the pickup note

**Files:**
- Modify: `proposal/service/ProposalService.java`, `proposal/web/ProposalController.java`
- Create: `proposal/web/PickupNoteRequest.java` (`record PickupNoteRequest(String note)`)
- Test: modify `proposal/RejectCancelIT.java` (replace `cancellingAfterAcceptanceIsNotOpenUntilStep5`), create `proposal/PickupNoteIT.java`

**Interfaces:**
- Consumes: `lockCopiesThenLoad`, `Proposal.cancel` returning true for an accepted proposal, `Copy.release`.
- Produces: `cancel(UUID me, UUID id)` now goes through `lockCopiesThenLoad`; when `proposal.cancel` returns true it releases every copy (all must be `RESERVED`, else `IllegalStateException`). `ProposalService.setPickupNote(UUID me, UUID id, String note)` returns `ProposalView`; text validated like the message (500 chars, no NUL, blank clears, 400 `INVALID_PROPOSAL`). `PUT /api/proposals/{id}/pickup-note` with `{note}`.

- [ ] **Step 1: Write failing ITs.** `RejectCancelIT`: replace the old test with `eitherPartyCanCancelAnAcceptedExchangeNobodyConfirmed` (both directions, status `CANCELLED`, cause `WITHDRAWN`, all copies on both sides `AVAILABLE`, back in the feed); `afterOneConfirmationTheConfirmerCannotCancel` (403 `PROPOSAL_ACTION_NOT_ALLOWED`, nothing changes); `afterOneConfirmationTheOtherPartyCan` (releases all copies, exchange `CANCELLED`); `competingProposalsCancelledByTheAcceptStayCancelled`; proposed-state cancel unchanged. `PickupNoteIT`: either party sets and edits it, both parties see it in detail, a stranger gets 404, a third user never, `PROPOSED` and `COMPLETED` and `CANCELLED` give 409, 501 characters and NUL give 400, blank clears.
- [ ] **Step 2: Run.** `mvn -B -q test -Dtest=RejectCancelIT,PickupNoteIT`. Expected: FAIL.
- [ ] **Step 3: Implement** as in Interfaces; controller routes.
- [ ] **Step 4: Run** `mvn -B test`. Expected: green.
- [ ] **Step 5: Commit** `feat: cancel an accepted exchange and add a pickup note`.

---

### Task 5: Scheduled housekeeping

**Files:**
- Create: `proposal/service/ProposalHousekeeping.java` (a `@Component` holding the `@Scheduled` method), `common/config/SchedulingConfig.java` (`@Configuration @EnableScheduling`), `proposal/service/JobProperties.java` (`@ConfigurationProperties("bookswap.jobs")`, `Duration interval`)
- Modify: `proposal/repository/ProposalRepository.java`, `proposal/service/ProposalService.java`, `application.yml` (`bookswap.jobs.interval: 1h`)
- Test: create `proposal/HousekeepingIT.java`; unit test for `settle` orchestration in `ProposalServiceTest`

**Interfaces:**
- Produces: `ProposalRepository.expireLapsed(Instant now)` (`@Modifying` bulk update `PROPOSED` with `expires_at <= now` to `EXPIRED`, bumps `version`, sets `updated_at`, returns count) and `List<UUID> findOverdueAccepted(Instant now, Pageable limit)` (`ACCEPTED` and `confirm_by <= now`, oldest first); `ProposalService.settle(UUID id)` (own transaction; `lockCopiesThenLoad`-style without participant check; `proposal.settle(now)`; `COMPLETED` transfers copies, `CANCELLED` releases them; `NONE` does nothing); `ProposalHousekeeping.run()` doing expire, then loops `findOverdueAccepted` in batches of 100 calling `settle` per id and logging-and-skipping `ObjectOptimisticLockingFailureException`, `PessimisticLockingFailureException` and `IllegalStateException`. The `@Scheduled` method uses `fixedDelayString` and `initialDelayString` both from `bookswap.jobs.interval` so nothing runs at test start-up.

- [ ] **Step 1: Write failing ITs** in `HousekeepingIT` calling `housekeeping.run()` directly: `lapsedProposedBecomeExpired` (also still `PROPOSED` ones untouched), `runningTwiceChangesNothingMore`, `overdueAcceptedWithNoConfirmationIsCancelledNoShowAndCopiesReleased`, `overdueAcceptedWithOneConfirmationCompletesAndTransfers`, `notYetOverdueAcceptedIsUntouched`, `oneBrokenProposalDoesNotStopTheRest` (make one proposal's copy non-reserved via jdbc; the other still settles), `expiredProposalsCanNotBeAccepted` (existing behaviour, now persisted), and the context starts with scheduling enabled.
- [ ] **Step 2: Run.** `mvn -B -q test -Dtest=HousekeepingIT`. Expected: FAIL.
- [ ] **Step 3: Implement** per Interfaces.
- [ ] **Step 4: Run** `mvn -B test`. Expected: green.
- [ ] **Step 5: Commit** `feat: scheduled expiry and settlement of overdue proposals`.

---

### Task 6: Completed-exchange history

**Files:**
- Create: `proposal/web/ExchangeView.java`, `proposal/web/ExchangeController.java`
- Modify: `proposal/repository/ProposalRepository.java`, `proposal/service/ProposalService.java` (or a small `ExchangeHistoryService` in the same package if `ProposalService` grows past comfort), `proposal/service/ProposalViewAssembler.java`
- Test: create `proposal/ExchangeHistoryIT.java`

**Interfaces:**
- Produces: `record ExchangeView(UUID proposalId, Instant completedAt, ProposalView.Party with, List<ProposalView.Item> gave, List<ProposalView.Item> received)`; `PageResponse<ExchangeView> history(UUID me, int page, int size)`; `GET /api/exchanges?page=&size=` (bearer). `ProposalRepository.findCompleted(UUID me, Pageable)` ordered `completedAt desc, id asc`. The `with` party never carries email or contact note.

- [ ] **Step 1: Write failing ITs.** `completedExchangesAreListedNewestFirstForBothParties`; `gaveAndReceivedAreFromTheCallersPointOfView`; `onlyCompletedExchangesAppear` (proposed, accepted, cancelled, rejected excluded); `strangersSeeNothing`; `noContactDetailsInHistory`; `historyKeepsItemsAfterANewOwnerRemovesTheBook`; `historyStatementCountDoesNotGrowWithPageSize` (Hibernate statistics, same pattern as `InboxIT`); paging clamps; 401 without token.
- [ ] **Step 2: Run.** `mvn -B -q test -Dtest=ExchangeHistoryIT`. Expected: FAIL.
- [ ] **Step 3: Implement** reusing the assembler's batched item and user loading (viewer `null`, no contact).
- [ ] **Step 4: Run** `mvn -B test`. Expected: green.
- [ ] **Step 5: Commit** `feat: completed exchange history`.

---

### Task 7: Account deletion

**Files:**
- Create: `user/service/AccountService.java`, `user/service/ActiveExchangeException.java` (409 `ACCOUNT_HAS_ACTIVE_EXCHANGE`), `user/web/DeleteAccountRequest.java` (`record DeleteAccountRequest(@NotBlank String password)`)
- Modify: `user/domain/User.java` (`deletedAt`, `anonymize(Instant now)`), `user/service/UserService.java` (`load` treats a deleted user as not found), `user/web/UserController.java` (`DELETE /api/users/me`), `copy/service/CopyService.java` (`removeAll(UUID ownerId)`), `copy/repository/CopyRepository.java` (`lockAllOwnedBy(UUID ownerId)`, `PESSIMISTIC_WRITE`, ordered by id), `proposal/service/ProposalService.java` (`requireNoAcceptedExchange(UUID userId)`), `proposal/repository/ProposalRepository.java` (`existsActiveFor(userId)`), `auth/service/RefreshTokenService.java` + `RefreshTokenRepository` (`deleteAll(UUID userId)` bulk delete), `AuthService` login refuses a deleted user
- Test: create `user/AccountDeletionIT.java`, `user/domain/UserTest.java` additions

**Interfaces:**
- Consumes: `CopyService`/`ProposalService.cancelOpenForCopy`, `PasswordEncoder`.
- Produces: `AccountService.delete(UUID me, String password)`: verify password (wrong gives `InvalidCredentialsException`, 401) before taking any lock; then in one transaction `copyService.removeAll(me)` (locks all the user's copies, calls `proposals.requireNoAcceptedExchange(me)`, cancels open proposals containing those copies with cause `COPY_UNAVAILABLE`, deletes the copies), `refreshTokens.deleteAll(me)`, `user.anonymize(now)` (email `deleted-<id>@deleted.invalid`, unusable password hash, display name `Deleted user`, city, postal code, contact note cleared, `deleted_at` set). `ProposalViewAssembler.party` must not throw for a deleted user. `DELETE /api/users/me` returns 204.

- [ ] **Step 1: Write failing ITs.** `wrongPasswordIs401AndNothingChanges`; `refusedWhileAnAcceptedExchangeExists` (409, nothing deleted, for proposer and recipient); `afterCompletionDeletionIsAllowed` (the transferred copies leave with the new owner, the other party's history shows `Deleted user` with no city or email); `openProposalsInvolvingTheUserAreCancelled` (both as proposer and recipient, cause `COPY_UNAVAILABLE`, competing parties see them `CANCELLED`); `copiesAreRemovedAndPersonalDataErased` (jdbc checks the users row: email pattern, null city/postal code/contact note/display name `Deleted user`, hash unusable, no refresh tokens); `deletedUserCannotLogInOrRefresh`; `theEmailCanBeRegisteredAgain`; `aStillValidAccessTokenGetsNotFoundOnProfile`; `deletedUserNeverAppearsInFeedShelfSearch`; the other party's closed proposal detail still renders (assembler). Plus a `UserTest` for `anonymize`.
- [ ] **Step 2: Run.** `mvn -B -q test -Dtest=AccountDeletionIT,UserTest`. Expected: FAIL.
- [ ] **Step 3: Implement** per Interfaces; `unlist`/`list`-style rules unchanged.
- [ ] **Step 4: Run** `mvn -B test`. Expected: green.
- [ ] **Step 5: Commit** `feat: delete an account (anonymize, remove copies, keep other people's history)`.

---

### Task 8: Concurrency, API surface, wrap-up

**Files:**
- Modify: `proposal/ConcurrencyIT.java` (new scenarios, extend `assertConsistent`), `common/OpenApiIT.java`, `docs/superpowers/deferred-minors.md`
- Test: as listed

**Interfaces:** Consumes everything above; produces nothing new.

- [ ] **Step 1: Write failing/new scenarios** (real threads, `ROUNDS`, deadlock counter before/after): `bothPartiesConfirmingAtOnceCompleteOnce` (end state `COMPLETED` or one party confirmed with a 409 for the loser; never two owners per copy, never a `COMPLETED` exchange with an untransferred copy; if the loser can retry the retry completes); `confirmRacingTheOtherPartysCancel` (exactly one wins: either `COMPLETED`/one confirmation stands, or `CANCELLED` with all copies `AVAILABLE`); `confirmRacingTheJob` (overdue exchange settled once); `deletionRacingAnAccept` (either the accept wins and the deletion is refused, or the deletion wins and the accept is 409; user row and copies consistent). Extend `assertConsistent`: no `COMPLETED` proposal with a copy still `RESERVED`, no `RESERVED` copy without an `ACCEPTED` proposal (existing), no copy in two `ACCEPTED` proposals (existing), no copy owner outside the two parties of a `COMPLETED` proposal that still holds the copy. `OpenApiIT`: `/api/proposals/{id}/confirm`, `/pickup-note`, `/api/exchanges`, `DELETE /api/users/me` listed.
- [ ] **Step 2: Run.** `mvn -B -q test -Dtest=ConcurrencyIT,OpenApiIT`. Expected: new scenarios FAIL only if a rule is missing; fix any real inconsistency with a RED to GREEN test.
- [ ] **Step 3: Update `deferred-minors.md`:** close the step 4 ruling about removed copies and minor 2 (parent_id index) and 6 (assembler on a vanished user), note the open items (the lapsed-proposed rows still cancelled by `cancelOpenContaining`; a deleted user's access token stays valid for the rest of its 15 minutes for endpoints that do not load the user; account deletion by anonymization keeps the users row).
- [ ] **Step 4: Full verification.** `mvn -B verify`. Expected: all tests green. Smoke test with compose and `SERVER_PORT=8081 mvn -B -q spring-boot:run`: register two users, add and list copies, propose, accept, confirm twice, read `/api/exchanges`, delete one account.
- [ ] **Step 5: Commit** `test: completion concurrency scenarios and docs`.

---

## Self-review notes

- Spec coverage: confirm and transfer (T3), cancel after accept and pickup note (T4), expiry/settle job (T5), history (T6), deletion (T7), snapshots and schema (T1), rules in the entity (T2), races (T8).
- Calls made while planning (carry into the ledger as rulings if unchanged): `confirm_by` is stored at accept time (`accepted_at + window`) rather than computed, so the entity needs no properties and the job query is a plain column test; account deletion cancels open proposals through the copy removal (every proposal involving the user contains one of their copies) with cause `COPY_UNAVAILABLE`; one-sided settlement stamps `completed_at` with the settle time; `lockCopiesThenLoad` is used for cancel, confirm and settle so every writer takes copy locks before the proposal.
