import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const cors = { "content-type": "application/json" };

Deno.serve(async (request) => {
  if (request.method !== "POST") {
    return new Response(JSON.stringify({ error: "method_not_allowed" }), { status: 405, headers: cors });
  }

  const expected = Deno.env.get("REVENUECAT_WEBHOOK_SECRET") ?? "";
  const supplied = request.headers.get("authorization") ?? "";
  if (!expected || supplied !== `Bearer ${expected}`) {
    return new Response(JSON.stringify({ error: "unauthorized" }), { status: 401, headers: cors });
  }

  const body = await request.json();
  const event = body?.event ?? body;
  const eventId = String(event?.id ?? "");
  const userId = String(event?.app_user_id ?? "");
  const productId = String(event?.product_id ?? "");
  const type = String(event?.type ?? "");
  // ONE_PRODUCT_TICKETS remains a temporary backwards-compatible secret name.
  // There is deliberately no guessed product map: every ID must come from the dashboard.
  const grants: Record<string, number> = JSON.parse(
    Deno.env.get("ONE_PRODUCT_CREDITS") ?? Deno.env.get("ONE_PRODUCT_TICKETS") ?? "{}",
  );
  const credits = grants[productId] ?? 0;

  if (!eventId || !userId || !credits || !["INITIAL_PURCHASE", "NON_RENEWING_PURCHASE"].includes(type)) {
    return new Response(JSON.stringify({ ok: true, ignored: true }), { headers: cors });
  }

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  const { error } = await supabase.rpc("grant_revenge_tickets", {
    p_event_id: eventId,
    p_user_id: userId,
    p_product_id: productId,
    p_tickets: credits,
    p_raw_event: event,
  });

  if (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500, headers: cors });
  }
  return new Response(JSON.stringify({ ok: true, granted: credits }), { headers: cors });
});
