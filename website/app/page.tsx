'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { ArrowUpRight, ArrowDown, Zap, Play, X, Volume2 } from 'lucide-react';
import './launch.css';
import './trump.css';
import './presence.css';

const PLAY_URL = 'https://play.google.com/store/apps/details?id=com.tomribowei.one';
const SUPABASE_URL = (import.meta.env.VITE_SUPABASE_URL as string | undefined) || 'https://ajkdzohnntrbkeqjskel.supabase.co';
const SUPABASE_KEY = (import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined) || 'sb_publishable_PGTCO8gqoSrbPgjxXbYqdg_F_D6oqO-';
type LiveState = {
  live_watchers: number; takeovers_today: number;
  reign: { sequence: number; started_at_ms: number; palette: string; owner: { handle: string; initials: string }; message: { text: string } };
};
const palettes: Record<string, string> = { ACID: '#d7ff00', COBALT: '#315cff', ORANGE: '#ff4e2b', MAGENTA: '#ff3bbe', ICE: '#72e7ff' };
const examples = [
  { text: 'CEREAL\nIS SOUP.', reply: 'ABSOLUTELY\nNOT.', color: '#76dce9' },
  { text: 'YOU CAN\nSTART AGAIN.', reply: 'I NEEDED\nTHAT.', color: '#d7ff00' },
  { text: 'TOO\nSLOW.', reply: 'YOUR TURN\nIS OVER.', color: '#ff83b6' },
];
function PlayLink({ className = '', children = 'Get ONE on Google Play' }: { className?: string; children?: React.ReactNode }) {
  return <a className={`one-button ${className}`} href={PLAY_URL} target="_blank" rel="noopener noreferrer">{children}<ArrowUpRight size={20} /></a>;
}
function time(ms: number) {
  const seconds = Math.max(0, Math.floor(ms / 1000));
  return [Math.floor(seconds / 3600), Math.floor(seconds / 60) % 60, seconds % 60].map(n => String(n).padStart(2, '0')).join(':');
}
function PhoneMockup({ src, alt, className = '' }: { src: string; alt: string; className?: string }) {
  return <div className={`one-device ${className}`}><div className="device-metal"><span className="device-button device-volume"/><span className="device-button device-power"/><div className="device-glass"><img src={src} alt={alt} loading="lazy" decoding="async"/><span className="device-camera" aria-hidden="true"/><span className="device-reflection" aria-hidden="true"/></div></div></div>;
}

