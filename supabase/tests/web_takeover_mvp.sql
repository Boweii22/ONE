-- Acceptance tests for the browser-playable takeover MVP
-- (202610060001_web_takeover_mvp.sql). Covers every scenario called out as
-- required before this ships: cleared cookies / fresh identity after a ban,
-- an existing banned account linking Google, a direct RPC call trying to
-- bypass both the ban and the approval requirement, simultaneous takeover
-- requests, the rollout flag blocking an already-signed-in browser session
-- (not just new ones), and that none of this changes existing Android
-- behaviour for an ordinary unlinked account.
--
-- Tests 21-30 below were added after review found that take_one_web's own
-- checks (web_take_enabled, "must be linked") are NOT a security boundary -
-- a direct call to take_one skips them entirely. That was proven empirically
-- (see verify2.js output from this session), not just inferred. These tests
-- assert the FIX: require_linked_identity_for_takes, checked inside take_one
-- itself. They assert REJECTION of the bypass, not just observe and record
-- that it succeeds - test 21 is the one exception, and it's explicitly
-- labeled as documenting today's shipped default (flag off), not as an
-- accepted gap.
begin;
select plan(35);

-- Actors. U1/U5 are plain anonymous accounts (no banned_at, no linked
-- identity) created the same way any real Android/web anonymous user is -
-- via ensure_profile, not a hand-crafted fixture. U4 is the one exception:
-- it must already be banned BEFORE it links Google, so its banned_at has to
-- be seeded up front.
--
-- Note: the actor ids below deliberately vary in their first 6 hex digits
-- (41/42/43/44/45...) rather than all sharing a "40000000..." prefix -
-- ensure_profile derives its auto-generated handle from exactly those 6
-- hex digits, so identical prefixes across actors collide on the handle's
-- unique constraint. Not a product bug, just a fixture-design note.
select set_config('request.jwt.claims', '{"sub":"41000000-0000-0000-0000-000000000001","role":"authenticated"}', true);
select public.ensure_profile(null);
select set_config('request.jwt.claims', '{"sub":"42000000-0000-0000-0000-000000000002","role":"authenticated"}', true);
select public.ensure_profile(null);
select set_config('request.jwt.claims', '{"sub":"43000000-0000-0000-0000-000000000003","role":"authenticated"}', true);
select public.ensure_profile(null);
select set_config('request.jwt.claims', '{"sub":"45000000-0000-0000-0000-000000000005","role":"authenticated"}', true);
select public.ensure_profile(null);

insert into public.profiles(id, handle, initials, banned_at)
values ('44000000-0000-0000-0000-000000000004', '@RACER_BANNED', 'RB', now());

-- auth.identities.user_id is a real FK into auth.users, so every actor
-- that will ever link a Google identity (U2, U3, U4, U5) needs a matching
-- auth.users row first - a bare row is enough, every other column either
-- has a default or is nullable.
insert into auth.users (id) values
  ('42000000-0000-0000-0000-000000000002'),
  ('43000000-0000-0000-0000-000000000003'),
  ('44000000-0000-0000-0000-000000000004'),
  ('45000000-0000-0000-0000-000000000005');

-- U2 and U3 are the two accounts that have actually linked a Google
-- identity - populated directly in auth.identities, exactly like a real
-- OAuth exchange would, never accepted as a client parameter.
insert into auth.identities (user_id, provider, provider_id, identity_data, created_at, updated_at)
values
  ('42000000-0000-0000-0000-000000000002', 'google', 'sub-u2', jsonb_build_object('sub', 'sub-u2', 'email', 'u2@example.com'), now(), now()),
  ('43000000-0000-0000-0000-000000000003', 'google', 'sub-u3', jsonb_build_object('sub', 'sub-u3', 'email', 'u3@example.com'), now(), now());

update public.app_settings set web_take_enabled = true where singleton;

-- TEST A: the rollout flag blocks the web entry point even for a fully
-- valid, already-linked, already-approved-message account - proving the
-- flag is enforced inside the RPC itself, not just hidden in the UI.
select set_config('request.jwt.claims', '{"sub":"42000000-0000-0000-0000-000000000002","role":"authenticated"}', true);
update public.app_settings set web_take_enabled = false where singleton;
select throws_like(
    $$select public.take_one_web(
        (select id from public.messages where author_id = '42000000-0000-0000-0000-000000000002' limit 1),
        (select sequence from public.reigns where ended_at is null),
        '50000000-0000-0000-0000-000000000001'
    )$$,
    '%WEB_TAKES_DISABLED%',
    'flag off blocks take_one_web for an already-linked user'
);
update public.app_settings set web_take_enabled = true where singleton;

