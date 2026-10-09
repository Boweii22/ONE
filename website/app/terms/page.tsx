export const metadata = {
  title: 'Terms of Service — ONE',
  description: 'The rules for playing ONE, on the app and on the web.',
};

const email = 'oneglobalscreen@gmail.com';

export default function Terms() {
  return (
    <main className="legal-shell">
      <div className="legal-wrap">
        <nav className="legal-nav"><a href="https://oneis.live/" className="brand"><strong>1</strong><span>ONE</span></a><a href="https://oneis.live/">BACK TO LIVE SCREEN</a></nav>
        <div className="legal-kicker">PUBLIC POLICY / VERSION 1.0</div>
        <h1>ONE SCREEN,<br />SOME RULES.</h1>
        <div className="legal-meta"><span>EFFECTIVE 13 SEPTEMBER 2026</span><span>ONE / ANDROID + WEB</span></div>
        <p className="legal-lead">These terms cover the ONE Android app and the public oneis.live spectator site. By using either, you agree to them. If you do not agree, do not use ONE.</p>

        <section className="legal-card"><h2>Who operates ONE</h2><p>ONE is operated by the independent developer of the ONE app. Questions about these terms can be sent to <a href={`mailto:${email}`}>{email}</a>.</p></section>
        <section className="legal-card"><h2>Eligibility</h2><p>ONE is intended for people aged 18 or older. By using ONE you confirm you meet this requirement and that your use complies with the laws that apply to you.</p></section>
        <section className="legal-card"><h2>What ONE is</h2><p>ONE is a live, public, single-screen game: one account holds the screen and its message at a time, everyone watching sees the same state, and any Android player can take it. The web spectator at oneis.live is read-only and cannot take the screen — only the Android app can.</p></section>
        <section className="legal-card"><h2>Your account and content</h2><p>ONE creates an anonymous account for you automatically; no email or password is required to play. You choose your own public handle and messages. Anything you publish as the owner, or approve into your message library, may be shown worldwide in the app and on the public web spectator. Do not submit anyone&apos;s legal name, address, phone number, illegal content, hate speech, harassment, or other material that violates these terms or applicable law.</p></section>
        <section className="legal-card"><h2>Moderation and enforcement</h2><p>Messages are screened before they can be broadcast. Other players can report content, and you can block accounts you do not want to interact with. We may remove content, restrict an account, or permanently disable it for violating these terms, attempting to exploit or manipulate the game&apos;s mechanics, or abusing other players. Reports are reviewed and acted on at our discretion.</p></section>
        <section className="legal-card"><h2>Fair play</h2><p>Taking the screen is always free. Do not use bots, scripts, multiple accounts, or technical exploits to gain takeovers, credits, or reactions you would not otherwise get. We may reverse effects of exploited behaviour and disable accounts involved.</p></section>
        <section className="legal-card"><h2>Purchases</h2><p>ONE Credits let you skip the take-refill wait; they never remove the free, core action of taking the screen. Credits are sold either as an in-app purchase through Google Play, or through a web checkout powered by RevenueCat and Stripe. Purchases are billed and processed by Google Play or Stripe respectively, and are subject to their own payment terms and refund policies — ONE does not process or store your card details. Rewarded ads grant a take directly and are never purchasable or exchangeable for credits.</p></section>
        <section className="legal-card"><h2>Availability</h2><p>ONE is a live service and may be interrupted, changed, or discontinued, including for maintenance, moderation, or abuse response. We do not guarantee uninterrupted access to the screen, the web spectator, or any specific reign duration.</p></section>
        <section className="legal-card"><h2>Disclaimer and liability</h2><p>ONE is provided &quot;as is&quot; without warranties of any kind. To the fullest extent permitted by law, we are not liable for indirect, incidental, or consequential damages arising from your use of ONE, including content posted by other players.</p></section>
        <section className="legal-card"><h2>Changes to these terms</h2><p>We may update these terms as ONE changes. Material changes will be published on this page with a revised effective date. Continuing to use ONE after a change means you accept the update.</p></section>
        <section className="legal-card"><h2>Account deletion</h2><p>You can delete your account and its data at any time — see <a href="https://oneis.live/delete-account">delete-account instructions</a>. Read the <a href="https://oneis.live/privacy">ONE Privacy Policy</a> for how your data is handled.</p></section>
      </div>
    </main>
  );
}
