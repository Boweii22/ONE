const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function escapeHtml(value: string) {
  return value.replace(/[&<>'"]/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[character] || character);
}

export async function POST(request: Request) {
  try {
    const body = await request.json() as { email?: string; name?: string; device?: string; company?: string };
    if (body.company) return Response.json({ ok: true });
    const email = String(body.email || '').trim().toLowerCase();
    const name = String(body.name || '').trim().slice(0, 80);
    const device = String(body.device || 'Android phone').trim().slice(0, 80);
    if (!emailPattern.test(email)) return Response.json({ error: 'Enter a valid email.' }, { status: 400 });

    const supabaseUrl = process.env.VITE_SUPABASE_URL;
    const supabaseKey = process.env.VITE_SUPABASE_ANON_KEY;
    if (supabaseUrl && supabaseKey) {
      await fetch(`${supabaseUrl}/rest/v1/tester_interest`, { method: 'POST', headers: { apikey: supabaseKey, authorization: `Bearer ${supabaseKey}`, 'content-type': 'application/json', Prefer: 'return=minimal' }, body: JSON.stringify({ email, name: name || null, device, source: 'one_launch_site' }) });
    }

    const apiKey = process.env.RESEND_API_KEY;
    const playUrl = process.env.TESTER_PLAY_URL;
    if (apiKey && playUrl) {
      const safeName = escapeHtml(name || 'Founding tester');
      const response = await fetch('https://api.resend.com/emails', {
        method: 'POST', headers: { authorization: `Bearer ${apiKey}`, 'content-type': 'application/json' },
        body: JSON.stringify({ from: process.env.TESTER_FROM_EMAIL || 'ONE <onboarding@resend.dev>', reply_to: process.env.TESTER_REPLY_TO || 'oneglobalscreen@gmail.com', to: [email], subject: "You're invited to test ONE", html: `<!doctype html><html><body style="margin:0;background:#09090b;color:#f5f5ef;font-family:Arial,sans-serif"><div style="max-width:600px;margin:auto;padding:44px 22px"><div style="color:#c8ff33;font-weight:900;letter-spacing:.2em">● ONE / FOUNDING TEST</div><h1 style="font-size:52px;line-height:.92;letter-spacing:-.06em;margin:42px 0 22px">THE SCREEN<br>IS WAITING.</h1><p style="font-size:18px;line-height:1.6;color:#aaaab2">Hey ${safeName} — you’re in. Install ONE with the same Google account you used to join the test.</p><a href="${escapeHtml(playUrl)}" style="display:block;margin:34px 0;padding:20px;background:#c8ff33;color:#09090b;text-align:center;text-decoration:none;font-weight:900">INSTALL ONE →</a><p style="font-size:13px;color:#71717a">If Play Store says unavailable, confirm this email is on the tester list and use the same Google account.</p><hr style="border:0;border-top:1px solid #29292f;margin:38px 0"><p style="font-size:11px;letter-spacing:.12em;color:#71717a">ONE PERSON. ONE MESSAGE. ONE GLOBAL SCREEN.</p></div></body></html>` }),
      });
      if (!response.ok) return Response.json({ error: 'The invite could not be sent.' }, { status: 502 });
    }
    return Response.json({ ok: true, emailed: Boolean(apiKey && playUrl) });
  } catch { return Response.json({ error: 'Something went wrong.' }, { status: 500 }); }
}
