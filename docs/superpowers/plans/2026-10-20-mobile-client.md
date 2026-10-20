# Mobile Client (Step 6) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A cosy, bookish Expo app (Android phone through Expo Go) that exercises the whole finished BookSwap API: library with covers, barcode/ISBN adding, discovery and search, propose/counter on one screen, the full exchange lifecycle with history, profile and account deletion.

**Architecture:** `mobile/` in this repo. expo-router file routes for screens; everything else under `mobile/src`: a typed `fetch` API client with transparent token refresh, a session provider backed by `expo-secure-store`, TanStack Query for server state, and three small pure-logic modules (ISBN normalisation, proposal composition limits, which exchange actions to show) that carry the unit tests. No business rule is decided in the app: buttons are hidden from fields the API returns and the backend remains the authority.

**Tech Stack:** Expo (SDK that matches the installed Node), TypeScript, expo-router, @tanstack/react-query, expo-secure-store, expo-camera, Jest (jest-expo) + @testing-library/react-native.

**Spec:** `docs/superpowers/specs/2026-10-20-mobile-client-design.md`

## Global Constraints

- All app code in `mobile/`; backend untouched. A backend change is allowed only if truly blocking, as its own TDD change flagged to the owner.
- Run on an Android phone via Expo Go; no emulator, no Android Studio, no native builds. API address from `EXPO_PUBLIC_API_URL` (e.g. `http://192.168.1.20:8080`); no hard-coded host.
- Tokens only in `expo-secure-store`; never in AsyncStorage, never logged.
- Look and feel: cosy and bookish, warm cream and brown, serif titles, soft rounded covers, light mode only. Palette lives in `mobile/src/theme.ts` and nothing else hard-codes colours.
- Library layout: two-column grid of big covers; under each cover title, author, a condition/status badge. Tabs in this order: Library, Discover, Exchanges, Profile.
- Exchanges sections along the top: Incoming, Outgoing, Active (two stacked groups "You received" / "You proposed", each its own paginated query), History (`GET /api/exchanges`).
- Errors arrive as `{status, code, message, fieldErrors, timestamp, path}`. Only a 401 with `code === "UNAUTHORIZED"` triggers a token refresh; a 401 `INVALID_CREDENTIALS` (wrong login password, wrong delete-account password) never does.
- Proposal sides hold 1 to 5 copies; message and pickup note at most 500 chars; profile limits: name 60, city 100, postal code 20, contact note 200.
- Every list is paginated (`PageResponse{content,page,size,totalElements,totalPages}`, size at most 100) and uses infinite scroll.
- A missing cover, city, message or contact note must never crash a screen.
- Commit messages end with `-m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>" -m "Claude-Session: https://claude.ai/code/session_01A9ZdQrYBVzmxQSkUW2wWSw"`. Do not touch the untracked `.claude/` folder. Write source with the Write/Edit tools, not shell heredocs.
- Mobile checks: `cd mobile && npx tsc --noEmit && npm test`.

## Review Focus

- Token refresh races: N concurrent 401s refresh once; a failed refresh signs out once, with no loop (Task 2, tests there).
- Wrong-party/wrong-status actions: the confirmer is never shown Cancel; nothing shown past `confirmBy` or on closed proposals (Task 9, matrix test).
- Lists with 0, 1 and several pages, and with null cover/city/message (Tasks 4 to 8, component tests).
- Offering a RESERVED/UNLISTED book, or more than 5 per side (Task 7, selection tests).
- Scanning a non-ISBN barcode, or camera permission denied (Task 5, tests there).

---

### Task 1: Scaffold, theme, test harness, CI

**Files:**
- Create: `mobile/` (Expo TypeScript app), `mobile/src/theme.ts`, `mobile/jest.config.js` (or `package.json` jest block), `mobile/__tests__/theme.test.ts`
- Modify: `.github/workflows/ci.yml` (second job `mobile`), root `.gitignore` if needed

**Interfaces:**
- Produces: `theme` in `mobile/src/theme.ts`: `{ colors: { cream, paper, brown, ink, accent, muted, danger, success, border }, fonts: { title, body }, radius: { card, button } }` (strings/numbers); the serif title font is the platform serif (`Georgia`/`serif`), no font download.

