# Exchange Core (Step 4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Multi-book exchange proposals (propose, inbox, detail, accept, reject, counter, cancel) with reservation of copies, contact details revealed only after acceptance, and a per-user shelf.

**Architecture:** New `proposal` feature package (`domain` / `repository` / `service` / `web`). The `Proposal` entity owns every status transition; `Copy` owns `reserve`. `ProposalService` is the only orchestrator; `CopyService` calls it to cancel open proposals when a copy's grade, listing or existence changes. Concurrency is optimistic (`@Version` on `Proposal` and `Copy`) with copies reserved and flushed in ascending id order.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA (JPQL), Flyway, PostgreSQL 16, JUnit 5, Mockito, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-10-18-exchange-core-design.md`

## Global Constraints

- Proposal statuses: `PROPOSED, ACCEPTED, REJECTED, COUNTERED, CANCELLED, EXPIRED`; cancel causes `WITHDRAWN, COPY_UNAVAILABLE`; item sides `OFFERED, REQUESTED`.
- 1 to 5 copies per side, no duplicate ids, message and reject reason at most 500 characters and no NUL; expiry is `createdAt + 14 days` (property `bookswap.proposals.expiry`, default `14d`).
- Error codes (via `ApiException`): 400 `INVALID_PROPOSAL`, 400 `SELF_PROPOSAL`, 409 `COPY_NOT_AVAILABLE`, 404 `PROPOSAL_NOT_FOUND`, 409 `PROPOSAL_NOT_OPEN`, 404 `USER_NOT_FOUND` (existing), 409 `CONCURRENT_UPDATE` (existing). Plan decisions where the spec is silent: a participant acting with the wrong role gets 403 `PROPOSAL_ACTION_NOT_ALLOWED`; a bad `role` query value is 400 `INVALID_ROLE`. Check order for actions: participant (404) then role (403) then open (409).
- Email and `contactNote` appear only in the detail response of an `ACCEPTED` proposal, only for the party who is not the caller, and nowhere else (no list, feed, search, shelf, pending/closed proposal).
- A lapsed `PROPOSED` proposal is reported as `EXPIRED` everywhere and cannot be acted on.
- Paging: existing `Paging.pageable` clamps, default size 20, sort `createdAt desc, id asc`; response wrapper `PageResponse`.
- No user input may produce a 500. Schema changes only via Flyway (`V5__proposals.sql`), `ddl-auto=validate`.
- Commit messages end with `-m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>" -m "Claude-Session: https://claude.ai/code/session_01A9ZdQrYBVzmxQSkUW2wWSw"`. Whole suite: `mvn -B test`; one class: `mvn -B -q test -Dtest=ClassName`. Never use Python heredocs for Java source (backslashes/NUL); scan `src/` for NUL bytes if in doubt.

## Review Focus

- Propose with null/empty lists, a null element, duplicate ids, 6 ids on a side, a 501-character or NUL message -> 400 `INVALID_PROPOSAL`, never 500 (Task 3).
- Malformed UUID in a path, unknown `role`/`status` value, huge `page` -> 400/200, never 500 (Tasks 4, 8).
- A stranger reading or acting on a proposal -> 404; the proposer trying to accept, or the recipient trying to cancel -> 403, and neither changes anything (Tasks 3, 5).
- Accepting after the other side unlisted, regraded, removed or sold a copy elsewhere -> 409 `COPY_NOT_AVAILABLE`, nothing reserved, proposal still `PROPOSED` (Tasks 5, 9).
- A failed counter (invalid copies) must leave the original proposal `PROPOSED` (Task 6).
- Accept racing with cancel, unlist, counter, or a competing accept -> consistent final state, loser gets 409, never 500 (Task 9).

---

### Task 1: Schema and the profile contact note

**Files:**
- Create: `src/main/resources/db/migration/V5__proposals.sql`
- Modify: `src/main/java/com/bookswap/user/domain/User.java`, `user/web/UpdateProfileRequest.java`, `user/web/ProfileResponse.java`, `user/service/UserService.java`
- Modify tests: `user/service/UserServiceTest.java`, `discovery/service/DiscoveryServiceTest.java` (constructor calls), `user/ProfileIT.java`, `SchemaIT.java`

