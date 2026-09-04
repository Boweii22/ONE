'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import Image from 'next/image';
import { ArrowDown, ArrowRight, Check, Clock3, Eye, Radio, Share2, ShieldCheck, Sparkles, Swords, Users, Zap } from 'lucide-react';
import { Button } from '@/components/ui/button';

type Activity = {
  sequence: number;
  owner: string;
  message: string;
  started_at_ms: number;
  used_ticket: boolean;
};

type Reaction = { reaction: string; handle: string; created_at_ms: number };

type OneState = {
  server_time_ms: number;
  connected: boolean;
  app_views: number;
  web_views: number;
  live_watchers: number;
  takeovers_today: number;
  reign: {
    id: string;
    sequence: number;
    started_at_ms: number;
    protected_until_ms: number;
    palette: string;
    owner: { id: string; handle: string; city: string; country_code: string; initials: string };
    message: { id: string; text: string };
  };
  activity: Activity[];
  reactions: Reaction[];
};

const SUPABASE_URL = import.meta.env.VITE_SUPABASE_URL as string | undefined;
const SUPABASE_KEY = import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined;

const accentByPalette: Record<string, string> = {
  ACID: '#c8ff33', COBALT: '#5865ff', ORANGE: '#ff7043', MAGENTA: '#ff4ca4', ICE: '#67dfff',
};

