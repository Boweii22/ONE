# ONE — Shipaton pitch

## One sentence

ONE is a live global game with exactly one screen: anyone can take it for free, everyone sees the takeover instantly, and the previous owner gets one chance to fight back.

## The 20-second opening

“Every social network has infinite posts, so nothing feels important. ONE has one post for the entire planet. This is live — that person owns it, these people are watching it, and I’m about to steal it from my phone.”

Perform a real takeover on one physical phone while the public web mirror is visible on a second screen. Stop talking. Let the owner, colour world, live counter, activity feed, and dethroned push change in front of the judges.

## The mechanic

- Taking ONE is free. There is no paywall between a new user and the moment that makes the product fun.
- After taking it, an account has a short cooldown before taking it again.
- A RevenueCat consumable **Revenge Ticket** skips that cooldown. It is consumed only after the server successfully awards ownership; losing a simultaneous race costs nothing.
- Owners can publish an approved original message or instantly “echo” the current message with attribution.
- Spectators use preset reactions, not an unmoderated global chat.
- Every app and browser reads one server-authoritative reign. There is no simulated winner and no client-side balance.

## Why people share it

The product creates a tiny live event instead of another feed. A takeover changes the same object for every viewer. The displaced owner gets a push with their exact reign and a deep link to retaliate. Every owner can share a receipt that opens the live zero-install web mirror, so sharing increases the audience they briefly controlled.

## What changed after testing

The original prototype treated ONE like a rising-price billboard. A 15-person WhatsApp test produced roughly 50 attempts, seven active participants, 15 ownership announcements, and one intense two-person battle — but no organic spread. The strongest behaviour was stealing, defending, and announcing ownership, not bidding.

That evidence caused the central pivot: free takeovers for reach, a cooldown for fairness, and optional Revenge Tickets for the emotional rematch. Monetisation now accelerates a fun action instead of being the entry fee.

## Sponsor and award story

- **RevenueCat / HAMM:** consumable Revenge Tickets are the game economy. RevenueCat purchase webhooks credit an append-only server ledger; the client never grants currency.
- **OneSignal Boost:** the signature re-engagement loop is the dethroned push — who took it, how long the reign lasted, and one tap back to the live fight.
- **Build in Public:** the test failure, public metrics, pricing pivot, race-condition work, and daily real-device demos form an honest development story rather than a cosmetic launch thread.
- **Buzziest Launch / Best Vibes:** every takeover is a shareable live event with a theatrical visual identity, a public mirror, reactions, ownership receipts, and a hall of fame.
- **Design:** the interface has one dominant object, one obvious action, meaningful motion, accessible contrast, and no dashboard clutter.
- **Peace:** approved charity and public-interest messages can earn the planet’s only screen without changing the game’s rules.
- **Kotlin:** the production Android client is native Kotlin and Jetpack Compose.

## Technical proof

- Supabase/Postgres serialises takeovers with a transaction lock, expected sequence number, idempotency key, and exactly one current-reign constraint.
- Anonymous Supabase identities are linked to RevenueCat and OneSignal using the same user UUID.
- Row-level security blocks direct balance and ownership edits.
- A real heartbeat-based watcher count and deduplicated view events replace invented counters.
- RevenueCat and OneSignal secrets live only in server functions.
- The public Next.js mirror reads the same state as Android.
- Reports, approved message libraries, owner blocking, and server-side moderation are part of the launch path.

## Honest judging disclosure

The repository contains a real multi-device backend, Android client, public spectator site, webhook functions, and concurrency contract tests. Without Supabase credentials the APK deliberately enters a labelled local demo mode so the UI remains inspectable; all launch claims and the judging demo must use the configured cloud mode.

## 60-second demo

1. Show Phone A owning ONE and the same reign on the browser.
2. Take ONE from Phone B for free.
3. Show Phone A receive the dethroned push and the browser update.
4. Tap a reaction from the browser or another client and show it appear live.
5. Try an immediate retake on Phone A: show the cooldown and one-ticket skip.
6. Trigger simultaneous take requests: show one winner, one stale result, and no charge to the loser.
7. End on the real metrics and shareable live URL.

## Closing line

“The internet has infinite content and almost no shared moments. ONE gives the whole world one screen — and gives everyone the power to steal it.”