-- TEST B: an anonymous, never-linked account cannot use the web entry
-- point at all, regardless of message/sequence validity.
select set_config('request.jwt.claims', '{"sub":"41000000-0000-0000-0000-000000000001","role":"authenticated"}', true);
select throws_like(
    $$select public.take_one_web(
        (select id from public.messages where author_id = '41000000-0000-0000-0000-000000000001' limit 1),
        (select sequence from public.reigns where ended_at is null),
        '50000000-0000-0000-0000-000000000002'
    )$$,
    '%IDENTITY_REQUIRED%',
    'unlinked anonymous account is blocked from the web takeover entry point'
);

-- TEST K: Android's path (direct take_one, same as the production app has
-- always called) is completely unaffected by any of this - same unlinked
-- account, same message, succeeds exactly as before.
create temporary table k_result as
select public.take_one(
    (select id from public.messages where author_id = '41000000-0000-0000-0000-000000000001' limit 1),
    (select sequence from public.reigns where ended_at is null),
    '50000000-0000-0000-0000-000000000003'
) result;
select ok((select (result->>'ok')::boolean from k_result), 'existing Android-style direct take_one is unchanged for an unlinked account');

-- Neutralize the brand-new reign's protection window so the tests below
-- (which are about identity/ban/approval logic, not the unrelated
-- protected_until mechanic) aren't blocked by a PROTECTED result that has
-- nothing to do with what's under test here.
update public.reigns set protected_until = now() - interval '1 second' where ended_at is null;

-- TEST C (+ starter message correctness): the actual browser flow - claim
-- a starter by id only, then take with it. Capture the sequence BEFORE
-- either U2 or U3 acts, so the next block can also prove concurrency.
create temporary table seq_before_race as select sequence as s from public.reigns where ended_at is null;

select set_config('request.jwt.claims', '{"sub":"42000000-0000-0000-0000-000000000002","role":"authenticated"}', true);
create temporary table claim_u2 as
select public.claim_starter_message(
    (select id from public.starter_messages where active limit 1),
    '50000000-0000-0000-0000-000000000004'
) result;
select ok((select (result->>'ok')::boolean from claim_u2), 'U2 claims a starter message by id');

select set_config('request.jwt.claims', '{"sub":"43000000-0000-0000-0000-000000000003","role":"authenticated"}', true);
create temporary table claim_u3 as
select public.claim_starter_message(
    (select id from public.starter_messages where active limit 1),
    '50000000-0000-0000-0000-000000000005'
) result;
select ok((select (result->>'ok')::boolean from claim_u3), 'U3 claims a starter message by id');

-- TEST H: U3 takes first against the captured sequence and wins; U2 then
-- races against the SAME now-stale sequence and loses cleanly, spending
-- nothing - the exact concurrency guarantee takeover_race.sql already
-- covers for take_one, now proven through the web entry point too.
select set_config('request.jwt.claims', '{"sub":"43000000-0000-0000-0000-000000000003","role":"authenticated"}', true);
create temporary table win_u3 as
select public.take_one_web(
    (select (result->>'message_id')::uuid from claim_u3),
    (select s from seq_before_race),
    '50000000-0000-0000-0000-000000000006'
) result;
select ok((select (result->>'ok')::boolean from win_u3), 'U3 wins the race for the captured sequence');
select is((select owner_id from public.reigns where ended_at is null), '43000000-0000-0000-0000-000000000003'::uuid, 'U3 is the sole new owner');
update public.reigns set protected_until = now() - interval '1 second' where ended_at is null;

select set_config('request.jwt.claims', '{"sub":"42000000-0000-0000-0000-000000000002","role":"authenticated"}', true);
select is(
    (public.take_one_web(
        (select (result->>'message_id')::uuid from claim_u2),
        (select s from seq_before_race),
        '50000000-0000-0000-0000-000000000007'
    )->>'code'),
    'STALE_REIGN',
    'U2 loses the race against the now-stale sequence it captured'
);

