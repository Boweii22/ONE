# Authoritative backend contract

The Android app and web page are displays and input devices. Neither client decides ownership, cooldown completion, ticket spending, view totals, or moderation state.

## Takeover invariant

`public.take_one(message_id, expected_sequence, request_id)` is the only ownership mutation.

1. Require a Supabase-authenticated player.
2. Insert the idempotency key.
3. Acquire one transaction-scoped advisory lock for ONE.
4. Lock the current reign and compare its sequence with the client expectation.
5. Reject stale races before any ticket mutation.
6. Verify the approved message, account state, protection window, and cooldown.
7. Spend one ticket only when the cooldown is active.
8. End the old reign and append exactly one new reign in the same transaction.
9. Record the result against the request ID so retries return the same outcome.

A partial unique index enforces exactly one unfinished reign even if application code is wrong.

## Ticket invariant

Client purchase callbacks never grant spendable tickets. RevenueCat calls `revenuecat-webhook`, which:

- authenticates with a dedicated webhook secret;
- maps configured product IDs to ticket amounts;
- inserts the RevenueCat event ID once;
- updates the profile and immutable ledger in one database call.

The client refreshes its server balance after purchasing.

## Audience invariant

Authenticated app and web sessions send heartbeats. A viewer counts at most once per reign, while `watching now` includes heartbeats from the previous 20 seconds. This is intentionally conservative and documented in the product.

## Safety invariant

Only approved message IDs can enter a reign. Reports never block the takeover transaction. Moderators can review uncertain messages, ban accounts, and activate `app_settings.global_kill_switch` independently of the mobile release.

## Scale path

The first release polls state every 1.5 seconds and uses Supabase Realtime publication for server-side expansion. Before thousands of concurrent viewers, move spectator delivery to Realtime Broadcast, add IP/device risk scoring at an Edge Function, and load-test the takeover RPC under simultaneous reservations.