**Interfaces:**
- Produces: `User.getContactNote()`; `User.updateProfile(String displayName, String city, String postalCode, String contactNote, Instant now)`; `UpdateProfileRequest(displayName, city, postalCode, contactNote)` (fourth component optional); `ProfileResponse(id, email, displayName, city, postalCode, contactNote, profileComplete)`; `UserService.requireExists(UUID id)` (throws `UserNotFoundException`).
- Produces (database): `users.contact_note varchar(200)`; tables `proposal`, `proposal_item` per the spec's Data section (PK `(proposal_id, copy_id)`, side/status/cancel_cause CHECKs, `proposer_id <> recipient_id` CHECK, the four indexes listed there, `parent_id` FK to `proposal`).

- [ ] **Step 1: Write failing tests.** In `ProfileIT`: `contactNoteIsSavedTrimmedAndReturned` (PUT with `"  WhatsApp +49 170  "` -> GET `$.contactNote` is `"WhatsApp +49 170"`); `blankOrMissingContactNoteIsNull`; `contactNoteOver200CharsIs400` (201 chars -> `VALIDATION_FAILED`, 200 accepted); `contactNoteWithNulIs400`; `contactNoteDoesNotAffectProfileComplete`. In `UserServiceTest`: `requireExistsThrowsForUnknownId`. In `SchemaIT` add assertions that `proposal` and `proposal_item` exist and that inserting a proposal with `proposer_id = recipient_id` violates a constraint.
- [ ] **Step 2: Run** `mvn -B -q test -Dtest=ProfileIT,UserServiceTest,SchemaIT`. Expected: FAIL (compile or assertions).
- [ ] **Step 3: Write `V5__proposals.sql`** (style of V2). `copy_id` and `proposal_id` FKs `ON DELETE CASCADE`; user FKs `ON DELETE CASCADE`; the partial index is `proposal (expires_at) WHERE status = 'PROPOSED'`; the other three indexes are `(recipient_id, status, created_at DESC)`, `(proposer_id, status, created_at DESC)`, and `proposal_item (copy_id)`.
- [ ] **Step 4: Implement the `contactNote` plumbing.** `UpdateProfileRequest` strips it and maps blank to null, `@Size(max = 200)`, same NUL `@Pattern` as the other fields, not `@NotBlank`. Update the existing constructor calls in `UserServiceTest` and `DiscoveryServiceTest`.
- [ ] **Step 5: Run** `mvn -B test`. Expected: all PASS.
- [ ] **Step 6: Commit** `feat: proposal schema and profile contact note`.

---

### Task 2: `Copy.reserve` and the `Proposal` state machine

**Files:**
- Create (package `com.bookswap.proposal.domain`): `ProposalStatus`, `CancelCause`, `Side`, `Proposal`, `ProposalItem`, `ProposalNotFoundException`, `ProposalNotOpenException`, `ProposalActionNotAllowedException`, `InvalidProposalException`, `SelfProposalException`; `copy/domain/CopyNotAvailableException` (409 `COPY_NOT_AVAILABLE`)
- Modify: `copy/domain/Copy.java`
- Test: `copy/domain/CopyTest.java` (add), `proposal/domain/ProposalTest.java`

**Interfaces:**
- Produces: `Copy.reserve(Instant now)` (`AVAILABLE` -> `RESERVED`, otherwise throws `CopyNotAvailableException`).
- Produces `Proposal` (`@Entity`, table `proposal`, `@Version Long version`, assigned UUID id, same `Persistable` new-flag idiom as `Copy`): getters `getId, getProposerId, getRecipientId, getStatus, getCancelCause, getMessage, getRejectReason, getParentId, getCreatedAt, getUpdatedAt, getExpiresAt`; `static Proposal create(UUID proposerId, UUID recipientId, String message, UUID parentId, Instant now, Duration ttl)`; `boolean involves(UUID userId)`; `boolean isOpen(Instant now)` (status `PROPOSED` and `now` before `expiresAt`); `ProposalStatus effectiveStatus(Instant now)` (`EXPIRED` when `PROPOSED` and lapsed); `void accept(UUID actor, Instant now)`; `void reject(UUID actor, String reason, Instant now)`; `void cancel(UUID actor, Instant now)` (cause `WITHDRAWN`); `void markCountered(UUID actor, Instant now)`. `accept/reject/markCountered` require `actor == recipientId`, `cancel` requires `actor == proposerId` (else `ProposalActionNotAllowedException`, 403); all then require `isOpen(now)` (else `ProposalNotOpenException`, 409). Each updates `updatedAt`.
- Produces `ProposalItem` (`@Entity`, table `proposal_item`, composite key `proposalId` + `copyId`): constructor `ProposalItem(UUID proposalId, UUID copyId, Side side, ConditionGrade gradeAtProposal)` and matching getters. Mapping of the composite key is the implementer's choice; saving a new item must not trigger a SELECT.
- Produces exceptions with the codes and statuses in Global Constraints; `ProposalNotFoundException()` takes no arguments.

