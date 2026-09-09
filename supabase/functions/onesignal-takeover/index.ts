import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

type ReignRecord = {
  id?: string;
  owner_id?: string;
  previous_reign_id?: string;
};

Deno.serve(async (request) => {
  if (request.method !== "POST") {
    return Response.json({ error: "method_not_allowed" }, { status: 405 });
  }

  const hookSecret = Deno.env.get("TAKEOVER_HOOK_SECRET") ?? "";
  if (!hookSecret || request.headers.get("authorization") !== `Bearer ${hookSecret}`) {
    return Response.json({ error: "unauthorized" }, { status: 401 });
  }

  const payload = await request.json();
  const record = (payload?.record ?? {}) as ReignRecord;
  let previousOwnerId = String(payload?.previous_owner_id ?? "");
  let newHandle = String(payload?.new_owner_handle ?? "");
  let reignSeconds = Number(payload?.reign_seconds ?? 0);
  let verifiedViews = Number(payload?.verified_views ?? 0);

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  if ((!previousOwnerId || !newHandle) && supabaseUrl && serviceKey && record.previous_reign_id) {
    const supabase = createClient(supabaseUrl, serviceKey);
    const { data: previousReign, error: previousError } = await supabase
      .from("reigns")
      .select("owner_id,started_at,ended_at")
      .eq("id", record.previous_reign_id)
      .single();
    if (previousError) {
      return Response.json({ error: "previous_reign_lookup_failed" }, { status: 500 });
    }

    previousOwnerId = String(previousReign.owner_id);
    const endedAt = previousReign.ended_at ? new Date(previousReign.ended_at).getTime() : Date.now();
    reignSeconds = Math.max(0, Math.round((endedAt - new Date(previousReign.started_at).getTime()) / 1000));

    if (record.owner_id) {
      const { data: nextOwner } = await supabase
        .from("profiles")
        .select("handle")
        .eq("id", record.owner_id)
        .single();
      newHandle = String(nextOwner?.handle ?? "Someone");
    }

    const { count } = await supabase
      .from("view_events")
      .select("*", { count: "exact", head: true })
      .eq("reign_id", record.previous_reign_id);
    verifiedViews = count ?? 0;
  }

  if (!previousOwnerId) return Response.json({ ok: true, ignored: true });

  const appId = Deno.env.get("ONESIGNAL_APP_ID") ?? "";
  const apiKey = Deno.env.get("ONESIGNAL_REST_API_KEY") ?? "";
  if (!appId || !apiKey) return Response.json({ ok: true, configured: false });

  // The emotional window to react to a takeover is short, so the copy names the
  // thief directly instead of a generic "you've been dethroned" - that's the
  // difference between a push someone opens and one they swipe away.
  const handle = newHandle || "Someone";
  const peopleCount = verifiedViews.toLocaleString("en-US");
  const peopleWord = verifiedViews === 1 ? "person" : "people";
  const response = await fetch("https://api.onesignal.com/notifications", {
    method: "POST",
    headers: {
      "Authorization": `Key ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      app_id: appId,
      include_aliases: { external_id: [previousOwnerId] },
      target_channel: "push",
      // Collapses repeated notifications from the same thief into one, so a war
      // (dethroned a dozen times in an hour) doesn't spam the tray - the device
      // only ever shows the latest.
      android_group: `dethroned-${previousOwnerId}`,
      collapse_id: `dethroned-${previousOwnerId}`,
      headings: { en: `${handle} TOOK ONE FROM YOU` },
      contents: { en: `${peopleCount} ${peopleWord} saw your words. Take it back →` },
      data: {
        destination: "revenge",
        new_owner_handle: newHandle,
        reign_seconds: reignSeconds,
        verified_views: verifiedViews,
      },
    }),
  });

  // OneSignal can return HTTP 200 even when it matched zero devices (e.g. the
  // account never registered a push subscription) - surface the real body
  // instead of collapsing every 2xx into a bare {ok:true}, so this is
  // debuggable from net._http_response instead of guessing blind.
  const onesignalResult = await response.json().catch(() => null);
  if (!response.ok) {
    return Response.json({ ok: false, onesignal_status: response.status, onesignal_result: onesignalResult }, { status: 502 });
  }
  return Response.json({ ok: true, onesignal_result: onesignalResult });
});
