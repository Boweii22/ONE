'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import Image from 'next/image';
import { ArrowDown, ArrowRight, Check, Clock3, Download, Eye, Radio, Share2, ShieldCheck, Sparkles, Swords, Users, Zap } from 'lucide-react';
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

// Sites injects production environment variables at runtime, while Vite replaces
// import.meta.env values during the build. Keep the public Supabase client config
// as a build-safe fallback so the browser bundle is never left unconfigured.
const SUPABASE_URL =
  (import.meta.env.VITE_SUPABASE_URL as string | undefined) ||
  'https://ajkdzohnntrbkeqjskel.supabase.co';
const SUPABASE_KEY =
  (import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined) ||
  'sb_publishable_PGTCO8gqoSrbPgjxXbYqdg_F_D6oqO-';
const PLAY_URL = 'https://play.google.com/store/apps/details?id=com.tomribowei.one';

const accentByPalette: Record<string, string> = {
  ACID: '#d7ff00', COBALT: '#315cff', ORANGE: '#ff4e2b', MAGENTA: '#ff3bbe', ICE: '#72e7ff',
};
const reactionIcon: Record<string, string> = { FIRE: '🔥', THIEF: '🔥', RESPECT: '👏', '100': '💯', LOL: '💯', WATCH: '👀', 'TOO SLOW': '👀', ROCKET: '🚀', 'TAKE IT BACK': '🚀' };

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
  const [inviteEmailed, setInviteEmailed] = useState(false);
  const [shareNotice, setShareNotice] = useState('');
  const [webSignedIn, setWebSignedIn] = useState(false);
  const [checkoutBusy, setCheckoutBusy] = useState(false);
  const [checkoutNotice, setCheckoutNotice] = useState('');

  useEffect(() => {
    // A referral link (oneis.live/?ref=alex) - remembered so it still counts
    // even if someone browses around before actually signing up.
    const ref = new URLSearchParams(window.location.search).get('ref');
    if (ref && /^[a-z0-9-]{2,32}$/i.test(ref)) {
      try { localStorage.setItem('one_referral_code', ref.toLowerCase()); } catch { /* ignore */ }
    }
    // Supabase OAuth (implicit flow) returns here with tokens in the URL fragment.
    const hash = window.location.hash;
    if (hash.includes('access_token=')) {
      const params = new URLSearchParams(hash.slice(1));
      const access_token = params.get('access_token');
      const refresh_token = params.get('refresh_token');
      if (access_token && refresh_token) {
        const session: BrowserSession = {
          access_token,
          refresh_token,
          expires_at: Date.now() + Number(params.get('expires_in') ?? '3600') * 1000,
        };
        localStorage.setItem('one_web_session', JSON.stringify(session));
      }
      window.history.replaceState(null, '', window.location.pathname + window.location.search);
    }
    void (async () => {
      if (!SUPABASE_URL || !SUPABASE_KEY) return;
      const raw = localStorage.getItem('one_web_session');
      if (!raw) return;
      try {
        const session = JSON.parse(raw) as BrowserSession;
        const response = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
          headers: { apikey: SUPABASE_KEY, authorization: `Bearer ${session.access_token}` },
        });
        if (!response.ok) return;
        const user = await response.json() as { is_anonymous?: boolean };
        setWebSignedIn(user.is_anonymous === false);
      } catch { /* stay signed out */ }
    })();
  }, []);

  const buyCredits = useCallback(async () => {
    if (!webSignedIn) {
      if (!SUPABASE_URL) return;
      const redirect = window.location.origin + window.location.pathname;
      window.location.href = `${SUPABASE_URL}/auth/v1/authorize?provider=google&redirect_to=${encodeURIComponent(redirect)}`;
      return;
    }
    setCheckoutBusy(true);
    setCheckoutNotice('');
    try {
      const raw = localStorage.getItem('one_web_session');
      const session = raw ? JSON.parse(raw) as BrowserSession : null;
      if (!session?.access_token) throw new Error('no_session');
      const response = await fetch('/api/checkout', {
        method: 'POST',
        headers: { authorization: `Bearer ${session.access_token}` },
      });
      const payload = await response.json() as { url?: string; error?: string };
      if (payload.url) window.location.href = payload.url;
      else setCheckoutNotice(payload.error || 'Web checkout is not available yet.');
    } catch {
      setCheckoutNotice('Web checkout is not available yet.');
    } finally {
      setCheckoutBusy(false);
    }
  }, [webSignedIn]);

  useEffect(() => {
    const observer = new IntersectionObserver((entries) => entries.forEach((entry) => {
      if (entry.isIntersecting) { entry.target.classList.add('revealed'); observer.unobserve(entry.target); }
    }), { threshold: 0.08 });
    document.querySelectorAll('.mechanics, .manifesto, .download-section, .tester-section, .screen-reel').forEach((el) => { el.classList.add('reveal-ready'); observer.observe(el); });
    return () => observer.disconnect();
  }, []);

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
    try {
      if (navigator.share) await navigator.share({ title: 'ONE', text, url: location.href });
      else { await navigator.clipboard.writeText(`${text}\n${location.href}`); setShareNotice('Link copied'); }
    } catch (error) { if (!(error instanceof DOMException && error.name === 'AbortError')) setShareNotice('Copy the link from your address bar.'); }
  };

  const submitTesterInterest = async (event: React.SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    setTesterStatus('sending');
    try {
      let ref = '';
      try { ref = localStorage.getItem('one_referral_code') || ''; } catch { /* ignore */ }
      const response = await fetch('/api/tester', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ ...testerForm, company: '', ref }),
      });
      if (!response.ok) throw new Error('tester_interest_failed');
      const result = await response.json() as { emailed?: boolean };
      setInviteEmailed(Boolean(result.emailed));
      setTesterStatus('sent');
      setTesterForm({ email: '', name: '', device: 'Android phone' });
    } catch { setTesterStatus('error'); }
  };

  return (
    <main className="one-shell" style={{ '--accent': accent, '--acid': accent } as React.CSSProperties}>
      <a href="#live" className="skip-link">Skip to the live screen</a>
      <div className="grid-glow" aria-hidden="true" />
      <header className="topbar">
        <Link href="/" className="brand" aria-label="ONE home"><strong>1</strong><span>ONE</span></Link>
        <nav className="main-nav" aria-label="Main navigation"><a href="#live">THE SCREEN</a><a href="#how">THE RULES</a><a href="#download">DOWNLOAD ↗</a></nav>
        <div className={`live-pill ${status}`}><i /><span>{status === 'live' ? 'LIVE WORLD STATE' : status.replace('_', ' ')}</span></div>
        <Button variant="outline" className="share-button" onClick={() => void share()}><Share2 /> {shareNotice || 'Share ONE'}</Button>
      </header>

      <section className="launch-hero">
        <Image className="hero-world" src="/world-map.png" alt="" fill priority sizes="100vw" />
        <div className="hero-copy">
          <div className="eyebrow"><span>01</span> A SOCIAL GAME PLAYED IN PUBLIC</div>
          <h1>THE INTERNET<br />HAS <em>ONE</em><br />SCREEN.</h1>
          <p>One person owns it. Everyone sees it. Anyone can steal it. Your words, in front of the world—until somebody takes your place.</p>
          <div className="hero-actions">
            <a className="primary-cta" href="/downloads/ONE-Android-preview.apk" download="ONE-Android-preview.apk">DOWNLOAD APK <Download /></a>
            <a className="ghost-cta" href="#live">WATCH IT LIVE <ArrowDown /></a>
            <button type="button" className="ghost-cta" onClick={() => void buyCredits()} disabled={checkoutBusy}>
              {checkoutBusy ? 'OPENING CHECKOUT…' : webSignedIn ? 'BUY ONE CREDITS' : 'SIGN IN TO BUY CREDITS'} <ArrowRight />
            </button>
          </div>
          {checkoutNotice && <p className="checkout-notice">{checkoutNotice}</p>}
          {!webSignedIn && !checkoutBusy && <p className="checkout-notice">Use the same Google account you&apos;ve linked in the app, so credits show up there.</p>}
          <div className="hero-proof"><b>BUILT FOR ANDROID</b><span /><b>REAL PEOPLE</b><span /><b>ONE GLOBAL STAGE</b></div>
        </div>
        <div className="phone-theatre" aria-label="Live ONE screen preview">
          <div className="hero-ticket"><span>ADMIT ONE / ANDROID</span><strong>YOUR NEXT<br />MAIN CHARACTER<br />MOMENT.</strong><a href="#join">TAKE YOUR PLACE <ArrowRight /></a></div>
          <div className="broadcast-phone">
            <div className="phone-status"><span className="status-dot" />{status === 'live' ? 'LIVE WORLD STATE' : 'ONE / GLOBAL SCREEN'}<Radio size={18} /></div>
            <div className="phone-owner"><span>{state?.reign.owner.initials || '1'}</span><div><b>{state?.reign.owner.handle || 'ONE GLOBAL STAGE'}</b><small>{state ? `${state.reign.owner.city}, ${state.reign.owner.country_code}` : 'EVERYONE IS INVITED'}</small></div></div>
            <div className="phone-message"><Image src="/world-map.png" fill alt="" sizes="360px" /><strong>{state?.reign.message.text || 'THE WORLD IS WATCHING.'}</strong></div>
            <div className="phone-metrics"><div><small><Eye size={14} /> WATCHING NOW</small><b>{state ? compact(state.live_watchers) : '—'}</b></div><div><small><Clock3 size={14} /> CURRENT REIGN</small><b>{state ? reignTime : '—'}</b></div></div>
            <a className="phone-take" href="#join"><Zap size={20} fill="currentColor" />TAKE THE SCREEN</a>
            <div className="phone-bottom"><span><Zap />LIVE</span><span><Swords />CHALLENGES</span><span><ShieldCheck />HALL</span><span><Users />YOU</span></div>
          </div>
          <div className="signal-badge"><span>ONE OWNER.<br />EVERYONE<br />WATCHING.</span><ArrowRight /></div>
        </div>
        <div className="hero-index" aria-hidden="true">001 / ∞</div>
      </section>
      <div className="takeover-tape" aria-hidden="true"><div>{Array.from({ length: 4 }, (_, i) => <span key={i}>ONE PERSON. <b>ONE MESSAGE.</b> THE WHOLE WORLD. <Zap fill="currentColor" /></span>)}</div></div>

      <section id="live" className="live-wrapper">
        <div className="section-intro"><span>01 / THE GLOBAL SCREEN</span><h2>RIGHT NOW.<br /><em>ONE OWNER.</em></h2><p>Same screen. Same message. Anywhere on Earth. This is the live reign from the app.</p></div>
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
                <span key={`${reaction.created_at_ms}-${index}`}><b aria-label={reaction.reaction}>{reactionIcon[reaction.reaction] || '✨'}</b> {reaction.handle}</span>
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
            <a href={PLAY_URL} className="download-card"><small>THINK YOU CAN TAKE IT?</small><strong>THE WEB CAN WATCH.<br />ONLY THE APP CAN STEAL.</strong><span>Already a tester? Open Google Play ↗</span></a>
          </aside>

          <section className="battle-feed">
            <div className="feed-title"><span>RECENT TAKEOVERS</span><b>{state.activity.length} DELIVERED</b></div>
            <div className="feed-list">
              {state.activity.slice(0, 7).map((item, index) => (
                <article key={item.sequence} className={index === 0 ? 'current' : ''}>
                  <span className="feed-seq">#{item.sequence}</span>
                  <div><strong>{item.owner}</strong><p>{item.message}</p></div>
                  {item.used_ticket && <em>CREDIT SKIP</em>}
                  <time>{duration(now - item.started_at_ms)}</time>
                </article>
              ))}
            </div>
          </section>
        </div>
      ) : (
        <section className="connection-state">
          <span>1</span>
          <p>{status === 'connecting' ? 'TUNING INTO THE WORLD…' : 'THE SCREEN IS TAKING A BREATHER.'}</p>
          <small>{status === 'connecting' ? 'The latest reign will appear here.' : 'Live updates are temporarily unavailable. Try again in a moment.'}</small>
          {status !== 'connecting' && <button className="ghost-cta" onClick={() => void load()}>RECONNECT <Radio /></button>}
        </section>
      )}
      </section>

      <section className="mechanics" id="how">
        <div className="mechanics-heading"><span>HOW ONE WORKS</span><h2>THREE MOVES.<br />ZERO HIDING.</h2></div>
        <div className="mechanic-grid">
          <article><b>01</b><div className="mechanic-icon"><Eye /></div><h3>WATCH</h3><p>The whole world sees the same message, at the same time. No feeds. No algorithm. Just ONE.</p></article>
          <article className="acid-card"><b>02</b><div className="mechanic-icon"><Zap /></div><h3>TAKE IT</h3><p>Challenge the owner. Win the screen. Put your words where everyone can see them.</p></article>
          <article><b>03</b><div className="mechanic-icon"><Swords /></div><h3>DEFEND</h3><p>Your reign is public. Your timer is running. Hold the screen—or watch somebody steal it.</p></article>
        </div>
      </section>

      <section className="screen-reel" aria-label="Inside the ONE app">
        <div className="reel-heading"><span>03 / IN YOUR HANDS</span><h2>SMALL SCREEN.<br />GLOBAL ENERGY.</h2><p>The ONE visual direction. Swipe to explore the screens.</p></div>
        <div className="reel-track">
          {[
            ['one-live.png', 'The live ONE global screen', 'WATCH / REACT / TAKE'],
            ['one-words.png', 'The approved message library', 'WRITE / APPROVE / DEPLOY'],
            ['one-hall.png', 'The real ONE leaderboard', 'REIGNS / TAKEOVERS / HALL'],
          ].map(([image, alt, caption]) => (
            <figure key={image}><Image src={`/screens/${image}`} alt={alt} fill sizes="(max-width: 700px) 78vw, 360px" /><figcaption>{caption}</figcaption></figure>
          ))}
        </div>
      </section>

      <section className="manifesto">
        <div className="manifesto-mark"><Image src="/one-app-icon.png" alt="ONE app icon" fill sizes="180px" /></div>
        <p><span>NO FOLLOWERS.</span> NO INFINITE SCROLL. NO PRIVATE LITTLE BUBBLES.</p>
        <h2>JUST ONE PLACE<br />TO BE <em>SEEN.</em></h2>
        <div className="manifesto-note"><Sparkles /> Founding users will shape the rules, culture and chaos of ONE.</div>
      </section>

      <section id="download" className="download-section" aria-labelledby="download-title">
        <div className="download-copy">
          <span className="section-kicker">ANDROID / DIRECT ACCESS</span>
          <h2 id="download-title">PUT ONE<br />IN YOUR <em>POCKET.</em></h2>
          <p>Download the current Android preview and step onto the only screen everyone shares. No desktop imitation—the real game lives on your phone.</p>
          <div className="download-trust"><span><ShieldCheck /> Served securely by oneis.live</span><span><Download /> 22.4 MB APK</span></div>
        </div>

        <div className="download-ticket">
          <div className="download-ticket-top"><span>ONE / ANDROID PREVIEW</span><b>DIRECT BUILD</b></div>
          <div className="download-glyph" aria-hidden="true">1</div>
          <div className="download-ticket-copy"><small>THE WORLD HAS ONE SCREEN.</small><strong>CLAIM<br />YOUR TURN.</strong></div>
          <a className="apk-download" href="/downloads/ONE-Android-preview.apk" download="ONE-Android-preview.apk">
            <span><b>DOWNLOAD ONE APK</b><small>Android preview · 22.4 MB</small></span><Download />
          </a>
          <p className="sideload-note">Your phone may ask permission to install apps from this browser. Download only from <strong>oneis.live</strong>.</p>
          <div className="play-store-soon" aria-label="Google Play release coming soon">
            <span><small>OFFICIAL RELEASE</small><b>GOOGLE PLAY</b></span><em>COMING SOON</em>
          </div>
        </div>
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
            <output className="tester-success"><Check /><span>REQUEST RECEIVED.</span><p>{inviteEmailed ? 'Check your inbox — you’re on the list. I’ll personally email your Play Store install link once you’re added as a tester.' : 'Your interest is saved. I’ll email your Play Store install link personally once you’re added as a tester.'}</p><button type="button" onClick={() => setTesterStatus('idle')}>ADD ANOTHER EMAIL <ArrowRight /></button></output>
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
        <nav aria-label="Legal"><a href="https://oneis.live/privacy">PRIVACY</a><a href="https://oneis.live/terms">TERMS</a><a href="https://oneis.live/delete-account">DELETE ACCOUNT</a></nav>
        <span>EVERY NUMBER ON THIS PAGE COMES FROM THE LIVE LEDGER.</span>
      </footer>
    </main>
  );
}

function Metric({ icon, value, label }: { icon: React.ReactNode; value: string; label: string }) {
  return <div className="metric"><span>{icon}</span><div><strong>{value}</strong><small>{label}</small></div></div>;
}