async function rpc<T>(name: string, body: object, token?: string): Promise<T> {
  if (!SUPABASE_URL || !SUPABASE_KEY) throw new Error('backend_not_configured');
  const response = await fetch(`${SUPABASE_URL}/rest/v1/rpc/${name}`, {
    method: 'POST',
    headers: {
      apikey: SUPABASE_KEY,
      authorization: `Bearer ${token || SUPABASE_KEY}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify(body),
  });
  if (!response.ok) throw new Error(await response.text());
  return response.json() as Promise<T>;
}

type BrowserSession = {
  access_token: string;
  refresh_token: string;
  expires_at: number;
};

type TesterForm = { email: string; name: string; device: string };

async function anonymousToken(forceRefresh = false): Promise<string | undefined> {
  if (!SUPABASE_URL || !SUPABASE_KEY) return undefined;
  const raw = localStorage.getItem('one_web_session');
  let cached: BrowserSession | undefined;
  try {
    cached = raw ? (JSON.parse(raw) as BrowserSession) : undefined;
  } catch {
    localStorage.removeItem('one_web_session');
  }
  if (!forceRefresh && cached?.access_token && cached.expires_at > Date.now() + 60_000) {
    return cached.access_token;
  }

  const refreshing = Boolean(cached?.refresh_token);
  const response = await fetch(
    refreshing
      ? `${SUPABASE_URL}/auth/v1/token?grant_type=refresh_token`
      : `${SUPABASE_URL}/auth/v1/signup`,
    {
    method: 'POST',
    headers: { apikey: SUPABASE_KEY, 'content-type': 'application/json' },
      body: refreshing ? JSON.stringify({ refresh_token: cached?.refresh_token }) : '{}',
    },
  );
  if (!response.ok) {
    if (refreshing) {
      localStorage.removeItem('one_web_session');
      return anonymousToken(false);
    }
    return undefined;
  }
  const payload = await response.json() as Partial<BrowserSession> & { expires_in?: number };
  const session: BrowserSession = {
    access_token: String(payload.access_token ?? ''),
    refresh_token: String(payload.refresh_token ?? cached?.refresh_token ?? ''),
    expires_at: Date.now() + Number(payload.expires_in ?? 3600) * 1000,
  };
  if (session.access_token) localStorage.setItem('one_web_session', JSON.stringify(session));
  return session.access_token || undefined;
}

function duration(milliseconds: number) {
  const total = Math.max(0, Math.floor(milliseconds / 1000));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  return hours
    ? `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
    : `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
}

function compact(value: number) {
  return new Intl.NumberFormat('en', { notation: value > 9999 ? 'compact' : 'standard' }).format(value);
}

export default function Home() {
  const [state, setState] = useState<OneState | null>(null);
  const [status, setStatus] = useState<'connecting' | 'live' | 'unconfigured' | 'offline'>('connecting');
  const [now, setNow] = useState(0);
  const [testerForm, setTesterForm] = useState<TesterForm>({ email: '', name: '', device: 'Android phone' });
  const [testerStatus, setTesterStatus] = useState<'idle' | 'sending' | 'sent' | 'error'>('idle');

  const load = useCallback(async (token?: string) => {
    try {
      const next = await rpc<OneState>('get_one_state', {}, token);
      setState(next);
      setStatus('live');
    } catch (error) {
      setStatus(error instanceof Error && error.message === 'backend_not_configured' ? 'unconfigured' : 'offline');
    }
  }, []);

  useEffect(() => {
    let stopped = false;
    let poll: ReturnType<typeof setInterval> | undefined;
    let pulse: ReturnType<typeof setInterval> | undefined;
    let clock: ReturnType<typeof setInterval> | undefined;
    let renew: ReturnType<typeof setInterval> | undefined;
    void (async () => {
      let token = await anonymousToken();
      if (stopped) return;
      await load(token);
      if (token) void rpc('heartbeat', { p_source: 'web' }, token).catch(() => undefined);
      poll = setInterval(() => void load(token), 1600);
      pulse = setInterval(() => {
        if (token) void rpc('heartbeat', { p_source: 'web' }, token).catch(() => undefined);
      }, 8000);
      clock = setInterval(() => setNow(Date.now()), 1000);
      renew = setInterval(() => {
        void anonymousToken(true).then((next) => {
          if (next) token = next;
        });
      }, 45 * 60 * 1000);
    })();
    return () => {
      stopped = true;
      if (poll) clearInterval(poll);
      if (pulse) clearInterval(pulse);
      if (clock) clearInterval(clock);
      if (renew) clearInterval(renew);
    };
  }, [load]);

  const accent = state ? accentByPalette[state.reign.palette] || accentByPalette.ACID : accentByPalette.ACID;
  const reignTime = useMemo(() => state ? duration(now - state.reign.started_at_ms) : '00:00', [now, state]);
  const reactions = state?.reactions.slice(0, 5) ?? [];

  const share = async () => {
    const text = state
      ? `${state.reign.owner.handle} owns ONE: “${state.reign.message.text}”\nWatch the only live screen.`
      : 'Watch ONE — the only live screen.';
    if (navigator.share) await navigator.share({ title: 'ONE', text, url: location.href });
    else await navigator.clipboard.writeText(`${text}\n${location.href}`);
  };

  const submitTesterInterest = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setTesterStatus('sending');
    try {
      const response = await fetch('/api/tester', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ ...testerForm, company: '' }),
      });
      if (!response.ok) throw new Error('tester_interest_failed');
      setTesterStatus('sent');
      setTesterForm({ email: '', name: '', device: 'Android phone' });
    } catch { setTesterStatus('error'); }
  };

  return (
    <main className="one-shell" style={{ '--accent': accent } as React.CSSProperties}>
      <div className="grid-glow" aria-hidden="true" />
      <header className="topbar">
        <Link href="/" className="brand" aria-label="ONE home"><strong>1</strong><span>ONE</span></Link>
        <div className={`live-pill ${status}`}><i /><span>{status === 'live' ? 'LIVE WORLD STATE' : status.replace('_', ' ')}</span></div>
        <Button variant="outline" className="share-button" onClick={() => void share()}><Share2 /> Share reign</Button>
      </header>

      <section className="launch-hero">
        <div className="hero-orbit orbit-one" aria-hidden="true" />
        <div className="hero-orbit orbit-two" aria-hidden="true" />
        <div className="hero-copy">
          <div className="eyebrow"><span>01</span> A SOCIAL GAME PLAYED IN PUBLIC</div>
          <h1>THE INTERNET<br />HAS <em>ONE</em><br />SCREEN.</h1>
          <p>One person owns it. Everyone sees it. Anyone can steal it. Your words, in front of the world—until somebody takes your place.</p>
          <div className="hero-actions">
            <a className="primary-cta" href="#join">JOIN THE CLOSED TEST <ArrowRight /></a>
            <a className="ghost-cta" href="#live">WATCH IT LIVE <ArrowDown /></a>
          </div>
          <div className="hero-proof"><b>BUILT FOR ANDROID</b><span /><b>REAL PEOPLE</b><span /><b>ONE GLOBAL STAGE</b></div>
        </div>
        <div className="phone-theatre" aria-label="ONE app previews">
          <div className="phone-card phone-back"><Image src="/screens/one-stolen.png" alt="ONE stolen screen" fill priority sizes="(max-width: 900px) 42vw, 260px" /></div>
          <div className="phone-card phone-front"><Image src="/screens/one-live.png" alt="ONE live global screen" fill priority sizes="(max-width: 900px) 52vw, 320px" /></div>
          <div className="signal-badge"><Radio /><span>THE WORLD<br />IS WATCHING</span></div>
        </div>
        <div className="hero-index" aria-hidden="true">001 / ∞</div>
      </section>

      <section id="live" className="live-wrapper">
        <div className="section-intro"><span>LIVE / RIGHT NOW</span><h2>DON'T TAKE OUR<br />WORD FOR IT.</h2><p>This is the actual global screen. No mock data. No polite little demo.</p></div>
      {state ? (
        <div className="stage-layout">
          <section className="live-stage" aria-live="polite">
            <div className="stage-kicker"><Radio /><span>THE ONLY LIVE SCREEN</span><b>#{state.reign.sequence}</b></div>
            <div className="owner-line">
              <div className="avatar">{state.reign.owner.initials}</div>
              <div><strong>{state.reign.owner.handle}</strong><span>{state.reign.owner.city}, {state.reign.owner.country_code} OWNS ONE</span></div>
            </div>
            <blockquote>{state.reign.message.text}</blockquote>
            <div className="accent-rule"><span /><em>TAKE IT IN THE ANDROID APP</em></div>
            <div className="crowd-strip">
              {reactions.length ? reactions.map((reaction, index) => (
                <span key={`${reaction.created_at_ms}-${index}`}><b>{reaction.reaction}</b> {reaction.handle}</span>
              )) : <span><b>THE CROWD IS QUIET.</b> SOMEONE MAKE A MOVE.</span>}
            </div>
          </section>

          <aside className="scoreboard">
            <div className="score-head"><span>BATTLE STATE</span><Swords /></div>
            <Metric icon={<Clock3 />} value={reignTime} label="CURRENT REIGN" />
            <Metric icon={<Users />} value={compact(state.live_watchers)} label="WATCHING NOW" />
            <Metric icon={<Eye />} value={compact(state.app_views + state.web_views)} label="VERIFIED VIEWS" />
            <Metric icon={<ShieldCheck />} value={compact(state.takeovers_today)} label="TAKEOVERS TODAY" />
            <div className="source-split"><span>APP <b>{compact(state.app_views)}</b></span><span>WEB <b>{compact(state.web_views)}</b></span></div>
            <div className="download-card"><small>THINK YOU CAN TAKE IT?</small><strong>THE WEB CAN WATCH.<br />ONLY THE APP CAN STEAL.</strong><span>Android closed beta opening soon.</span></div>
          </aside>

          <section className="battle-feed">
            <div className="feed-title"><span>RECENT TAKEOVERS</span><b>{state.activity.length} DELIVERED</b></div>
            <div className="feed-list">
              {state.activity.slice(0, 7).map((item, index) => (
                <article key={item.sequence} className={index === 0 ? 'current' : ''}>
                  <span className="feed-seq">#{item.sequence}</span>
                  <div><strong>{item.owner}</strong><p>{item.message}</p></div>
                  {item.used_ticket && <em>REVENGE</em>}
                  <time>{duration(now - item.started_at_ms)}</time>
                </article>
              ))}
            </div>
          </section>
        </div>
      ) : (
        <section className="connection-state">
          <span>1</span>
          <p>{status === 'unconfigured' ? 'THE LIVE BACKEND IS NOT CONNECTED YET.' : 'CONNECTING TO THE ONLY SCREEN…'}</p>
          <small>{status === 'unconfigured' ? 'Set the Supabase public URL and key to begin the founding reign.' : 'No simulated audience. No fake numbers.'}</small>
        </section>
      )}
      </section>

      <section className="mechanics">
        <div className="mechanics-heading"><span>HOW ONE WORKS</span><h2>THREE MOVES.<br />ZERO HIDING.</h2></div>
        <div className="mechanic-grid">
          <article><b>01</b><div className="mechanic-icon"><Eye /></div><h3>WATCH</h3><p>The whole world sees the same message, at the same time. No feeds. No algorithm. Just ONE.</p></article>
          <article className="acid-card"><b>02</b><div className="mechanic-icon"><Zap /></div><h3>TAKE IT</h3><p>Challenge the owner. Win the screen. Put your words where everyone can see them.</p></article>
          <article><b>03</b><div className="mechanic-icon"><Swords /></div><h3>DEFEND</h3><p>Your reign is public. Your timer is running. Hold the screen—or watch somebody steal it.</p></article>
        </div>
      </section>

      <section className="screen-reel" aria-label="Inside the ONE app">
        <div className="reel-track">
          {['one-words.png','one-takeover.png','one-proof.png','one-hall.png','one-stolen.png'].map((image, index) => (
            <figure key={image}><Image src={`/screens/${image}`} alt={`ONE app experience ${index + 1}`} fill sizes="(max-width: 700px) 72vw, 330px" /><figcaption>0{index + 1} / ONE</figcaption></figure>
          ))}
        </div>
      </section>

      <section className="manifesto">
        <div className="manifesto-mark"><Image src="/one-app-icon.png" alt="ONE app icon" fill sizes="180px" /></div>
        <p><span>NO FOLLOWERS.</span> NO INFINITE SCROLL. NO PRIVATE LITTLE BUBBLES.</p>
        <h2>JUST ONE PLACE<br />TO BE <em>SEEN.</em></h2>
        <div className="manifesto-note"><Sparkles /> Founding users will shape the rules, culture and chaos of ONE.</div>
      </section>

      <section id="join" className="tester-section" aria-labelledby="tester-title">
        <div className="tester-copy">
          <span className="section-kicker">ANDROID CLOSED TEST</span>
          <h1 id="tester-title">DON&apos;T JUST WATCH THE SCREEN.<br /><em>TRY TO TAKE IT.</em></h1>
          <p>ONE is a live game where one message owns the internet&apos;s only screen. Watch on the web. Challenge for it in the Android app.</p>
          <ol><li><b>01</b><span>Join the Android closed test.</span></li><li><b>02</b><span>Open ONE, make a move, and report what breaks.</span></li><li><b>03</b><span>Help decide what the global screen becomes.</span></li></ol>
        </div>
        <div className="tester-card">
          {testerStatus === 'sent' ? (
            <div className="tester-success" role="status"><Check /><span>YOU&apos;RE ON THE LIST.</span><p>We&apos;ll email you when a tester place opens. Use this same Google account on your Android phone.</p><button type="button" onClick={() => setTesterStatus('idle')}>ADD ANOTHER EMAIL <ArrowRight /></button></div>
          ) : (
            <form onSubmit={submitTesterInterest}>
              <div className="form-head"><span>TEST ONE BEFORE LAUNCH</span><b>LIMITED PLACES</b></div>
              <label htmlFor="tester-email">GOOGLE PLAY EMAIL <strong>*</strong></label>
              <input id="tester-email" type="email" autoComplete="email" required placeholder="you@gmail.com" value={testerForm.email} onChange={(event) => setTesterForm((current) => ({ ...current, email: event.target.value }))} />
              <label htmlFor="tester-name">NAME <small>(OPTIONAL)</small></label>
              <input id="tester-name" type="text" autoComplete="name" placeholder="How should we address you?" value={testerForm.name} onChange={(event) => setTesterForm((current) => ({ ...current, name: event.target.value }))} />
              <label htmlFor="tester-device">DEVICE</label>
              <select id="tester-device" value={testerForm.device} onChange={(event) => setTesterForm((current) => ({ ...current, device: event.target.value }))}><option>Android phone</option><option>Android tablet</option><option>Chromebook</option><option>Google Play Games on PC</option></select>
              <Button type="submit" className="tester-submit" disabled={testerStatus === 'sending'}>{testerStatus === 'sending' ? 'SAVING YOUR PLACE…' : <>REQUEST TEST ACCESS <ArrowRight /></>}</Button>
              {testerStatus === 'error' && <p className="form-error" role="alert">Couldn&apos;t save that yet. Please email <a href="mailto:oneglobalscreen@gmail.com">oneglobalscreen@gmail.com</a>.</p>}
              <p className="form-note">By requesting access, you agree that ONE may contact you about closed testing. No marketing list, no spam.</p>
            </form>
          )}
        </div>
      </section>

      <footer>
        <span>ONE / PUBLIC SPECTATOR</span>
        <nav aria-label="Legal"><Link href="/privacy">PRIVACY</Link><Link href="/delete-account">DELETE ACCOUNT</Link></nav>
        <span>EVERY NUMBER ON THIS PAGE COMES FROM THE LIVE LEDGER.</span>
      </footer>
    </main>
  );
}

function Metric({ icon, value, label }: { icon: React.ReactNode; value: string; label: string }) {
  return <div className="metric"><span>{icon}</span><div><strong>{value}</strong><small>{label}</small></div></div>;
}
