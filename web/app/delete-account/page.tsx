import Link from 'next/link';

export const metadata = {
  title: 'Delete Your ONE Account',
  description: 'Delete your ONE anonymous account and associated data.',
};

const email = 'oneglobalscreen@gmail.com';

export default function DeleteAccount() {
  const subject = encodeURIComponent('Delete my ONE account');
  return (
    <main className="legal-shell">
      <div className="legal-wrap">
        <nav className="legal-nav"><Link href="/" className="brand"><strong>1</strong><span>ONE</span></Link><Link href="/">BACK TO LIVE SCREEN</Link></nav>
        <div className="legal-kicker">ACCOUNT + DATA DELETION</div>
        <h1>LEAVE<br />NO IDENTITY.</h1>
        <p className="legal-lead">This page is the public deletion route for <strong>ONE</strong>, package <strong>com.tomribowei.one</strong>. You do not need an email or password to use ONE.</p>

        <section className="legal-card"><h2>Fastest: delete inside the app</h2><p>Open ONE, go to <strong>YOU</strong>, tap <strong>DELETE ACCOUNT</strong>, read the warning, then tap <strong>PERMANENTLY DELETE ACCOUNT</strong>. The server deletes the current anonymous identity immediately and the app creates a fresh anonymous identity.</p></section>
        <section className="legal-card"><h2>If you cannot open the app</h2><p>Email <a href={`mailto:${email}?subject=${subject}`}>{email}</a> with the subject <strong>Delete my ONE account</strong>. Include your public handle and any details that can help us locate the anonymous account. Because ONE does not collect an email login, we may need reasonable proof that the account belongs to you before deleting it.</p><a className="delete-cta" href={`mailto:${email}?subject=${subject}`}>EMAIL DELETION REQUEST</a></section>
        <section className="legal-card danger-note"><h2>What is deleted</h2><ul><li>Anonymous profile and public handle</li><li>Message library and authored messages</li><li>Reactions, reports and block list</li><li>Credit ledger, takeover requests and app session</li><li>Associated presence and view events</li></ul></section>
        <section className="legal-card"><h2>What may remain</h2><p>Past reigns remain only as anonymised <strong>@DELETED / ACCOUNT DELETED</strong> entries to preserve the integrity of the shared global ledger. Purchase providers may retain transaction records required for accounting, fraud prevention or law. Minimal safety or legal records may also be retained where necessary. Email-assisted requests are normally completed within 30 days.</p></section>
        <section className="legal-card"><h2>Need help?</h2><p>Contact <a href={`mailto:${email}`}>{email}</a>. Read the full <Link href="/privacy">ONE Privacy Policy</Link>.</p></section>
      </div>
    </main>
  );
}