- [ ] **Step 1:** Run `node -v`. Current Expo SDKs need Node 20.19+; if the installed Node is older, STOP and ask the owner to install Node LTS (outside this repo) before continuing.
- [ ] **Step 2:** From the repo root run `npx create-expo-app@latest mobile --template blank-typescript`, then in `mobile/` install: `npx expo install expo-router expo-secure-store expo-camera react-native-safe-area-context react-native-screens expo-linking expo-constants expo-status-bar @tanstack/react-query` and dev deps `jest-expo jest @testing-library/react-native @types/jest`. Configure expo-router (`"main": "expo-router/entry"`, `scheme`, the `expo-router` and `expo-camera` plugins with a camera permission message in `app.json`), and make `app/_layout.tsx` render a `Stack`. Pin exact versions in `package.json`.
- [ ] **Step 3: Write the failing test** `mobile/__tests__/theme.test.ts`: `theme.colors.cream` and `theme.colors.brown` are hex strings, `theme.fonts.title` is a non-empty string.
- [ ] **Step 4:** Run `npm test` in `mobile/`. Expected: FAIL, cannot find `../src/theme`.
- [ ] **Step 5:** Implement `theme.ts` (warm cream background, brown text, terracotta accent). Add `"test": "jest"` and `"typecheck": "tsc --noEmit"` scripts, the `jest-expo` preset.
- [ ] **Step 6:** Run `npx tsc --noEmit && npm test`. Expected: PASS, no type errors.
- [ ] **Step 7:** Add a `mobile` job to `.github/workflows/ci.yml`: checkout, `actions/setup-node@v4` (node 20, npm cache on `mobile/package-lock.json`), `npm ci`, `npm run typecheck`, `npm test`, all with `working-directory: mobile`. Add `mobile/node_modules`, `.expo` to `.gitignore` if the scaffold did not.
- [ ] **Step 8: Commit** `feat(mobile): scaffold Expo app with theme, tests and CI job`.

---

### Task 2: Types, API client, error mapping

**Files:**
- Create: `mobile/src/api/types.ts`, `mobile/src/api/errors.ts`, `mobile/src/api/client.ts`
- Test: `mobile/__tests__/client.test.ts`, `mobile/__tests__/errors.test.ts`

**Interfaces:**
- Produces `types.ts`: TypeScript types mirroring the backend records exactly: `TokenResponse{accessToken,refreshToken,tokenType,expiresInSeconds}`, `Profile{id,email,displayName,city,postalCode,contactNote,profileComplete}`, `ConditionGrade = 'NEW'|'LIKE_NEW'|'GOOD'|'ACCEPTABLE'|'POOR'`, `GradeInfo{code,label,criteria}`, `CopyStatus = 'AVAILABLE'|'UNLISTED'|'RESERVED'`, `DetailsSource` (read `Copy.DetailsSource` in the backend for the values), `Copy{id,isbn,title,author,coverUrl,grade,status,detailsSource,createdAt,updatedAt}`, `CopyCard{copyId,isbn,title,author,coverUrl,grade,detailsSource,owner:{id,displayName,city}}`, `BookLookup{isbn,title,author,coverUrl,source:'LOCAL'|'CATALOG'}`, `ProposalStatus` (`PROPOSED|ACCEPTED|REJECTED|COUNTERED|CANCELLED|EXPIRED|COMPLETED`), `CancelCause` (`WITHDRAWN|COPY_UNAVAILABLE|NO_SHOW`), `Party{id,displayName,city?,email?,contactNote?}`, `Item{copyId,isbn,title,author,coverUrl,detailsSource,gradeAtProposal}`, `Proposal{id,status,cancelCause,parentId,message,rejectReason,createdAt,expiresAt,acceptedAt,confirmBy,completedAt,confirmedByMe,confirmedByOther,pickupNote,proposer,recipient,offered,requested}` (nullable fields typed `| null`), `Exchange{proposalId,completedAt,with,gave,received}`, `Page<T>{content,page,size,totalElements,totalPages}`, `ApiErrorBody`.
- Produces `errors.ts`: `class ApiError extends Error {status:number; code:string; fieldErrors:{field:string;message:string}[]}`, `friendlyMessage(e: unknown): string` (known codes mapped to plain sentences, `fieldErrors` joined for `VALIDATION_FAILED`, a network failure gives "Can't reach the server", otherwise the server `message`, otherwise "Something went wrong").
- Produces `client.ts`: `createApiClient({baseUrl, tokens: TokenStore, onSignedOut: () => void, fetchImpl?: typeof fetch}) => { request<T>(method, path, opts?: {body?: unknown; query?: Record<string,string|number|undefined>; auth?: boolean}): Promise<T> }` and `interface TokenStore { get(): Promise<{access:string; refresh:string}|null>; set(t:{access:string;refresh:string}): Promise<void>; clear(): Promise<void> }`. 204 responses resolve to `undefined`.