-- TEST J: U4 was banned BEFORE ever touching Google. Linking now must
-- retroactively record its Google sub as banned too.
insert into auth.identities (user_id, provider, provider_id, identity_data, created_at, updated_at)
values ('44000000-0000-0000-0000-000000000004', 'google', 'sub-shared-banned', jsonb_build_object('sub', 'sub-shared-banned', 'email', 'u4@example.com'), now(), now());
select set_config('request.jwt.claims', '{"sub":"44000000-0000-0000-0000-000000000004","role":"authenticated"}', true);
select public.mark_google_linked('u4@example.com');
select ok(
    (select exists(select 1 from public.banned_google_subs where google_sub = 'sub-shared-banned')),
    'linking Google on an already-banned account records that Google sub as banned'
);

-- TEST D: cleared cookies / fresh identity after a ban. Supabase itself
-- enforces one auth.users per (provider, provider_id) - the same real
-- Google account cannot be linked to two live users at once, so the actual
-- evasion path isn't "link it twice", it's "the first account's identity
-- gets freed (e.g. via this app's existing delete-my-account flow, which
-- already does `delete from auth.users`) and a fresh account claims it".
-- Model that mechanism directly: free U4's identity, then U5 (a brand new,
-- never-banned profile) claims it. Without the banned_google_subs check
-- this would be a clean bypass - the whole point of this migration.
delete from auth.identities where user_id = '44000000-0000-0000-0000-000000000004' and provider = 'google';
insert into auth.identities (user_id, provider, provider_id, identity_data, created_at, updated_at)
values ('45000000-0000-0000-0000-000000000005', 'google', 'sub-shared-banned', jsonb_build_object('sub', 'sub-shared-banned', 'email', 'u5@example.com'), now(), now());
select set_config('request.jwt.claims', '{"sub":"45000000-0000-0000-0000-000000000005","role":"authenticated"}', true);
select is((select banned_at from public.profiles where id = '45000000-0000-0000-0000-000000000005'::uuid), null, 'U5 itself was never banned at the profile level');
select throws_like(
    $$select public.take_one(
        (select id from public.messages where author_id = '45000000-0000-0000-0000-000000000005' limit 1),
        (select sequence from public.reigns where ended_at is null),
        '50000000-0000-0000-0000-000000000008'
    )$$,
    '%ACCOUNT_BLOCKED%',
    'a fresh profile reusing a banned Google account is still blocked, even calling take_one directly'
);

-- TEST F: direct RPC attempts to fabricate an approved message.
select set_config('request.jwt.claims', '{"sub":"42000000-0000-0000-0000-000000000002","role":"authenticated"}', true);
select throws_like(
    $$select public.take_one(
        (select id from public.starter_messages limit 1),
        (select sequence from public.reigns where ended_at is null),
        '50000000-0000-0000-0000-000000000009'
    )$$,
    '%APPROVED_MESSAGE_REQUIRED%',
    'passing a starter_messages id straight to take_one as p_message_id is rejected - it was never copied into messages'
);
select throws_like(
    $$select public.claim_starter_message('99999999-9999-9999-9999-999999999999'::uuid, '50000000-0000-0000-0000-000000000010')$$,
    '%STARTER_MESSAGE_NOT_FOUND%',
    'claiming a nonexistent/inactive starter id is rejected, not silently approved'
);

-- TEST G: starter-claim rate limit. U2 already made 1 successful claim
-- above; the default cap is 5 per 60s, so 4 more should succeed and the
-- 6th should be rejected.
select lives_ok(
    format($$select public.claim_starter_message((select id from public.starter_messages where active limit 1), %L::uuid)$$, '50000000-0000-0000-0000-00000000' || lpad((10 + 1)::text, 4, '0')),
    'U2 claim #2 within the per-minute cap succeeds'
);
select lives_ok(
    format($$select public.claim_starter_message((select id from public.starter_messages where active limit 1), %L::uuid)$$, '50000000-0000-0000-0000-00000000' || lpad((10 + 2)::text, 4, '0')),
    'U2 claim #3 within the per-minute cap succeeds'
);
select lives_ok(
    format($$select public.claim_starter_message((select id from public.starter_messages where active limit 1), %L::uuid)$$, '50000000-0000-0000-0000-00000000' || lpad((10 + 3)::text, 4, '0')),
    'U2 claim #4 within the per-minute cap succeeds'
);
select lives_ok(
    format($$select public.claim_starter_message((select id from public.starter_messages where active limit 1), %L::uuid)$$, '50000000-0000-0000-0000-00000000' || lpad((10 + 4)::text, 4, '0')),
    'U2 claim #5 reaches the per-minute cap and still succeeds'
);
select throws_like(
    format($$select public.claim_starter_message((select id from public.starter_messages where active limit 1), %L::uuid)$$, '50000000-0000-0000-0000-00000000' || lpad((10 + 5)::text, 4, '0')),
    '%STARTER_CLAIM_RATE_LIMIT%',
    'U2 claim #6 within the same minute is rate limited'
);

