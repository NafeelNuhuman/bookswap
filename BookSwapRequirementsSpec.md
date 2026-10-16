# BookSwap Requirements Spec

Oct 16, 2026 · @Nafeel

## Overview

BookSwap lets people list the physical books they own and propose multi-book swaps with other users nearby. Exchanges are local pickups only; there is no shipping. "BookSwap" is a working title.

**Goals**

- Users keep a personal library of physical copies, each with a condition grade.
- Users find books held by others in their area and propose swaps of one or more books per side.
- Both parties confirm completion, and ownership moves in the app.

**Portfolio goal:** the Java backend is the showcase. That means a clean Spring Boot REST API, a rigorously modelled exchange state machine, correct concurrency handling, security, and an automated test suite. The client is secondary and kept thin (React Native).

## Scope

Version 1 covers accounts, a personal library, discovery of nearby books, and the full multi-book exchange flow through to completion.

**In scope (v1)**

- Registration, login, and a profile with city and postal code
- Library of book copies added by ISBN, each with a condition grade
- Browse and search other users' available copies, filtered by location
- Multi-book exchange proposals: propose, accept, reject, counter, cancel, expire
- Two-party completion confirmation and ownership transfer
- REST API with OpenAPI docs; thin React Native client after the backend is stable

**Out of scope (later)**

- Shipping and address handling (local pickup only)
- Ratings, user reputation, and disputing a copy's condition after an exchange
- Notifications (email or push) and in-app chat
- Wishlists and automatic match suggestions
- Value estimates or price bands as fairness hints
- Photos of copies

## Actors and glossary

Three actors interact with the system: visitors, registered users, and the system itself (scheduled jobs). The terms below are used consistently in every story.

| Term | Meaning |
| --- | --- |
| Visitor | Not logged in; can only register and log in |
| User | Registered person who owns copies and takes part in exchanges |
| System | Scheduled jobs, for example expiring old proposals |
| Book | A title identified by ISBN, with title, author and cover from an external catalog |
| Copy | One physical copy of a book, owned by one user; carries a condition grade and a status |
| Condition grade | One of five levels set by the copy's owner (see Business rules) |
| Proposal | A request to swap: copies offered by the proposer for copies requested from the recipient |
| Proposer / Recipient | The user who creates a proposal / the user it is addressed to |
| Counter-offer | A new proposal sent back by the recipient, linked to the original |
| Reserved | Status of a copy locked into an accepted proposal |

## User stories

There are 25 stories in five epics. Priority is Must (needed for v1 to work) or Should (valuable, can slip). Acceptance criteria are the testable conditions for "done".

### Epic A: Accounts and profile

**US-01 Register (Must)**

As a visitor, I want to register with an email and password so that I can use the app.

- Email must be unique and well-formed; duplicates return a clear error.
- Password must be at least 8 characters and is stored only as a salted hash.
- Validation errors are returned per field.

**US-02 Log in (Must)**

As a user, I want to log in so that I can access my library and proposals.

- Valid credentials return a short-lived access token and a refresh token.
- Wrong credentials return 401 with a generic message that does not reveal which part was wrong.
- A refresh token can be exchanged once for a new pair; reuse of an old one is rejected.

**US-03 Edit my profile (Must)**

As a user, I want to set my display name, city and postal code so that others can find me and I can find books near me.

- Display name, city and postal code are required before any copy can be listed as available.
- Only the profile owner can change it.
- Other users see display name and city, never email or postal code.

**US-04 Delete my account (Should)**

As a user, I want to delete my account so that my personal data is removed.

- Deletion is refused while I have accepted, uncompleted exchanges.
- Open (proposed) proposals involving me are cancelled.
- My copies are removed and personal data is erased.

### Epic B: My library

**US-05 Add a book by ISBN (Must)**

As a user, I want to add a book by entering its ISBN so that I do not have to type its details.

- The ISBN is validated (format and check digit); invalid ISBNs are rejected.
- Title, author and cover are looked up from an external catalog (Open Library or Google Books) and shown for confirmation.
- If the lookup fails or finds nothing, I can enter title and author manually.
- Adding creates a copy owned by me; the book record is shared between users.

**US-06 Set a condition grade (Must)**

As a user, I want to grade the condition of each copy so that others know what to expect.

- The grade is required and is one of: New, Like New, Good, Acceptable, Poor.
- The API exposes the grade scale with its criteria text so the client can show it.

**US-07 Own several copies of one book (Should)**

As a user, I want to add two copies of the same ISBN so that I can offer duplicates.

- Each copy is a separate record with its own grade and status.

**US-08 Edit or remove a copy (Must)**

As a user, I want to change a copy's grade or remove it so that my library stays accurate.

- Only the owner can edit or remove a copy.
- A reserved copy cannot be edited or removed.
- Changing the grade of, or removing, a copy that is in a proposed proposal cancels that proposal, so nobody accepts based on outdated information.

