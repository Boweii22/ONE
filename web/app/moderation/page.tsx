'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { AlertTriangle, Ban, Check, Clock3, Copy, Flag, LogOut, Power, Radio, ShieldCheck, Trophy, Users, X } from 'lucide-react';
import './moderation.css';

// These are public browser credentials. Sites runtime variables are not available
// while Vite compiles client code, so retain a build-safe fallback.
const SUPABASE_URL =
  (import.meta.env.VITE_SUPABASE_URL as string | undefined) ||
  'https://ajkdzohnntrbkeqjskel.supabase.co';
const SUPABASE_KEY =
  (import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined) ||
  'sb_publishable_PGTCO8gqoSrbPgjxXbYqdg_F_D6oqO-';
const SESSION_KEY = 'one_web_session';
const UNDO_WINDOW_MS = 20000;
const QUEUE_POLL_MS = 4000;

type BrowserSession = { access_token: string; refresh_token: string; expires_at: number };

type QueueItem = {
  reign_id: string;
  sequence: number;
  reign_started_at_ms: number;
  owner_id: string;
  owner_handle: string;
  message_id: string;
  message_text: string;
  reasons: string[];
  unique_reporters: number;
  reporter_handles: string[];
  auto_pulled: boolean;
  hold_active: boolean;
};

type Action = 'dismiss' | 'remove' | 'ban';

type PendingAction = { item: QueueItem; action: Action; deadline: number };

type PendingMessageItem = {
  message_id: string;
  text: string;
  created_at_ms: number;
  author_id: string;
  author_handle: string;
  is_authors_first_review: boolean;
};

type PendingTesterItem = {
  id: string;
  email: string;
  name: string | null;
  device: string;
  created_at_ms: number;
};

type ReferralItem = {
  code: string;
  label: string;
  signups: number;
  created_at_ms: number;
};

