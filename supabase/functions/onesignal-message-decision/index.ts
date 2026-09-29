Deno.serve(async (request) => {
  if (request.method !== "POST") {
    return Response.json({ error: "method_not_allowed" }, { status: 405 });
  }

  const hookSecret = Deno.env.get("MODERATION_HOOK_SECRET") ?? "";
  if (!hookSecret || request.headers.get("authorization") !== `Bearer ${hookSecret}`) {
    return Response.json({ error: "unauthorized" }, { status: 401 });
  }

  const payload = await request.json();
  const authorId = String(payload?.author_id ?? "");
  const messageText = String(payload?.message_text ?? "");
  const verdict = String(payload?.verdict ?? "");
  const reason = String(payload?.reason ?? "");

  if (!authorId || (verdict !== "approved" && verdict !== "rejected")) {
    return Response.json({ ok: true, ignored: true });
  }

  const appId = Deno.env.get("ONESIGNAL_APP_ID") ?? "";
  const apiKey = Deno.env.get("ONESIGNAL_REST_API_KEY") ?? "";
  if (!appId || !apiKey) return Response.json({ ok: true, configured: false });

  // Someone who submitted a message and never heard back has no reason to
  // trust the "ready to deploy" queue exists at all - this closes that loop
  // for the human-review path, the same way the automatic path's in-app
  // toast closes it for the fast path.
  const preview = messageText.length > 60 ? `${messageText.slice(0, 57)}...` : messageText;
  const heading = verdict === "approved" ? "YOUR MESSAGE WAS APPROVED" : "YOUR MESSAGE WASN'T APPROVED";
  const body = verdict === "approved"
    ? (preview ? `"${preview}" is ready to deploy.` : "Your message is ready to deploy.")
    : (reason || "It didn't pass review. You can write a new one.");

  const response = await fetch("https://api.onesignal.com/notifications", {
    method: "POST",
    headers: {
      "Authorization": `Key ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      app_id: appId,
      include_aliases: { external_id: [authorId] },
      target_channel: "push",
      collapse_id: `message-decision-${authorId}`,
      headings: { en: heading },
      contents: { en: body },
      data: {
        destination: "message_library",
        verdict,
      },
    }),
  });

  const onesignalResult = await response.json().catch(() => null);
  if (!response.ok) {
    return Response.json({ ok: false, onesignal_status: response.status, onesignal_result: onesignalResult }, { status: 502 });
  }
  return Response.json({ ok: true, onesignal_result: onesignalResult });
});