**US-09 List or unlist a copy (Must)**

As a user, I want to mark a copy as available or unlisted so that I control what others can see and request.

- Only available copies appear in browse and search and can be put into a proposal.
- Unlisting a copy cancels proposed proposals that include it.

**US-10 View my library (Must)**

As a user, I want to see all my copies with their grade and status so that I know what I own.

- The list is paginated and can be filtered by status (available, unlisted, reserved).

### Epic C: Discovery

**US-11 Browse books near me (Must)**

As a user, I want to browse available copies from other users in my area so that I can find books worth swapping for.

- Results exclude my own copies and any copy that is not available.
- Results can be filtered by city or postal code and default to my own location.
- Results are paginated and show title, author, cover, grade and the owner's display name and city.

**US-12 Search by title, author or ISBN (Must)**

As a user, I want to search for a specific book so that I can see who has it.

- Search matches partial, case-insensitive title and author text, and exact ISBN.
- Search respects the same exclusions and location filter as browse.

**US-13 Filter by minimum grade (Should)**

As a user, I want to filter results by condition so that I only see copies in acceptable shape.

- Filtering by "Good" returns copies graded Good or better.

**US-14 View another user's library (Must)**

As a user, I want to see everything another user has available so that I can build a proposal from their shelf.

- Only that user's available copies are shown; unlisted and reserved copies are hidden.

### Epic D: Exchange proposals

**US-15 Propose an exchange (Must)**

As a user, I want to offer one or more of my copies in return for one or more copies from another user so that books of different value can be balanced.

- A proposal has at least one offered copy and at least one requested copy, plus an optional message.
- All offered copies must be mine and available; all requested copies must belong to the recipient and be available.
- I cannot propose to myself.
- The proposal starts as proposed and gets an expiry date.
- Each item records the copy's grade at proposal time.
- The same copy may be part of several proposed proposals at once.

**US-16 See my proposals (Must)**

As a user, I want to see incoming and outgoing proposals so that I can respond to them and follow my own.

- Lists are separated into incoming and outgoing, paginated, and filterable by status.
- The detail view shows both sides' copies with grade and the message.
- Only the proposer and the recipient can see a proposal.

**US-17 Accept a proposal (Must)**

As a recipient, I want to accept a proposal so that the exchange can go ahead.

- Only the recipient can accept, and only while the proposal is proposed and not expired.
- Acceptance checks that every copy is still available and owned by the right party; otherwise it fails with a conflict error and nothing changes.
- All copies in the proposal become reserved in one transaction.
- Every other proposed proposal that contains any of these copies is cancelled automatically.
- If two competing proposals are accepted at the same moment, exactly one succeeds.

**US-18 Reject a proposal (Must)**

As a recipient, I want to reject a proposal so that the proposer knows I am not interested.

- Only the recipient can reject, and only while the proposal is proposed.
- An optional reason can be given. No copy changes status.

**US-19 Counter a proposal (Must)**

As a recipient, I want to answer with a changed offer, for example asking for one more book, so that we can reach a fair swap.

- The counter is a new proposal in which I become the proposer and the original proposer becomes the recipient.
- It links to the original, and the original moves to countered.
- Copies on either side can be added or removed, subject to the same checks as US-15.
- A counter is a normal proposal: it can be accepted, rejected, or countered again.
- A proposal is never edited after creation; every change is a counter.

**US-20 Cancel a proposal (Must)**

As a proposer, I want to withdraw a proposal so that it no longer clutters the recipient's inbox.

- The proposer can cancel while the proposal is proposed.
- Cancelling after acceptance is an open question (see Open questions).

**US-21 Proposals expire (Should)**

As a user, I want old proposals to close by themselves so that stale offers do not pile up.

- A scheduled job moves proposed proposals past their expiry to expired.
- Running the job twice has no extra effect.
- An expired proposal cannot be accepted, even if the job has not run yet.

**US-22 Add a pickup note (Should)**

As a participant in an accepted exchange, I want to leave a short note so that we can agree where and when to meet.

- Either party can add or edit the note while the exchange is accepted.
- The note is limited in length (500 characters) and visible only to the two parties.

### Epic E: Completion

**US-23 Confirm the exchange happened (Must)**

As a participant, I want to confirm that the swap took place so that the app reflects reality.

- Only the two parties can confirm, and only while the exchange is accepted.
- Each party can confirm once; the first confirmation is recorded and the exchange stays accepted.
- The second confirmation completes the exchange.

**US-24 Ownership moves automatically (Must)**

As a user, I want the books to change owner in the app when the exchange completes so that my library is correct.

- Ownership of every copy transfers in the same transaction as the second confirmation; if anything fails, nothing changes.
- Transferred copies are unlisted by default, and the new owner decides whether to list them.
- Copies are no longer reserved.