async function rpc<T>(name: string, body: object, token: string): Promise<T> {
  if (!SUPABASE_URL || !SUPABASE_KEY) throw new Error('backend_not_configured');
  const response = await fetch(`${SUPABASE_URL}/rest/v1/rpc/${name}`, {
    method: 'POST',
    headers: {
      apikey: SUPABASE_KEY,
      authorization: `Bearer ${token}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify(body),
  });
  if (!response.ok) throw new Error(await response.text());
  return response.json() as Promise<T>;
}

function readSession(): BrowserSession | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY);
    return raw ? (JSON.parse(raw) as BrowserSession) : null;
  } catch {
    return null;
  }
}

function timeAgo(ms: number, now: number) {
  const seconds = Math.max(0, Math.floor((now - ms) / 1000));
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}

export default function ModerationConsole() {
  const [phase, setPhase] = useState<'loading' | 'signed_out' | 'not_staff' | 'staff'>('loading');
  const [handle, setHandle] = useState('');
  const [token, setToken] = useState<string | null>(null);
  const [killSwitch, setKillSwitch] = useState(false);
  const [queueView, setQueueView] = useState<'reports' | 'messages' | 'testers' | 'referrals'>('reports');
  const [queue, setQueue] = useState<QueueItem[]>([]);
  const [pending, setPending] = useState<Record<string, PendingAction>>({});
  const [pendingMessages, setPendingMessages] = useState<PendingMessageItem[]>([]);
  const [decidingMessageId, setDecidingMessageId] = useState<string | null>(null);
  const [pendingTesters, setPendingTesters] = useState<PendingTesterItem[]>([]);
  const [approvingTesterId, setApprovingTesterId] = useState<string | null>(null);
  const [referrals, setReferrals] = useState<ReferralItem[]>([]);
  const [newReferralCode, setNewReferralCode] = useState('');
  const [newReferralLabel, setNewReferralLabel] = useState('');
  const [creatingReferral, setCreatingReferral] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  const [toast, setToast] = useState<{ text: string; error?: boolean } | null>(null);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  const showToast = useCallback((text: string, error = false) => {
    setToast({ text, error });
    clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(null), 3200);
  }, []);

  // Resolve the initial session: handle an OAuth redirect fragment first,
  // then validate whatever session we end up with.
  useEffect(() => {
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
        localStorage.setItem(SESSION_KEY, JSON.stringify(session));
      }
      window.history.replaceState(null, '', window.location.pathname);
    }

    void (async () => {
      if (!SUPABASE_URL || !SUPABASE_KEY) { setPhase('signed_out'); return; }
      const session = readSession();
      if (!session?.access_token) { setPhase('signed_out'); return; }
      try {
        const response = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
          headers: { apikey: SUPABASE_KEY, authorization: `Bearer ${session.access_token}` },
        });
        if (!response.ok) { setPhase('signed_out'); return; }
        const user = await response.json() as { is_anonymous?: boolean };
        if (user.is_anonymous !== false) { setPhase('signed_out'); return; }
        const who = await rpc<{ is_staff: boolean; handle: string }>('moderation_whoami', {}, session.access_token);
        setToken(session.access_token);
        setHandle(who.handle);
        setPhase(who.is_staff ? 'staff' : 'not_staff');
      } catch {
        setPhase('signed_out');
      }
    })();
  }, []);

  const loadQueue = useCallback(async () => {
    if (!token) return;
    try {
      const items = await rpc<QueueItem[]>('moderation_queue', {}, token);
      setQueue(items);
    } catch {
      // transient network hiccup — keep showing the last known queue
    }
  }, [token]);

  const loadPendingMessages = useCallback(async () => {
    if (!token) return;
    try {
      const items = await rpc<PendingMessageItem[]>('pending_messages_queue', {}, token);
      setPendingMessages(items);
    } catch {
      // transient network hiccup — keep showing the last known queue
    }
  }, [token]);

  const decideMessage = useCallback(async (item: PendingMessageItem, verdict: 'approved' | 'rejected') => {
    if (!token) return;
    setDecidingMessageId(item.message_id);
    try {
      await rpc('moderate_pending_message', { p_message_id: item.message_id, p_verdict: verdict }, token);
      setPendingMessages((prev) => prev.filter((entry) => entry.message_id !== item.message_id));
      showToast(verdict === 'approved' ? 'Approved.' : 'Rejected.');
    } catch {
      showToast(`${verdict === 'approved' ? 'APPROVE' : 'REJECT'} failed — still in the queue.`, true);
    } finally {
      setDecidingMessageId(null);
    }
  }, [token, showToast]);

  const loadPendingTesters = useCallback(async () => {
    if (!token) return;
    try {
      const items = await rpc<PendingTesterItem[]>('pending_testers_queue', {}, token);
      setPendingTesters(items);
    } catch {
      // transient network hiccup — keep showing the last known queue
    }
  }, [token]);

  const approveTester = useCallback(async (item: PendingTesterItem) => {
    if (!token) return;
    setApprovingTesterId(item.id);
    try {
      await rpc('approve_tester_invite', { p_id: item.id }, token);
      setPendingTesters((prev) => prev.filter((entry) => entry.id !== item.id));
      showToast(`Invite sent to ${item.email}.`);
    } catch {
      showToast('Approve failed — still in the queue.', true);
    } finally {
      setApprovingTesterId(null);
    }
  }, [token, showToast]);

  const loadReferrals = useCallback(async () => {
    if (!token) return;
    try {
      const items = await rpc<ReferralItem[]>('referral_leaderboard', {}, token);
      setReferrals(items);
    } catch {
      // transient network hiccup — keep showing the last known leaderboard
    }
  }, [token]);

  const createReferral = useCallback(async (event: React.SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!token) return;
    const code = newReferralCode.trim().toLowerCase();
    const label = newReferralLabel.trim();
    if (!/^[a-z0-9-]{2,32}$/.test(code) || !label) {
      showToast('Use a short code (letters, numbers, dashes) and a name.', true);
      return;
    }
    setCreatingReferral(true);
    try {
      await rpc('create_referral_code', { p_code: code, p_label: label }, token);
      setNewReferralCode('');
      setNewReferralLabel('');
      showToast(`Link created for ${label}.`);
      void loadReferrals();
    } catch {
      showToast('Could not create that link — code may already be taken.', true);
    } finally {
      setCreatingReferral(false);
    }
  }, [token, newReferralCode, newReferralLabel, showToast, loadReferrals]);

  const copyReferralLink = useCallback(async (code: string) => {
    const link = `https://oneis.live/?ref=${code}`;
    try {
      await navigator.clipboard.writeText(link);
      showToast('Link copied.');
    } catch {
      showToast(link);
    }
  }, [showToast]);

  const loadKillSwitch = useCallback(async () => {
    if (!token || !SUPABASE_URL || !SUPABASE_KEY) return;
    try {
      const response = await fetch(`${SUPABASE_URL}/rest/v1/rpc/get_one_state`, {
        method: 'POST',
        headers: { apikey: SUPABASE_KEY, authorization: `Bearer ${token}`, 'content-type': 'application/json' },
        body: '{}',
      });
      const state = await response.json() as { global_kill_switch?: boolean };
      if (typeof state.global_kill_switch === 'boolean') setKillSwitch(state.global_kill_switch);
    } catch { /* keep last known state */ }
  }, [token]);

  useEffect(() => {
    if (phase !== 'staff') return;
    void (async () => { await loadQueue(); await loadPendingMessages(); await loadPendingTesters(); await loadReferrals(); await loadKillSwitch(); })();
    const poll = setInterval(() => { void loadQueue(); void loadPendingMessages(); void loadPendingTesters(); void loadReferrals(); void loadKillSwitch(); }, QUEUE_POLL_MS);
    const clock = setInterval(() => setNow(Date.now()), 1000);
    return () => { clearInterval(poll); clearInterval(clock); };
  }, [phase, loadQueue, loadPendingMessages, loadPendingTesters, loadReferrals, loadKillSwitch]);

  const signIn = () => {
    if (!SUPABASE_URL) return;
    const redirect = window.location.origin + window.location.pathname;
    window.location.href = `${SUPABASE_URL}/auth/v1/authorize?provider=google&redirect_to=${encodeURIComponent(redirect)}`;
  };

  const signOut = () => {
    localStorage.removeItem(SESSION_KEY);
    setToken(null);
    setPhase('signed_out');
  };

  const toggleKillSwitch = useCallback(async () => {
    if (!token) return;
    const next = !killSwitch;
    setKillSwitch(next);
    try {
      await rpc('moderation_set_kill_switch', { p_enabled: next }, token);
    } catch {
      setKillSwitch(!next);
      showToast('Could not update the kill switch.', true);
    }
  }, [token, killSwitch, showToast]);

  const commitAction = useCallback(async (item: QueueItem, action: Action) => {
    setPending((prev) => {
      const next = { ...prev };
      delete next[item.reign_id];
      return next;
    });
    if (!token) return;
    try {
      await rpc('resolve_moderation_report', { p_reign_id: item.reign_id, p_action: action }, token);
      setQueue((prev) => prev.filter((entry) => entry.reign_id !== item.reign_id));
      showToast(action === 'dismiss' ? 'Dismissed.' : action === 'remove' ? 'Removed.' : 'Banned.');
    } catch {
      showToast(`${action.toUpperCase()} failed — still in the queue.`, true);
      void loadQueue();
    }
  }, [token, showToast, loadQueue]);

  // The undo window is a real countdown, not a debounce: each pending entry
  // schedules its own commit, and removing it from `pending` (via undoAction)
  // cancels that commit through this effect's cleanup — no ref needed.
  useEffect(() => {
    const entries = Object.entries(pending);
    if (entries.length === 0) return;
    const timeouts = entries.map(([, info]) => setTimeout(() => void commitAction(info.item, info.action), Math.max(0, info.deadline - Date.now())));
    return () => { timeouts.forEach((id) => clearTimeout(id)); };
  }, [pending, commitAction]);

  const startAction = useCallback((item: QueueItem, action: Action) => {
    setPending((prev) => ({ ...prev, [item.reign_id]: { item, action, deadline: Date.now() + UNDO_WINDOW_MS } }));
  }, []);

  const undoAction = useCallback((reignId: string) => {
    setPending((prev) => {
      const next = { ...prev };
      delete next[reignId];
      return next;
    });
  }, []);

  if (phase === 'loading') {
    return (
      <main className="mod-shell">
        <div className="grid-glow" aria-hidden="true" />
        <div className="mod-gate">
          <div className="mod-mark"><span>1</span></div>
          <p className="mod-loading-text">SYNCING WITH THE SCREEN…</p>
        </div>
      </main>
    );
  }

  if (phase === 'signed_out') {
    return (
      <main className="mod-shell">
        <div className="grid-glow" aria-hidden="true" />
        <div className="mod-gate">
          <div className="mod-gate-eyebrow"><ShieldCheck /> STAFF ACCESS ONLY</div>
          <div className="mod-mark"><span>1</span></div>
          <h1>THE CONTROL<br />ROOM.</h1>
          <p>Sign in with the Google account linked to your ONE staff profile to open the report queue.</p>
          <button type="button" className="mod-cta" onClick={signIn}><Radio /> SIGN IN WITH GOOGLE</button>
        </div>
      </main>
    );
  }

  if (phase === 'not_staff') {
    return (
      <main className="mod-shell">
        <div className="grid-glow" aria-hidden="true" />
        <div className="mod-gate">
          <div className="mod-denied-tag"><AlertTriangle /> NOT AUTHORIZED</div>
          <div className="mod-mark"><span>1</span></div>
          <h1>WRONG<br />ACCOUNT.</h1>
          <p>Signed in as <strong>{handle}</strong>, but this account doesn&apos;t have staff access on ONE.</p>
          <button type="button" className="mod-ghost" onClick={signOut}><LogOut /> SIGN OUT</button>
        </div>
      </main>
    );
  }

  const displayIds = Array.from(new Set([...queue.map((item) => item.reign_id), ...Object.keys(pending)]));
  const items = displayIds
    .map((id) => queue.find((entry) => entry.reign_id === id) ?? pending[id]?.item)
    .filter((item): item is QueueItem => Boolean(item));
  const autoPulledCount = items.filter((item) => item.auto_pulled).length;

  return (
    <main className="mod-shell">
      <div className="grid-glow" aria-hidden="true" />
      <header className="mod-top">
        <div className="mod-brand">
          <span className="mod-brand-mark">1</span>
          <div><strong>MODERATION</strong><small>CONTROL ROOM</small></div>
        </div>
        <button type="button" className="mod-signout" onClick={signOut}><LogOut /> {handle}</button>
      </header>

      <div className="mod-inner">
        <section className="mod-overview">
          <div className="mod-hero">
            <div className="mod-hero-eyebrow"><i />ONE / TRUST &amp; SAFETY</div>
            <h1>Keep ONE <em>worth watching.</em></h1>
            <p>A little care. A better screen. Review what needs you.</p>
          </div>
            <div className={`mod-kill${killSwitch ? ' active' : ''}`}>
              <div className="mod-kill-icon"><Power /></div>
              <div className="mod-kill-label">
                <strong>{killSwitch ? 'Screen paused' : 'Screen is open'}</strong>
                <span>{killSwitch ? 'Takeovers are disabled. Switch off to resume.' : 'Global pause is off. Takeovers are enabled.'}</span>
              </div>
              <button type="button" className={`mod-toggle${killSwitch ? ' on' : ''}`} onClick={() => void toggleKillSwitch()} role="switch" aria-checked={killSwitch} aria-label="Pause all screen takeovers"><i /></button>
            </div>
        </section>

        <nav className="mod-toolbar" aria-label="Moderation queues">
          <div className="mod-tabs">
            <button type="button" className="mod-tab" aria-pressed={queueView === 'reports'} aria-controls="report-queue" onClick={() => setQueueView('reports')}><Flag />Reports <b>{items.length}</b></button>
            <button type="button" className="mod-tab" aria-pressed={queueView === 'messages'} aria-controls="message-queue" onClick={() => setQueueView('messages')}><Clock3 />Messages <b>{pendingMessages.length}</b></button>
            <button type="button" className="mod-tab" aria-pressed={queueView === 'testers'} aria-controls="tester-queue" onClick={() => setQueueView('testers')}><Users />Testers <b>{pendingTesters.length}</b></button>
            <button type="button" className="mod-tab" aria-pressed={queueView === 'referrals'} aria-controls="referral-queue" onClick={() => setQueueView('referrals')}><Trophy />Referrals <b>{referrals.length}</b></button>
          </div>
          <span className={`mod-auto-count${autoPulledCount ? ' warn' : ''}`}><AlertTriangle />{autoPulledCount} auto-pulled</span>
        </nav>

        <section id="report-queue" className="mod-queue-section" hidden={queueView !== 'reports'} aria-label="Reported reigns">
          <header className="mod-section-head">
            <div><h2>Reported reigns</h2></div>
            <b>{items.length} OPEN</b>
          </header>

        {items.length === 0 ? (
          <div className="mod-empty">
            <ShieldCheck />
            <strong>QUEUE IS CLEAR</strong>
            <span>Nothing waiting on you right now. New reports show up here within a few seconds.</span>
          </div>
        ) : (
          <div className="mod-list mod-report-list">
            {items.map((item, index) => {
              const activePending = pending[item.reign_id];
              if (activePending) {
                const secondsLeft = Math.max(0, Math.ceil((activePending.deadline - now) / 1000));
                const actionLabel = activePending.action === 'dismiss' ? 'DISMISSING' : activePending.action === 'remove' ? 'REMOVING' : 'BANNING';
                return (
                  <div key={item.reign_id} className="mod-card">
                    <div className={`mod-undo ${activePending.action}`}>
                      <svg className="mod-undo-ring" viewBox="0 0 40 40">
                        <circle className="mod-undo-track" cx="20" cy="20" r="17" />
                        <circle className="mod-undo-progress" cx="20" cy="20" r="17" style={{ animationDuration: `${UNDO_WINDOW_MS}ms` }} />
                      </svg>
                      <div className="mod-undo-copy">
                        <span>{actionLabel} IN</span>
                        <strong>{secondsLeft}s</strong>
                      </div>
                      <div className="mod-undo-fill" />
                      <button type="button" className="mod-undo-btn" onClick={() => undoAction(item.reign_id)}><X /> UNDO</button>
                    </div>
                  </div>
                );
              }
              return (
                <article key={item.reign_id} className={`mod-card${item.hold_active ? ' pulled' : ''}`} style={{ animationDelay: `${Math.min(index, 6) * 45}ms` }}>
                  {item.auto_pulled && <div className="mod-pulled-tag"><AlertTriangle /> AUTO-PULLED</div>}
                  <div className="mod-card-head">
                    <div className="mod-owner-chip">
                      <span className="mod-avatar">{item.owner_handle.replace('@', '').slice(0, 1) || '1'}</span>
                      <strong>{item.owner_handle}</strong>
                    </div>
                    <span className="mod-when"><Clock3 /> {timeAgo(item.reign_started_at_ms, now)}</span>
                  </div>
                  <blockquote className="mod-message">&ldquo;{item.message_text}&rdquo;</blockquote>
                  <div className="mod-meta">
                    {item.reasons.map((reason) => <span key={reason} className="mod-chip">{reason}</span>)}
                    <span className="mod-chip"><Users /><strong>{item.unique_reporters}</strong>&nbsp;reporter{item.unique_reporters === 1 ? '' : 's'}</span>
                  </div>
                  <p className="mod-reporters">REPORTED BY <b>{item.reporter_handles.join(', ')}</b></p>
                  <div className="mod-actions">
                    <button type="button" className="mod-btn dismiss" onClick={() => startAction(item, 'dismiss')}><Check /> DISMISS</button>
                    <button type="button" className="mod-btn remove" onClick={() => startAction(item, 'remove')}><Flag /> REMOVE</button>
                    <button type="button" className="mod-btn ban" onClick={() => startAction(item, 'ban')}><Ban /> BAN</button>
                  </div>
                </article>
              );
            })}
          </div>
        )}
        </section>

        <section id="message-queue" className="mod-queue-section" hidden={queueView !== 'messages'} aria-label="Pending messages">
          <header className="mod-section-head">
            <div><h2>Pending messages</h2></div>
            <b>{pendingMessages.length} OPEN</b>
          </header>

        {pendingMessages.length === 0 ? (
          <div className="mod-empty">
            <ShieldCheck />
            <strong>NOTHING WAITING</strong>
            <span>Every submitted message has either auto-cleared or been decided.</span>
          </div>
        ) : (
          <div className="mod-list mod-message-list">
            {pendingMessages.map((item, index) => (
              <article key={item.message_id} className="mod-card mod-pending-card" style={{ animationDelay: `${Math.min(index, 6) * 45}ms` }}>
                {item.is_authors_first_review && <div className="mod-pulled-tag"><AlertTriangle /> AUTHOR&apos;S FIRST MESSAGE</div>}
                <div className="mod-card-head">
                  <div className="mod-owner-chip">
                    <span className="mod-avatar">{item.author_handle.replace('@', '').slice(0, 1) || '1'}</span>
                    <strong>{item.author_handle}</strong>
                  </div>
                  <span className="mod-when"><Clock3 /> {timeAgo(item.created_at_ms, now)}</span>
                </div>
                <blockquote className="mod-message">&ldquo;{item.text}&rdquo;</blockquote>
                <div className="mod-actions mod-message-actions">
                  <button type="button" className="mod-btn dismiss" disabled={decidingMessageId === item.message_id} onClick={() => void decideMessage(item, 'approved')}><Check /> APPROVE</button>
                  <button type="button" className="mod-btn remove" disabled={decidingMessageId === item.message_id} onClick={() => void decideMessage(item, 'rejected')}><X /> REJECT</button>
                </div>
              </article>
            ))}
          </div>
        )}
        </section>

        <section id="tester-queue" className="mod-queue-section" hidden={queueView !== 'testers'} aria-label="Pending testers">
          <header className="mod-section-head">
            <div><h2>Pending testers</h2></div>
            <b>{pendingTesters.length} WAITING</b>
          </header>

        {pendingTesters.length === 0 ? (
          <div className="mod-empty">
            <ShieldCheck />
            <strong>NOTHING WAITING</strong>
            <span>Everyone who signed up has already been approved.</span>
          </div>
        ) : (
          <div className="mod-list mod-message-list">
            {pendingTesters.map((item, index) => (
              <article key={item.id} className="mod-card mod-pending-card" style={{ animationDelay: `${Math.min(index, 6) * 45}ms` }}>
                <div className="mod-card-head">
                  <div className="mod-owner-chip">
                    <span className="mod-avatar">{(item.name || item.email).slice(0, 1).toUpperCase()}</span>
                    <strong>{item.name || item.email}</strong>
                  </div>
                  <span className="mod-when"><Clock3 /> {timeAgo(item.created_at_ms, now)}</span>
                </div>
                <blockquote className="mod-message">{item.email} — {item.device}</blockquote>
                <div className="mod-actions mod-message-actions">
                  <button type="button" className="mod-btn dismiss" disabled={approvingTesterId === item.id} onClick={() => void approveTester(item)}><Check /> APPROVE — SEND INVITE</button>
                </div>
              </article>
            ))}
          </div>
        )}
        </section>

        <section id="referral-queue" className="mod-queue-section" hidden={queueView !== 'referrals'} aria-label="Referral leaderboard">
          <header className="mod-section-head">
            <div><h2>Referral leaderboard</h2></div>
            <b>{referrals.reduce((sum, item) => sum + item.signups, 0)} SIGNUPS</b>
          </header>

          <form className="mod-referral-form" onSubmit={(event) => void createReferral(event)}>
            <input
              type="text"
              placeholder="code (e.g. alex)"
              value={newReferralCode}
              onChange={(event) => setNewReferralCode(event.target.value)}
              maxLength={32}
            />
            <input
              type="text"
              placeholder="name (e.g. Alex)"
              value={newReferralLabel}
              onChange={(event) => setNewReferralLabel(event.target.value)}
              maxLength={80}
            />
            <button type="submit" className="mod-btn dismiss" disabled={creatingReferral}><Trophy /> CREATE LINK</button>
          </form>

          {referrals.length === 0 ? (
            <div className="mod-empty">
              <Trophy />
              <strong>NO LINKS YET</strong>
              <span>Create a named link above for each person you want to track.</span>
            </div>
          ) : (
            <div className="mod-list mod-message-list">
              {referrals.map((item, index) => (
                <article key={item.code} className="mod-card mod-pending-card" style={{ animationDelay: `${Math.min(index, 6) * 45}ms` }}>
                  <div className="mod-card-head">
                    <div className="mod-owner-chip">
                      <span className="mod-avatar">{index === 0 && item.signups > 0 ? '🏆' : item.label.slice(0, 1).toUpperCase()}</span>
                      <strong>{item.label}</strong>
                    </div>
                    <span className="mod-when">{item.signups} signup{item.signups === 1 ? '' : 's'}</span>
                  </div>
                  <blockquote className="mod-message">oneis.live/?ref={item.code}</blockquote>
                  <div className="mod-actions mod-message-actions">
                    <button type="button" className="mod-btn dismiss" onClick={() => void copyReferralLink(item.code)}><Copy /> COPY LINK</button>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      </div>

      {toast && (
        <output className={`mod-toast${toast.error ? ' error' : ''}`}>
          {toast.error ? <AlertTriangle /> : <Check />}
          {toast.text}
        </output>
      )}
    </main>
  );
}
