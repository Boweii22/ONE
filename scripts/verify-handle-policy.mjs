const url = process.env.SUPABASE_URL?.replace(/\/$/, '');
const key = process.env.SUPABASE_ANON_KEY;

if (!url || !key) {
  throw new Error('Set SUPABASE_URL and SUPABASE_ANON_KEY before running this test.');
}

async function jsonRequest(path, options = {}) {
  const response = await fetch(`${url}${path}`, {
    ...options,
    headers: {
      apikey: key,
      'content-type': 'application/json',
      ...(options.headers || {}),
    },
  });
  const payload = await response.json().catch(() => ({}));
  return { response, payload };
}

const signup = await jsonRequest('/auth/v1/signup', { method: 'POST', body: '{}' });
if (!signup.response.ok || !signup.payload.access_token) {
  throw new Error(`Anonymous sign-in failed with ${signup.response.status}.`);
}

const auth = { authorization: `Bearer ${signup.payload.access_token}` };
const profile = await jsonRequest('/rest/v1/rpc/ensure_profile', {
  method: 'POST',
  headers: auth,
  body: '{}',
});
if (!profile.response.ok) {
  throw new Error(`Profile creation failed with ${profile.response.status}.`);
}

const state = await jsonRequest('/rest/v1/rpc/get_one_state', {
  method: 'POST',
  headers: auth,
  body: '{}',
});
if (!state.response.ok || !state.payload.reign?.id || state.payload.connected !== true) {
  throw new Error(`Live state failed with ${state.response.status}.`);
}

// @ONE already exists. Without the new trigger this would fail as a duplicate;
// with the migration it must be rejected earlier as an explicitly protected ID.
const protectedAttempt = await jsonRequest('/rest/v1/rpc/update_handle', {
  method: 'POST',
  headers: auth,
  body: JSON.stringify({ p_handle: 'ONE' }),
});
const detail = String(protectedAttempt.payload.message || protectedAttempt.payload.hint || '');

if (protectedAttempt.response.ok || !detail.includes('HANDLE_RESERVED')) {
  throw new Error(`Expected HANDLE_RESERVED from production, received ${protectedAttempt.response.status}: ${detail}`);
}

console.log('PASS: anonymous auth, profile, live state, and reserved-handle enforcement are healthy.');
