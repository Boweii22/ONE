const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function escapeHtml(value: string) {
  return value.replace(/[&<>'"]/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[character] || character);
}

function emailShell(eyebrow: string, body: string) {
  return `<!doctype html><html><body style="margin:0;background:#09090b;color:#f5f5ef;font-family:Arial,sans-serif"><div style="max-width:600px;margin:auto;padding:48px 24px">
<div style="display:flex;align-items:center;gap:10px;color:#c8ff33;font-weight:900;letter-spacing:.2em;font-size:12px">
  <span style="display:inline-flex;align-items:center;justify-content:center;width:22px;height:22px;border-radius:50%;background:#c8ff33;color:#09090b;font-family:Georgia,serif;font-weight:900">1</span>
  ${eyebrow}
</div>
${body}
<p style="font-size:11px;letter-spacing:.12em;color:#52525b;margin:36px 0 0">ONE PERSON. ONE MESSAGE. ONE GLOBAL SCREEN.</p>
</div></body></html>`;
}

// Sent the moment someone signs up. Deliberately carries no Play Store link -
// they aren't actually on the Play Console tester list yet, so a link here
// would just 404 for them. The real link only goes out from
// send-tester-invite, once a staff member has manually added them and hit
// Approve in the moderation console's Pending Testers queue.
function welcomeEmailHtml(name: string, device: string) {
  const safeName = escapeHtml(name || 'friend');
  const safeDevice = escapeHtml(device);
  return emailShell('YOU\'RE ON THE LIST', `
<h1 style="font-size:46px;line-height:1;letter-spacing:-.05em;margin:34px 0 20px;font-weight:900">Welcome to<br>the ONE family.</h1>
<p style="font-size:17px;line-height:1.65;color:#d4d4d8;margin:0 0 18px">Hey ${safeName} — thank you for wanting to test ONE. I've saved your spot for a ${safeDevice}, and honestly, that means a lot. This is a one-person project, and early testers like you are what actually make it real.</p>
<p style="font-size:17px;line-height:1.65;color:#d4d4d8;margin:0 0 6px">I'm adding testers to the Play Console by hand right now, so it's not instant — but you'll get a second email with your install link personally from me as soon as you're in.</p>
<hr style="border:0;border-top:1px solid #29292f;margin:40px 0 28px">
<p style="font-size:15px;line-height:1.6;color:#a1a1aa;margin:0 0 6px">Questions, bugs, anything at all — just reply to this email. I read every one.</p>
<p style="font-size:15px;color:#f5f5ef;margin:0;font-weight:700">— Bowei, builder of ONE</p>
`);
}

export async function POST(request: Request) {
  try {
    const body = await request.json() as { email?: string; name?: string; device?: string; company?: string; ref?: string };
    if (body.company) return Response.json({ ok: true });
    const email = String(body.email || '').trim().toLowerCase();
    const name = String(body.name || '').trim().slice(0, 80);
    const device = String(body.device || 'Android phone').trim().slice(0, 80);
    const ref = /^[a-z0-9-]{2,32}$/.test(String(body.ref || '').toLowerCase()) ? String(body.ref).toLowerCase() : '';
    if (!emailPattern.test(email)) return Response.json({ error: 'Enter a valid email.' }, { status: 400 });

    const supabaseUrl = process.env.VITE_SUPABASE_URL;
    const supabaseKey = process.env.VITE_SUPABASE_ANON_KEY;
    let saved = false;
    if (supabaseUrl && supabaseKey) {
      const insertRow = (referral_code: string | null) => fetch(`${supabaseUrl}/rest/v1/tester_interest`, { method: 'POST', headers: { apikey: supabaseKey, authorization: `Bearer ${supabaseKey}`, 'content-type': 'application/json', Prefer: 'return=minimal' }, body: JSON.stringify({ email, name: name || null, device, source: 'one_public_spectator', referral_code }) });
      let storage = await insertRow(ref || null);
      // An unknown/stale ref code fails the foreign key - retry without it
      // rather than let attribution block a real signup.
      if (!storage.ok && storage.status !== 409 && ref) storage = await insertRow(null);
      saved = storage.ok || storage.status === 409;
    }
    if (!saved) return Response.json({ error: 'Test requests are temporarily unavailable.' }, { status: 503 });

    const apiKey = process.env.RESEND_API_KEY;
    let emailed = false;
    if (apiKey) {
      const response = await fetch('https://api.resend.com/emails', {
        method: 'POST', headers: { authorization: `Bearer ${apiKey}`, 'content-type': 'application/json' },
        body: JSON.stringify({
          from: process.env.TESTER_FROM_EMAIL || 'ONE <onboarding@resend.dev>',
          reply_to: process.env.TESTER_REPLY_TO || 'oneglobalscreen@gmail.com',
          to: [email],
          subject: "You're on the list — welcome to ONE",
          html: welcomeEmailHtml(name, device),
        }),
      });
      emailed = response.ok;
    }
    return Response.json({ ok: true, emailed });
  } catch { return Response.json({ error: 'Something went wrong.' }, { status: 500 }); }
}