- [ ] **Step 1: Write failing tests** in `client.test.ts` with a fake `fetchImpl` and in-memory `TokenStore`:
  - `sendsBearerTokenAndJsonBody`.
  - `a 401 UNAUTHORIZED triggers one refresh then retries once with the new token and stores the new pair`.
  - `a 401 INVALID_CREDENTIALS is thrown as ApiError without any refresh call`.
  - `five concurrent 401s cause exactly one refresh call`.
  - `a failed refresh clears the tokens, calls onSignedOut once, and rejects with ApiError`.
  - `a request retried after refresh that 401s again does not refresh again` (no loop).
  - `non-JSON error body or a network failure becomes a friendly ApiError / TypeError handled by friendlyMessage`.
  In `errors.test.ts`: each of these codes maps to a non-empty sentence: `ALREADY_CONFIRMED`, `ACCOUNT_HAS_ACTIVE_EXCHANGE`, `INVALID_CREDENTIALS`, `PROFILE_INCOMPLETE`, `BOOK_NOT_FOUND`, `COPY_NOT_AVAILABLE`, `COPY_RESERVED`, `PROPOSAL_NOT_OPEN`, `PROPOSAL_ACTION_NOT_ALLOWED`, `CONCURRENT_UPDATE`, `EMAIL_TAKEN`; unknown code falls back to the server message.
- [ ] **Step 2:** Run `npm test -- client errors`. Expected: FAIL, modules missing.
- [ ] **Step 3:** Implement the three files. Single-flight refresh: one shared in-flight promise reused by all waiting 401s; refresh calls `POST /api/auth/refresh` with `{refreshToken}` and no bearer; never refresh for the refresh call itself.
- [ ] **Step 4:** Run `npx tsc --noEmit && npm test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(mobile): typed API client with single-flight token refresh`.

---

### Task 3: Session, sign-in and register

**Files:**
- Create: `mobile/src/auth/tokenStore.ts` (SecureStore-backed `TokenStore`), `mobile/src/auth/session.tsx`, `mobile/src/api/queryClient.ts`, `mobile/src/api/endpoints/auth.ts`, `mobile/app/(auth)/_layout.tsx`, `mobile/app/(auth)/login.tsx`, `mobile/app/(auth)/register.tsx`, `mobile/src/components/Screen.tsx`, `Button.tsx`, `TextField.tsx`; modify `mobile/app/_layout.tsx` (providers, redirect: no session → `(auth)`, session → `(tabs)`), create a placeholder `mobile/app/(tabs)/_layout.tsx` with the four empty tabs (Library, Discover, Exchanges, Profile).
- Test: `mobile/__tests__/session.test.tsx`, `mobile/__tests__/login.test.tsx`

**Interfaces:**
- Consumes: `createApiClient`, `TokenStore`, `friendlyMessage`, `theme`.
- Produces: `useSession(): { status: 'loading'|'signedOut'|'signedIn'; signIn(email,password): Promise<void>; register(email,password): Promise<void>; signOut(): Promise<void>; api: ApiClient }` and `SessionProvider({tokenStore?, baseUrl?})`; `register` posts register then signs in; `signOut` clears tokens and the query cache. `useApi()` returns the client. `Screen`, `Button({title,onPress,loading?,variant?})`, `TextField` are the shared building blocks later tasks reuse.

- [ ] **Step 1: Write failing tests:** `signIn stores the tokens and status becomes signedIn`; `register then signs in with the same credentials`; `signOut clears tokens and status becomes signedOut`; `a stored token pair at start gives signedIn without a request` (status `loading` first); `onSignedOut from the client flips the session to signedOut`. In `login.test.tsx`: wrong password shows the friendly INVALID_CREDENTIALS message and stays on the screen; the submit button is disabled while the request is pending.
- [ ] **Step 2:** Run `npm test -- session login`. Expected: FAIL.
- [ ] **Step 3:** Implement. Base URL from `process.env.EXPO_PUBLIC_API_URL`; if unset, the provider shows a clear "API address not set, see mobile/README.md" screen instead of crashing.
- [ ] **Step 4:** Run `npx tsc --noEmit && npm test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(mobile): session, secure token storage, login and register`.

