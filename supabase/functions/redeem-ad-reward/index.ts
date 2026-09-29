import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// Turns a RevenueCat-verified rewarded ad into a take.
//
// RevenueCat only credits the ad-reward virtual currency after AdMob's
// server-side verification succeeds (the app cannot mint it). This function
// spends one unit of that currency through RevenueCat's REST API and only then
// grants the take, so a client that never watched an ad has nothing to spend.
//
// Secrets (Edge Function env):
//   REVENUECAT_SECRET_API_KEY   v2 secret key with read+write on customer configuration/purchases
//   REVENUECAT_PROJECT_ID       e.g. projXXXXXXXX
//   REVENUECAT_AD_CURRENCY_CODE optional, defaults to ADTAKE
const cors = { "content-type": "application/json" };
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: cors });

const messages: Record<string, string> = {
  AD_BYPASS_DAILY_CAP: "You've used all your ad skips for today.",
  NO_REFILL_IN_PROGRESS: "You already have a take ready.",
  ACCOUNT_BLOCKED: "Taking ONE is temporarily unavailable for this account.",
};

async function adjust(base: string, key: string, customerId: string, delta: number, idempotencyKey: string) {
  return await fetch(base + "/customers/" + encodeURIComponent(customerId) + "/virtual_currencies/transactions", {
    method: "POST",
    headers: { authorization: "Bearer " + key, "content-type": "application/json", "idempotency-key": idempotencyKey },
    body: JSON.stringify({ adjustments: { [CURRENCY]: delta } }),
    signal: AbortSignal.timeout(10000),
  });
}

let CURRENCY = "ADTAKE";

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ message: "method_not_allowed" }, 405);

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  const rcKey = Deno.env.get("REVENUECAT_SECRET_API_KEY") ?? "";
  const rcProject = Deno.env.get("REVENUECAT_PROJECT_ID") ?? "";
  CURRENCY = Deno.env.get("REVENUECAT_AD_CURRENCY_CODE") || "ADTAKE";
  const authorization = request.headers.get("authorization") ?? "";
  if (!authorization.startsWith("Bearer ") || !supabaseUrl || !serviceKey) return json({ message: "unauthorized" }, 401);
  if (!rcKey || !rcProject) return json({ message: "Ad rewards aren't configured yet." }, 503);

  let requestId = "";
  try {
    requestId = String((await request.json())?.request_id ?? "");
  } catch {
    return json({ message: "invalid_body" }, 400);
  }
  if (!requestId || requestId.length > 100) return json({ message: "request_id_required" }, 400);

  const userResponse = await fetch(supabaseUrl + "/auth/v1/user", {
    headers: { apikey: anonKey, authorization: authorization },
  });
  if (!userResponse.ok) return json({ message: "invalid_session" }, 401);
  const user = await userResponse.json();
  const userId: string | null = user ? user.id : null;
  if (!userId) return json({ message: "invalid_session" }, 401);

  const supabase = createClient(supabaseUrl, serviceKey);
  const base = "https://api.revenuecat.com/v2/projects/" + encodeURIComponent(rcProject);

  // A retry of a request that was already redeemed must not spend another unit.
  const prior = await supabase.from("take_bypass_requests").select("request_id").eq("request_id", requestId).maybeSingle();
  if (prior.data) {
    const again = await supabase.rpc("grant_verified_ad_bypass", { p_user_id: userId, p_request_id: requestId });
    return again.error ? json({ message: again.error.message }, 400) : json(again.data);
  }

  // RevenueCat credits the reward server-side around the time the SDK reports
  // verification, so allow a short window for the balance to appear.
  let spent = false;
  for (let attempt = 0; attempt < 4 && !spent; attempt++) {
    if (attempt > 0) await new Promise((resolve) => setTimeout(resolve, 1500));
    let spend: Response;
    try {
      spend = await adjust(base, rcKey, userId, -1, "spend-" + requestId);
    } catch (_error) {
      return json({ message: "Couldn't confirm your ad reward. Try again." }, 502);
    }
    if (spend.ok) spent = true;
    else if (spend.status !== 422) return json({ message: "Couldn't confirm your ad reward. Try again." }, 502);
  }
  if (!spent) return json({ message: "Your ad reward hasn't been verified yet." }, 402);

  const grant = await supabase.rpc("grant_verified_ad_bypass", { p_user_id: userId, p_request_id: requestId });
  if (grant.error) {
    // Nothing was granted, so give the verified reward back rather than burn it.
    try { await adjust(base, rcKey, userId, 1, "refund-" + requestId); } catch (_error) { /* logged by RevenueCat */ }
    const code = Object.keys(messages).find((key) => grant.error!.message.includes(key));
    return json({ message: code ? messages[code] : grant.error.message }, 400);
  }
  return json(grant.data);
});
