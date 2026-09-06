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
0. Apply `202609060001_unique_reactions.sql` once, in full. It is transactional and rerunnable. It keeps historical rows, counts unique voters and prevents repeated emoji reactions in the same reign. This new migration has NOT been applied remotely by Codex.
1. Verify identity ID, handle, reigns, Hall, and balance before/after Google linking, Play update, process death, Google cancellation, refresh failure and cross-device restore. Do not uninstall an unlinked identity to test upgrades.
2. Android challenge URL handling exists, but automatic App Link opening needs the website's `/.well-known/assetlinks.json` using the Play signing SHA-256. Without domain verification Android may keep opening the website.

## Remaining integrations — do not advertise as shipped
- See `MONETIZATION-SETUP.md` for the prepared AdMob adapter and disabled authenticated checkout endpoint. The owner confirmed on September 6 that AdMob IDs and the RevenueCat Funnel have not yet been created. Activation and end-to-end monetization remain pending.
- AdMob app ID, rewarded unit ID, consent setup, RevenueCat AdMob adapter and SSV rule. Configure https://api.revenuecat.com/v1/incoming-webhooks/admob-ssv-rewarded.
- Use a separate verified ad-skip reward, not client-side ONE Credits. The production economy is fixed in `ECONOMY.md`: takeovers are free when ready and one credit skips one active cooldown. A verified RevenueCat reward still needs an idempotent server redemption; a local earned callback is NOT sufficient.
- Daily ad cap is configurable (default 3) but not enforced by an ad flow yet because that flow is not integrated.
- RevenueCat Funnel URL, Stripe/RevenueCat product mapping and authenticated app-user identity handoff. Web purchases must be reconciled server-side exactly once. Do not add a mobile external-purchase CTA without checking Play policy/region eligibility.
- Update privacy policy and Play Data Safety for optional Google authentication, public photos, country and advertising before rollout.

## Gameplay migration
Three introductory wins: first and second impose no cooldown, third starts 90 seconds; fourth starts 240 seconds; subsequent wins start 720 seconds. Resets after 1800 seconds since last win (not last screen tap). Settings are server-controlled. Reaction cap raised from 3 to 20 per ten seconds under the existing per-user lock.

## References
- https://supabase.com/docs/guides/auth/auth-anonymous
- https://developer.android.com/identity/sign-in/credential-manager-siwg
- https://www.revenuecat.com/docs/ad-monetization/rewards
- https://www.revenuecat.com/docs/tools/funnels