- [ ] **Step 1: Write failing tests.** `CopyTest`: `reserveMovesAvailableToReserved`; `reserveFailsFromUnlistedAndReserved` (`CopyNotAvailableException`). `ProposalTest` (pure unit, fixed `Instant`): `createStartsProposedWithExpiryFourteenDaysOut`; a parameterised matrix `everyActionFromEveryNonProposedStatusIsNotOpen` (for each of `ACCEPTED, REJECTED, COUNTERED, CANCELLED, EXPIRED` x each of the four actions as the right actor -> `ProposalNotOpenException`, state unchanged; use reflection-free setup by driving real transitions or a package-private test factory `Proposal.restore(...)` if you add one); `acceptSetsAccepted`; `rejectStoresReasonAndSetsRejected`; `cancelSetsCancelledWithdrawn`; `markCounteredSetsCountered`; `wrongRoleIsForbiddenAndUnchanged` (proposer accepts/rejects/counters; recipient cancels); `lapsedProposalIsNotOpenAtTheExpiryInstant` (exactly `expiresAt` is lapsed, one millisecond earlier is open); `effectiveStatusReportsExpiredWhenLapsed`; `involvesOnlyTheTwoParties`.
- [ ] **Step 2: Run** `mvn -B -q test -Dtest=CopyTest,ProposalTest`. Expected: compile FAIL.
- [ ] **Step 3: Implement** the types above. All transition logic lives in `Proposal`; keep the open/role checks in one private helper so each action is a few lines.
- [ ] **Step 4: Run** `mvn -B -q test -Dtest=CopyTest,ProposalTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat: proposal state machine and copy reservation`.

---

### Task 3: Propose and the detail view

**Files:**
- Move/rename: `src/test/java/com/bookswap/discovery/DiscoveryTestBase.java` -> `src/test/java/com/bookswap/support/MarketplaceTestBase.java` (public abstract, members `protected`); update `FeedIT`, `SearchIT`, `DiscoveryHardeningIT` to extend it.
- Create: `proposal/repository/ProposalRepository.java`, `ProposalItemRepository.java`, `ProposalItemRow.java`; `proposal/service/ProposalService.java`, `ProposalProperties.java` (`@ConfigurationProperties("bookswap.proposals")`, record `ProposalProperties(Duration expiry)`); `proposal/web/ProposalController.java`, `ProposalRequest.java`, `ProposalView.java`
- Modify: `src/main/resources/application.yml` (add `bookswap.proposals.expiry: 14d`), `copy/repository/CopyRepository.java`
- Test: `proposal/service/ProposalServiceTest.java`, `proposal/ProposalTestBase.java`, `proposal/ProposeIT.java`