-- TEST I: rollback. Flipping the flag off blocks an ALREADY-authenticated
-- session on its very next call (not just at a future sign-in), and
-- flipping it back on immediately restores it - the entire rollback story
-- is one settings update either direction, no schema change needed.
select set_config('request.jwt.claims', '{"sub":"42000000-0000-0000-0000-000000000002","role":"authenticated"}', true);
update public.app_settings set web_take_enabled = false where singleton;
select throws_like(
    $$select public.take_one_web(
        (select id from public.messages where author_id = '42000000-0000-0000-0000-000000000002' and status = 'approved' limit 1),
        (select sequence from public.reigns where ended_at is null),
        '50000000-0000-0000-0000-000000000020'
    )$$,
    '%WEB_TAKES_DISABLED%',
    'rollback: flipping the flag off blocks the same already-signed-in session immediately'
);
update public.app_settings set web_take_enabled = true where singleton;
-- U2 is linked and unbanned, so once the flag is back on this must reach
-- take_one's own game-state logic (ok:true, or a normal in-game rejection
-- like PROTECTED/STALE_REIGN) rather than raising WEB_TAKES_DISABLED or
-- IDENTITY_REQUIRED again - that it returns at all (no exception) is the
-- proof the flag flip actually took effect, independent of unrelated
-- in-game timing like the current owner's protection window.
select lives_ok(
    $$select public.take_one_web(
        (select id from public.messages where author_id = '42000000-0000-0000-0000-000000000002' and status = 'approved' and times_deployed = 0 limit 1),
        (select sequence from public.reigns where ended_at is null),
        '50000000-0000-0000-0000-000000000021'
    )$$,
    'flipping the flag back on restores the web path immediately (reaches normal game logic, no longer raises)'
);

-- ===========================================================================
-- TESTS 21-30: require_linked_identity_for_takes - the actual fix for the
-- proven take_one_web bypass. New, previously-unused actors throughout so
-- this block doesn't depend on reign/ownership state left by tests above.
-- ===========================================================================
update public.reigns set protected_until = now() - interval '1 second' where ended_at is null;

-- P: unlinked, unbanned. Q, R: linked, unbanned (for the flag=on race).
-- S: linked to a sub that WILL be banned, profile-level banned_at left null
-- on purpose - this isolates "does the google_sub ban still bite under the
-- new flag" from the already-covered profile-ban case.
select set_config('request.jwt.claims', '{"sub":"48000000-0000-0000-0000-000000000001","role":"authenticated"}', true);
select public.ensure_profile(null);
select set_config('request.jwt.claims', '{"sub":"49000000-0000-0000-0000-000000000002","role":"authenticated"}', true);
select public.ensure_profile(null);
select set_config('request.jwt.claims', '{"sub":"4a000000-0000-0000-0000-000000000003","role":"authenticated"}', true);
select public.ensure_profile(null);
select set_config('request.jwt.claims', '{"sub":"4b000000-0000-0000-0000-000000000004","role":"authenticated"}', true);
select public.ensure_profile(null);

insert into auth.users (id) values
  ('49000000-0000-0000-0000-000000000002'),
  ('4a000000-0000-0000-0000-000000000003'),
  ('4b000000-0000-0000-0000-000000000004');
insert into auth.identities (user_id, provider, provider_id, identity_data, created_at, updated_at) values
  ('49000000-0000-0000-0000-000000000002', 'google', 'sub-flag-q', jsonb_build_object('sub', 'sub-flag-q'), now(), now()),
  ('4a000000-0000-0000-0000-000000000003', 'google', 'sub-flag-r', jsonb_build_object('sub', 'sub-flag-r'), now(), now()),
  ('4b000000-0000-0000-0000-000000000004', 'google', 'sub-flag-s-willbeban', jsonb_build_object('sub', 'sub-flag-s-willbeban'), now(), now());

