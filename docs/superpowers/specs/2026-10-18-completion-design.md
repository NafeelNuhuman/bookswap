# BookSwap Step 5: Completion and Housekeeping Design (US-21 to US-25, US-04)

## Why
Step 4 lets two people agree on a swap, but an accepted exchange is a dead end: books stay reserved forever and nothing ever changes hands in the app. This step finishes the lifecycle: both sides confirm, ownership moves, stale things clear themselves, history is kept, and an account can be deleted safely. Requirements: US-21..25 and US-04, and the "cancel after acceptance" open question.

## Decisions from the brainstorm (the owner's words)
- Both parties must confirm the swap. If that does not happen, the exchange is cancelled after a few days.
- One-sided confirmation: do not cancel. After the window the exchange completes (the confirmer's word counts; silence is consent). *Accepted: "no that is fine".*
- Either party may cancel an accepted exchange while nobody has confirmed. After the first confirmation, **only the party who has not confirmed may cancel** (a dispute); the confirmer may not.
- Cancelling releases **all** copies on both sides, back to `AVAILABLE` (the state they had before accept). Proposals that the accept had cancelled stay cancelled.
- Completed exchanges are history. When a party later deletes their account, the other party still sees the exchange, with that person shown as "Deleted user".
- Deleting an account requires the current password.

## Goals
- Confirm, complete (ownership transfer in the same transaction), cancel-after-accept, pickup note, scheduled housekeeping, completed history, account deletion.
- All status rules stay on `Proposal` / `Copy`; the window length is one property.

## Non-goals
Ratings, notifications, email, admin or dispute arbitration, reviving proposals cancelled by an accept, undoing a completed exchange, listing transferred books automatically.

## Anti-goals (a failure even if it works)
- A copy owned by one person in the database but shown as another's, or a completed exchange with any copy not transferred.
- A confirmer who can cancel, or a cancel that leaves any copy reserved.
- Contact details or pickup note visible to a non-party, or any personal data of a deleted user surviving.
- A completed exchange that loses what changed hands.
- Any 500 for user input.

## Lifecycle changes
```
PROPOSED -> ACCEPTED -> COMPLETED   (second confirmation, or the one-sided deadline)
                    \-> CANCELLED   (cause WITHDRAWN by a permitted party, or NO_SHOW by the deadline)
```
New status `COMPLETED`; new cancel causes `NO_SHOW` (nobody confirmed in time). Existing cause `WITHDRAWN` is reused for a manual cancel after accept.

New proposal fields: `accepted_at`, `proposer_confirmed_at`, `recipient_confirmed_at`, `completed_at`, `pickup_note varchar(500)`. Deadline = `accepted_at + bookswap.proposals.confirm-window` (default `14d`).

Rules, all as methods on `Proposal` (unit-tested state x action x actor):
- `confirm(actor, now)`: participant only; status `ACCEPTED`; each party once (a second confirm by the same party is 409 `ALREADY_CONFIRMED`); past the deadline is not confirmable (409 `PROPOSAL_NOT_OPEN`). Returns whether it just completed.
- `cancelAccepted(actor, now)`: participant; status `ACCEPTED`; allowed if nobody confirmed, or the actor is the one who has not confirmed; otherwise 403 `PROPOSAL_ACTION_NOT_ALLOWED`.
- `setPickupNote(actor, note, now)`: participant, status `ACCEPTED`.
- `settle(now)` (system): `ACCEPTED` past deadline; if exactly one confirmed -> completed; if none -> cancelled `NO_SHOW`.
- Check order stays: participant (404) -> role (403) -> state (409).

## API (bearer)
| Endpoint | Behaviour |
| --- | --- |
| `POST /api/proposals/{id}/confirm` | First call records it (status stays ACCEPTED); second party's call completes and transfers. Returns detail view. |
| `POST /api/proposals/{id}/cancel` | Existing; now also handles `ACCEPTED` per the rules above (proposer-while-proposed rule unchanged). |
| `PUT /api/proposals/{id}/pickup-note` `{note}` | Either party, while ACCEPTED; ≤500 chars, no NUL, blank clears. |
| `GET /api/exchanges?page=&size=` | My `COMPLETED` exchanges, newest completion first: other party (name or "Deleted user"), items given and received, `completedAt`. |
| `DELETE /api/users/me` `{password}` | 204. Wrong password 401 `INVALID_CREDENTIALS`; accepted-uncompleted exchange 409 `ACCOUNT_HAS_ACTIVE_EXCHANGE`. |

Detail view gains `acceptedAt`, `confirmDeadline`, `confirmedByMe`, `confirmedByOther`, `completedAt`, `pickupNote` (pickup note only for parties, only when ACCEPTED or COMPLETED).

## Completion in one transaction
Lock the proposal's copies `FOR UPDATE` in id order first (same rule as accept, so no deadlocks), then the proposal. Re-check each copy is `RESERVED` and owned by the expected side. Move `ownerId` on every copy, status `UNLISTED`, bump version, set proposal `COMPLETED` and `completed_at`. Any failure rolls everything back. `Copy` gains `release(now)` (RESERVED -> AVAILABLE, for cancel) and `transferTo(newOwner, now)` (RESERVED -> UNLISTED).

## Scheduled job (US-21)
`@Scheduled` (hourly, `bookswap.jobs.*` properties; `@EnableScheduling`), single instance so no distributed lock: (1) `PROPOSED` past `expires_at` -> `EXPIRED`; (2) `ACCEPTED` past the deadline -> `settle`. Batch with a bounded page, each proposal in its own transaction, idempotent, optimistic-lock failures skipped and retried next run. Uses the partial expiry index already there; adds one for `ACCEPTED` deadlines.

## History survives (snapshots)
`proposal_item` gains `title`, `author`, `isbn13`, `cover_url`, `details_source` snapshot columns filled at proposal time, and `copy_id` becomes nullable with `ON DELETE SET NULL` (it stays part of identity through a surrogate key). Views read the snapshot, so a removed copy no longer drops items from closed proposals (this also closes the step 4 ruling). Completed exchanges therefore show what changed hands even after the new owner removes a book.

## Account deletion (US-04)
In one transaction: refuse if the user is party to an `ACCEPTED` proposal; cancel their open `PROPOSED` proposals (cause `WITHDRAWN`/`COPY_UNAVAILABLE` as fits); delete their copies (items keep snapshots); revoke and delete refresh tokens; **anonymize, do not delete, the user row**: `deleted_at` set, email -> `deleted-<id>@deleted.invalid`, unusable password hash, display name "Deleted user", city, postal code and contact note cleared (the profile CHECK is relaxed for deleted rows). Login and refresh refuse deleted users; their email becomes free to register again. The row stays so other people's history and closed proposals keep their link.

## Data (`V6__completion.sql`)
Proposal status/cause CHECKs extended; new proposal columns; `proposal_item` snapshot columns (backfilled from `copy`/`book` for existing rows) and nullable `copy_id` with surrogate key; `users.deleted_at` and relaxed CHECK; indexes for `ACCEPTED` deadline, `proposal.parent_id` (deferred minor), completed history by user.

## Testing
- Unit: every state x action x actor for confirm / cancelAccepted / setPickupNote / settle (deadline boundary); `Copy.release`, `transferTo`.
- Integration: two confirmations complete and move every copy (UNLISTED, new owners, appear in new owners' libraries, gone from feeds); first confirmation keeps ACCEPTED; double confirm 409; cancel before confirmation by either party releases all copies to AVAILABLE; after one confirmation the confirmer is refused and the other may cancel; pickup note privacy; job: expires, settles one-sided -> COMPLETED, none -> CANCELLED NO_SHOW, twice is a no-op, copies released; history lists, pages, no N+1; deletion: wrong password, blocked by accepted exchange, open proposals cancelled, copies gone, row anonymized, other party's history shows "Deleted user", login refused, email reusable; removed copy leaves closed proposal items intact.
- Concurrency (real threads): both parties confirm at once -> exactly one completion, ownership consistent; confirm racing cancel; confirm racing the job; deadlock counter stays flat.
- Review Focus: deadline boundary with clock edges, confirm after expiry, cancel/confirm races, snapshot columns on very long titles, NUL in notes, deleting while a proposal is being accepted, a user's last copy referenced by closed proposals.

## Calls I made (for you to check)
14-day confirm window = proposal expiry; hourly job; `NO_SHOW` cause name; anonymize rather than delete the user row; `GET /api/exchanges` as a separate endpoint instead of a status filter on the inbox; surrogate key on `proposal_item`; relisting transferred books stays manual.

## Verification
`mvn -B verify` green; with compose, Swagger: two users propose, accept, confirm both, check ownership and history; cancel path; delete an account.