export default function Home() {
  const [example, setExample] = useState(0);
  const [taken, setTaken] = useState(false);
  const [live, setLive] = useState<LiveState | null>(null);
  const [status, setStatus] = useState('Connecting to ONE');
  const [now, setNow] = useState(0);
  const [videoOpen, setVideoOpen] = useState(false);
  const [checkoutBusy, setCheckoutBusy] = useState(false);
  const [checkoutNotice, setCheckoutNotice] = useState('');
  const [draft, setDraft] = useState('');
  const [previewMessage, setPreviewMessage] = useState('');
  const [move, setMove] = useState(0);
  const [showInstall, setShowInstall] = useState(false);
  const [feature, setFeature] = useState(0);
  const [autoplay, setAutoplay] = useState(true);
  const [reducedMotion, setReducedMotion] = useState(false);
  const heroRef = useRef<HTMLElement>(null);
  const productRef = useRef<HTMLElement>(null);
  const [heroVisible, setHeroVisible] = useState(true);
  const [productVisible, setProductVisible] = useState(false);

  useEffect(() => {
    const preference = matchMedia('(prefers-reduced-motion: reduce)');
    const update = () => setReducedMotion(preference.matches);
    update(); preference.addEventListener('change', update);
    const visibility = new IntersectionObserver(entries => entries.forEach(entry => {
      if (entry.target === heroRef.current) setHeroVisible(entry.isIntersecting);
      if (entry.target === productRef.current) setProductVisible(entry.isIntersecting);
    }), { threshold: .12 });
    if (heroRef.current) visibility.observe(heroRef.current);
    if (productRef.current) visibility.observe(productRef.current);
    return () => { preference.removeEventListener('change', update); visibility.disconnect(); };
  }, []);
  useEffect(() => {
    if (!autoplay || reducedMotion || !heroVisible || previewMessage || videoOpen) return;
    const timer = setInterval(() => {
      if (document.hidden) return;
      if (taken) { setExample(n => (n + 1) % examples.length); setTaken(false); }
      else setTaken(true);
      setMove(n => n + 1);
    }, 4200);
    return () => clearInterval(timer);
  }, [autoplay, reducedMotion, heroVisible, previewMessage, videoOpen, taken]);
  useEffect(() => {
    if (!autoplay || reducedMotion || !productVisible || videoOpen) return;
    const timer = setInterval(() => { if (!document.hidden) setFeature(n => (n + 1) % 3); }, 6000);
    return () => clearInterval(timer);
  }, [autoplay, reducedMotion, productVisible, videoOpen]);

  useEffect(() => {
    // Keep referral and Google-account callbacks, without collecting waitlist emails.
    try {
      const ref = new URLSearchParams(location.search).get('ref');
      if (ref && /^[a-z0-9-]{2,32}$/i.test(ref)) localStorage.setItem('one_referral_code', ref.toLowerCase());
      const params = new URLSearchParams(location.hash.slice(1));
      if (params.get('access_token') && params.get('refresh_token')) {
        localStorage.setItem('one_web_session', JSON.stringify({ access_token: params.get('access_token'), refresh_token: params.get('refresh_token'), expires_at: Date.now() + Number(params.get('expires_in') || 3600) * 1000 }));
        history.replaceState(null, '', location.pathname + location.search);
      }
    } catch { /* Keep the public page usable with storage disabled. */ }
    const observer = new IntersectionObserver(entries => entries.forEach(entry => {
      if (entry.isIntersecting) { entry.target.classList.add('is-visible'); observer.unobserve(entry.target); }
    }), { threshold: .08 });
    document.querySelectorAll('.one-reveal').forEach(el => observer.observe(el));
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    let stopped = false, inFlight = false;
    let controller: AbortController | undefined;
    const load = async () => {
      if (inFlight || document.hidden) return;
      inFlight = true;
      controller = new AbortController();
      const timeout = setTimeout(() => controller?.abort(), 8000);
      try {
        const response = await fetch(`${SUPABASE_URL}/rest/v1/rpc/get_one_state`, {
          method: 'POST', signal: controller.signal,
          headers: { apikey: SUPABASE_KEY, authorization: `Bearer ${SUPABASE_KEY}`, 'content-type': 'application/json' }, body: '{}',
        });
        if (!response.ok) throw new Error('unavailable');
        const data = await response.json() as LiveState;
        if (!data?.reign?.message?.text || !data.reign.owner) throw new Error('invalid_state');
        if (!stopped) { setLive(data); setStatus('Live from the app'); }
      } catch { if (!stopped) { setLive(null); setStatus('Live connection unavailable'); } }
      finally { clearTimeout(timeout); inFlight = false; }
    };
    setNow(Date.now()); void load();
    const poll = setInterval(() => void load(), 5000);
    const clock = setInterval(() => setNow(Date.now()), 1000);
    return () => { stopped = true; controller?.abort(); clearInterval(poll); clearInterval(clock); };
  }, []);

  useEffect(() => {
    if (!videoOpen) return;
    const old = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const close = (event: KeyboardEvent) => { if (event.key === 'Escape') setVideoOpen(false); };
    document.addEventListener('keydown', close);
    const previous = document.activeElement as HTMLElement | null;
    document.getElementById('close-film')?.focus();
    return () => { document.body.style.overflow = old; document.removeEventListener('keydown', close); previous?.focus(); };
  }, [videoOpen]);

  const buyCredits = async () => {
    setCheckoutBusy(true); setCheckoutNotice('');
    try {
      const raw = localStorage.getItem('one_web_session');
      let session = raw ? JSON.parse(raw) : null;
      if (session?.refresh_token && session.expires_at < Date.now() + 60000) {
        const refresh = await fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=refresh_token`, { method: 'POST', headers: { apikey: SUPABASE_KEY, 'content-type': 'application/json' }, body: JSON.stringify({ refresh_token: session.refresh_token }) });
        if (refresh.ok) { const data = await refresh.json() as { access_token: string; refresh_token: string; expires_in?: number }; session = { ...data, expires_at: Date.now() + Number(data.expires_in || 3600) * 1000 }; localStorage.setItem('one_web_session', JSON.stringify(session)); }
        else session = null;
      }
      const user = session?.access_token ? await fetch(`${SUPABASE_URL}/auth/v1/user`, { headers: { apikey: SUPABASE_KEY, authorization: `Bearer ${session.access_token}` } }) : null;
      const account = user?.ok ? await user.json() as { is_anonymous?: boolean } : null;
      if (!account || account.is_anonymous !== false) {
        location.href = `${SUPABASE_URL}/auth/v1/authorize?provider=google&redirect_to=${encodeURIComponent(location.origin + location.pathname)}`;
        return;
      }
      const response = await fetch('/api/checkout', { method: 'POST', headers: { authorization: `Bearer ${session.access_token}` } });
      const data = await response.json() as { url?: string };
      if (response.ok && data.url) location.href = data.url;
      else setCheckoutNotice('Web checkout is unavailable right now. You can buy credits inside ONE.');
    } catch { setCheckoutNotice('Could not open checkout. You can buy credits inside ONE.'); }
    finally { setCheckoutBusy(false); }
  };
  const selected = examples[example];
  const demoMessage = previewMessage || (taken ? selected.reply : selected.text);
  const takePreview = () => { setAutoplay(false); setPreviewMessage(''); setTaken(!taken); setMove(n => n + 1); };

  return <main className={`one-launch one-trump one-presence ${taken ? 'screen-taken' : ''} ${autoplay && !reducedMotion ? 'auto-running' : 'auto-paused'}`} style={{ '--demo-color': selected.color } as React.CSSProperties}>
    <a className="one-skip" href="#how">Skip to how ONE works</a>
    <header className="one-nav">
      <Link href="/" className="one-wordmark" aria-label="ONE home"><img src="/one-app-icon.png" alt="" />ONE<span>•</span></Link>
      <nav aria-label="Main navigation"><Link href="/play">Play in browser</Link><a href="#live">Live screen<span className="one-dot" /></a></nav>
      <PlayLink className="one-nav-download">Get the Android app</PlayLink>
    </header>

    <section className="trump-hero" ref={heroRef}>
      <div className="presence-atmosphere" aria-hidden="true"><div/><div/><div/></div>
      <div className="trump-hero-heading"><span className="one-eyebrow"><span className="one-dot" /> ONE SHARED SCREEN. NO INFINITE FEED.</span><h1>THE WORLD’S<br /><span>SMALLEST STAGE.</span></h1><p>Your words become the screen for everyone on ONE.<br /><strong>Until someone else takes your place.</strong></p></div>
      <div className="trump-stage-shell">
        <div className="presence-orbit presence-orbit-one" aria-hidden="true"/><div className="presence-orbit presence-orbit-two" aria-hidden="true"/>
        <div className="presence-device-left"><PhoneMockup src="/screens/current-live.jpg" alt="Real ONE live screen inside an Android phone mockup"/></div>
        <div className="presence-device-right"><PhoneMockup src="/screens/current-words.jpg" alt="Real ONE approved-message library inside an Android phone mockup"/></div>
        <div className="trump-coordinate left" aria-hidden="true">ONE / GLOBAL CHANNEL<br />01 SCREEN · ∞ POSSIBILITIES</div>
        <div className="trump-coordinate right" aria-hidden="true">NO SCROLLING.<br />NOWHERE TO GET BURIED.</div>
        <div className="trump-screen-wall" aria-hidden="true">{Array.from({length:6},(_,i)=><div key={i}><span>ONE / SHARED SCREEN</span><b>{demoMessage}</b><small>SAME SCREEN. DIFFERENT PLACE.</small></div>)}</div>
        <div className="trump-broadcast" key={`broadcast-${move}`}>
          <div className="trump-broadcast-head"><span><span className="one-dot" /> THE SHARED SCREEN</span><span>INTERACTIVE DEMO</span></div>
          <div className={`trump-message ${previewMessage ? 'has-custom-message' : ''}`} aria-live={autoplay && !reducedMotion ? 'off' : 'polite'}>{demoMessage}<i /></div>
          <div className="trump-broadcast-foot"><span>{previewMessage ? 'YOUR WORDS. IMAGINE THEM HERE.' : taken ? 'THE SCREEN JUST CHANGED HANDS.' : 'ONE MESSAGE. NOT A FEED.'}</span><span>↗ ONE</span></div>
          {move > 0 && <div className="trump-takeover-flash" aria-hidden="true">SCREEN TAKEN.</div>}
        </div>
        <button className="trump-take-button" onClick={takePreview}><Zap size={18} fill="currentColor" /><span>{taken ? 'TAKE IT BACK' : 'TAKE THIS SCREEN'}<small>TRY THE DEMO</small></span><ArrowUpRight size={22} /></button>
        <div className="trump-orbit-label" aria-hidden="true"><span>01</span> ONE OWNER AT A TIME.</div>
        <div className="presence-sequence"><span key={`progress-${move}`} className="presence-progress"/><span>{taken ? '02 / SOMEONE TAKES IT' : '01 / ONE MESSAGE OWNS THE SCREEN'}</span></div>
      </div>
      <div className="trump-hero-bottom"><div className="one-demo-tabs" role="group" aria-label="Explore example messages">{examples.map((item,i)=><button key={item.text} aria-pressed={i===example} onClick={()=>{setAutoplay(false);setExample(i);setTaken(false);setPreviewMessage('');setMove(n=>n+1);}}>{['Start a debate','Make someone’s day','Challenge a friend'][i]}</button>)}</div><button className="one-film-link" onClick={()=>setVideoOpen(true)}><span><Play size={13} fill="currentColor" /></span>Watch the film</button></div>
      <div className="trump-hero-caption"><span>Illustrative demo. No real takeover is submitted here.</span><a href="#playground">MAKE YOUR FIRST MOVE <ArrowDown size={14}/></a></div>
    </section>

    <div className="trump-marquee" aria-hidden="true"><div>{Array.from({length:4},(_,i)=><span key={i}>NOT FOR YOUR FOLLOWERS. <b>FOR EVERYONE ON ONE.</b><span>✳</span></span>)}</div></div>

    <section className="trump-playground one-reveal" id="playground">
      <div className="trump-playground-copy"><span className="one-eyebrow">01 / BEFORE YOU DOWNLOAD</span><h2>80 CHARACTERS.<br /><em>YOUR KIND<br />OF CHAOS.</em></h2><p>A ridiculously strong opinion. A tiny act of kindness. An inside joke that escaped the group chat.</p><p>What would you put on the screen?</p></div>
      <form className="trump-composer" onSubmit={e=>{e.preventDefault();if(!draft.trim())return;setAutoplay(false);setPreviewMessage(draft.trim().replace(/\s+/g,' '));setMove(n=>n+1);setShowInstall(true);}}>
        <div className="trump-composer-top"><span><span className="one-dot"/> YOUR FIRST MESSAGE</span><span>LOCAL SANDBOX</span></div>
        <label className="trump-sr-only" htmlFor="one-first-message">Your message, up to 80 characters</label><textarea id="one-first-message" maxLength={80} rows={3} placeholder="Say one thing worth stealing…" value={draft} onChange={e=>{setDraft(e.target.value);setShowInstall(false);}} />
        <div className="trump-composer-count"><span>NO ACCOUNT. NO EMAIL. JUST A PREVIEW.</span><b>{draft.length}/80</b></div>
        <div className="trump-prompt-chips">{['PINEAPPLE BELONGS.','YOU CAN START AGAIN.','MY TURN.'].map(text=><button key={text} type="button" onClick={()=>{setDraft(text);setShowInstall(false);}}>{text}<span>↗</span></button>)}</div>
        <button className="trump-preview-submit" type="submit" disabled={!draft.trim()}>PREVIEW MY MESSAGE<ArrowUpRight size={20}/></button>
        {showInstall && <div className="trump-install-reveal" role="status"><span className="one-eyebrow">LOOKS LIKE YOU HAVE SOMETHING TO SAY.</span><strong>“{previewMessage}”</strong><p>That’s your preview. To put it on the real shared screen, download ONE and submit it for approval.</p><PlayLink>Make it real on Android</PlayLink></div>}
        <small className="trump-sandbox-note">Nothing typed here is saved or sent. Real messages are approved in the app.</small>
      </form>
    </section>

    <section className="one-principle one-reveal" id="how">
      <span className="one-eyebrow">01 / A DIFFERENT KIND OF SOCIAL</span>
      <h2>Everyone on ONE sees<br />the same thing.<br /><em>Until you change it.</em></h2>
      <div className="one-steps">
        <article><span>01 / WRITE</span><h3>Something worth saying.</h3><p>A joke. A shout-out. An opinion nobody asked for. Write your message and get it approved before it goes live.</p></article>
        <article><span>02 / TAKE</span><h3>One move. Your screen.</h3><p>Take over in the app. Your message becomes the shared screen for everyone opening ONE.</p></article>
        <article><span>03 / REPEAT</span><h3>Nothing lasts forever.</h3><p>Someone else can take it next. Come for the competition—or just because you have something to say.</p></article>
      </div>
    </section>

    <section className="one-live-section" id="live" style={{ '--live-accent': live ? palettes[live.reign.palette] || '#d7ff00' : '#d7ff00' } as React.CSSProperties}>
      <div className="one-live-header"><span className="one-eyebrow">02 / THE ACTUAL SHARED SCREEN</span><span className={`one-connection ${live ? 'connected' : ''}`}><span className="one-dot" />{status}</span></div>
      <div className="one-live-stage">
        <div className="one-live-owner"><span className="one-live-avatar">{live?.reign.owner.initials || '1'}</span><div><small>{live ? 'CURRENT OWNER' : 'ONE / LIVE VIEW'}</small><strong>{live?.reign.owner.handle || 'THE NEXT WORD IS YOURS.'}</strong></div>{live && <span className="one-reign-number">REIGN #{live.reign.sequence}</span>}</div>
        <blockquote key={live?.reign.sequence}>{live?.reign.message.text || (status.startsWith('Connecting') ? 'TUNING IN.' : 'THE APP IS\nSTILL YOUR WAY IN.')}</blockquote>
        <div className="one-live-footer">{live ? <><span><small>WATCHING NOW</small><b>{live.live_watchers}</b></span><span><small>CURRENT REIGN</small><b>{time(now - live.reign.started_at_ms)}</b></span><span><small>TAKEOVERS TODAY</small><b>{live.takeovers_today}</b></span></> : <p>No made-up live numbers. The real screen appears here when the connection is available.</p>}<Link className="one-button one-live-cta" href="/play">Open the live game<ArrowUpRight size={20}/></Link></div>
      </div>
      <p className="one-live-note">Watch here. Take the screen in the Android app.</p>
    </section>

    <section className="one-inside trump-product one-reveal" ref={productRef}>
      <div className="trump-product-visual"><span className="one-eyebrow">03 / THE REAL APP</span><div className="trump-product-number" aria-hidden="true">0{feature+1}</div><figure key={feature}><PhoneMockup src={`/screens/${['current-words.jpg','current-live.jpg','current-hall.jpg'][feature]}`} alt={['Your Words: approved messages ready to deploy','Live ONE screen: watch, react and take over','The Hall: ranked from verified live reigns'][feature]}/><figcaption>REAL APP SCREEN / ANDROID DEVICE MOCKUP</figcaption></figure></div>
      <div className="trump-product-copy"><span className="one-eyebrow">ONE APP. THREE MOVES.</span><h2>NOT A FEED.<br /><em>A BACK-<br />AND-FORTH.</em></h2><div className="trump-feature-choices" role="group" aria-label="Explore the real app">{[
        ['Write it.','Your own little arsenal of approved messages, ready when it’s your turn.'],
        ['Take it.','One shared message. Live reactions. Make your move when the screen is open.'],
        ['Make the Hall.','Your reign has a history. See the standings built from verified live reigns.'],
      ].map(([title,copy],i)=><button key={title} aria-pressed={feature===i} onClick={()=>{setAutoplay(false);setFeature(i);}}><span>0{i+1}</span><div><strong>{title}</strong>{feature===i&&<p>{copy}</p>}</div><ArrowUpRight size={19}/></button>)}</div><PlayLink>Get ONE on Google Play</PlayLink></div>
    </section>

    <section className="one-fair one-reveal">
      <span className="one-eyebrow">04 / NO PERMANENT THRONES</span>
      <div><h2>MONEY BUYS<br /><em>IMPATIENCE.</em><br />NOT THE SCREEN.</h2><div className="one-fair-copy"><span className="one-free-label"><Zap size={16} /> FREE TO TAKE</span><p>Free takes refill after a wait. Optional credits let you skip that wait.</p><p><strong>You still don’t own the screen forever.</strong> Someone else can take it from you.</p><button className="one-text-link" onClick={() => void buyCredits()} disabled={checkoutBusy}>{checkoutBusy ? 'Opening checkout…' : 'Already playing? Get credits'}<ArrowUpRight size={18} /></button><small>Use the same Google account linked in your app.</small>{checkoutNotice && <p role="status" className="one-checkout-status">{checkoutNotice}</p>}</div></div>
    </section>

    <section className="one-final" id="download"><span className="one-eyebrow">ONE IS LIVE. NO INVITE NEEDED.</span><h2>WHAT WOULD<br /><span>YOU SAY?</span></h2><PlayLink className="one-final-button"><span className="one-play-symbol" aria-hidden="true">▶</span><span><small>AVAILABLE NOW ON</small>Google Play</span></PlayLink><p>One screen. Your Android. Your turn.</p><div className="one-final-watermark" aria-hidden="true">ONE</div></section>
    <footer className="one-footer"><Link href="/" className="one-wordmark">ONE</Link><span>Built by Bowei. Made for your next move.</span><nav aria-label="Legal and support"><a href="/privacy">Privacy</a><a href="/terms">Terms</a><a href="/delete-account">Delete account</a><a href="mailto:oneglobalscreen@gmail.com">Contact</a></nav></footer>

    {videoOpen && <div className="one-film-modal" role="dialog" aria-modal="true" aria-label="ONE — The Spot film" onClick={e => { if (e.target === e.currentTarget) setVideoOpen(false); }} onKeyDown={e => {
      if (e.key === 'Tab') { const items = Array.from(e.currentTarget.querySelectorAll<HTMLElement>('button, video')); const first = items[0], last = items[items.length - 1]; if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last?.focus(); } else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first?.focus(); } }
    }}><div className="one-film-window"><div><span><Volume2 size={16} /> ONE / THE SPOT</span><button id="close-film" aria-label="Close film" onClick={() => setVideoOpen(false)}><X /></button></div><video controls autoPlay playsInline tabIndex={0} preload="metadata" poster="/film-poster.png"><source src="/one-the-spot.mp4" type="video/mp4" /><track kind="captions" src="/one-the-spot.vtt" srcLang="en" label="English" default /></video><p>One shared screen. Make your move.</p></div></div>}
  </main>;
}
