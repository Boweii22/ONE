// Checkout stays unavailable until the funnel and purchase webhook are configured.
// The caller must supply its Supabase access token, never a user ID from a form.
export async function POST(request: Request) {
  const headers = { 'cache-control': 'no-store' };
  const funnel = process.env.REVENUECAT_FUNNEL_URL;
  if (!funnel || process.env.ONE_WEB_CHECKOUT_ENABLED !== 'true') {
    return Response.json({ error: 'Web checkout is not available yet.' }, { status: 503, headers });
  }
  const authorization = request.headers.get('authorization') || '';
  if (!authorization.startsWith('Bearer ')) return Response.json({ error: 'Sign in to your ONE account first.' }, { status: 401, headers });
  const url = process.env.SUPABASE_URL || import.meta.env.VITE_SUPABASE_URL;
  const key = process.env.SUPABASE_ANON_KEY || import.meta.env.VITE_SUPABASE_ANON_KEY;
  if (!url || !key) return Response.json({ error: 'Checkout configuration is incomplete.' }, { status: 503, headers });
  try {
    const result = await fetch(`${url}/auth/v1/user`, { headers: { apikey: key, authorization }, signal: AbortSignal.timeout(10000) });
    if (!result.ok) return Response.json({ error: 'Please sign in again.' }, { status: 401, headers });
    const user = await result.json() as { id?: string; is_anonymous?: boolean };
    if (!user.id || user.is_anonymous !== false) return Response.json({ error: 'Protect your ONE account with Google before buying on the web.' }, { status: 403, headers });
    const destination = new URL(funnel);
    if (destination.protocol !== 'https:' || destination.username || destination.password || destination.search || destination.hash) throw new Error('Invalid funnel URL');
    destination.pathname = `${destination.pathname.replace(/\/$/, '')}/${encodeURIComponent(user.id)}`;
    return Response.json({ url: destination.toString() }, { headers });
  } catch {
    return Response.json({ error: 'Checkout could not be opened. Please try again.' }, { status: 503, headers });
  }
}
