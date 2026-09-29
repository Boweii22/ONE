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

## Status update (September)
Both AdMob rewarded ads and the RevenueCat web funnel are now live, not pending. `local.properties` has real AdMob dashboard IDs; the challenge sheet's "WATCH AN AD" choice and the website's Google sign-in + "BUY ONE CREDITS" checkout are both active in production (oneis.live), with one real Stripe purchase confirmed end to end.

The ad-skip system described below was superseded by `bypass_take_refill` (`supabase/migrations/202609090001_take_balance_system.sql`) — `grant_ad_skip` and the `ad_skip_available` profile flag it references no longer exist; see `MONETIZATION-SETUP.md` for the current design. Ad takes are now granted only by `grant_verified_ad_bypass` (migration `202609260001_verified_ad_bypass.sql`), which is executable by the service role only. `bypass_take_refill` refuses method `'ad'` from clients and serves credits only. The grant is idempotent (deduped on `take_bypass_requests.request_id`), capped via `app_settings.take_ad_bypass_daily_cap` (default 2/UTC day), and only fires when a refill wait is actually in progress.

On server verification: an earlier version of this note said the client's `verifiedReward` check was sufficient. That was wrong — the RPC trusted whatever method string the client sent. Now: AdMob SSV → RevenueCat verifies and credits 1 `ADTAKE` virtual currency → the `redeem-ad-reward` Edge Function spends 1 `ADTAKE` via RevenueCat REST API v2 (idempotent per request) → only then calls `grant_verified_ad_bypass`; on any failure it refunds the unit. Requires the AdMob SSV callback URL, the RevenueCat Ads → Rewards rule granting `ADTAKE`, and the function secrets in `MONETIZATION-SETUP.md`. Not yet tested end to end on a device. Counters: `select * from public.ad_funnel_summary;` (migration `202609260002_ad_funnel_counters.sql`).

## Remaining
- RevenueCat Funnel URL, Stripe/RevenueCat product mapping and Google provider enablement in Supabase Auth are done. Continue reconciling web purchases server-side exactly once as volume grows. Do not add a mobile external-purchase CTA without checking Play policy/region eligibility.
- Update privacy policy and Play Data Safety for optional Google authentication, public photos, country and advertising before rollout, if not already covered by the current privacy policy.

## Gameplay migration
Three introductory wins: first and second impose no cooldown, third starts 90 seconds; fourth starts 240 seconds; subsequent wins start 720 seconds. Resets after 1800 seconds since last win (not last screen tap). Settings are server-controlled. Reaction cap raised from 3 to 20 per ten seconds under the existing per-user lock.

## References
- https://supabase.com/docs/guides/auth/auth-anonymous
- https://developer.android.com/identity/sign-in/credential-manager-siwg
- https://www.revenuecat.com/docs/ad-monetization/rewards
- https://www.revenuecat.com/docs/tools/funnels
