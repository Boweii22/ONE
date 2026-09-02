import Link from 'next/link';

export const metadata = {
  title: 'Privacy Policy — ONE',
  description: 'How ONE collects, uses, shares and deletes data.',
};

const email = 'oneglobalscreen@gmail.com';

export default function PrivacyPolicy() {
  return (
    <main className="legal-shell">
      <div className="legal-wrap">
        <nav className="legal-nav"><Link href="/" className="brand"><strong>1</strong><span>ONE</span></Link><Link href="/">BACK TO LIVE SCREEN</Link></nav>
        <div className="legal-kicker">PUBLIC POLICY / VERSION 1.0</div>
        <h1>PRIVACY,<br />WITHOUT FOG.</h1>
        <div className="legal-meta"><span>EFFECTIVE 2 SEPTEMBER 2026</span><span>ONE / ANDROID + WEB</span></div>
        <p className="legal-lead">ONE is a public, live message game. This policy explains what data we use to run the global screen, what other people can see, and how you can delete your anonymous account.</p>

        <section className="legal-card"><h2>Who operates ONE</h2><p>ONE is operated by the independent developer of the ONE app. Privacy and safety questions can be sent to <a href={`mailto:${email}`}>{email}</a>.</p></section>
        <section className="legal-card"><h2>Data we collect</h2><ul><li>An automatically created anonymous account ID and session tokens.</li><li>Your optional public handle, submitted messages, reactions, reports, blocks, takeovers, timestamps, ticket balance and purchase status.</li><li>App and web view events, brief presence heartbeats, and standard security or diagnostic logs such as IP address, device/browser information and request records.</li><li>Push-notification identifiers if you enable alerts.</li><li>Purchase identifiers and entitlement information from Google Play and RevenueCat. ONE does not receive your full payment-card details.</li></ul></section>
        <section className="legal-card"><h2>What becomes public</h2><p>Your chosen handle, approved live message, reactions, reign timing and leaderboard results may be displayed worldwide in the Android app and public web spectator. Never submit a legal name, address, phone number or other private information.</p></section>
        <section className="legal-card"><h2>Why we use data</h2><p>We use this data to create your anonymous identity; operate the live screen and race-safe takeovers; count legitimate views; provide purchases and alerts; moderate, investigate reports, block abuse and enforce our rules; prevent fraud; maintain reliability; and comply with law.</p></section>
        <section className="legal-card"><h2>Service providers</h2><p>ONE relies on Supabase for authentication, database and backend services; Google Play for Android distribution and billing; RevenueCat for purchase and entitlement processing; OneSignal for push notifications; and our website hosting provider for the public spectator. These providers process limited data under their own privacy terms. We do not sell personal data. ONE currently contains no third-party advertising; this policy and the Play declaration will be updated before that changes.</p></section>
        <section className="legal-card"><h2>What we do not request</h2><p>ONE does not require your legal name, email, phone number, contacts, precise location, camera, microphone or photo library. The game is intended for people aged 18 or older.</p></section>
        <section className="legal-card"><h2>Retention and deletion</h2><p>Active account data is kept while your anonymous account exists and only as long as reasonably needed to operate, secure and comply with legal obligations. You can delete the account inside the app under <strong>YOU → DELETE ACCOUNT</strong>. Your profile, handle, message library, reactions, reports, blocks, tickets and session are removed. Historical reign records are retained only in anonymised form as <strong>@DELETED</strong> so the shared ledger cannot be secretly rewritten. Limited fraud, transaction, safety or legal records may be retained where required.</p><Link className="delete-cta" href="/delete-account">DELETE-ACCOUNT INSTRUCTIONS</Link></section>
        <section className="legal-card"><h2>Your choices</h2><p>You can keep the generated alias, choose a public handle, disable notifications in Android settings, block other users, report content, clear your block list, or delete your account. To ask a privacy question or request help, email <a href={`mailto:${email}`}>{email}</a>.</p></section>
        <section className="legal-card"><h2>Security and changes</h2><p>We use access controls, server-authoritative actions and moderation safeguards, but no online service can promise absolute security. Material policy changes will be published on this page with a revised effective date.</p></section>
      </div>
    </main>
  );
}
