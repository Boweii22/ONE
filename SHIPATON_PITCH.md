# ONE — Shipaton pitch

## One sentence

ONE is a live global game with exactly one screen: anyone can take it for free, everyone sees the takeover instantly, and the previous owner gets one chance to fight back.

## The 20-second opening

“Every social network has infinite posts, so nothing feels important. ONE has one post for the entire planet. This is live — that person owns it, these people are watching it, and I’m about to steal it from my phone.”

Perform a real takeover on one physical phone while the public web mirror is visible on a second screen. Stop talking. Let the owner, colour world, live counter, activity feed, and dethroned push change in front of the judges.

## The mechanic

- Taking ONE is free. There is no paywall between a new user and the moment that makes the product fun.
- Every player holds two takes that refill on an escalating ladder — the wait grows the more you take, so the cost of playing is time, not money.
- One ONE Credit skips that wait instantly. It is consumed only after the server successfully awards ownership; losing a simultaneous race costs nothing.
- A rewarded ad grants a take directly and never touches the credit ledger, so watching an ad can never substitute for a sale — it serves players who will never pay, without cannibalising the ones who will.
- Owners can publish an approved original message or instantly “echo” the current message with attribution.
- Spectators use preset reactions, not an unmoderated global chat.
- Every app and browser reads one server-authoritative reign. There is no simulated winner and no client-side balance.

## Visual direction

- The whole interface is one theatrical stage, not a dashboard: a single dark control-room canvas (near-black ink, raised panels) with one accent colour that belongs to whoever currently owns the screen. Take it, and the entire app and the public web mirror shift into your colour within seconds — there is no settings toggle for this, it is a direct consequence of winning.
- Type carries the tone: oversized black condensed display type for the owner's message (auto-sized to the real content, never clipped or dwarfed), monospace for every stat and timestamp, so it reads like a broadcast ticker crossed with a stolen billboard rather than a social feed.
- Motion marks every state change instead of decorating the UI: winning a takeover triggers a real confetti burst and a spring-in seal, losing the screen slides a "dethroned" banner in with a haptic buzz over a torn-texture background, and the live website's marquee and accent glow move continuously to sell "this is happening right now."
- Memorable because it commits to scarcity: there is exactly one message, one owner, one colour, at any moment, for the entire planet — the visual identity has nowhere to hide that fact.

## Why people share it

The product creates a tiny live event instead of another feed. A takeover changes the same object for every viewer. The displaced owner gets a push with their exact reign and a deep link to retaliate. Every owner can share a receipt that opens the live zero-install web mirror, so sharing increases the audience they briefly controlled.

## What changed after testing

Before writing any code, I ran the game manually in a WhatsApp group. Seven people (Sammy, Roy, Shima, Ameen, Ehizojie, Tokoni, Kerry) took the screen roughly 58 times between 19:23 and 19:51 on 1 September — 28 minutes, no points, no prize, nothing at stake. At any per-take price, that pace is unpayable: charging per action was dead before it was built. The same test also showed 15 ownership announcements and one genuinely intense two-person battle, with almost no engagement with the idea of a rising bid price itself — the strongest behaviour was stealing, defending, and announcing ownership, not bidding.

That evidence caused the central pivot: free takeovers for reach, a refill wait for fairness, and optional ONE Credits for the emotional rematch. Monetisation now accelerates a fun action instead of being the entry fee.

## Pricing rationale

A credit's real value scales with the wait it removes, not with a fixed price tag. `take_ladder_seconds` escalates across five steps — 60s, 180s, 480s, 1200s, 1800s — so the same one-credit skip removes anywhere from one minute to half an hour depending on how far up the ladder a player has climbed. The price was never set by guessing what a player's time is worth in the abstract; it's set by the mechanic itself, because a player who's played enough to reach the expensive end of the ladder is exactly the player for whom a credit is worth the most.

Spark (3 credits, £0.99 — £0.33/credit) is priced for someone testing their first skip. Headliner (50 credits, £9.99 — £0.20/credit) rewards the player already committed enough to be climbing the ladder repeatedly. The packs aren't three arbitrary price points; they're the same mechanic sold at the two ends of who's actually buying it.

**The ad-cannibalisation firewall.** Rewarded ads and ONE Credits both remove the same wait, but they never touch the same ledger — a verified ad grants a take directly through the backend-only `grant_verified_ad_bypass`, which never writes to `ticket_ledger`. A free player watching ads can never earn their way into paid currency, and a paying player never gets undercut by a free alternative to the thing they paid for. Most apps that add both ads and IAP let one quietly erode the other; this one is structurally incapable of it.

**Three RevenueCat systems, one coherent stack.** Not one integration point but three: RevenueCat-tracked in-app purchases for ONE Credits, RevenueCat's AdMob SSV integration for server-verified rewarded ads (confirmed live — a real ad has actually paid out end-to-end, tracked under the `cooldown_skip` placement), and RevenueCat Funnels with Stripe for the web checkout path. All three feed the same economy without conflicting with each other, and all three are RevenueCat doing the verification and reconciliation work, not homegrown trust.

