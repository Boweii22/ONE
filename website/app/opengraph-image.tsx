import { ImageResponse } from 'next/og';

export const alt = 'ONE — the only live screen';
export const size = { width: 1200, height: 630 };
export const contentType = 'image/png';

const SUPABASE_URL =
  process.env.SUPABASE_URL ||
  (import.meta.env.VITE_SUPABASE_URL as string | undefined) ||
  'https://ajkdzohnntrbkeqjskel.supabase.co';
const SUPABASE_KEY =
  process.env.SUPABASE_ANON_KEY ||
  (import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined) ||
  'sb_publishable_PGTCO8gqoSrbPgjxXbYqdg_F_D6oqO-';

const accentByPalette: Record<string, string> = {
  ACID: '#d7ff00', COBALT: '#315cff', ORANGE: '#ff4e2b', MAGENTA: '#ff3bbe', ICE: '#72e7ff',
};

type LiveReign = { palette: string; owner: { handle: string }; message: { text: string } } | null;

// The OG scraper hitting this route has no browser session, so it gets its
// own short-lived anonymous Supabase identity - same auth path a real first-
// time spectator takes - purely to read the current reign. 30s caching below
// keeps this to roughly one signup per half-minute, not one per share.
async function fetchLiveReign(): Promise<LiveReign> {
  try {
    const signup = await fetch(`${SUPABASE_URL}/auth/v1/signup`, {
      method: 'POST',
      headers: { apikey: SUPABASE_KEY, 'content-type': 'application/json' },
      body: '{}',
    });
    if (!signup.ok) return null;
    const session = (await signup.json()) as { access_token?: string };
    if (!session.access_token) return null;
    const state = await fetch(`${SUPABASE_URL}/rest/v1/rpc/get_one_state`, {
      method: 'POST',
      headers: {
        apikey: SUPABASE_KEY,
        authorization: `Bearer ${session.access_token}`,
        'content-type': 'application/json',
      },
      body: '{}',
    });
    if (!state.ok) return null;
    const data = (await state.json()) as { reign?: LiveReign };
    return data.reign ?? null;
  } catch {
    return null;
  }
}

export default async function Image() {
  const reign = await fetchLiveReign();
  const accent = (reign && accentByPalette[reign.palette]) || accentByPalette.ACID;
  const message = reign?.message.text?.toUpperCase() || 'THE WORLD IS WATCHING.';
  const handle = reign?.owner.handle || 'NOBODY — YET';

  return new ImageResponse(
    (
      <div
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between',
          padding: '64px',
          backgroundColor: '#08080a',
          color: '#f4f3ed',
          fontFamily: 'sans-serif',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 16, fontSize: 30, fontWeight: 900, letterSpacing: -1 }}>
          <div
            style={{
              display: 'flex',
              width: 46,
              height: 46,
              borderRadius: 999,
              backgroundColor: accent,
              color: '#08080a',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 26,
            }}
          >
            1
          </div>
          ONE — THE ONLY LIVE SCREEN
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
          <div style={{ display: 'flex', fontSize: 26, color: accent, fontWeight: 800, letterSpacing: 2 }}>{handle} OWNS IT RIGHT NOW</div>
          <div style={{ display: 'flex', fontSize: 62, fontWeight: 900, lineHeight: 1.08, letterSpacing: -2, maxWidth: 1030 }}>
            &ldquo;{message}&rdquo;
          </div>
        </div>
        <div style={{ display: 'flex', fontSize: 22, color: '#8e8e91' }}>oneis.live — one person owns it, anyone can steal it</div>
      </div>
    ),
    { ...size, headers: { 'cache-control': 'public, max-age=30, s-maxage=30' } },
  );
}
