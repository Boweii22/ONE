# ONE — Take the only live screen

One person owns ONE. Everyone sees the same message. Anyone can steal it.

This repository contains the native Android game, the authoritative Supabase backend, and the public web spectator. The original single-phone concept is still available as a clearly labelled fallback when cloud configuration is absent; configured builds use one shared database state across every device.

## What is real now

- Anonymous but persistent player identities.
- One atomic `take_one` database transaction with a monotonic reign sequence.
- Stale-race protection: if two players attack the same reign, only the first commits and the loser spends nothing.
- Free steals with a server-controlled cooldown.
- Revenge Tickets that are spent only when a player successfully skips their cooldown.
- Idempotent request IDs, an immutable ticket ledger, and RevenueCat webhook deduplication.
- Pre-screened message libraries, attributable message echoes, reports, rate limits, a moderation queue, and a global kill switch.
- Preset crowd reactions without an unmoderated global chat.
- Verified app/web views, active watcher heartbeats, a live Hall, and recent takeover activity.
- RevenueCat and OneSignal identities linked to the same Supabase player UUID.
- A responsive public spectator site that never invents audience numbers.

## Repository map

- `app/` — Kotlin + Jetpack Compose Android client.
- `supabase/migrations/` — schema and authoritative game functions.
- `supabase/functions/revenuecat-webhook/` — idempotent purchase-to-ticket grants.
- `supabase/functions/onesignal-takeover/` — targeted dethroned notification handler.
- `supabase/tests/` — schema and race-contract tests.
- `web/` — live public spectator built with React/Vinext.
- `DEPLOYMENT.md` — exact cloud and store setup.
- `PLAYTEST.md` — the stranger test and success metrics.

## Run Android locally

Open this folder in Android Studio and run the `app` configuration, or:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

For convenience, the local build workspace also contains these ignored artifacts:

- `ONE-debug.apk` — installable development build. It uses the labelled local demo until public Supabase values are supplied at build time.
- `ONE-release-unsigned.aab` — verified release bundle output that still requires your private Google Play upload-key signature before submission.

Without Supabase settings the app enters `LOCAL DEMO MODE`. That mode is useful for design review, but it is not presented as global multiplayer.

## Activate global mode

Create a Supabase project, enable anonymous sign-ins, then apply the migration in `supabase/migrations`. Put only public client values in your user-level Gradle properties:

```properties
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_ANON_KEY=YOUR_PUBLISHABLE_KEY
REVENUECAT_API_KEY=YOUR_PUBLIC_ANDROID_SDK_KEY
ONESIGNAL_APP_ID=YOUR_PUBLIC_ONESIGNAL_APP_ID
ONE_WEB_URL=https://YOUR_DEPLOYED_SPECTATOR_URL
```

On Windows this is normally `C:\Users\YOUR_NAME\.gradle\gradle.properties`. Never commit service-role, RevenueCat secret, OneSignal REST, or webhook secrets.

See `DEPLOYMENT.md` for the rest.

## Verify the live race contract

After deploying Supabase, run `scripts/live-race-test.mjs` with `SUPABASE_URL` and `SUPABASE_ANON_KEY` in the process environment. It creates two disposable anonymous players and sends simultaneous takeover requests against the same reign. The command fails unless exactly one player wins, the loser receives `STALE_REIGN`, the loser keeps every Revenge Ticket, and the server records the winner as the sole owner.

## Product model

The WhatsApp prototype showed that speed, retakes, stealing words, and rivalry produced the fun. Charging for every takeover would destroy that tempo. ONE therefore monetizes impatience:

- A normal steal is free when the player is charged.
- Each successful steal starts a short personal cooldown.
- One Revenge Ticket skips that cooldown immediately.
- Rewarded ads and RevenueCat packs can grant tickets after server verification.

The cooldown and protection values live in `public.app_settings`, so they can be tuned without releasing another APK.

## Safety

ONE is public user-generated content. The backend blocks obvious links, contact details, scams, and violent phrases; uncertain messages enter review instead of going live. Production operation still requires a human moderation console, published contact information, ban/appeal handling, and monitoring of the included emergency kill switch.

No hackathon result is guaranteed. This implementation makes the multiplayer, monetization, moderation, and audience claims technically demonstrable instead of simulated.