## Sponsor and award story

- **RevenueCat / HAMM:** taking the screen is always free; money only ever buys you out of the wait, never the action itself. This monetises impatience at the exact moment it peaks, instead of taxing the part of the game that makes people play. ONE Credits (RevenueCat IAP) and rewarded ads serve the same function for different people — those who will pay, and those who never will — and ad rewards grant takes directly and never touch the credit ledger, so ads cannot cannibalise sales. A web funnel through RevenueCat Funnels and Stripe adds a third path at better margin, live now at oneis.live.
- **OneSignal Boost:** the signature re-engagement loop is the dethroned push — who took it, how long the reign lasted, and one tap back to the live fight.
- **Build in Public:** the test failure, public metrics, pricing pivot, race-condition work, and daily real-device demos form an honest development story rather than a cosmetic launch thread.
- **Buzziest Launch / Best Vibes:** every takeover is a shareable live event with a theatrical visual identity, a public mirror, reactions, ownership receipts, and a hall of fame.
- **Design:** the interface has one dominant object, one obvious action, meaningful motion, and no dashboard clutter — backed by real accessibility craft, not just a look: a visible focus-ring on every interactive element, a working skip-to-content link on the spectator site, full `prefers-reduced-motion` support, and `aria-live`/`aria-label` coverage on the live stage and moderation console.
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
5. Try an immediate retake on Phone A: show the refill wait and a one-credit skip.
6. Trigger simultaneous take requests: show one winner, one stale result, and no charge to the loser.
7. End on the real metrics and shareable live URL.

## Best Game Award submission

The category's four required elements, answered directly:

1. **Core gameplay loop** — see "The mechanic" above: take the free screen, hold it while everyone watches, get dethroned or successfully defend, repeat.
2. **Visual direction, tone, memorable qualities** — see "Visual direction" above.
3. **Monetisation fits the genre** — see the RevenueCat / HAMM bullet above: the game never charges for the core action, only for skipping the wait between actions.
4. **Demonstration video of actual gameplay on the real device** — not yet recorded. This is the one item on this list that only exists once you actually film it; nothing below is a substitute for that.

### Video shot list

Film on the real device, in the actual production build, not the demo/mock UI. One continuous story beats a montage:

1. Open the app straight to the LIVE tab — show a real owner, real message, real accent colour, real watcher count.
2. Take the screen. Let the take-over motion (confetti, seal) actually play on camera, uncut.
3. Cut to (or split-screen with) the web mirror at oneis.live updating to your new reign and colour within seconds.
4. Let someone else take it back from a second device — show the "dethroned" banner and haptic moment landing on the first phone.
5. Show one refill-wait skip: spend a credit or watch a rewarded ad to take again immediately.
6. End on the Hall/leaderboard tab or a shared receipt link, closing the loop back to "everyone saw this happen."

Keep it under the platform's stated video length, narrate only the first two lines of the "20-second opening" above, then let the product play out on screen without a voiceover talking over it.

## Catvertising Award submission

**ONE never interrupts gameplay with advertising. Advertising only appears when the game has already asked you to wait.**

**How ONE implements RevenueCat Ads:** a single rewarded placement — "WATCH AN AD" — offered only at the one moment a player is already blocked: mid-wait, inside the same refill-skip choice sheet where they'd otherwise spend a ONE Credit. It's never a mandatory watch, never a pre-roll, and never shown to a player who's free to take the screen right now. The reward is verified end to end on the server: AdMob's server-side verification confirms the ad to RevenueCat, RevenueCat credits an `ADTAKE` virtual currency, and ONE's backend spends that currency through RevenueCat's API before granting the take. The database function that grants an ad take can only be called by that backend — the client cannot claim a watch, so a modified app gets nothing.

**Placement reasoning:** the ad and the paid option sit side by side in the exact same UI, at the exact same decision point, because they're solving the exact same problem for two different players — impatience. A player with money skips the wait with a credit; a player without it skips the wait with thirty seconds of attention. Neither path touches the other's economy.

**Coordination with other monetisation:** this is the part judges are asked to weigh most — ad rewards grant a take directly and never touch the ONE Credits ledger. There's no shared currency, no conversion rate, and no way to stack one into the other. That's a deliberate constraint, not an omission: an ad that could ever substitute for a sale would let players farm free credits by watching ads, which would quietly cannibalise the paying side of the same economy. Keeping the two paths structurally separate is what lets both exist without one eating the other.

**Early signal:** ONE records three counters for this placement (offers shown, ads completed, and takeovers within five minutes of a completed ad), readable from the `ad_funnel_summary` view. Fill in the real numbers from that view at submission time; do not estimate them. Design-wise, the ad is also capped at a small number of ad-bypasses per player per day (server-enforced, not just a client-side limit), so the mechanic stays a genuine convenience rather than a grind loop a player could lean on instead of ever paying — the daily cap is itself part of the "doesn't degrade the experience" argument, not just an anti-abuse measure.

## Closing line

“The internet has infinite content and almost no shared moments. ONE gives the whole world one screen — and gives everyone the power to steal it.”
