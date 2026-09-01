const url = process.env.SUPABASE_URL?.replace(/\/$/, '');
const key = process.env.SUPABASE_ANON_KEY;

if (!url || !key) {
  throw new Error('Set SUPABASE_URL and SUPABASE_ANON_KEY before running this test.');
}

async function request(path, options = {}) {
  const response = await fetch(`${url}${path}`, {
    ...options,
    headers: {
      apikey: key,
      'content-type': 'application/json',
      ...(options.headers || {}),
    },
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(`${path} returned ${response.status}: ${payload.message || payload.msg || JSON.stringify(payload)}`);
  }
  return payload;
}

async function createPlayer(label) {
  const session = await request('/auth/v1/signup', { method: 'POST', body: '{}' });
  const token = session.access_token;
  const auth = { authorization: `Bearer ${token}` };
  await request('/rest/v1/rpc/ensure_profile', { method: 'POST', headers: auth, body: '{}' });
  await request('/rest/v1/rpc/update_handle', {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({ p_handle: label }),
  });
  const state = await request('/rest/v1/rpc/get_one_state', { method: 'POST', headers: auth, body: '{}' });
  const message = state.messages.find((item) => item.status === 'approved');
  if (!message) throw new Error(`No approved message was created for ${label}.`);
  return { id: session.user.id, token, messageId: message.id, tickets: state.revenge_tickets };
}

async function stateFor(player) {
  return request('/rest/v1/rpc/get_one_state', {
    method: 'POST',
    headers: { authorization: `Bearer ${player.token}` },
    body: '{}',
  });
}

async function take(player, expectedSequence) {
  return request('/rest/v1/rpc/take_one', {
    method: 'POST',
    headers: { authorization: `Bearer ${player.token}` },
    body: JSON.stringify({
      p_message_id: player.messageId,
      p_expected_sequence: expectedSequence,
      p_request_id: crypto.randomUUID(),
    }),
  });
}

const suffix = Date.now().toString(36).slice(-7).toUpperCase();
const [alpha, beta] = await Promise.all([
  createPlayer(`RACEA_${suffix}`),
  createPlayer(`RACEB_${suffix}`),
]);

const before = await stateFor(alpha);
const protectionWait = Math.max(0, before.reign.protected_until_ms - before.server_time_ms + 150);
if (protectionWait) await new Promise((resolve) => setTimeout(resolve, protectionWait));

const [alphaResult, betaResult] = await Promise.all([
  take(alpha, before.reign.sequence),
  take(beta, before.reign.sequence),
]);

const results = [
  { player: alpha, result: alphaResult },
  { player: beta, result: betaResult },
];
const winners = results.filter((entry) => entry.result.ok);
const losers = results.filter((entry) => !entry.result.ok);
if (winners.length !== 1 || losers.length !== 1) {
  throw new Error(`Expected one winner and one loser, got ${JSON.stringify(results.map((entry) => entry.result))}`);
}
if (losers[0].result.code !== 'STALE_REIGN') {
  throw new Error(`Expected STALE_REIGN, got ${losers[0].result.code}`);
}

const loserState = await stateFor(losers[0].player);
const after = await stateFor(winners[0].player);
if (loserState.revenge_tickets !== losers[0].player.tickets) {
  throw new Error('The stale-race loser lost a Revenge Ticket.');
}
if (after.reign.owner.id !== winners[0].player.id) {
  throw new Error('The authoritative final owner does not match the committed winner.');
}
if (after.reign.sequence !== before.reign.sequence + 1) {
  throw new Error('The global reign sequence did not advance exactly once.');
}

console.log('LIVE_RACE_TEST=PASS');
console.log(`START_SEQUENCE=${before.reign.sequence}`);
console.log(`FINAL_SEQUENCE=${after.reign.sequence}`);
console.log(`WINNER=${after.reign.owner.handle}`);
console.log(`LOSER_CODE=${losers[0].result.code}`);
console.log(`LOSER_TICKETS_BEFORE=${losers[0].player.tickets}`);
console.log(`LOSER_TICKETS_AFTER=${loserState.revenge_tickets}`);
