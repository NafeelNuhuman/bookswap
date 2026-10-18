# BookSwap Step 4: Exchange Core Design (proposals, accept/reject/counter/cancel, shelf)

## Why
Users can now list books (step 2) and find other people's books near them (step 3). They cannot yet ask for one. This step turns "I want that" into an exchange proposal, and is the heart of the backend: a state machine, reservation of copies, and correct behaviour when two people act at the same moment. Requirements: `BookSwapRequirementsSpec.md` (Epic C US-14, Epic D US-15 to US-20).

## Decisions from the brainstorm (the owner's words)
- A user proposes an exchange by offering books from **their own library** for books of someone else.
- **Multi-book from the start**: up to 5 copies on each side (the requirements' default), not a one-to-one special case.
- The other person must be **told** about the proposal and be able to **see what is offered**.
- Chat is too complicated for now. Instead, the other person's **contact details are shown**, but **hidden until the recipient accepts**.
- Contact details are the account **email plus an optional free-text contact line** on the profile.
- Cancelling an accepted proposal (and freeing the reserved copies) is **left for step 5**.
- The per-user shelf (US-14), deferred from step 3, is built here.

## Goals
- Propose an exchange of 1 to 5 copies for 1 to 5 copies of one other user.
- Inbox of incoming and outgoing proposals, with a detail view of what each side offers.
- Accept (reserving every copy), reject, counter, and cancel, with all transition rules in one place.
- Exactly one of any set of competing proposals can be accepted, even under simultaneous requests.
- The shelf: everything one user currently has available.
- Contact details revealed to both parties once a proposal is accepted, and never before.

## Non-goals (this step)
- Chat or messaging; push or email notifications; "unread" tracking. The inbox is the notification: `role=incoming&status=PROPOSED` and its `totalElements` is the pending count.
- Cancelling after acceptance, confirming the handover, completing the exchange, ratings (step 5).
- The scheduled job that persists expiry (step 5). Expiry is already enforced when a proposal is read or acted on.
- Account deletion handling for proposals (step 5).
- The minimum-grade filter (US-13).
- Proposing copies that are not yet listed, or "wishlist" style requests with no specific copy.

## Anti-goals (a failure even if it technically works)
- A user's email or contact line visible before acceptance, to a non-participant, or in any feed, search, shelf or list card.
- A copy that ends up reserved by two accepted proposals, or an accepted proposal containing a copy that was not available.
- A counter that leaves both the old and the new proposal open.
- A request that returns 500 for any user-supplied input.

## Lifecycle

```
            +--> ACCEPTED   (recipient accepts; copies RESERVED)
            +--> REJECTED   (recipient rejects, optional reason)
PROPOSED ---+--> COUNTERED  (recipient answers with a new proposal)
            +--> CANCELLED  (proposer withdraws, or the system cancels it, see below)
            +--> EXPIRED    (past its expiry date)
```
All other states are final in this step. Every transition is a method on the `Proposal` entity (one place, unit-tested for every state against every action).

Rules:
- Only the recipient can accept, reject or counter. Only the proposer can cancel.
- Every action needs the status to be `PROPOSED` and not past `expiresAt`; otherwise 409 `PROPOSAL_NOT_OPEN` (a lapsed proposal is reported as `EXPIRED` everywhere, even before the step 5 job persists it).
- A proposal is never edited. A change is a counter.
- Not a participant, or unknown id: 404 `PROPOSAL_NOT_FOUND` (does not reveal existence).

## API (all endpoints need a bearer token)

### Propose: `POST /api/proposals` (201)
`{ "requestedCopyIds": [..], "offeredCopyIds": [..], "message": "optional" }`
- 1 to 5 ids per side, no duplicates, message at most 500 characters, no NUL; otherwise 400 `INVALID_PROPOSAL`.
- The recipient is not sent; it is the owner of the requested copies. All requested copies must have the same owner; otherwise 400 `INVALID_PROPOSAL`. If that owner is the caller: 400 `SELF_PROPOSAL`.
- Every requested copy must belong to the recipient and be `AVAILABLE`; every offered copy must belong to the caller and be `AVAILABLE`. Anything else (unknown, someone else's, not available) gives the same 409 `COPY_NOT_AVAILABLE`, so existence is not revealed.
- Expiry is `createdAt + 14 days` (property `bookswap.proposals.expiry`, default `P14D`). Each item stores the copy's grade at that moment.
- The same copy can be in many open proposals at once; nothing is reserved until acceptance.

### Counter: `POST /api/proposals/{id}/counter` (201)
Body as for propose (without any recipient). Only the recipient of an open proposal. The caller becomes the proposer and the original proposer the recipient; the new proposal links to the original (`parentId`) and the original becomes `COUNTERED`, in one transaction. Copies may differ freely on both sides, subject to the same checks as propose (offered = caller's, requested = the other party's).

### Accept: `POST /api/proposals/{id}/accept`
In one transaction: every copy on both sides must still be `AVAILABLE` and owned by the right party; otherwise 409 `COPY_NOT_AVAILABLE` and nothing changes. All copies become `RESERVED`, the proposal becomes `ACCEPTED`, and every other `PROPOSED` proposal containing any of those copies becomes `CANCELLED` (cause `COPY_UNAVAILABLE`). If two accepts race for the same copy, exactly one succeeds; the loser gets 409 (`COPY_NOT_AVAILABLE` or `CONCURRENT_UPDATE`) and changes nothing.

### Reject: `POST /api/proposals/{id}/reject` with optional `{ "reason": "..." }`
Reason at most 500 characters, stored and shown to the proposer. No copy changes status.

### Cancel: `POST /api/proposals/{id}/cancel`
Proposer only, while `PROPOSED`. Cause `WITHDRAWN`. Cancelling an `ACCEPTED` proposal is 409 `PROPOSAL_NOT_OPEN` until step 5.

### Inbox: `GET /api/proposals?role=incoming|outgoing&status=&page=&size=`
`role` is required (missing or invalid: 400). Optional `status` filter (a lapsed `PROPOSED` counts as `EXPIRED`). Newest first, same paging clamps as the other lists. Same card shape as the detail but without contact details. Statement count must not grow with page size.

### Detail: `GET /api/proposals/{id}`
```
{ id, status, cancelCause, parentId, message, rejectReason, createdAt, expiresAt,
  proposer: { id, displayName, city },     recipient: { id, displayName, city },
  offered:   [ item ],  requested: [ item ] }
item = { copyId, isbn, title, author, coverUrl, detailsSource, gradeAtProposal }
```
When `status = ACCEPTED`, the party who is **not** the caller additionally gets `email` and `contactNote`. Nobody else, ever.

### Shelf: `GET /api/users/{id}/copies?page=&size=`
That user's `AVAILABLE` copies, newest first, as the step 3 `CopyCard` (no city filter). Unknown user: 404 `USER_NOT_FOUND`. The step 3 `CopyCard.owner` object gains the owner's `id` (an opaque UUID) so a client can open the shelf and address a proposal.

### Profile: `contactNote`
`PUT /api/users/me` accepts an optional `contactNote` (at most 200 characters after trimming, no NUL, blank stored as none). It is returned by `GET/PUT /api/users/me`. It does not affect "profile complete".

## Effects on existing code
- `Copy` gains `reserve(now)` (`AVAILABLE` to `RESERVED`, else a conflict). Releasing waits for step 5. All status rules stay in `Copy`.
- `CopyService` (the one place for owner-side changes) now cancels open proposals containing a copy when its owner changes the grade, unlists it or removes it (cause `COPY_UNAVAILABLE`), using a method on the proposal service. The proposal service reads copies through `CopyRepository`, not `CopyService`, so there is no dependency cycle.
- `DiscoveryRow`/`CopyCard` carry the owner id; the discovery queries add `u.id`.
- `UpdateProfileRequest`, `ProfileResponse`, `User` gain `contactNote`.

## Data (`V5__proposals.sql`, `ddl-auto=validate`)
- `users.contact_note varchar(200)` nullable.
- `proposal`: `id uuid` PK; `proposer_id`, `recipient_id` FK to `users` (ON DELETE CASCADE, CHECK proposer <> recipient); `status` (CHECK of the six values); `cancel_cause` nullable (CHECK `WITHDRAWN`, `COPY_UNAVAILABLE`); `message varchar(500)`; `reject_reason varchar(500)`; `parent_id` nullable FK to `proposal`; `created_at`, `updated_at`, `expires_at`; `version bigint` (`@Version`).
- `proposal_item`: `proposal_id` FK (ON DELETE CASCADE), `copy_id` FK to `copy` (ON DELETE CASCADE), `side` (`OFFERED`/`REQUESTED`), `grade_at_proposal`; PK `(proposal_id, copy_id)`.
- Indexes: `proposal (recipient_id, status, created_at desc)`, `proposal (proposer_id, status, created_at desc)`, `proposal_item (copy_id)`, partial index for open proposals by expiry for the step 5 job.

## Concurrency
- `Proposal` and `Copy` both carry `@Version`. Every writer takes row locks on copies first, in ascending id order, and only then touches proposals: accept locks the copies `FOR UPDATE` before changing anything and re-checks owner, status and grade (so two accepts over overlapping copies, or accept versus unlist, cannot deadlock); propose reads the copies `FOR SHARE`, so a concurrent regrade, unlist or reservation waits and then sees (and cancels) the new proposal. A stale version aborts the loser, mapped to 409. (Added after the whole-branch review, which showed the first design could deadlock and let a racing propose leave an open proposal at a stale grade.)
- Required tests with real threads against Testcontainers PostgreSQL: two competing proposals (overlapping copies) accepted at once leave exactly one `ACCEPTED`, the other `CANCELLED`/untouched, each copy `RESERVED` once; the same proposal accepted twice at once; accept racing with the owner unlisting or changing the grade of an included copy; accept racing with the proposer's cancel; accept racing with a counter. In every case the final database state must be consistent (no accepted proposal with a non-reserved copy, no copy reserved by two accepted proposals).

## Testing
- Unit: `Proposal` transitions (every state x every action x right and wrong actor, expiry boundary); `Copy.reserve`; creation validation (sizes, duplicates, mixed owners, self, message length); `ProposalService` with mocks for orchestration.
- Integration (Testcontainers, stub catalog): propose then see it in the recipient's inbox; privacy (JSON before acceptance contains no email, contact line or postal code; after acceptance only the other party's email and note, only to the two parties; a third user gets 404); accept reserves all copies and cancels competing proposals; reject; cancel; counter chain (original `COUNTERED`, new one linked, original proposer is the recipient); expiry (a proposal past its date cannot be accepted and reads as `EXPIRED`); owner unlisting or regrading or removing a copy cancels its open proposals; reserved copies disappear from feed, search and shelf; shelf visibility rules; paging clamps and inbox filters; statement count flat across page sizes; the concurrency tests above; 401 without a token; OpenAPI lists the new paths.

## Calls I made (for you to check)
- `COPY_NOT_AVAILABLE` covers unknown, not-owned and not-available copies alike, so a client cannot probe for other people's copy ids.
- The recipient is derived from the requested copies, not sent.
- `cancel_cause` exists so a user can tell "withdrawn" from "a copy was taken or changed"; no other history is stored.
- A lapsed proposal is reported as `EXPIRED` immediately; step 5's job only persists it.
- Changing a copy's grade also cancels open proposals containing it (the item records the grade seen at proposal time).
- Removing a copy deletes its proposal items by cascade (after cancelling the open proposals), so a closed proposal's detail can show fewer items afterwards. Keeping a snapshot of the book on the item is a possible later polish.
- The shelf of your own id returns your own available copies; harmless, no special case.
- No unread tracking; the inbox count is the notification.

## Open and deferred
- Cancel after accept, confirm, complete, expiry job, account deletion (step 5); grade filter (US-13).
- Contact details are a snapshot of the profile at read time, not of acceptance time, and cannot be un-shared once revealed.
