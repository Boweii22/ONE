# ONE economy — canonical launch rules

This is the single source of truth before any new permanent store product IDs are created.

## Core action

- A takeover costs **0 credits** when the player's cooldown is ready.
- During an active cooldown, a successful takeover consumes **1 ONE Credit**.
- A failed or stale takeover consumes **0 credits**.
- There is no auction price, decaying price, or 126/486/662-credit takeover price in the production game.
- The old 100/600/1400 mockup and hundreds-based demo pricing are retired and must not be used in store art or product configuration.

## Paid packs

| Pack | Grant | Launch price |
| --- | ---: | ---: |
| Spark | 3 ONE Credits | £0.99 |
| Challenger | 20 ONE Credits | £4.99 |
| Headliner | 50 ONE Credits | £9.99 |

One credit equals one successful cooldown skip. It is not a lottery ticket, does not change takeover odds, and does not guarantee a win.

## Rewarded ads

An ad grants one immediate **ad skip**, not a ONE Credit and not wallet balance. It must be server verified, idempotent, capped at three per UTC day, and consumed only by the reserved takeover attempt. If an ad cannot load, the UI returns to the ordinary wait-or-use-one-credit choices.

## Permanent identifiers

- Do not delete or recreate already-reserved Android IDs solely to change wording. Legacy `one_tickets_*` identifiers remain valid internal aliases.
- User-facing names, RevenueCat offering names, analytics labels and all newly created web products use **ONE Credits**.
- Preferred new product IDs: `one_credits_spark_v1`, `one_credits_challenger_v1`, and `one_credits_headliner_v1`.
- The RevenueCat webhook receives an explicit `ONE_PRODUCT_CREDITS` JSON map copied from the real dashboards. It deliberately has no guessed fallback map.
