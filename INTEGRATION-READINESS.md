# ONE identity and gameplay update — release checklist

Implemented in Android:
- Country-only ISO picker. Device locale suggestion, explicit toggle, no GPS or city.
- Google Credential Manager linking using Supabase link_identity=true, nonce verification and same-user-ID check. Restore is separate and explicitly confirmed; it does not merge balances.
- Stored session credentials are retained on refresh rejection. No automatic fallback signup after that failure.
- Recovery-code creation UI removed. Existing issued codes remain valid on the backend during migration to avoid locking out existing owners.
- Map fills its message container; singular person label; visible How to play entry.
- Challenge links resolve the current owner from the server.

## Completed configuration
- Google provider and manual identity linking are enabled in Supabase. Google web and Android OAuth clients are configured for `com.tomribowei.one`.
- `GOOGLE_WEB_CLIENT_ID` is present in local.properties. It is public and is not a client secret.
- Migrations `202609050001` through `202609050003` have been applied by the project owner.

## Must verify before release
0. Apply `202609060001_unique_reactions.sql` and `202609060002_ad_skip_rewards.sql` once, in full, in that order. Both are transactional and rerunnable. The first keeps historical rows, counts unique voters and prevents repeated emoji reactions in the same reign; the second adds the ad-skip columns/RPC described below. Neither has been applied remotely by Codex.
1. Verify identity ID, handle, reigns, Hall, and balance before/after Google linking, Play update, process death, Google cancellation, refresh failure and cross-device restore. Do not uninstall an unlinked identity to test upgrades.
2. Android challenge URL handling exists, but automatic App Link opening needs the website's `/.well-known/assetlinks.json` using the Play signing SHA-256. Without domain verification Android may keep opening the website.

## Remaining integrations — do not advertise as shipped
- See `MONETIZATION-SETUP.md`. The owner confirmed on September 6 that AdMob IDs and the RevenueCat Funnel have not yet been created. Both the rewarded-ad UI (challenge sheet "WATCH AN AD" choice) and the website's Google sign-in + "BUY ONE CREDITS" checkout button now exist in code, gated inert behind those missing dashboard IDs. Activation and end-to-end monetization remain pending.
- AdMob app ID, rewarded unit ID, consent setup, RevenueCat AdMob adapter and SSV rule. Configure https://api.revenuecat.com/v1/incoming-webhooks/admob-ssv-rewarded.
- Ad skips use a separate `ad_skip_available` flag on the profile (migration `202609060002_ad_skip_rewards.sql`), never client-side ONE Credits, per `ECONOMY.md`. `grant_ad_skip` is idempotent and capped at 3/UTC day; `take_one` consumes the flag only on a successful takeover. This migration has NOT been applied remotely yet — run `supabase db push`.
- The RevenueCat AdMob adapter's own client-observed reward verification (`rewardVerificationCompleted`) is what triggers `grant_ad_skip` today. There is still no independent server-side AdMob SSV check on our backend.
- RevenueCat Funnel URL, Stripe/RevenueCat product mapping and Google provider enablement in Supabase Auth. Web purchases must be reconciled server-side exactly once. Do not add a mobile external-purchase CTA without checking Play policy/region eligibility.
- Update privacy policy and Play Data Safety for optional Google authentication, public photos, country and advertising before rollout.

## Gameplay migration
Three introductory wins: first and second impose no cooldown, third starts 90 seconds; fourth starts 240 seconds; subsequent wins start 720 seconds. Resets after 1800 seconds since last win (not last screen tap). Settings are server-controlled. Reaction cap raised from 3 to 20 per ten seconds under the existing per-user lock.

## References
- https://supabase.com/docs/guides/auth/auth-anonymous
- https://developer.android.com/identity/sign-in/credential-manager-siwg
- https://www.revenuecat.com/docs/ad-monetization/rewards
- https://www.revenuecat.com/docs/tools/funnels