**US-25 See my exchange history (Should)**

As a user, I want to see my completed exchanges so that I can remember what I swapped and with whom.

- The list shows what changed hands, with whom, and when it completed.

## Exchange lifecycle

A proposal starts as proposed and ends in exactly one of five closed states. Only the second confirmation turns an accepted exchange into a completed one.

&#91;embedded content: proposal states · 7 states, 6 transitions\]

Only the recipient can accept, reject or counter. The proposer can cancel while a proposal is proposed, and a counter opens a new proposal.

The system cancels a proposal when one of its copies is reserved elsewhere, changed or removed, and expires it past its expiry date. Cancelling after acceptance is still an open question.

## Business rules

These invariants must hold at all times, whatever the client does. Each should have at least one automated test.

1. A copy has exactly one owner and one status: available, unlisted, or reserved.
2. Only available copies can be put into a new proposal.
3. At creation and again at acceptance, offered copies belong to the proposer and requested copies belong to the recipient.
4. A copy can be in many proposed proposals but in at most one accepted proposal.
5. Acceptance reserves all copies of the proposal in one transaction; if any copy is no longer available, the whole acceptance fails.
6. When a proposal is accepted, every other proposed proposal containing one of its copies is cancelled.
7. A proposal is immutable after creation; a change is a new counter-offer, and the original becomes countered.
8. A proposal can only be seen and acted on by its two participants, and only the recipient can accept, reject or counter it.
9. The exchange completes only when both parties have confirmed; ownership transfer happens in that same transaction.
10. Changing a copy's grade or listing status, or removing it, cancels proposed proposals that contain it. A reserved copy cannot be changed.

**Condition grades (draft wording)**

| Grade | Criteria |
| --- | --- |
| New | Unread, no marks, no wear |
| Like New | Read once or twice, no visible wear or marks |
| Good | Light wear on cover or spine, no marks inside, all pages intact |
| Acceptable | Clear wear, possibly light notes or highlights, all pages intact and readable |
| Poor | Heavy wear or damage, but complete and readable |

## Non-functional requirements

These are where the backend shows its quality, so they are as much part of the spec as the features.

| Area | Requirement |
| --- | --- |
| Security | Password hashing with BCrypt or Argon2; JWT access and refresh tokens; authorization checked in the service layer, not only in controllers (for example "only the recipient can accept") |
| Concurrency | Optimistic locking (`@Version`) on copies and proposals, with a defined retry or conflict response; a test proves that two simultaneous accepts of competing proposals leave exactly one accepted |
| Data | PostgreSQL, schema managed by Flyway migrations, constraints enforced in the database as well as in code |
| Architecture | Layered or hexagonal structure; DTOs separate from entities; global error handling with a consistent error format; Bean Validation on inputs |
| State machine | Valid transitions defined in one place; every invalid transition is rejected and covered by a unit test |
| Testing | JUnit 5 and Mockito for units; Testcontainers integration tests for the full exchange flow against a real PostgreSQL |
| Performance | All list endpoints paginated; no N+1 queries on browse, search and proposal lists (checked by a test or query log) |
| API | REST with OpenAPI/Swagger documentation; Swagger UI usable as the interim client |
| Privacy | Store only what is needed (email, display name, city, postal code); account deletion erases personal data |
| Operations | Docker Compose for local setup; CI pipeline (GitHub Actions) running build and tests on every push |

## Open questions

Eight decisions are still open. Each has a suggested default so work can start without waiting.

| Question | Suggested default |
| --- | --- |
| Can a party cancel after acceptance? | Yes, either party, until one has confirmed completion; reserved copies are released |
| What if only one party confirms? | After 14 days the exchange auto-completes; if neither confirms, it is cancelled and the copies are released |
| How long until a proposal expires? | 14 days |
| Is email verification required at registration? | Not in v1; add later together with notifications |
| How is "near me" defined? | Exact city match or postal-code prefix in v1; no distance calculation |
| Is there a maximum number of books per side? | Yes, 5 per side, to keep proposals readable |
| Is there an admin or moderation role? | Not in v1 |
| Which language do the UI and book metadata use? | English UI; metadata as returned by the catalog |

## Delivery order

Build the backend in six steps, each usable through Swagger UI before the next begins. The exchange core is the centrepiece, so it gets its own step with the heaviest testing.

1. **Foundation:** project skeleton, PostgreSQL with Flyway, Docker Compose, CI pipeline, then US-01 to US-03.
2. **Library:** US-05 to US-10, including the ISBN catalog lookup.
3. **Discovery:** US-11 to US-14.
4. **Exchange core:** US-15 to US-20, the state machine, reservation logic, and the concurrency tests.
5. **Completion and housekeeping:** US-21 to US-25, plus US-04.
6. **Thin React Native client:** add a book, browse, and propose an exchange against the finished API.
