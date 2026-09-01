# ONE live verification

Verified against the linked Supabase project on 1 September 2026.

## Live now

- The production database migration is deployed.
- Anonymous authentication creates real Supabase users and sessions.
- The Android release build refuses to compile without a live Supabase URL and public key.
- `get_one_state`, presence, views, reactions, reports, profiles, leaderboards, and the server-authoritative `take_one` transaction are deployed.
- `revenuecat-webhook` and `onesignal-takeover` Edge Functions are deployed and require authorization.

## Race proof

`scripts/live-race-test.mjs` created two real anonymous users and challenged the same reign simultaneously. The test passed only after proving:

- exactly one request won;
- the losing request returned `STALE_REIGN`;
- the losing player did not spend a Revenge Ticket;
- the global sequence increased exactly once; and
- the final server owner matched the winner.

Observed result:

```text
LIVE_RACE_TEST=PASS
START_SEQUENCE=1
FINAL_SEQUENCE=2
LOSER_CODE=STALE_REIGN
LOSER_TICKETS_BEFORE=3
LOSER_TICKETS_AFTER=3
```

## Provider setup still required

The core multiplayer product is live. RevenueCat purchases and OneSignal takeover notifications cannot deliver real money or pushes until their provider-side apps, products, and private secrets are configured. Those integrations must never be represented as operational before that final setup is completed.
