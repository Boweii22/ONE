import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

type ReignRecord = {
  id?: string;
  owner_id?: string;
  previous_reign_id?: string;
};

const SYSTEM_IDS = new Set([
  "00000000-0000-0000-0000-000000000001",
  "00000000-0000-0000-0000-000000000004",
]);

async function updateTags(appId: string, apiKey: string, externalId: string, tags: Record<string, string>) {
  const response = await fetch(`https://api.onesignal.com/apps/${appId}/users/by/external_id/${externalId}`, {
    method: "PATCH",
    headers: { "Authorization": `Key ${apiKey}`, "Content-Type": "application/json" },
    body: JSON.stringify({ properties: { tags } }),
  });
  return { ok: response.ok, status: response.status, body: await response.json().catch(() => null) };
}

async function fireCustomEvent(
  appId: string,
  apiKey: string,
  externalId: string,
  name: string,
  idempotencyKey: string,
  properties: Record<string, unknown>,
) {
  const response = await fetch(`https://api.onesignal.com/apps/${appId}/custom_events`, {
    method: "POST",
    headers: { "Authorization": `Key ${apiKey}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      events: [{ name, external_id: externalId, idempotency_key: idempotencyKey, properties }],
    }),
  });
  return { ok: response.ok, status: response.status, body: await response.json().catch(() => null) };
}

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
  let timesDethroned = Number(payload?.times_dethroned ?? 0);
  const newOwnerId = String(record.owner_id ?? "");

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

    if (!payload?.times_dethroned) {
      const { data: previousProfile } = await supabase
        .from("profiles")
        .select("times_dethroned")
        .eq("id", previousOwnerId)
        .single();
      timesDethroned = Number(previousProfile?.times_dethroned ?? 0);
    }
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
  const pushPromise = fetch("https://api.onesignal.com/notifications", {
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
  }).then(async (response) => ({ ok: response.ok, status: response.status, body: await response.json().catch(() => null) }));

  // Journey signals: computed from the exact same data as the push above, in
  // the same execution, so the tag/event and the notification can never
  // drift out of sync. Skipped for the reserved system account - it's not a
  // real player and has no push subscription worth targeting.
  const sideEffects: Promise<unknown>[] = [pushPromise];
  if (!SYSTEM_IDS.has(previousOwnerId)) {
    sideEffects.push(
      updateTags(appId, apiKey, previousOwnerId, {
        owns_screen: "false",
        times_dethroned: String(timesDethroned),
      }),
      fireCustomEvent(
        appId,
        apiKey,
        previousOwnerId,
        "lost_the_screen",
        // The ended reign's id is a natural, unique-per-event idempotency key -
        // this trigger fires exactly once per reign ending.
        record.previous_reign_id ?? crypto.randomUUID(),
        {
          new_owner_handle: newHandle,
          reign_seconds: reignSeconds,
          verified_views: verifiedViews,
          times_dethroned: timesDethroned,
        },
      ),
    );
  }
  if (newOwnerId && !SYSTEM_IDS.has(newOwnerId)) {
    sideEffects.push(updateTags(appId, apiKey, newOwnerId, { owns_screen: "true" }));
  }

  const [pushResult, ...journeyResults] = await Promise.allSettled(sideEffects);

  // OneSignal can return HTTP 200 even when it matched zero devices (e.g. the
  // account never registered a push subscription) - surface the real body
  // instead of collapsing every 2xx into a bare {ok:true}, so this is
  // debuggable from net._http_response instead of guessing blind.
  const push = pushResult.status === "fulfilled" ? pushResult.value as { ok: boolean; status: number; body: unknown } : null;
  if (!push || !push.ok) {
    return Response.json({ ok: false, onesignal_status: push?.status, onesignal_result: push?.body, journey_results: journeyResults }, { status: 502 });
  }
  return Response.json({ ok: true, onesignal_result: push.body, journey_results: journeyResults });
});