-- TEST 21: documents TODAY'S SHIPPED DEFAULT (flag off) - not an accepted
-- gap. This is the only test in this block that observes success rather
-- than requiring rejection, and it's labeled as exactly that.
update public.app_settings set require_linked_identity_for_takes = false where singleton;
select set_config('request.jwt.claims', '{"sub":"48000000-0000-0000-0000-000000000001","role":"authenticated"}', true);
select lives_ok(
    $$select public.take_one(
        (select id from public.messages where author_id = '48000000-0000-0000-0000-000000000001' limit 1),
        (select sequence from public.reigns where ended_at is null),
        '50000000-0000-0000-0000-00000000d001'
    )$$,
    'DEFAULT-DOCUMENTING, NOT A PASS CONDITION: with require_linked_identity_for_takes OFF (shipped default), an unlinked direct take_one call still succeeds today'
);
update public.reigns set protected_until = now() - interval '1 second' where ended_at is null;

update public.app_settings set require_linked_identity_for_takes = true where singleton;

-- TEST 22/23: concurrency still holds with the new check in place, using
-- two genuinely eligible (linked, unbanned) accounts - Q and R race for the
-- same captured sequence.
create temporary table seq_before_flag_race as select sequence as s from public.reigns where ended_at is null;
select ok((select (s is not null) from seq_before_flag_race), 'captured a sequence to race for under the new flag');

select set_config('request.jwt.claims', '{"sub":"4a000000-0000-0000-0000-000000000003","role":"authenticated"}', true);
create temporary table flagrace_r as
select public.take_one_web(
    (select id from public.messages where author_id = '4a000000-0000-0000-0000-000000000003' limit 1),
    (select s from seq_before_flag_race),
    '50000000-0000-0000-0000-00000000e001'
) result;
select ok((select (result->>'ok')::boolean from flagrace_r), 'R (linked, eligible) wins the race under require_linked_identity_for_takes=true');

select set_config('request.jwt.claims', '{"sub":"49000000-0000-0000-0000-000000000002","role":"authenticated"}', true);
select is(
    (public.take_one_web(
        (select id from public.messages where author_id = '49000000-0000-0000-0000-000000000002' limit 1),
        (select s from seq_before_flag_race),
        '50000000-0000-0000-0000-00000000e002'
    )->>'code'),
    'STALE_REIGN',
    'Q (linked, eligible) correctly loses the same race against the now-stale sequence - new check did not break concurrency'
);

-- TEST 24: THE ACTUAL FIX. P is unlinked. Direct take_one call (not
-- take_one_web) must now be REJECTED, not merely observed to succeed.
select set_config('request.jwt.claims', '{"sub":"48000000-0000-0000-0000-000000000001","role":"authenticated"}', true);
update public.reigns set protected_until = now() - interval '1 second' where ended_at is null;
select throws_like(
    $$select public.take_one(
        (select id from public.messages where author_id = '48000000-0000-0000-0000-000000000001' limit 1),
        (select sequence from public.reigns where ended_at is null),
        '50000000-0000-0000-0000-00000000f001'
    )$$,
    '%IDENTITY_REQUIRED%',
    'FIX CONFIRMED: direct take_one call from an unlinked account is now rejected with require_linked_identity_for_takes=true - the take_one_web bypass is closed'
);

-- TEST 25: the ban check still applies independently of the identity flag.
-- S is linked (passes the identity check) but its sub is in
-- banned_google_subs - direct take_one must still reject it.
insert into public.banned_google_subs(google_sub, reason) values ('sub-flag-s-willbeban', 'test fixture');
select set_config('request.jwt.claims', '{"sub":"4b000000-0000-0000-0000-000000000004","role":"authenticated"}', true);
select throws_like(
    $$select public.take_one(
        (select id from public.messages where author_id = '4b000000-0000-0000-0000-000000000004' limit 1),
        (select sequence from public.reigns where ended_at is null),
        '50000000-0000-0000-0000-00000000f002'
    )$$,
    '%ACCOUNT_BLOCKED%',
    'a linked-but-google-sub-banned account is still blocked by direct take_one, independent of the identity flag'
);

-- TEST 26/27: bypass_take_refill rejects S (blocked) BEFORE spending a
-- credit - not just blocked at the final take.
update public.profiles set take_balance = 0, take_refill_at = now() + interval '1 hour', revenge_tickets = 5 where id = '4b000000-0000-0000-0000-000000000004';
select throws_like(
    $$select public.bypass_take_refill('60000000-0000-0000-0000-000000000001', 'credits')$$,
    '%ACCOUNT_BLOCKED%',
    'bypass_take_refill rejects a google-sub-banned account before touching credits'
);
select is(
    (select revenge_tickets from public.profiles where id = '4b000000-0000-0000-0000-000000000004'),
    5,
    'credits were NOT spent on the rejected bypass_take_refill call'
);

