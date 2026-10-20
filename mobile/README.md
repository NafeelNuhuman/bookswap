# BookSwap mobile app

A small Expo (React Native, TypeScript) client for the BookSwap API: your library with covers, adding books by barcode
or ISBN, discovery and search, proposing and answering swaps, history, and your profile. All business rules live in
the backend; the app only shows what the API allows.

Design: `docs/superpowers/specs/2026-10-20-mobile-client-design.md`.

## What you need

- Node 20.19 or newer (tested with Node 24)
- The **Expo Go** app on your Android phone
- The phone and the computer on the same Wi-Fi network
- Docker, Java 21 and Maven for the backend (see the root `README.md`)

## Run it

1. Start the backend from the repository root:

   ```bash
   docker compose up -d db
   mvn spring-boot:run
   ```

   The first time, Windows asks whether Java may accept connections on the network. Allow it on private networks,
   otherwise the phone cannot reach port 8080.

2. Find the computer's address on the Wi-Fi network (`ipconfig` on Windows, look for the IPv4 address, for example
   `192.168.1.20`). Check from the phone's browser that `http://192.168.1.20:8080/swagger-ui.html` opens.

3. Tell the app where the API is:

   ```bash
   cd mobile
   cp .env.example .env      # then edit EXPO_PUBLIC_API_URL=http://<your-ip>:8080
   npm install
   npx expo start
   ```

4. Open Expo Go on the phone and scan the QR code shown in the terminal. After changing `.env`, restart
   `npx expo start` (use `--clear` if the old address sticks).

If the app shows "API address not set", step 3 was skipped. If every request says it can't reach the server, the
address is wrong or the firewall is blocking port 8080.

## Check the code

```bash
npm run typecheck
npm test
```

CI runs both on every push.

## Manual acceptance run-through

You need two accounts, so use the phone for one and Swagger UI (or a second phone) for the other.

- [ ] Register on the phone; you land on Profile with a "complete your profile" banner. Fill in name, city and postal code and save; the banner disappears.
- [ ] Library: add a book by scanning its barcode, and another by typing its ISBN. Covers show. A made-up ISBN offers title and author fields.
- [ ] Open a book: change its condition, list it, unlist it. Remove one (it asks first).
- [ ] With the second account, add and list a book in the same city.
- [ ] Discover: the other account's book appears with the owner's name and city. Search by title and by ISBN.
- [ ] Propose a swap: the tapped book is pre-selected, tick one of yours, add another of theirs from their shelf, add a message, send. It shows under Exchanges, Outgoing.
- [ ] As the other account, answer it: counter-offer once, then accept the counter as the first account. It appears under Active.
- [ ] Open the accepted exchange: the other person's email and contact note show. Save a pickup note.
- [ ] Confirm on one account: you see "Waiting for the other person", and you cannot cancel. The other account can still cancel; try it on a second exchange to see both books return to the library as Available.
- [ ] Confirm on both accounts: the exchange moves to History; the books changed owners (they are Unlisted in the new library).
- [ ] Profile: log out and back in. Delete the second account (the password is asked; it is refused while an exchange is accepted). The first account still sees the exchange in History with "Deleted user".
