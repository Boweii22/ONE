# ONE identity and gameplay update — not yet release-ready

Implemented in Android:
- Country-only ISO picker. Device locale suggestion, explicit toggle, no GPS or city.
- Google Credential Manager linking using Supabase link_identity=true, nonce verification and same-user-ID check. Restore is separate and explicitly confirmed; it does not merge balances.
- Stored session credentials are retained on refresh rejection. No automatic fallback signup after that failure.
- Recovery-code creation UI removed. Existing issued codes remain valid on the backend during migration to avoid locking out existing owners.
- Map fills its message container; singular person label; visible How to play entry.
- Challenge links resolve the current owner from the server.

## Must configure before release
1. Enable Google provider and manual identity linking in Supabase. Configure the Google web OAuth client and Android OAuth client for com.tomribowei.one with Play App Signing SHA-1.
2. Add GOOGLE_WEB_CLIENT_ID (public identifier, not a client secret) to local.properties. It is currently missing, so the app disables the button with an honest setup message.
3. Apply migrations 202609050001 through 202609050003 in order. They have not been remotely validated or applied: current Supabase CLI access returns 403.
4. Verify identity ID, handle, reigns, Hall, and balance before/after Google linking, Play update, process death, Google cancellation, refresh failure and cross-device restore. Do not uninstall an unlinked identity to test upgrades.
5. Android challenge URL handling exists, but automatic App Link opening needs the website's /.well-known/assetlinks.json using the Play signing SHA-256. Without domain verification Android may keep opening the website.

## Remaining integrations — do not advertise as shipped
- AdMob app ID, rewarded unit ID, consent setup, RevenueCat AdMob adapter and SSV rule. Configure https://api.revenuecat.com/v1/incoming-webhooks/admob-ssv-rewarded.
- Use a separate verified skip reward, not client-side credits. ONE uses its own server ticket ledger, so a verified RevenueCat reward still needs an idempotent bridge to that ledger or a server-verified skip redemption. A local earned callback is NOT sufficient.
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