-- TEST 28/29: claim_starter_message rejects P (unlinked, flag on) BEFORE
-- creating a message row.
select set_config('request.jwt.claims', '{"sub":"48000000-0000-0000-0000-000000000001","role":"authenticated"}', true);
create temporary table p_message_count_before as select count(*) as c from public.messages where author_id = '48000000-0000-0000-0000-000000000001';
select throws_like(
    $$select public.claim_starter_message((select id from public.starter_messages where active limit 1), '60000000-0000-0000-0000-000000000002')$$,
    '%IDENTITY_REQUIRED%',
    'claim_starter_message rejects an unlinked account before creating a message, when the identity flag is on'
);
select is(
    (select count(*) from public.messages where author_id = '48000000-0000-0000-0000-000000000001'),
    (select c from p_message_count_before),
    'no message row was created by the rejected claim_starter_message call'
);

-- TEST 30: rollback - turning the identity flag back off restores P's
-- ability to take directly, proving this is a real, reversible flag and
-- not a one-way migration.
update public.app_settings set require_linked_identity_for_takes = false where singleton;
update public.reigns set protected_until = now() - interval '1 second' where ended_at is null;
select lives_ok(
    $$select public.take_one(
        (select id from public.messages where author_id = '48000000-0000-0000-0000-000000000001' limit 1),
        (select sequence from public.reigns where ended_at is null),
        '50000000-0000-0000-0000-00000000f003'
    )$$,
    'turning require_linked_identity_for_takes back off restores direct take_one for an unlinked account'
);

-- ===========================================================================
-- TESTS 32-34: identity-conflict case for linking, not just inferred from
-- the constraint error seen earlier in this session. Two different,
-- never-linked accounts (V, W) both try to link the SAME real Google
-- account. The DB-level constraint this relies on
-- (identities_provider_id_provider_unique) is exactly what backs
-- GoTrue's real identity_already_exists error that the Android app already
-- handles (CloudOneRepository.kt:489) - this proves the constraint exists
-- and that a failed second attempt leaves the FIRST account's data (here:
-- its message count, standing in for "existing profile/messages/credits/
-- history") completely untouched, not partially written then rolled back
-- in some lossy way.
-- ===========================================================================
select set_config('request.jwt.claims', '{"sub":"4c000000-0000-0000-0000-000000000005","role":"authenticated"}', true);
select public.ensure_profile(null);
select set_config('request.jwt.claims', '{"sub":"4d000000-0000-0000-0000-000000000006","role":"authenticated"}', true);
select public.ensure_profile(null);
insert into auth.users (id) values
  ('4c000000-0000-0000-0000-000000000005'),
  ('4d000000-0000-0000-0000-000000000006');

-- V links first - succeeds.
select lives_ok(
    $$insert into auth.identities (user_id, provider, provider_id, identity_data, created_at, updated_at)
      values ('4c000000-0000-0000-0000-000000000005', 'google', 'sub-conflict-shared', jsonb_build_object('sub', 'sub-conflict-shared'), now(), now())$$,
    'V links a Google account that nobody else has - succeeds'
);
create temporary table v_message_count_before_conflict as select count(*) as c from public.messages where author_id = '4c000000-0000-0000-0000-000000000005';

-- W tries to link the SAME Google account second - must fail. This is the
-- real mechanism behind GoTrue's identity_already_exists error.
select throws_like(
    $$insert into auth.identities (user_id, provider, provider_id, identity_data, created_at, updated_at)
      values ('4d000000-0000-0000-0000-000000000006', 'google', 'sub-conflict-shared', jsonb_build_object('sub', 'sub-conflict-shared'), now(), now())$$,
    '%duplicate key value violates unique constraint%',
    'W cannot link the SAME Google account V already linked - rejected at the database level, not a client-side-only check'
);

-- V's data is completely untouched by W's failed attempt.
select is(
    (select count(*) from public.messages where author_id = '4c000000-0000-0000-0000-000000000005'),
    (select c from v_message_count_before_conflict),
    'V''s message history is unchanged by W''s rejected conflicting link attempt'
);
select ok(
    (select public.linked_google_sub('4c000000-0000-0000-0000-000000000005'::uuid) = 'sub-conflict-shared'),
    'V is still the one holding the linked identity after the conflict attempt'
);

select * from finish();
rollback;
