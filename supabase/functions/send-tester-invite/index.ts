function escapeHtml(value: string) {
  return value.replace(/[&<>'"]/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[character] || character);
}

Deno.serve(async (request) => {
  if (request.method !== "POST") {
    return Response.json({ error: "method_not_allowed" }, { status: 405 });
  }

  const hookSecret = Deno.env.get("MODERATION_HOOK_SECRET") ?? "";
  if (!hookSecret || request.headers.get("authorization") !== `Bearer ${hookSecret}`) {
    return Response.json({ error: "unauthorized" }, { status: 401 });
  }

  const payload = await request.json();
  const email = String(payload?.email ?? "");
  const name = String(payload?.name ?? "");
  const device = String(payload?.device ?? "Android phone");
  if (!email) return Response.json({ ok: true, ignored: true });

  const apiKey = Deno.env.get("RESEND_API_KEY") ?? "";
  const playUrl = Deno.env.get("TESTER_PLAY_URL") ?? "";
  if (!apiKey || !playUrl) return Response.json({ ok: true, configured: false });

  const safeName = escapeHtml(name || "friend");
  const safeDevice = escapeHtml(device);
  const html = `<!doctype html><html><body style="margin:0;background:#09090b;color:#f5f5ef;font-family:Arial,sans-serif"><div style="max-width:600px;margin:auto;padding:48px 24px">
<div style="display:flex;align-items:center;gap:10px;color:#c8ff33;font-weight:900;letter-spacing:.2em;font-size:12px">
  <span style="display:inline-flex;align-items:center;justify-content:center;width:22px;height:22px;border-radius:50%;background:#c8ff33;color:#09090b;font-family:Georgia,serif;font-weight:900">1</span>
  YOU'RE APPROVED
</div>
<h1 style="font-size:46px;line-height:1;letter-spacing:-.05em;margin:34px 0 20px;font-weight:900">You're in.<br>Take the screen.</h1>
<p style="font-size:17px;line-height:1.65;color:#d4d4d8;margin:0 0 18px">Hey ${safeName} — you've been added as a founding tester on your ${safeDevice}. Tap below to open Google Play and install ONE.</p>
<p style="font-size:17px;line-height:1.65;color:#d4d4d8;margin:0 0 30px">Use <strong style="color:#f5f5ef">this exact email's Google account</strong> on that device, or Play won't recognise you as a tester.</p>
<a href="${escapeHtml(playUrl)}" style="display:block;padding:20px;background:#c8ff33;color:#09090b;text-align:center;text-decoration:none;font-weight:900;font-size:15px;letter-spacing:.02em;border-radius:6px">OPEN GOOGLE PLAY →</a>
<p style="font-size:13px;line-height:1.6;color:#71717a;margin:28px 0 0">If Play says the app isn't available, double-check you're signed into this same Google account on that device.</p>
<hr style="border:0;border-top:1px solid #29292f;margin:40px 0 28px">
<p style="font-size:15px;line-height:1.6;color:#a1a1aa;margin:0 0 6px">Questions, bugs, anything at all — just reply to this email. I read every one.</p>
<p style="font-size:15px;color:#f5f5ef;margin:0;font-weight:700">— Bowei, builder of ONE</p>
<p style="font-size:11px;letter-spacing:.12em;color:#52525b;margin:36px 0 0">ONE PERSON. ONE MESSAGE. ONE GLOBAL SCREEN.</p>
</div></body></html>`;

  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { authorization: `Bearer ${apiKey}`, "content-type": "application/json" },
    body: JSON.stringify({
      from: Deno.env.get("TESTER_FROM_EMAIL") || "ONE <onboarding@resend.dev>",
      reply_to: Deno.env.get("TESTER_REPLY_TO") || "oneglobalscreen@gmail.com",
      to: [email],
      subject: "You're approved — install ONE now",
      html,
    }),
  });

  const result = await response.json().catch(() => null);
  if (!response.ok) {
    return Response.json({ ok: false, resend_status: response.status, resend_result: result }, { status: 502 });
  }
  return Response.json({ ok: true, resend_result: result });
});