---

### Task 4: Shared book UI and infinite lists

**Files:**
- Create: `mobile/src/components/BookCover.tsx`, `BookCard.tsx`, `ConditionBadge.tsx`, `StatusBadge.tsx`, `EmptyState.tsx`, `ErrorState.tsx`, `mobile/src/components/BookGrid.tsx` (two-column `FlatList`, `onEndReached` fetches the next page), `mobile/src/api/usePagedQuery.ts`
- Test: `mobile/__tests__/bookCard.test.tsx`, `mobile/__tests__/usePagedQuery.test.tsx`

**Interfaces:**
- Produces: `BookCover({coverUrl: string|null, title: string})` (image, or a warm coloured tile with the title when null or when the image fails to load); `BookCard({title, author, coverUrl, grade?: ConditionGrade, status?: CopyStatus, subtitle?: string, onPress})` (subtitle used by Discover for "owner · city", tolerant of a null city); `usePagedQuery<T>(key: unknown[], fetchPage: (page:number)=>Promise<Page<T>>)` wrapping `useInfiniteQuery`, returning `{items: T[], fetchNextPage(), hasNextPage, isLoading, isError, error, refetch}`; `BookGrid` accepts `items`, a render of `BookCard` per item, `onEndReached`, empty and error states.

- [ ] **Step 1: Write failing tests:** `BookCard shows title, author, the grade label and the status label`; `BookCover with null coverUrl renders the title tile and does not crash`; `BookCard with null city shows the owner name only`; `usePagedQuery flattens pages and hasNextPage is false when page+1 >= totalPages`; `zero results shows the EmptyState text passed in`.
- [ ] **Step 2:** Run `npm test -- bookCard usePagedQuery`. Expected: FAIL.
- [ ] **Step 3:** Implement. Grade labels: fetch is not needed for display, use a static map `NEW→New, LIKE_NEW→Like New, GOOD→Good, ACCEPTABLE→Acceptable, POOR→Poor` mirroring the backend labels; status labels `Available/Unlisted/Reserved`.
- [ ] **Step 4:** Run `npx tsc --noEmit && npm test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(mobile): cover, card, badge and infinite list building blocks`.

---

### Task 5: Library tab, book details and adding a book

**Files:**
- Create: `mobile/src/logic/isbn.ts`, `mobile/src/api/endpoints/copies.ts` (list, get, add, changeGrade, list/unlist, remove, `lookupBook`, `conditionGrades`), `mobile/app/(tabs)/library/_layout.tsx` (Stack), `index.tsx` (grid with "+" in the header), `[id].tsx` (details), `add.tsx` (type ISBN / scan / result / grade / manual fallback / save), `mobile/src/components/BarcodeScanner.tsx`, `GradePicker.tsx`
- Test: `mobile/__tests__/isbn.test.ts`, `mobile/__tests__/library.test.tsx`, `mobile/__tests__/addBook.test.tsx`

**Interfaces:**
- Consumes: Task 3 `useSession().api`, Task 4 components.
- Produces: `normalizeIsbn(raw: string): string | null` (strips spaces and hyphens, returns a 13-digit string for a valid ISBN-13 with 978/979 prefix and valid check digit, a 10-char string for a valid ISBN-10, else null); `copyActions(copy: Copy): {canChangeGrade: boolean; canList: boolean; canUnlist: boolean; canRemove: boolean}` (RESERVED: all false; UNLISTED: canList; AVAILABLE: canUnlist; both: canChangeGrade, canRemove) — display hiding only.

