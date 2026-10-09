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

The independent Worker is deployed at
https://one-web.tombribowei01.workers.dev. Cloudflare Builds is connected to
`Boweii22/ONE`, branch `main`, root directory `website`, using the commands above.
Changes pushed to `main` trigger a new build and deployment.

## Navigation regression checks

Internal navigation intentionally uses native `<a href>` links. The current
Vinext build's `next/link` navigation throws during prefetch and click handling
on the deployed Worker; native links keep routes usable without that router.
After publishing, click (do not only open the URLs directly):

- Homepage → Play in browser → Google sign-in → `/play` return.
- Homepage → Live screen, and Open the live game → `/play`.
- Play → Home, Experience, Privacy, Terms, and Delete account.
- Experience → Home; legal pages → their return and cross-policy links.

Verify starter selection and takeover separately with an authorized test account.
Publishing a frontend fix must not change database rollout flags.

`oneis.live` and `www.oneis.live` are connected to the production Worker and
declared in `wrangler.jsonc` so future deployments retain both domains. Namecheap
has been switched to `maxine.ns.cloudflare.com` and `nick.ns.cloudflare.com`;
Cloudflare activation and public DNS propagation must complete before all
visitors reach the new deployment. Email and payment DNS records were retained.
The original Sites deployment remains available for rollback, and Supabase
rollout flags have not been changed.

To update the live website, edit files under `website/`, commit, and push to
`main`. Cloudflare automatically builds and publishes the changes; Sites is not
required. Collaborators need repository write access to publish updates.
