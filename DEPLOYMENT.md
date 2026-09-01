# Deployment checklist

## 1. Supabase

1. Create a project near the first test audience.
2. In Authentication settings, enable anonymous sign-ins and CAPTCHA/rate limits before public launch.
3. Install the Supabase CLI, sign in, then run:

```bash
supabase link --project-ref YOUR_PROJECT_REF
supabase db push
supabase functions deploy revenuecat-webhook
supabase functions deploy onesignal-takeover
```

4. Set server secrets:

```bash
supabase secrets set REVENUECAT_WEBHOOK_SECRET=GENERATE_A_LONG_RANDOM_VALUE
supabase secrets set ONE_PRODUCT_TICKETS='{"one_spark":3,"one_challenger":20,"one_headliner":50}'
supabase secrets set ONESIGNAL_APP_ID=YOUR_APP_ID
supabase secrets set ONESIGNAL_REST_API_KEY=YOUR_REST_KEY
supabase secrets set TAKEOVER_HOOK_SECRET=GENERATE_ANOTHER_LONG_RANDOM_VALUE
```

5. Run `supabase test db` and confirm the race tests pass.
6. Keep service-role and database credentials out of Android, web source, screenshots, chat, and Git.

## 2. RevenueCat and Google Play

Create three consumable Play products whose RevenueCat product identifiers map to:

- `one_spark` — 3 Revenge Tickets.
- `one_challenger` — 20 Revenge Tickets.
- `one_headliner` — 50 Revenge Tickets.

Place their packages in the current Offering in that order. Configure a RevenueCat webhook pointing to:

`https://YOUR_PROJECT.supabase.co/functions/v1/revenuecat-webhook`

Set its Authorization header to `Bearer <REVENUECAT_WEBHOOK_SECRET>`.

The permanent Android application ID is `com.tomribowei.one`. Do not upload an older build under another ID.

## 3. OneSignal

Create the Android app in OneSignal, add the public App ID to Gradle properties, and configure Firebase credentials in the OneSignal dashboard. Android logs into OneSignal with the Supabase UUID, allowing the server to target the dethroned owner.

Create a Supabase Database Webhook for `INSERT` events on `public.reigns` that invokes `onesignal-takeover`. Add an `Authorization: Bearer <TAKEOVER_HOOK_SECRET>` header. The function resolves the previous owner, new handle, reign duration, and verified views from the database; do not expose the OneSignal REST key to Android.

## 4. Android configuration

Add the public values from `README.md` to your user Gradle properties. Build a signed Android App Bundle in Android Studio with **Build > Generate Signed Bundle / APK**. Preserve the upload keystore and passwords outside this repository.

Start the Play closed test with at least 15 recruited testers to maintain a buffer above Google's minimum. Keep them opted in and actively collect feedback throughout the required period.

## 5. Web spectator

Copy `web/.env.example` to `web/.env.local`, fill in the Supabase URL and publishable key, then:

```bash
cd web
npm install
npm run build
```

The web key is intentionally public and protected by function grants/RLS. Never use the service-role key in a browser variable.

## 6. Moderation before public launch

- Create a moderator-only dashboard for reviewing `messages` and `reports`.
- Publish privacy policy, community rules, safety contact, blocking, appeal, and deletion procedures.
- Test the kill switch.
- Add CAPTCHA and abuse-rate limits to anonymous sign-up.
- Run adversarial message and bot-view tests.