- [ ] **Step 1: Write failing tests:** `isbn.test.ts`: `978-0-441-01359-3` → `9780441013593`; a bad check digit → null; an ISBN-10 `0441013597` is accepted; a UPC/EAN `012345678905` (not 978/979) → null; empty → null. `library.test.tsx`: grid shows each copy's title, author, grade and status; empty library shows the empty state with an "Add a book" prompt; RESERVED detail screen shows no change/list/remove buttons; AVAILABLE detail shows Unlist and Remove (Remove confirms first); a `PROFILE_INCOMPLETE` error on List shows the friendly message with a link to Profile. `addBook.test.tsx`: typing a valid ISBN calls lookup and shows the found cover and title; choosing a grade and saving POSTs `{isbn, grade}` and returns to the library; `BOOK_NOT_FOUND` reveals title and author fields and the POST then includes them; an invalid ISBN shows an error without calling the API; a scanned non-ISBN barcode shows "That doesn't look like a book barcode" and keeps scanning; camera permission denied shows an explanation and the typing option stays usable.
- [ ] **Step 2:** Run `npm test -- isbn library addBook`. Expected: FAIL.
- [ ] **Step 3:** Implement `normalizeIsbn`, `copyActions`, the endpoints, the screens. The scanner uses `CameraView` with `barcodeScannerSettings={{barcodeTypes:['ean13']}}`, requests permission through `useCameraPermissions`, ignores repeat scans of the same code. Grades list from `GET /api/condition-grades` (labels and criteria shown as help text). Invalidate the `['copies']` query after every mutation.
- [ ] **Step 4:** Run `npx tsc --noEmit && npm test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(mobile): library grid, book details and adding by scan or ISBN`.

---

### Task 6: Discover tab

**Files:**
- Create: `mobile/src/api/endpoints/discovery.ts` (`feed`, `search`, `shelf`), `mobile/app/(tabs)/discover/_layout.tsx`, `index.tsx` (search bar + grid), `book.tsx` (details of a card, "Propose swap" button), `mobile/src/logic/useDebounced.ts`
- Test: `mobile/__tests__/discover.test.tsx`

**Interfaces:**
- Consumes: Task 4 `BookGrid`, `usePagedQuery`; Task 3 `api`.
- Produces: route `discover/book` takes a `card` param (JSON-encoded `CopyCard`; there is no single-card endpoint). "Propose swap" navigates to `/compose` with params `recipientId`, `wantedCopyId` (Task 7).

- [ ] **Step 1: Write failing tests:** `with an empty search box the feed is shown`; `typing text queries /api/search with q after the debounce and not before`; `the card subtitle shows owner name and city, and only the name when city is null`; `no results shows "Nothing here yet"`; `INVALID_QUERY (over 100 chars) shows the friendly message`; `the details screen shows cover, title, author, grade, owner and a Propose swap button that navigates with recipientId and wantedCopyId`.
- [ ] **Step 2:** Run `npm test -- discover`. Expected: FAIL.
- [ ] **Step 3:** Implement. Search input trimmed; blank means feed; debounce 400 ms.
- [ ] **Step 4:** Run `npx tsc --noEmit && npm test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(mobile): discover feed, search and book details`.

---

### Task 7: Compose proposal (propose and counter)

**Files:**
- Create: `mobile/src/logic/compose.ts`, `mobile/src/api/endpoints/proposals.ts` (`propose`, `counter`, plus the rest added in Tasks 8 and 9), `mobile/app/compose.tsx` (registered in the root Stack as a modal)
- Test: `mobile/__tests__/compose.test.ts`, `mobile/__tests__/composeScreen.test.tsx`

**Interfaces:**
- Consumes: Task 5 copies endpoints, Task 6 `shelf` endpoint, `Item`/`Copy`/`CopyCard` types.
- Produces: `compose.ts`: `MAX_PER_SIDE = 5`; `type Selection = {requested: string[]; offered: string[]}`; `toggle(sel: Selection, side: 'requested'|'offered', copyId: string): Selection` (adds up to `MAX_PER_SIDE`, removes when present, ignores additions beyond the limit); `canSubmit(sel: Selection): boolean` (both sides 1..5); `toRequest(sel: Selection, message: string): {requestedCopyIds: string[]; offeredCopyIds: string[]; message: string | null}` (blank message becomes null). Screen params: `recipientId` and `wantedCopyId` for a new proposal; `counterOf` (proposal id) for a counter, which loads the proposal and pre-selects its items swapped to the counter's direction (the counterer offers what the original requested and requests what the original offered, as the backend expects: read `ProposalService.counter` for the exact direction before coding).