**Interfaces:**
- Consumes: `Proposal.create`, `ProposalItem`, `Copy` getters (Task 2).
- Produces `ProposalRequest(List<UUID> requestedCopyIds, List<UUID> offeredCopyIds, String message)`; `ProposalView(UUID id, ProposalStatus status, CancelCause cancelCause, UUID parentId, String message, String rejectReason, Instant createdAt, Instant expiresAt, Party proposer, Party recipient, List<Item> offered, List<Item> requested)`; `ProposalView.Party(UUID id, String displayName, String city, String email, String contactNote)` annotated `@JsonInclude(NON_NULL)`; `ProposalView.Item(UUID copyId, String isbn, String title, String author, String coverUrl, Copy.DetailsSource detailsSource, ConditionGrade gradeAtProposal)`; `ProposalService.propose(UUID me, ProposalRequest request) -> ProposalView`; `ProposalService.get(UUID me, UUID id) -> ProposalView`; a package-private view assembler in the service package used by every later task: `ProposalView toView(Proposal p, UUID viewer, Instant now)` and a batch form `List<ProposalView> toViews(List<Proposal> ps, UUID viewer, Instant now)` that issues one query for items (JPQL constructor expression `ProposalItemRow(proposalId, side, copyId, gradeAtProposal, isbn13, title, author, coverUrl, fromCatalog)` over `ProposalItem` join `Copy` left join `c.book`, display rule `coalesce(b.title, c.manualTitle)` as in `DiscoveryRepository`) and one for the users (`UserRepository.findAllById`). Contact (`email`, `contactNote`) is set on the party that is not `viewer` only when `p.getStatus() == ACCEPTED`; `status` in the view is `effectiveStatus(now)`.
- Produces `CopyRepository.findAllByIdIn(Collection<UUID> ids)` (no entity graph needed).
- Produces endpoints `POST /api/proposals` (201, body `ProposalRequest`) and `GET /api/proposals/{id}`.
- Produces test helpers in `ProposalTestBase` (extends `MarketplaceTestBase`): `ResultActions propose(JsonNode caller, List<String> requested, List<String> offered, String message)`, `String proposeOk(JsonNode caller, List<String> requested, List<String> offered)` (asserts 201, returns id), `ResultActions action(JsonNode caller, String id, String verb)` (POST `/api/proposals/{id}/{verb}`), `ResultActions detail(JsonNode caller, String id)`.

