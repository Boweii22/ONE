Deno.serve(async (request) => {
  if (request.method !== "POST") {
    return Response.json({ error: "method_not_allowed" }, { status: 405 });
  }

  const hookSecret = Deno.env.get("MODERATION_HOOK_SECRET") ?? "";
  if (!hookSecret || request.headers.get("authorization") !== `Bearer ${hookSecret}`) {
    return Response.json({ error: "unauthorized" }, { status: 401 });
  }

  const payload = await request.json();
  const reignId = String(payload?.reign_id ?? "");
  const messageText = String(payload?.message_text ?? "");
  const reason = String(payload?.reason ?? "");
  const reporterIds = Array.isArray(payload?.reporter_ids)
    ? payload.reporter_ids.filter((id: unknown): id is string => typeof id === "string" && id.length > 0)
    : [];

  if (reporterIds.length === 0) return Response.json({ ok: true, ignored: true });

  const appId = Deno.env.get("ONESIGNAL_APP_ID") ?? "";
  const apiKey = Deno.env.get("ONESIGNAL_REST_API_KEY") ?? "";
  if (!appId || !apiKey) return Response.json({ ok: true, configured: false });

  // Reporters who never hear back stop reporting, so this confirms the
  // outcome directly rather than a generic "thanks for the report" push.
  const preview = messageText.length > 60 ? `${messageText.slice(0, 57)}...` : messageText;
  const response = await fetch("https://api.onesignal.com/notifications", {
    method: "POST",
    headers: {
      "Authorization": `Key ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      app_id: appId,
      include_aliases: { external_id: reporterIds },
      target_channel: "push",
      collapse_id: `report-resolved-${reignId}`,
      headings: { en: "YOUR REPORT WAS UPHELD" },
      contents: { en: preview ? `The message you reported — "${preview}" — was removed.` : "The message you reported was removed." },
      data: {
        destination: "reports",
        reign_id: reignId,
        reason,
      },
    }),
  });

  const onesignalResult = await response.json().catch(() => null);
  if (!response.ok) {
    return Response.json({ ok: false, onesignal_status: response.status, onesignal_result: onesignalResult }, { status: 502 });
  }
  return Response.json({ ok: true, onesignal_result: onesignalResult });
});