- [ ] **Step 1: Write failing tests:** `compose.test.ts`: `toggle adds, removes, and refuses a 6th item per side`; `canSubmit is false when either side is empty`; `toRequest turns a blank message into null`. `composeScreen.test.tsx`: `opens with the tapped book selected on their side and submit disabled until one of my books is ticked`; `my side lists only my AVAILABLE copies`; `their side lists their shelf and lets me tick more`; `submit POSTs /api/proposals with both id lists and the message, then goes to Exchanges Outgoing`; `counter mode is pre-filled and POSTs /api/proposals/{id}/counter`; `COPY_NOT_AVAILABLE shows the friendly message and keeps the selection`; `a 500-char message is accepted and a 501st character is blocked`.
- [ ] **Step 2:** Run `npm test -- compose`. Expected: FAIL.
- [ ] **Step 3:** Implement. My side uses `GET /api/copies?status=AVAILABLE`, their side `GET /api/users/{recipientId}/copies`, both with `usePagedQuery`. Invalidate `['proposals']` and `['copies']` after success.
- [ ] **Step 4:** Run `npx tsc --noEmit && npm test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(mobile): compose screen for proposals and counter-offers`.

---

### Task 8: Exchanges tab lists

**Files:**
- Modify: `mobile/src/api/endpoints/proposals.ts` (`listProposals(role, status?, page)`, `listExchanges(page)`, `getProposal(id)`)
- Create: `mobile/app/(tabs)/exchanges/_layout.tsx`, `index.tsx` (section tabs Incoming | Outgoing | Active | History), `mobile/src/components/ProposalCard.tsx`, `ExchangeCard.tsx`, `SectionTabs.tsx`
- Test: `mobile/__tests__/exchangesLists.test.tsx`

**Interfaces:**
- Consumes: Task 4 `usePagedQuery`; Task 2 types.
- Produces: `ProposalCard({proposal: Proposal, perspective: 'incoming'|'outgoing', onPress})` showing the other party's name, a status chip, the first item titles with "+N more"; `ExchangeCard({exchange, onPress})` showing the other party (Deleted user as the API sends it), date and counts; tapping a card navigates to `exchanges/[id]` (Task 9). Query keys start with `['proposals', ...]` or `['exchanges']`.

- [ ] **Step 1: Write failing tests:** `Incoming requests role=incoming and Outgoing role=outgoing`; `Active shows two groups, "You received" with role=incoming&status=ACCEPTED and "You proposed" with role=outgoing&status=ACCEPTED`; `History requests /api/exchanges and shows the other party and date`; `an empty section shows its own empty text`; `a card with null message and null city renders`; `proposals past confirmBy are labelled "Settling soon"` (display only, `confirmBy` in the past).
- [ ] **Step 2:** Run `npm test -- exchangesLists`. Expected: FAIL.
- [ ] **Step 3:** Implement. Pull-to-refresh refetches the visible section.
- [ ] **Step 4:** Run `npx tsc --noEmit && npm test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(mobile): exchanges tab with incoming, outgoing, active and history`.

---

### Task 9: Exchange detail and actions

**Files:**
- Modify: `mobile/src/api/endpoints/proposals.ts` (`accept`, `reject(id, reason?)`, `cancel`, `confirm`, `setPickupNote(id, note)`)
- Create: `mobile/src/logic/proposalActions.ts`, `mobile/app/(tabs)/exchanges/[id].tsx`, `mobile/src/components/PickupNoteEditor.tsx`
- Test: `mobile/__tests__/proposalActions.test.ts`, `mobile/__tests__/exchangeDetail.test.tsx`

**Interfaces:**
- Consumes: Task 7 route `/compose?counterOf=`; Task 2 types; the signed-in user's id from `GET /api/users/me` (cache key `['me']`, added to `endpoints/profile.ts` in Task 10, create it here if missing and let Task 10 extend it).
- Produces: `proposalActions(p: Proposal, myId: string, now: Date): {accept; reject; counter; cancel; confirm; editPickupNote: boolean}`. Rules, derived from the backend (read `Proposal.java` before coding and match it): status `PROPOSED` and I am recipient → accept, reject, counter; `PROPOSED` and I am proposer → cancel; `ACCEPTED` and `now < confirmBy` → I may editPickupNote; confirm when `confirmedByMe === false`; cancel when nobody has confirmed, or when `confirmedByMe === false && confirmedByOther === true`; `ACCEPTED` at or after `confirmBy`, and every closed status → all false.

