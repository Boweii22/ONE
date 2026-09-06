# ONE monetization activation

Prepared, not active: RewardedAds.kt loads consent, requests an AdMob rewarded ad through RevenueCat, and reports only a server-verified reward. The checkout endpoint validates a Supabase user and constructs an identified RevenueCat Funnel URL. Neither is exposed to users yet.

## AdMob
1. Add ONE as an Android app with package com.tomribowei.one. Link its Play listing.
2. Create a Rewarded ad unit called Cooldown skip. Send the app ID (contains ~) and ad unit ID (contains /).
3. Configure Privacy & messaging for applicable consent regions. Enable impression-level ad revenue.
4. Connect AdMob in RevenueCat. Configure this rewarded unit's server-side verification URL as https://api.revenuecat.com/v1/incoming-webhooks/admob-ssv-rewarded.
5. Create a dedicated skip currency reward, separate from ticket purchases. Do not enable the app button until server redemption is implemented and tested.

Remaining engineering: server-verified redemption from the RevenueCat reward into ONE's cooldown, idempotency, daily cap of 3, reservation before showing an ad, identity matching, ad dismissal/timeout recovery, and privacy-options entry. Test real ad units with test devices; Google sample units cannot test RevenueCat SSV.

## RevenueCat Funnels
1. Connect Stripe Billing (or RevenueCat Billing) as a web configuration in the existing ONE project.
2. Create/import web products for Spark (3 tickets), Challenger (20), and Headliner (50). Add them to an offering.
3. In Funnels, create a purchase flow with the offering and a confirmation page. Publish a sandbox URL first and send it to me, along with exact web product IDs. Send the production URL after sandbox testing passes.
4. Configure the existing Supabase revenuecat-webhook function with the webhook authorization secret privately and the exact product-to-ticket mapping in ONE_PRODUCT_TICKETS. Include Android IDs too; the current default aliases must be verified against the actual dashboard products.
5. Finish Google sign-in on the website so checkout uses the same Supabase user ID as Android. POST /api/checkout requires that authenticated access token. Never take the account ID from an input field.
6. Test a sandbox payment, cancelled payment, duplicate webhook, and delayed webhook. Verify the correct account receives tickets exactly once. Only then set ONE_WEB_CHECKOUT_ENABLED=true and REVENUECAT_FUNNEL_URL to the production base funnel URL.

The Android app does not currently link to web checkout. A mobile purchase link requires a separate review of the applicable Play program and region before enabling it.

Sources: https://www.revenuecat.com/docs/ad-monetization/rewards and https://www.revenuecat.com/docs/tools/funnels/deploying-funnels
