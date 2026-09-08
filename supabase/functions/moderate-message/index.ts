import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// Called by the app right after submit_message succeeds for a message that
// passed the fast deterministic checks and landed as 'reviewing'. Scores it
// with a moderation model and hands the raw score to apply_message_moderation,
// which does the actual auto-approve / auto-reject / keep-queued decision.
// If OPENAI_API_KEY isn't configured, the message safely stays 'reviewing'
// (the human queue) instead of silently auto-approving unscreened content.
const cors = { "content-type": "application/json" };

Deno.serve(async (request) => {
  if (request.method !== "POST") {
    return new Response(JSON.stringify({ error: "method_not_allowed" }), { status: 405, headers: cors });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  const authorization = request.headers.get("authorization") ?? "";
  if (!authorization.startsWith("Bearer ") || !supabaseUrl || !serviceKey) {
    return new Response(JSON.stringify({ error: "unauthorized" }), { status: 401, headers: cors });
  }

  let messageId = "";
  try {
    const body = await request.json();
    messageId = String(body?.message_id ?? "");
  } catch {
    return new Response(JSON.stringify({ error: "invalid_body" }), { status: 400, headers: cors });
  }
  if (!messageId) return new Response(JSON.stringify({ error: "message_id_required" }), { status: 400, headers: cors });

  // Confirm this is a real signed-in user before doing any work on their behalf.
  const userResponse = await fetch(supabaseUrl + "/auth/v1/user", {
    headers: { apikey: anonKey, authorization: authorization },
  });
  if (!userResponse.ok) return new Response(JSON.stringify({ error: "invalid_session" }), { status: 401, headers: cors });
  const user = await userResponse.json();
  const userId = user ? user.id : null;
  if (!userId) return new Response(JSON.stringify({ error: "invalid_session" }), { status: 401, headers: cors });

  const supabase = createClient(supabaseUrl, serviceKey);
  const messageLookup = await supabase
    .from("messages")
    .select("id,text,author_id,status")
    .eq("id", messageId)
    .single();
  const message = messageLookup.data;
  if (messageLookup.error || !message) return new Response(JSON.stringify({ error: "message_not_found" }), { status: 404, headers: cors });
  if (message.author_id !== userId) return new Response(JSON.stringify({ error: "not_your_message" }), { status: 403, headers: cors });
  if (message.status !== "reviewing") return new Response(JSON.stringify({ ok: true, skipped: true }), { headers: cors });

  const openaiKey = Deno.env.get("OPENAI_API_KEY") ?? "";
  if (!openaiKey) {
    // No model configured: leave it in the human queue rather than guessing.
    return new Response(JSON.stringify({ ok: true, queued: true, reason: "moderation_model_not_configured" }), { headers: cors });
  }

  // Fail safe: start at the worst score, so any error below keeps the message
  // in the human queue instead of silently auto-approving unscreened content.
  let maxScore = 1;
  try {
    const moderation = await fetch("https://api.openai.com/v1/moderations", {
      method: "POST",
      headers: { "Authorization": "Bearer " + openaiKey, "Content-Type": "application/json" },
      body: JSON.stringify({ model: "omni-moderation-latest", input: message.text }),
      signal: AbortSignal.timeout(10000),
    });
    if (moderation.ok) {
      const payload = await moderation.json();
      const scores = (payload && payload.results && payload.results[0] && payload.results[0].category_scores) || {};
      let highest = 0;
      let sawAny = false;
      for (const key in scores) {
        const value = scores[key];
        if (typeof value === "number") {
          sawAny = true;
          if (value > highest) highest = value;
        }
      }
      maxScore = sawAny ? highest : 1;
    }
  } catch (_error) {
    // keep maxScore at the fail-safe value
  }

  const verdictResult = await supabase.rpc("apply_message_moderation", {
    p_message_id: messageId,
    p_max_score: maxScore,
  });
  if (verdictResult.error) return new Response(JSON.stringify({ error: verdictResult.error.message }), { status: 500, headers: cors });
  return new Response(JSON.stringify(verdictResult.data), { headers: cors });
});