- [ ] **Step 1: Write failing tests:** `proposalActions.test.ts` a table over {proposer, recipient} x {PROPOSED, ACCEPTED none confirmed, ACCEPTED I confirmed, ACCEPTED other confirmed, ACCEPTED past confirmBy, REJECTED, COUNTERED, CANCELLED, EXPIRED, COMPLETED} asserting every flag; explicitly `the confirmer cannot cancel` and `nothing is allowed at exactly confirmBy`. `exchangeDetail.test.tsx`: `accepted detail shows the other party's email and contact note when the API sends them, and nothing when it does not`; `confirm calls POST /confirm and shows "Waiting for the other person"` when only I confirmed; `ALREADY_CONFIRMED` shows the friendly message; `reject asks for an optional reason and sends it`; `counter navigates to compose with counterOf`; `saving a pickup note calls PUT with the text and an empty text clears it`; `a COUNTERED proposal shows a link to the new proposal` (child whose `parentId` is this id is not queryable, so show only "Countered" and the status); `a completed detail shows what changed hands`.
- [ ] **Step 2:** Run `npm test -- proposalActions exchangeDetail`. Expected: FAIL.
- [ ] **Step 3:** Implement. After every action update the cached proposal from the response and invalidate `['proposals']`, `['exchanges']`, `['copies']`.
- [ ] **Step 4:** Run `npx tsc --noEmit && npm test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(mobile): exchange details with accept, reject, counter, cancel, confirm and pickup note`.

---

### Task 10: Profile tab and account deletion

**Files:**
- Create: `mobile/src/api/endpoints/profile.ts` (`getMe`, `updateMe`, `deleteMe(password)`), `mobile/app/(tabs)/profile/index.tsx`, `mobile/src/components/ProfileBanner.tsx`, `DeleteAccountSheet.tsx`
- Modify: `mobile/app/(tabs)/_layout.tsx` (open the Profile tab with the banner when `profileComplete` is false after sign-in)
- Test: `mobile/__tests__/profile.test.tsx`

**Interfaces:**
- Consumes: Task 3 `useSession().signOut`, `friendlyMessage`.
- Produces: `getMe(): Promise<Profile>` (query key `['me']`), `updateMe(p: {displayName,city,postalCode,contactNote}): Promise<Profile>`, `deleteMe(password: string): Promise<void>` (DELETE with body `{password}`).

- [ ] **Step 1: Write failing tests:** `shows the profile and a "complete your profile" banner when profileComplete is false`; `saving PUTs the four fields trimmed, and the banner disappears`; `client validation blocks empty name/city/postal code and over-limit values (60/100/20/200) without calling the API`; `logout clears the session`; `delete account asks for the password, calls DELETE with it, and signs out on 204`; `wrong password shows the INVALID_CREDENTIALS message and does not sign out`; `ACCOUNT_HAS_ACTIVE_EXCHANGE shows "Finish or cancel your accepted exchanges first" and does not sign out`.
- [ ] **Step 2:** Run `npm test -- profile`. Expected: FAIL.
- [ ] **Step 3:** Implement. A user whose profile is incomplete lands on the Profile tab right after register.
- [ ] **Step 4:** Run `npx tsc --noEmit && npm test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(mobile): profile editing, completion banner and account deletion`.

---

### Task 11: README, run-through and polish

**Files:**
- Create: `mobile/README.md`
- Modify: root `README.md` (short "Mobile app" section linking to it)

- [ ] **Step 1:** Write `mobile/README.md`: prerequisites (Node 20.19+, Expo Go on the phone, phone and PC on the same Wi-Fi); start the backend (`docker compose up -d db` then `mvn spring-boot:run`, or the full compose); find the PC's LAN IP; allow the Windows firewall prompt for port 8080; copy `.env.example` to `.env` with `EXPO_PUBLIC_API_URL=http://<LAN-IP>:8080`; `npm install`, `npx expo start`, scan the QR code; `npm test`. Add `mobile/.env.example`.
- [ ] **Step 2:** Add an acceptance checklist to the README (the manual run-through): register two accounts on two devices or one device plus Swagger; complete both profiles; add books by scan and by typed ISBN; discover, search, propose, counter, accept, set a pickup note, confirm both sides, see History; delete an account.
- [ ] **Step 3:** Run `cd mobile && npx tsc --noEmit && npm test` and `mvn -B test` in the repo root. Expected: both green (backend unchanged, 401 tests).
- [ ] **Step 4:** Ask the owner to run the checklist on their phone (a real-device check this session cannot do); record what they report.
- [ ] **Step 5: Commit** `docs(mobile): setup guide and acceptance checklist`.
