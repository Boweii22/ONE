'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import './moderation.css';

const SUPABASE_URL = import.meta.env.VITE_SUPABASE_URL as string | undefined;
const SUPABASE_KEY = import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined;
const SESSION_KEY = 'one_web_session';
const UNDO_WINDOW_MS = 4000;
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
  const [queue, setQueue] = useState<QueueItem[]>([]);
  const [pending, setPending] = useState<Record<string, PendingAction>>({});
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
    void (async () => { await loadQueue(); await loadKillSwitch(); })();
    const poll = setInterval(() => { void loadQueue(); void loadKillSwitch(); }, QUEUE_POLL_MS);
    const clock = setInterval(() => setNow(Date.now()), 1000);
    return () => { clearInterval(poll); clearInterval(clock); };
  }, [phase, loadQueue, loadKillSwitch]);

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
    return <main className="mod-shell"><div className="mod-center"><p>Loading…</p></div></main>;
  }

  if (phase === 'signed_out') {
    return (
      <main className="mod-shell">
        <div className="mod-center">
          <span style={{ fontWeight: 900, fontSize: 40, color: 'var(--acid)' }}>1</span>
          <p>The moderation console needs a Google sign-in with staff access on your ONE account.</p>
          <button type="button" className="mod-signin" onClick={signIn}>SIGN IN WITH GOOGLE</button>
        </div>
      </main>
    );
  }

  if (phase === 'not_staff') {
    return (
      <main className="mod-shell">
        <div className="mod-center">
          <p>Signed in as <strong>{handle}</strong>, but this account doesn&apos;t have staff access.</p>
          <button type="button" className="mod-signin" onClick={signOut}>SIGN OUT</button>
        </div>
      </main>
    );
  }

  const displayIds = Array.from(new Set([...queue.map((item) => item.reign_id), ...Object.keys(pending)]));
  const items = displayIds
    .map((id) => queue.find((entry) => entry.reign_id === id) ?? pending[id]?.item)
    .filter((item): item is QueueItem => Boolean(item));

  return (
    <main className="mod-shell">
      <header className="mod-top">
        <span className="mod-brand"><strong>1</strong><span>MODERATION</span></span>
        <button type="button" className="mod-signout" onClick={signOut}>SIGN OUT · {handle}</button>
      </header>

      <div className={`mod-kill${killSwitch ? ' active' : ''}`}>
        <div className="mod-kill-label">
          <strong>GLOBAL KILL SWITCH</strong>
          <span>{killSwitch ? 'ONE IS PAUSED FOR EVERYONE' : 'ONE IS LIVE'}</span>
        </div>
        <button type="button" className={`mod-toggle${killSwitch ? ' on' : ''}`} onClick={() => void toggleKillSwitch()} aria-label="Toggle global kill switch"><i /></button>
      </div>

      <div className="mod-count"><span>UNRESOLVED REPORTS</span><b>{items.length}</b></div>

      {items.length === 0 ? (
        <div className="mod-empty">Queue is clear.</div>
      ) : (
        items.map((item) => {
          const activePending = pending[item.reign_id];
          if (activePending) {
            const secondsLeft = Math.max(0, Math.ceil((activePending.deadline - now) / 1000));
            return (
              <div key={item.reign_id} className="mod-card">
                <div className="mod-undo">
                  <span>{activePending.action.toUpperCase()} in <b>{secondsLeft}s</b></span>
                  <button type="button" className="mod-undo-btn" onClick={() => undoAction(item.reign_id)}>UNDO</button>
                </div>
              </div>
            );
          }
          return (
            <div key={item.reign_id} className={`mod-card${item.hold_active ? ' pulled' : ''}`}>
              {item.auto_pulled && <div className="mod-pulled-tag">● AUTO-PULLED</div>}
              <div className="mod-card-head">
                <span className="mod-owner">{item.owner_handle}</span>
                <span className="mod-when">{timeAgo(item.reign_started_at_ms, now)}</span>
              </div>
              <p className="mod-message">&ldquo;{item.message_text}&rdquo;</p>
              <div className="mod-meta">
                {item.reasons.map((reason) => <span key={reason} className="mod-chip">{reason}</span>)}
                <span className="mod-chip"><strong>{item.unique_reporters}</strong> reporter{item.unique_reporters === 1 ? '' : 's'}</span>
              </div>
              <p className="mod-reporters">Reported by <b>{item.reporter_handles.join(', ')}</b></p>
              <div className="mod-actions">
                <button type="button" className="mod-btn dismiss" onClick={() => startAction(item, 'dismiss')}>DISMISS</button>
                <button type="button" className="mod-btn remove" onClick={() => startAction(item, 'remove')}>REMOVE</button>
                <button type="button" className="mod-btn ban" onClick={() => startAction(item, 'ban')}>BAN</button>
              </div>
            </div>
          );
        })
      )}

      {toast && <div className={`mod-toast${toast.error ? ' error' : ''}`}>{toast.text}</div>}
    </main>
  );
}
