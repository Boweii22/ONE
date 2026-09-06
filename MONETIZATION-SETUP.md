# ONE monetization activation

Prepared, not active: RewardedAds.kt loads consent, requests an AdMob rewarded ad through RevenueCat, and reports only a server-verified reward. It is now wired into a "WATCH AN AD" choice in the challenge sheet and a `grant_ad_skip` RPC (separate `ad_skip_available` flag on the profile, capped at 3/UTC day, consumed only by a successful `take_one`) — but it stays hidden until `ADMOB_APP_ID`/`ADMOB_REWARDED_UNIT_ID` are set in `local.properties`. The checkout endpoint validates a Supabase user and constructs an identified RevenueCat Funnel URL; the website now has a Google sign-in flow (Supabase OAuth, `/auth/v1/authorize?provider=google`) and a "BUY ONE CREDITS" button that calls it, but the endpoint still 503s until `REVENUECAT_FUNNEL_URL`/`ONE_WEB_CHECKOUT_ENABLED` are set and the Google provider is enabled in the Supabase Auth dashboard. Neither is exposed to real users yet.

## AdMob
1. Add ONE as an Android app with package com.tomribowei.one. Link its Play listing.
2. Create a Rewarded ad unit called Cooldown skip. Send the app ID (contains ~) and ad unit ID (contains /).
3. Configure Privacy & messaging for applicable consent regions. Enable impression-level ad revenue.
4. Connect AdMob in RevenueCat. Configure this rewarded unit's server-side verification URL as https://api.revenuecat.com/v1/incoming-webhooks/admob-ssv-rewarded.
5. Create a dedicated `skip` reward, separate from ONE Credits. An ad must never mint purchasable wallet balance. Do not enable the app button until server redemption is implemented and tested.

Done: idempotent redemption RPC, daily cap of 3, consumption tied to a successful takeover (not merely opening the challenge sheet), and a failed-load fallback to the normal credit/wait choices (see `RewardedAds.kt`'s existing status callback). Remaining: the actual AdMob/RevenueCat SSV bridge for server verification — right now `rewardVerificationCompleted`'s client-observed `verifiedReward` is what triggers `grant_ad_skip`, which is fine for the RevenueCat AdMob adapter's own verification but has no independent server-side check yet. Test real ad units with test devices; Google sample units cannot test RevenueCat SSV.

## RevenueCat Funnels
1. Connect Stripe Billing (or RevenueCat Billing) as a web configuration in the existing ONE project.
2. Follow `ECONOMY.md`: create/import Spark (3 ONE Credits), Challenger (20), and Headliner (50). One credit buys one successful cooldown skip; a normal takeover is free. Add them to a `credits` offering. Keep any already-reserved `one_tickets_*` Android IDs as legacy aliases rather than recreating them.
3. In Funnels, create a purchase flow with the offering and a confirmation page. Publish a sandbox URL first and send it to me, along with exact web product IDs. Send the production URL after sandbox testing passes.
4. Configure the existing Supabase `revenuecat-webhook` function with the webhook authorization secret privately and an exact product-to-credit map in `ONE_PRODUCT_CREDITS`. Copy every Android and web product ID from the dashboards. The function now fails closed when this map is absent instead of trusting aliases.
5. The website's Google sign-in (Supabase OAuth) and POST /api/checkout (which requires that authenticated access token) are now implemented in code. You still need to enable the Google provider for this Supabase project in Authentication → Providers, and add this site's URL to the provider's authorized redirect URIs. Never take the account ID from an input field.
6. Test a sandbox payment, cancelled payment, duplicate webhook, and delayed webhook. Verify the correct account receives credits exactly once. Only then set `ONE_WEB_CHECKOUT_ENABLED=true` and `REVENUECAT_FUNNEL_URL` to the production base funnel URL.

The Android app does not currently link to web checkout. A mobile purchase link requires a separate review of the applicable Play program and region before enabling it.

Sources: https://www.revenuecat.com/docs/ad-monetization/rewards and https://www.revenuecat.com/docs/tools/funnels/deploying-funnels
