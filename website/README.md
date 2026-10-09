# ONE website — independent Cloudflare deployment

This copy preserves the published ONE design and `/play` route without depending
on Sites hosting. Supabase remains the existing production backend. Deploying
this website does not change database migrations or rollout flags.

## Local development and manual publishing

Use Node.js 22.13 or newer:

```sh
npm ci
npm run dev
```

To publish to your own Cloudflare account:

```sh
npx wrangler login
npm run typecheck
npm run deploy
```

## GitHub automatic publishing

In Cloudflare Workers, connect the `one-web` Worker to the website GitHub source.
Set the root directory to this folder's location within the repository, then use:

- Build command: `npm run build`
- Deploy command: `npm run deploy:built`
- Production branch: the branch selected for this website

Only grant repository write access to people authorized to publish. No Cloudflare
OAuth token or other secret belongs in Git or source files. Cloudflare's native
Git integration manages its deployment token.

## Configuration and cutover

`wrangler.jsonc` contains only public Supabase browser credentials and deployment
configuration. Keep web checkout disabled unless its funnel and purchase handling
are configured. Optional email/funnel secrets must be added in the Cloudflare
dashboard or with `wrangler secret put`, never committed.

Test the Worker URL and required routes before changing `oneis.live` DNS/custom
domain configuration. Keep the current Sites deployment available for rollback.
Google OAuth must allow the chosen website return URL; do not test sign-in on a
new hostname unless that hostname is explicitly allowlisted in Supabase.

## Migration status

Prepared locally. Account linking, deployment, Git integration, and domain cutover
must each be confirmed before this copy is described as production.
