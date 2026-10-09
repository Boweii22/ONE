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
  web_take_enabled: boolean;
  google_linked: boolean;
};

type Starter = { id: string; text: string };

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
  const [starters, setStarters] = useState<Starter[]>([]);
  const [selectedStarterId, setSelectedStarterId] = useState('');
  const [takeBusy, setTakeBusy] = useState(false);
  const [takeNotice, setTakeNotice] = useState('');

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

  useEffect(() => {
    if (!state?.web_take_enabled || !state?.google_linked || starters.length) return;
    void (async () => {
      try {
        const raw = localStorage.getItem('one_web_session');
        const session = raw ? JSON.parse(raw) as BrowserSession : null;
        const list = await rpc<Starter[]>('list_starter_messages', {}, session?.access_token);
        setStarters(list);
        if (list.length) setSelectedStarterId(list[0].id);
      } catch { /* starters stay empty; the take button covers this case */ }
    })();
  }, [state?.web_take_enabled, state?.google_linked, starters.length]);

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

  const takeScreen = useCallback(async () => {
    if (!state) return;
    if (!webSignedIn) {
      if (!SUPABASE_URL) return;
      const redirect = window.location.origin + window.location.pathname;
      window.location.href = `${SUPABASE_URL}/auth/v1/authorize?provider=google&redirect_to=${encodeURIComponent(redirect)}`;
      return;
    }
    if (!selectedStarterId) return;
    setTakeBusy(true);
    setTakeNotice('');
    try {
      const raw = localStorage.getItem('one_web_session');
      const session = raw ? JSON.parse(raw) as BrowserSession : null;
      if (!session?.access_token) throw new Error('no_session');
      const claim = await rpc<{ ok: boolean; message_id: string }>(
        'claim_starter_message',
        { p_starter_id: selectedStarterId, p_request_id: crypto.randomUUID() },
        session.access_token,
      );
      const result = await rpc<{ ok: boolean; code?: string; message?: string }>(
        'take_one_web',
        { p_message_id: claim.message_id, p_expected_sequence: state.reign.sequence, p_request_id: crypto.randomUUID() },
        session.access_token,
      );
      if (!result.ok) setTakeNotice(result.message || 'Someone else reached ONE first.');
      else {
        setTakeNotice('YOU TOOK ONE.');
        await load(session.access_token);
      }
    } catch {
      setTakeNotice('Could not take the screen. Try again.');
    } finally {
      setTakeBusy(false);
    }
  }, [state, webSignedIn, selectedStarterId, load]);

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
    <main className="one-shell">
      <header className="topbar">
        <Link href="/" className="brand" aria-label="ONE home"><strong>1</strong><span>ONE</span></Link>
        <nav className="main-nav" aria-label="Main navigation"><Link href="/">HOME</Link><Link href="/experience">EXPERIENCE</Link><a href={PLAY_URL}>GET THE ANDROID APP ↗</a></nav>
      </header>
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
            {state.web_take_enabled ? (
              <div className="web-take" aria-label="Take the screen from your browser">
                {!webSignedIn ? (
                  <button type="button" className="ghost-cta" onClick={() => void takeScreen()}>
                    SIGN IN WITH GOOGLE TO TAKE IT <ArrowRight />
                  </button>
                ) : !state.google_linked ? (
                  <p className="checkout-notice">Finish linking your Google account to take the screen from here.</p>
                ) : (
                  <>
                    {starters.length > 0 && (
                      <select
                        aria-label="Choose a message"
                        value={selectedStarterId}
                        onChange={(event) => setSelectedStarterId(event.target.value)}
                        disabled={takeBusy}
                      >
                        {starters.map((starter) => <option key={starter.id} value={starter.id}>{starter.text}</option>)}
                      </select>
                    )}
                    <button type="button" className="ghost-cta" onClick={() => void takeScreen()} disabled={takeBusy || !selectedStarterId}>
                      {takeBusy ? 'TAKING…' : 'TAKE THE SCREEN'} <Zap size={16} fill="currentColor" />
                    </button>
                  </>
                )}
                {takeNotice && <p className="checkout-notice">{takeNotice}</p>}
              </div>
            ) : (
              <div className="accent-rule"><span /><em>TAKE IT IN THE ANDROID APP</em></div>
            )}
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
            <a href={PLAY_URL} className="download-card"><small>THINK YOU CAN TAKE IT?</small><strong>CUSTOM MESSAGES.<br />IN THE ANDROID APP.</strong><span>Available now on Google Play ↗</span></a>
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


      <footer>
        <span>ONE / LIVE SCREEN</span>
        <nav aria-label="Legal"><Link href="/privacy">PRIVACY</Link><Link href="/terms">TERMS</Link><Link href="/delete-account">DELETE ACCOUNT</Link></nav>
      </footer>
    </main>
  );
}

function Metric({ icon, value, label }: { icon: React.ReactNode; value: string; label: string }) {
  return <div className="metric"><span>{icon}</span><div><strong>{value}</strong><small>{label}</small></div></div>;
}