- [ ] **Step 1: Move the test base** as described and run `mvn -B -q test -Dtest=FeedIT,SearchIT,DiscoveryHardeningIT` (still PASS), then commit-ready.
- [ ] **Step 2: Write failing tests.** `ProposalServiceTest` (Mockito, fixed clock): `proposeSavesProposalAndItemsWithGradesAtProposalTime`; `emptyOrNullListsAreInvalid`; `moreThanFiveOnEitherSideIsInvalid`; `duplicateIdsAreInvalid`; `nullElementIsInvalid`; `messageOver500OrWithNulIsInvalid`; `blankMessageIsStoredAsNull`; `unknownOrUnavailableOrForeignOfferedCopyIsCopyNotAvailable`; `requestedCopiesWithDifferentOwnersAreInvalid`; `requestingYourOwnCopiesIsSelfProposal`. `ProposeIT`: `proposeReturns201WithBothSidesAndTheGradesSeen` (bob lists 2 copies, alice lists 2 and offers both for one; assert `status` `PROPOSED`, `proposer.id`/`recipient.id`, 1 requested, 2 offered, `gradeAtProposal`, `expiresAt` about 14 days out, `parentId` absent or null); `sameCopyCanBeInSeveralOpenProposals`; `proposalDoesNotReserveCopies` (DB status still `AVAILABLE`); `unlistedAndReservedCopiesCannotBeProposed` (409 `COPY_NOT_AVAILABLE`); `cannotOfferSomeoneElsesCopy`; `detailVisibleToBothPartiesNotToCarol` (carol 404 `PROPOSAL_NOT_FOUND`); `noEmailOrContactNoteBeforeAcceptance` (raw JSON for both parties lacks `@example.com`, `email`, `contactNote`, `postalCode`, `10115`); `acceptedDetailRevealsOnlyTheOtherPartysContact` (set status to `ACCEPTED` via `jdbc.update`; bob's view contains alice's email and contact note but not his own; alice's view the reverse; carol still 404); `lapsedProposalReadsAsExpired` (set `expires_at` to the past via `jdbc`); `malformedProposalIdIs400`; `proposeRequiresAuthentication` (401).
- [ ] **Step 3: Run** `mvn -B -q test -Dtest=ProposalServiceTest,ProposeIT`. Expected: FAIL.
- [ ] **Step 4: Implement `ProposalService.propose`** through a private method `ProposalView createProposal(UUID proposerId, UUID fixedRecipientId /* null on first proposal */, UUID parentId, ProposalRequest request)` that Task 6 reuses. Validation order: shape (`InvalidProposalException`) -> load all copies with `findAllByIdIn` -> any missing or not `AVAILABLE` or offered not owned by the proposer -> `CopyNotAvailableException` -> requested owners not all equal -> `InvalidProposalException` -> owner equals proposer -> `SelfProposalException`; with `fixedRecipientId` set, a requested copy of anyone else is `CopyNotAvailableException`. Save the proposal and its items in one transaction. Expiry from `ProposalProperties` and the injected `Clock`. `ProposalController` follows `DiscoveryController`'s style (`@SecurityRequirement(name = "bearerAuth")`, `UUID.fromString(principal.getName())`).
- [ ] **Step 5: Run** `mvn -B test`. Expected: all PASS.
- [ ] **Step 6: Commit** `feat: propose an exchange and view a proposal`.

---

### Task 4: The inbox

**Files:**
- Create: `proposal/domain/ProposalRole.java` (enum `INCOMING, OUTGOING` with `static ProposalRole parse(String)` case-insensitive, otherwise 400 `INVALID_ROLE`)
- Modify: `proposal/repository/ProposalRepository.java`, `proposal/service/ProposalService.java`, `proposal/web/ProposalController.java`
- Test: `proposal/InboxIT.java`, add cases to `ProposalServiceTest`

**Interfaces:**
- Consumes: `toViews` (Task 3).
- Produces `ProposalService.list(UUID me, String role, ProposalStatus status, int page, int size) -> PageResponse<ProposalView>`; endpoint `GET /api/proposals?role=&status=&page=&size=` (`role` required).
- Produces repository methods `Page<Proposal> findIncoming(UUID me, Pageable p)`, `findIncomingByStatus(UUID me, ProposalStatus status, Instant now, Pageable p)`, `findOutgoing(...)`, `findOutgoingByStatus(...)` (JPQL; do not use an `:x is null` enum parameter, PostgreSQL cannot type it). Status filter semantics against `now`: `PROPOSED` means status `PROPOSED` and `expiresAt > now`; `EXPIRED` means status `EXPIRED` or (`PROPOSED` and `expiresAt <= now`); any other value is an exact match. Sort `createdAt desc, id asc`.

- [ ] **Step 1: Write failing tests** in `InboxIT`: `incomingAndOutgoingAreSeparate`; `newestFirst`; `statusFilterIncludingLapsedAsExpired` (a lapsed `PROPOSED` appears under `EXPIRED` and not under `PROPOSED`); `pagingIsClampedAndHugePageIsEmpty`; `roleIsRequiredAndValidated` (missing -> 400; `sideways` -> 400 `INVALID_ROLE`; `INCOMING` upper-case accepted); `unknownStatusValueIs400`; `listCardsNeverContainContactDetails` (even for an `ACCEPTED` proposal set via `jdbc`); `otherUsersProposalsNeverAppear`; `inboxRequiresAuthentication`. In `ProposalServiceTest`: `listClampsPaging`.
- [ ] **Step 2: Run** `mvn -B -q test -Dtest=InboxIT,ProposalServiceTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** Lists must use `toViews` with `viewer = null` semantics for contact: add a boolean or overload so list views never carry contact details even when accepted.
- [ ] **Step 4: Run** `mvn -B test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat: proposal inbox`.

---

### Task 5: Accept, reject, cancel

**Files:**
- Modify: `proposal/service/ProposalService.java`, `proposal/web/ProposalController.java`, `proposal/repository/ProposalRepository.java`, `common/error/GlobalExceptionHandler.java`
- Create: `proposal/web/RejectRequest.java` (`record RejectRequest(String reason)`)
- Test: `proposal/AcceptIT.java`, `proposal/RejectCancelIT.java`, add cases to `ProposalServiceTest` and `GlobalExceptionHandler` tests if they exist under `common/error`

**Interfaces:**
- Consumes: `Proposal.accept/reject/cancel`, `Copy.reserve`, `CopyRepository.findAllByIdIn`.
- Produces `ProposalService.accept(UUID me, UUID id) -> ProposalView`, `reject(UUID me, UUID id, String reason) -> ProposalView`, `cancel(UUID me, UUID id) -> ProposalView`; endpoints `POST /api/proposals/{id}/accept`, `/reject` (optional body, `@RequestBody(required = false)`), `/cancel`.
- Produces `ProposalRepository.cancelOpenContaining(Collection<UUID> copyIds, UUID exceptProposalId, Instant now) -> int`: a `@Modifying(flushAutomatically = true)` JPQL update setting `status = CANCELLED`, `cancelCause = COPY_UNAVAILABLE`, `updatedAt = :now`, `version = version + 1` for every `PROPOSED` proposal whose items include any of the copies (and whose id is not `exceptProposalId`; pass `null`-safe by using a random UUID when there is none).
- Produces `ProposalService.cancelOpenForCopy(UUID copyId, Instant now)` calling the same query (used by Task 7).
- Loading rule for all three actions: `proposals.findById(id)` filtered by `involves(me)`, else `ProposalNotFoundException`.

- [ ] **Step 1: Write failing tests.** `AcceptIT`: `acceptReservesEveryCopyOnBothSides` (2 offered + 1 requested: all three `RESERVED` in the DB, proposal `ACCEPTED`, response shows the other party's email and contact note); `acceptCancelsEveryCompetingOpenProposalContainingThoseCopies` (a second proposal sharing one copy becomes `CANCELLED` with `cancelCause` `COPY_UNAVAILABLE`; an unrelated proposal stays `PROPOSED`); `onlyTheRecipientCanAccept` (proposer -> 403 `PROPOSAL_ACTION_NOT_ALLOWED`, nothing reserved); `strangerGets404`; `acceptingTwiceIs409NotOpen`; `lapsedProposalCannotBeAccepted` (409 `PROPOSAL_NOT_OPEN`, copies stay `AVAILABLE`); `acceptFailsAtomicallyWhenOneCopyIsNoLongerAvailable` (one offered copy set `UNLISTED` through `jdbc`: 409 `COPY_NOT_AVAILABLE`, every other copy still `AVAILABLE`, proposal still `PROPOSED`); `acceptedCopiesDisappearFromFeedSearchAndOwnLists` (feed and search no longer show them). `RejectCancelIT`: `recipientRejectsWithReason` (reason shown to the proposer; copies unchanged); `rejectReasonOver500OrNulIs400`; `rejectWithoutBodyWorks`; `proposerCancels` (`CANCELLED`, cause `WITHDRAWN`, copies unchanged); `recipientCannotCancelAndProposerCannotReject` (403); `cancelAfterAcceptIs409NotOpen`; `actionsOnClosedProposalsAre409NotOpen`. `ProposalServiceTest`: `acceptReservesCopiesInAscendingIdOrder` (verify the order of `reserve` effects through the saved list) and `acceptWithForeignOwnedCopyIsCopyNotAvailable`.
- [ ] **Step 2: Run** `mvn -B -q test -Dtest=AcceptIT,RejectCancelIT,ProposalServiceTest`. Expected: FAIL.
- [ ] **Step 3: Implement `accept`** in one `@Transactional` method: load and check (`proposal.accept(me, now)` first), load items and copies, verify each copy is `AVAILABLE` and owned by `proposerId` (OFFERED) or `recipientId` (REQUESTED) else `CopyNotAvailableException`, sort by id, `reserve` each, then **flush the copies first** (`copies.flush()`), then the proposal, then `cancelOpenContaining`. This flush order is what avoids deadlocks between competing accepts. `reject` and `cancel` are short load + entity method + view.
- [ ] **Step 4: Map lock failures.** In `GlobalExceptionHandler` change the optimistic handler (or add a second one) so `org.springframework.dao.PessimisticLockingFailureException` also produces 409 `CONCURRENT_UPDATE`. Add a unit test in the existing handler test file if there is one; otherwise cover it in Task 9.
- [ ] **Step 5: Run** `mvn -B test`. Expected: PASS.
- [ ] **Step 6: Commit** `feat: accept, reject and cancel proposals`.

---

### Task 6: Counter

**Files:**
- Modify: `proposal/service/ProposalService.java`, `proposal/web/ProposalController.java`
- Test: `proposal/CounterIT.java`, add cases to `ProposalServiceTest`

**Interfaces:**
- Consumes: `createProposal(proposerId, fixedRecipientId, parentId, request)` (Task 3), `Proposal.markCountered`.
- Produces `ProposalService.counter(UUID me, UUID id, ProposalRequest request) -> ProposalView` (the new proposal) and endpoint `POST /api/proposals/{id}/counter` (201).

- [ ] **Step 1: Write failing tests.** `CounterIT`: `counterCreatesALinkedProposalWithRolesSwapped` (new proposal: proposer is the old recipient, recipient is the old proposer, `parentId` is the original, status `PROPOSED`; original is `COUNTERED`); `counterMayAddAndRemoveCopiesOnBothSides`; `counterCanBeAcceptedRejectedAndCounteredAgain` (chain of three); `onlyTheRecipientCanCounter` (proposer 403); `counterOnClosedOrLapsedProposalIs409`; `invalidCounterLeavesTheOriginalProposed` (offering a copy that is not available -> 409 and the original stays `PROPOSED`, no new row); `counterRequestedCopiesMustBelongToTheOriginalProposer` (a third user's copy -> 409 `COPY_NOT_AVAILABLE`); `counterCannotRequestOwnCopies` (-> 409 `COPY_NOT_AVAILABLE`). `ProposalServiceTest`: `counterMarksOriginalAndSavesNewProposalInOneTransaction` (use `verify` on repository calls).
- [ ] **Step 2: Run** `mvn -B -q test -Dtest=CounterIT,ProposalServiceTest`. Expected: FAIL.
- [ ] **Step 3: Implement** `counter` as `@Transactional`: load with `involves` check, `markCountered(me, now)`, then `createProposal(me, original.getProposerId(), original.getId(), request)`.
- [ ] **Step 4: Run** `mvn -B test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat: counter a proposal`.

---

### Task 7: Copy changes cancel open proposals

**Files:**
- Modify: `copy/service/CopyService.java` (new constructor parameter `ProposalService proposals`), `copy/service/CopyServiceTest.java`
- Test: `proposal/CopyChangeIT.java`

**Interfaces:**
- Consumes: `ProposalService.cancelOpenForCopy(UUID copyId, Instant now)` (Task 5).
- Produces: `CopyService` constructor `(CopyRepository, BookService, UserService, ProposalService, Clock)`. Behaviour: `changeGrade` cancels open proposals containing the copy only when the grade actually differs; `unlistCopy` and `remove` always do. The cancel runs in the same transaction as the change. Update the "Step 4 will add..." comment in the class Javadoc to say what the class does now.

- [ ] **Step 1: Write failing tests.** `CopyServiceTest` (update `setUp` for the new mock): `changingGradeCancelsOpenProposals`, `settingTheSameGradeDoesNotCancel`, `unlistCancelsOpenProposals`, `removeCancelsOpenProposalsBeforeDeleting` (verify order with `InOrder`), `listDoesNotCancel`. `CopyChangeIT`: `unlistingAnOfferedCopyCancelsItsOpenProposals` (cause `COPY_UNAVAILABLE`, other proposals untouched); `regradingARequestedCopyCancelsItsOpenProposals`; `removingACopyCancelsProposalsAndDropsItsItems` (proposal `CANCELLED`, detail still readable, the removed copy absent from the items, other items intact); `acceptedProposalsAreUntouchedByTheirReservedCopies` (a reserved copy cannot be changed: 409 `COPY_RESERVED`, proposal still `ACCEPTED`); `closedProposalsStayClosed`.
- [ ] **Step 2: Run** `mvn -B -q test -Dtest=CopyServiceTest,CopyChangeIT`. Expected: FAIL.
- [ ] **Step 3: Implement** the three hook calls in `CopyService`.
- [ ] **Step 4: Run** `mvn -B test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat: cancel open proposals when a copy is regraded, unlisted or removed`.

---

### Task 8: The shelf and the owner id on cards

**Files:**
- Modify: `discovery/repository/DiscoveryRow.java`, `discovery/repository/DiscoveryRepository.java`, `discovery/web/CopyCard.java`, `discovery/service/DiscoveryService.java`, `discovery/web/DiscoveryController.java`, `user/service/UserService.java` (already has `requireExists` from Task 1)
- Test: `discovery/ShelfIT.java`; update `FeedIT`, `SearchIT`, `DiscoveryServiceTest`

**Interfaces:**
- Produces: `DiscoveryRow(copyId, isbn13, title, author, coverUrl, grade, fromCatalog, ownerId, ownerName, ownerCity)`; `CopyCard.Owner(UUID id, String displayName, String city)`; `DiscoveryRepository.shelf(UUID ownerId, Pageable pageable) -> Page<DiscoveryRow>` (available copies of that owner, same projection and count query style as `feed`, no city or caller filter); `DiscoveryService.shelf(UUID userId, int page, int size) -> PageResponse<CopyCard>` (calls `userService.requireExists`); endpoint `GET /api/users/{id}/copies?page=&size=`.

- [ ] **Step 1: Write failing tests.** `ShelfIT`: `shelfShowsOnlyThatUsersAvailableCopiesNewestFirst` (unlisted and reserved hidden); `shelfWorksAcrossCities` (carol's shelf visible to alice); `shelfOfUnknownUserIs404UserNotFound`; `malformedUserIdIs400`; `ownShelfShowsOwnAvailableCopies`; `shelfCardsCarryOwnerIdButNoEmailOrPostalCode`; `shelfPagingIsClamped`; `shelfRequiresAuthentication`. Update `FeedIT.cardShowsBookGradeAndOwnerAreaButNothingPrivate`: owner has exactly three fields and `owner.id` equals bob's id (get it from `GET /api/users/me`); update `SearchIT` privacy test likewise; update `DiscoveryServiceTest` constructor calls and the `Owner` equality.
- [ ] **Step 2: Run** `mvn -B -q test -Dtest=ShelfIT,FeedIT,SearchIT,DiscoveryServiceTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** Add `u.id` to the three existing constructor expressions in the right position; the shelf JPQL mirrors `feed` minus the owner/city predicates, plus `c.ownerId = :ownerId`.
- [ ] **Step 4: Run** `mvn -B test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat: per-user shelf and owner id on discovery cards`.

---

### Task 9: Concurrency and hardening

**Files:**
- Create: `proposal/ConcurrencyIT.java`, `proposal/ProposalHardeningIT.java`
- Modify (only if a test exposes a defect): `ProposalService`, `GlobalExceptionHandler`

**Interfaces:**
- Consumes: everything above. Test helper in `ConcurrencyIT`: `List<Integer> race(Callable<ResultActions>... calls)` that starts all calls on separate threads released together by a `CountDownLatch`, and returns their HTTP statuses.

- [ ] **Step 1: Write `ConcurrencyIT`.** Each scenario repeats 5 rounds, creating fresh users' copies each round, and asserts the final database state, not just statuses:
  - `competingAcceptsOfOverlappingProposalsLeaveExactlyOneAccepted`: bob has copies X and Y; alice and carol each propose for X (carol's also for Y) at once; bob accepts both at once. Exactly one 200 and one 409; exactly one `ACCEPTED`, the other `CANCELLED` or still `PROPOSED`-then-failed, never `ACCEPTED`; X is `RESERVED`; no copy appears in two `ACCEPTED` proposals (SQL check over `proposal_item` join `proposal`).
  - `sameProposalAcceptedTwiceAtOnceSucceedsOnce`.
  - `acceptRacingProposerCancelIsConsistent` (final: either `ACCEPTED` with all copies `RESERVED`, or `CANCELLED` with all `AVAILABLE`; never a mix).
  - `acceptRacingOwnerUnlistIsConsistent` (final: `ACCEPTED` and `RESERVED`, or the copy `UNLISTED` and the proposal `CANCELLED`).
  - `acceptRacingCounterIsConsistent` (exactly one of `ACCEPTED` or `COUNTERED`).
  - In every scenario no response is 500, and losers are 409.
- [ ] **Step 2: Write `ProposalHardeningIT`.** `inboxStatementCountDoesNotGrowWithPageSize` (copy the approach of `DiscoveryHardeningIT`: 12 proposals with mixed catalog and manual copies, sizes 5 and 10 give equal prepared-statement counts, at most 5); `openApiListsProposalAndShelfPaths`; `allProposalEndpointsRequireAuthentication` (each verb 401); `malformedBodiesAre400` (invalid JSON, `{}`, wrong types); `hugeMessageIs400NotA500`.
- [ ] **Step 3: Run** `mvn -B -q test -Dtest=ConcurrencyIT,ProposalHardeningIT`. Expected: PASS. If a race yields 500 or an inconsistent state, fix the cause (flush order, explicit `flush()` inside the service so lock failures surface inside the handler's reach, exception mapping) rather than loosening the test.
- [ ] **Step 4: Run** `mvn -B verify`. Expected: BUILD SUCCESS, all tests green.
- [ ] **Step 5: Commit** `test: proposal concurrency and hardening`.
