# BookSwap Step 6: Mobile Client (Expo / React Native)

## Intent
The Java backend is the portfolio showcase, but a reviewer (and the owner) should be able to *see* it work. This step adds a small, good-looking Android-first app that exercises the whole finished API: build a personal library, discover other people's books, propose and complete swaps. It stays thin: no business rules live in the app; every status rule is the backend's, and the app only shows the actions the backend will accept.

## What the owner decided
- **Expo + TypeScript**, run on the owner's own Android phone through Expo Go (no Android Studio, no emulator). The owner has almost no React Native experience, so code must be plain and conventional, and setup steps must be written down.
- **Four tabs: Library, Discover, Exchanges, Profile.** The personal library is the main thing.
- **Library:** two-column grid of big covers (digital covers from the book lookup); under each cover the title, author and a condition/status badge. Tapping opens the book's details (condition, status, change condition, list/unlist, remove). A "+" starts adding a book.
- **Adding a book:** scan the barcode with the camera **or** type the ISBN. Both go through the backend lookup, show the found book with its cover, ask for the condition, and save. If nothing is found the user can type title and author (the backend already supports this).
- **Discover:** the same card grid of other people's available books (feed), a search bar on top (title, author or ISBN). Each card shows the owner's name and city. Tapping opens details with a "Propose swap" button.
- **Propose swap:** one screen. The tapped book is already selected as the one wanted; the user ticks which of their own available books to offer. The user can also add more of the other person's books from their shelf on the same screen (confirmed by the owner). Optional message. One submit.
- **Exchanges:** a row of sections along the top: **Incoming**, **Outgoing**, **Active** (accepted), **History** (completed). Tapping opens the detail with the actions that apply: accept, reject, counter, cancel, confirm, pickup note. **Everything is covered**, including counter-offers and the pickup note.
- **Profile:** show and edit name, city, postal code, contact note; log out; delete account (asks for the password).
- **Look and feel:** cosy and bookish. Warm cream and brown palette, serif titles, soft rounded covers. Light mode only.
- **Automated tests**: a small set on the app's logic (API client, token refresh, mapping of server state to available actions).

## Goals
- Every backend capability reachable from the UI: auth, profile, library, discovery, the full proposal lifecycle, history, account deletion.
- Works against the existing API unchanged. (If something truly blocks the client, any backend change goes through its own small TDD change, flagged to the owner.)
- Clear setup instructions: start the backend, set the API address, run Expo, open on the phone.
- Handles the real failure modes gracefully: expired access token, server error codes, no network, empty lists, missing cover.

## Non-goals
Push notifications, chat, ratings, offline mode, iOS verification, dark mode, i18n, store release builds, e2e/device-farm tests, image upload (covers only come from the catalog), maps.

## Anti-goals (a failure even if it works)
- A screen that offers an action the backend will refuse (e.g. a confirmer who is shown "Cancel").
- The other party's contact details or pickup note shown anywhere except where the backend returns them.
- Tokens stored in plain AsyncStorage, or logged.
- Any screen that crashes on a null cover, empty list, or unknown error code.
- Business rules duplicated in the app (it may *hide* buttons from fields the API sends, but never decide outcomes).

## Constraints
- Lives in `mobile/` in this repo. Backend code untouched unless blocked.
- Package manager npm; current Expo SDK at implementation time (pin exact versions).
- Tokens in `expo-secure-store`. API address from `EXPO_PUBLIC_API_URL` (a LAN address like `http://192.168.x.x:8080`), documented in `mobile/README.md` including the backend listening on the LAN and the Windows firewall prompt.
- Access token refreshed transparently once on a 401, concurrent requests share one refresh; if refresh fails the user is signed out.
- Errors: the backend returns `{code, ...}`; the app maps known codes (e.g. `ALREADY_CONFIRMED`, `ACCOUNT_HAS_ACTIVE_EXCHANGE`, `INVALID_CREDENTIALS`) to friendly messages and falls back to a generic one.
- Existing CI keeps passing; add a mobile job running type-check and tests.

## API the app uses (existing)
Auth `POST /api/auth/{register,login,refresh}`; profile `GET/PUT/DELETE /api/users/me`; library `GET/POST /api/copies`, `GET/PUT/DELETE /api/copies/{id}`, `POST /api/copies/{id}/{list,unlist}`; `GET /api/condition-grades`; `GET /api/books/lookup?isbn=`; discovery `GET /api/feed`, `/api/search?q=`, `/api/users/{id}/copies`; proposals `POST /api/proposals`, `GET /api/proposals` (+ detail), `POST .../{accept,counter,reject,confirm,cancel}`, `PUT .../pickup-note`; `GET /api/exchanges`. All lists are paginated; the app uses infinite scroll.

## Left to the builder (my calls, owner can overrule)
- **expo-router** for navigation (the Expo default), **TanStack Query** for server state, **expo-camera** for barcode scanning, **Jest + React Native Testing Library** for tests.
- Folder layout, component split, the exact palette values and serif font choice.
- Placeholder for books without a cover: a warm coloured tile with the title.
- Whether the "Active" section is a filter over the same proposals list or its own query, whichever the API makes simplest.
- Role/status to visible-action mapping, derived from the backend's rules (proposer vs recipient, PROPOSED/ACCEPTED/…, who has already confirmed).

## More calls settled while writing (mine; owner can overrule)
- **Counter-offer:** the backend marks the original `COUNTERED` and creates a new proposal going the other way, linked by `parentId`. The app reuses the propose screen (one shared "compose proposal" component), prefilled from the current proposal, both sides editable within what the backend accepts; server validation errors are shown as-is. The countered original appears under History-style "Countered" state in its detail, not in Incoming/Outgoing.
- **Active section:** `GET /api/proposals` needs `role=incoming|outgoing`, so Active is two stacked groups ("You received" / "You proposed"), each its own paginated query. No backend change. History uses `/api/exchanges` and its own card shape.
- **Sign-up flow:** a signed-out stack (login, register) before the tabs. Register takes email and password only, so the app logs in straight after and opens the Profile tab with a "complete your profile" banner; the Library "+" and listing explain why a complete profile is needed when the backend refuses.

## Review focus
Token refresh races and sign-out loops; actions shown to the wrong party/status; list screens with 0, 1 and >1 pages; null cover/city/message; a book that is RESERVED or UNLISTED being offered; scanning a non-ISBN barcode; camera permission denied; account deletion refused with an active exchange.

## Testing
Unit tests (Jest) for the API client (auth header, 401 → refresh once → retry, refresh failure signs out, concurrent 401s share one refresh), error-code mapping, the action-availability function for every role x status case, and ISBN/barcode normalisation. A few component tests (library card badges, propose screen disables submit with nothing offered). Manual run-through on the phone is documented as the acceptance check.
