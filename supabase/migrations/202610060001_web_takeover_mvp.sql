-- Browser-playable takeover, milestone 1 (inert by default - both flags
-- below default to false, so nothing here changes existing Android
-- behaviour until each is explicitly flipped on).
--
-- REVISED after review: an earlier version of this migration put the
-- "must be linked to take" requirement ONLY in take_one_web, and called
-- that a web-only gate "by design" - that was wrong. It was proven directly
-- (a linked, unbanned test account called take_one while web_take_enabled
-- was false and still succeeded) that take_one_web's checks are trivially
-- skippable by anyone calling take_one directly. Two flags now exist, doing
-- two genuinely different jobs:
--
--   web_take_enabled                   - UI-ONLY. Whether the officially
--                                         shipped browser take-flow shows
--                                         itself at all. NOT a security
--                                         boundary - calling take_one_web
--                                         directly while this is false
--                                         still gets refused (WEB_TAKES_
--                                         DISABLED), but a caller who skips
--                                         take_one_web entirely is wholly
--                                         unaffected by this flag, flag
--                                         state notwithstanding.
--
--   require_linked_identity_for_takes  - THE real, universal gate. Checked
--                                         directly inside take_one, so every
--                                         caller - Android, web, or a raw
--                                         curl to the RPC - goes through the
--                                         exact same check with no
--                                         alternative path. Off by default
--                                         because today's Android install
--                                         base is overwhelmingly anonymous;
--                                         flipping it on is a real breaking
--                                         change for them, not a web
--                                         rollout detail, and needs its own
--                                         sign-off and an Android client
--                                         update first (see the app's
--                                         IDENTITY_REQUIRED handling).
--
-- Threat model this migration is built around:
--   - CAPTCHA/verified-email are FRICTION, not identity binding. Neither
--     stops a banned human from making a brand new Google account. The only
--     thing that narrows that gap is banning the external identity itself
--     (the Google account's stable `sub` claim), not the local profile row,
--     which today is trivially replaced by clearing cookies.
--   - Google identity is read ONLY from auth.identities (populated by
--     Supabase Auth itself during the real OAuth exchange) - never accepted
--     as a parameter from a client. A client cannot forge this value.
--   - Android is completely unaffected while require_linked_identity_for_takes
--     is false (the shipped default): most existing Android users are still
--     on anonymous auth, and take_one's behaviour for them is byte-for-byte
--     unchanged. The banned_google_subs check is unconditional but a no-op
--     for any uid with no linked Google identity.
--
-- Known, accepted residual risk even with require_linked_identity_for_takes
-- on (documented, not assumed away): a banned person who creates a
-- genuinely NEW Google account is not stopped - only slowed by whatever
-- friction Google's own signup imposes. What IS enforced, regardless of
-- either flag's state and no matter which RPC is called, is the
-- banned_google_subs check, since it lives inside take_one itself.
begin;

alter table public.app_settings
    add column if not exists web_take_enabled boolean not null default false,
    add column if not exists require_linked_identity_for_takes boolean not null default false,
    add column if not exists starter_claim_limit_per_minute integer not null default 5 check (starter_claim_limit_per_minute between 1 and 60);

-- Looks up the CALLER's linked Google identity directly from auth.identities
-- (populated only by a real Supabase Auth OAuth exchange) - never trust a
-- client-supplied sub. No grants: internal helper only, called from other
-- security definer functions in this file (which, same as ensure_profile
-- elsewhere in this codebase, run as the function owner regardless of the
-- revoke below).
create or replace function public.linked_google_sub(p_uid uuid)
returns text
language sql
stable
security definer
set search_path = ''
as $$
    select identity_data->>'sub'
    from auth.identities
    where user_id = p_uid and provider = 'google'
    order by created_at asc
    limit 1;
$$;
revoke all on function public.linked_google_sub(uuid) from public, anon, authenticated;

-- A Google account (by its stable sub, not the local profile row) that has
-- been tied to a ban. Deliberately has no client-facing write RPC in this
-- MVP - populated only by sync_banned_google_sub_for below, itself only
-- ever called server-side (the ban trigger, and mark_google_linked).
create table if not exists public.banned_google_subs (
    google_sub text primary key,
    banned_at timestamptz not null default now(),
    reason text
);
alter table public.banned_google_subs enable row level security;
revoke all on public.banned_google_subs from public, anon, authenticated;

-- Idempotent: if p_uid has a linked Google identity AND is currently banned
-- (profiles.banned_at set, by whatever path), records that Google account as
-- banned too. Called from both directions so ORDER doesn't matter:
--   1. an already-linked user gets banned later (trigger below)
--   2. an already-banned user links Google later (called from
--      mark_google_linked)
create or replace function public.sync_banned_google_sub_for(p_uid uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_sub text := public.linked_google_sub(p_uid);
    v_banned boolean;
begin
    if v_sub is null then return; end if;
    select (banned_at is not null) into v_banned from public.profiles where id = p_uid;
    if coalesce(v_banned, false) then
        insert into public.banned_google_subs(google_sub, reason)
        values (v_sub, 'synced_from_profile_ban:' || p_uid)
        on conflict (google_sub) do nothing;
    end if;
end;
$$;
revoke all on function public.sync_banned_google_sub_for(uuid) from public, anon, authenticated;

create or replace function public.sync_banned_google_sub_on_ban()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if new.banned_at is not null and new.banned_at is distinct from old.banned_at then
        perform public.sync_banned_google_sub_for(new.id);
    end if;
    return new;
end;
$$;
drop trigger if exists trg_sync_banned_google_sub_on_ban on public.profiles;
create trigger trg_sync_banned_google_sub_on_ban
    after update of banned_at on public.profiles
    for each row execute function public.sync_banned_google_sub_on_ban();

-- Covers the "already-banned account then links Google" direction: run the
-- same sync right after a link completes, on every call (idempotent via
-- on conflict do nothing above), not just the first one.
create or replace function public.mark_google_linked(p_email text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_profile public.profiles%rowtype;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    if p_email is null or length(p_email) = 0 or position('@' in p_email) = 0 then
        raise exception 'EMAIL_REQUIRED';
    end if;

    select * into v_profile from public.profiles where id = v_uid for update;
    if not found then raise exception 'PROFILE_NOT_FOUND'; end if;

    perform public.sync_banned_google_sub_for(v_uid);

    if v_profile.google_welcome_sent_at is not null then
        return jsonb_build_object('ok', true, 'already_sent', true);
    end if;

    update public.profiles set google_welcome_sent_at = now() where id = v_uid;
    perform public.send_google_welcome_email(p_email, v_profile.handle);

    return jsonb_build_object('ok', true, 'already_sent', false);
end;
$$;
revoke all on function public.mark_google_linked(text) from public, anon;
grant execute on function public.mark_google_linked(text) to authenticated;

-- take_one: ONE new check, placed next to the existing banned_at check it
-- mirrors. For any uid with no linked Google identity (the vast majority of
-- today's Android users), linked_google_sub() returns null, the exists()
-- is false, and this is a byte-for-byte no-op - existing behaviour is
-- unchanged. Same signature, same grants as before.
create or replace function public.take_one(p_message_id uuid, p_expected_sequence bigint, p_request_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_current public.reigns%rowtype;
    v_message public.messages%rowtype;
    v_profile public.profiles%rowtype;
    v_settings public.app_settings%rowtype;
    v_new public.reigns%rowtype;
    v_result jsonb;
    v_palette text;
    v_wait_seconds integer;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    perform public.ensure_profile(null);

    select result into v_result from public.take_requests where id = p_request_id and user_id = v_uid;
    if found and v_result is not null then return v_result; end if;

    begin
        insert into public.take_requests(id, user_id, expected_sequence, status, expires_at)
        values (p_request_id, v_uid, p_expected_sequence, 'started', now() + interval '10 minutes');
    exception when unique_violation then
        select result into v_result from public.take_requests where id = p_request_id and user_id = v_uid;
        if v_result is not null then return v_result; end if;
        raise exception 'REQUEST_ID_COLLISION';
    end;

    perform pg_advisory_xact_lock(771041);
    select * into v_settings from public.app_settings where singleton;
    if v_settings.global_kill_switch then raise exception 'ONE_IS_TEMPORARILY_PAUSED'; end if;

    select * into v_current from public.reigns where ended_at is null for update;
    if v_current.sequence <> p_expected_sequence then
        v_result := jsonb_build_object('ok', false, 'code', 'STALE_REIGN', 'message', 'Someone reached ONE first. You spent nothing.');
        update public.take_requests set status = 'rejected', result = v_result where id = p_request_id;
        return v_result;
    end if;
    if v_current.owner_id = v_uid then
        v_result := jsonb_build_object('ok', false, 'code', 'ALREADY_OWNER', 'message', 'You already own ONE. Defend it.');
        update public.take_requests set status = 'rejected', result = v_result where id = p_request_id;
        return v_result;
    end if;
    if v_current.protected_until > now() then
        v_result := jsonb_build_object('ok', false, 'code', 'PROTECTED', 'message', 'The takeover is still landing. Try again in a moment.');
        update public.take_requests set status = 'rejected', result = v_result where id = p_request_id;
        return v_result;
    end if;

    select * into v_message from public.messages
    where id = p_message_id and author_id = v_uid and status = 'approved';
    if not found then raise exception 'APPROVED_MESSAGE_REQUIRED'; end if;

    select * into v_profile from public.profiles where id = v_uid for update;
    if v_profile.banned_at is not null then raise exception 'ACCOUNT_BLOCKED'; end if;
    if exists (select 1 from public.banned_google_subs b where b.google_sub = public.linked_google_sub(v_uid)) then
        raise exception 'ACCOUNT_BLOCKED';
    end if;
    -- The real, universal gate (see migration header). Checked here, inside
    -- the one function every caller must go through, specifically because
    -- take_one_web's own identity check is NOT enough - it can be skipped
    -- entirely by calling take_one directly. Off by default.
    if v_settings.require_linked_identity_for_takes and public.linked_google_sub(v_uid) is null then
        raise exception 'IDENTITY_REQUIRED';
    end if;

    -- Idle reset: a long break means the ladder shouldn't still be sitting at
    -- an escalated step from a session that's effectively over.
    if v_profile.take_ladder_touched_at is not null
        and v_profile.take_ladder_touched_at < now() - make_interval(secs => v_settings.take_ladder_reset_seconds) then
        v_profile.take_ladder_step := 0;
    end if;

    -- Collect a refill that has completed but not yet been applied.
    if v_profile.take_balance <= 0 and v_profile.take_refill_at is not null and v_profile.take_refill_at <= now() then
        v_profile.take_balance := 1;
        v_profile.take_refill_at := null;
    end if;

    if v_profile.take_balance <= 0 then
        v_wait_seconds := greatest(0, ceil(extract(epoch from (v_profile.take_refill_at - now())))::integer);
        v_result := jsonb_build_object('ok', false, 'code', 'REFILLING', 'message', 'Your next take is ready in ' || v_wait_seconds || 's.', 'take_refill_seconds', v_wait_seconds);
        -- Persist a possible idle-reset even though this attempt is rejected.
        update public.profiles set
            take_ladder_step = v_profile.take_ladder_step,
            take_balance = v_profile.take_balance,
            take_refill_at = v_profile.take_refill_at
        where id = v_uid;
        update public.take_requests set status = 'rejected', result = v_result where id = p_request_id;
        return v_result;
    end if;

    v_profile.take_balance := v_profile.take_balance - 1;
    v_profile.take_ladder_touched_at := now();
    if v_profile.take_balance <= 0 then
        v_profile.take_refill_at := now() + make_interval(secs => v_settings.take_ladder_seconds[v_profile.take_ladder_step + 1]);
        v_profile.take_ladder_step := (v_profile.take_ladder_step + 1) % array_length(v_settings.take_ladder_seconds, 1);
    end if;

    update public.reigns set ended_at = now() where id = v_current.id;
    v_palette := (array['ACID','COBALT','ORANGE','MAGENTA','ICE'])[((v_current.sequence % 5) + 1)::integer];
    insert into public.reigns(owner_id, message_id, previous_reign_id, protected_until, used_revenge_ticket, palette)
    values (v_uid, v_message.id, v_current.id, now() + make_interval(secs => v_settings.protection_seconds), false, v_palette)
    returning * into v_new;

    update public.messages set times_deployed = times_deployed + 1 where id = v_message.id;
    update public.profiles set
        last_take_at = now(),
        take_balance = v_profile.take_balance,
        take_ladder_step = v_profile.take_ladder_step,
        take_refill_at = v_profile.take_refill_at,
        take_ladder_touched_at = v_profile.take_ladder_touched_at
    where id = v_uid;

    v_result := jsonb_build_object(
        'ok', true,
        'reign_id', v_new.id,
        'sequence', v_new.sequence,
        'previous_owner_id', v_current.owner_id,
        'used_revenge_ticket', false,
        'started_at_ms', floor(extract(epoch from v_new.started_at) * 1000)::bigint
    );
    update public.take_requests set status = 'committed', result = v_result where id = p_request_id;
    return v_result;
end;
$$;
revoke all on function public.take_one(uuid, bigint, uuid) from public, anon;
grant execute on function public.take_one(uuid, bigint, uuid) to authenticated;

-- The entry point the OFFICIAL web client calls. These are fast, friendly
-- pre-checks for that one client - NOT the security boundary. web_take_enabled
-- here is a UI-rollout flag only; flipping it off blocks a browser tab that
-- is still calling THIS function on its very next call, but a caller that
-- skips take_one_web and calls take_one directly is untouched by it either
-- way. The identity check below is likewise just an earlier, friendlier
-- copy of the one now inside take_one itself - delegating to take_one means
-- the REAL enforcement (require_linked_identity_for_takes, the ban checks,
-- and the concurrency guarantees) is inherited unconditionally, not
-- reimplemented, and cannot be skipped by calling this wrapper instead of
-- take_one.
create or replace function public.take_one_web(p_message_id uuid, p_expected_sequence bigint, p_request_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_settings public.app_settings%rowtype;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    select * into v_settings from public.app_settings where singleton;
    if not coalesce(v_settings.web_take_enabled, false) then
        raise exception 'WEB_TAKES_DISABLED';
    end if;
    if public.linked_google_sub(v_uid) is null then
        raise exception 'IDENTITY_REQUIRED';
    end if;
    return public.take_one(p_message_id, p_expected_sequence, p_request_id);
end;
$$;
revoke all on function public.take_one_web(uuid, bigint, uuid) from public, anon;
grant execute on function public.take_one_web(uuid, bigint, uuid) to authenticated;

-- Starter messages: a server-side allowlist. The client only ever sends an
-- id - never asserts "this text is approved". No client-facing write RPCs
-- on this table in this MVP (seed/manage via SQL editor).
create table if not exists public.starter_messages (
    id uuid primary key default gen_random_uuid(),
    text text not null check (char_length(text) between 1 and 90),
    active boolean not null default true,
    created_at timestamptz not null default now()
);
alter table public.starter_messages enable row level security;
revoke all on public.starter_messages from public, anon, authenticated;

insert into public.starter_messages (text, active) values
    ('I TOOK ONE.', true),
    ('WATCHED. THEN TAKEN.', true),
    ('YOUR TURN IS OVER.', true),
    ('THE SCREEN IS MINE NOW.', true),
    ('FROM THE WEB, WITH LOVE.', true)
on conflict do nothing;

create or replace function public.list_starter_messages()
returns setof public.starter_messages
language sql
stable
security definer
set search_path = ''
as $$
    select id, text, active, created_at from public.starter_messages where active order by created_at asc;
$$;
revoke all on function public.list_starter_messages() from public;
grant execute on function public.list_starter_messages() to anon, authenticated;

-- Dedupes retries by request id (same pattern as take_requests), and its row
-- count per user is also the rate-limit window.
create table if not exists public.starter_message_claims (
    request_id uuid primary key,
    user_id uuid not null references public.profiles(id),
    starter_id uuid not null references public.starter_messages(id),
    message_id uuid not null references public.messages(id),
    claimed_at timestamptz not null default now()
);
alter table public.starter_message_claims enable row level security;
revoke all on public.starter_message_claims from public, anon, authenticated;
create index if not exists starter_message_claims_user_time_idx on public.starter_message_claims(user_id, claimed_at desc);

-- Copies a starter's TEXT (read server-side from the table row, never from
-- the client) into a brand new approved message. p_starter_id is an opaque
-- reference - there is no way to pass arbitrary text through this function
-- and have it come out "approved": take_one still separately requires
-- messages.status = 'approved' and author_id = caller, which this satisfies
-- exactly like any other approved message, with zero changes to take_one
-- needed for this part.
create or replace function public.claim_starter_message(p_starter_id uuid, p_request_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_starter public.starter_messages%rowtype;
    v_settings public.app_settings%rowtype;
    v_recent_count integer;
    v_existing public.starter_message_claims%rowtype;
    v_message_id uuid;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    if p_request_id is null then raise exception 'REQUEST_ID_REQUIRED'; end if;
    perform public.ensure_profile(null);

    select * into v_existing from public.starter_message_claims where request_id = p_request_id;
    if found then
        if v_existing.user_id <> v_uid then raise exception 'REQUEST_ID_COLLISION'; end if;
        return jsonb_build_object('ok', true, 'message_id', v_existing.message_id, 'already_claimed', true);
    end if;

    if (select count(*) from public.messages where author_id = v_uid and created_at > now() - interval '1 day') >= 20 then
        raise exception 'DAILY_MESSAGE_LIMIT';
    end if;

    select * into v_settings from public.app_settings where singleton;
    -- Not a credit/take/ownership spend (claiming a starter is free), but
    -- there's no point letting someone go through "pick a message" only to
    -- hit IDENTITY_REQUIRED at the actual take - fail fast here too.
    if v_settings.require_linked_identity_for_takes and public.linked_google_sub(v_uid) is null then
        raise exception 'IDENTITY_REQUIRED';
    end if;
    if exists (select 1 from public.banned_google_subs b where b.google_sub = public.linked_google_sub(v_uid)) then
        raise exception 'ACCOUNT_BLOCKED';
    end if;
    if (select banned_at from public.profiles where id = v_uid) is not null then
        raise exception 'ACCOUNT_BLOCKED';
    end if;
    select count(*) into v_recent_count from public.starter_message_claims
        where user_id = v_uid and claimed_at > now() - interval '60 seconds';
    if v_recent_count >= v_settings.starter_claim_limit_per_minute then
        raise exception 'STARTER_CLAIM_RATE_LIMIT';
    end if;

    select * into v_starter from public.starter_messages where id = p_starter_id and active for update;
    if not found then raise exception 'STARTER_MESSAGE_NOT_FOUND'; end if;

    insert into public.messages(author_id, text, status)
    values (v_uid, v_starter.text, 'approved')
    returning id into v_message_id;

    insert into public.starter_message_claims(request_id, user_id, starter_id, message_id)
    values (p_request_id, v_uid, v_starter.id, v_message_id);

    return jsonb_build_object('ok', true, 'message_id', v_message_id, 'already_claimed', false);
end;
$$;
revoke all on function public.claim_starter_message(uuid, uuid) from public, anon;
grant execute on function public.claim_starter_message(uuid, uuid) to authenticated;

-- Both of these already checked profiles.banned_at (202609260001). Add the
-- same two checks take_one got: banned_google_subs (unconditional) and the
-- universal identity requirement (flag-gated) - BEFORE the credit/ad-view
-- spend, so a blocked account doesn't burn a real resource on a refill that
-- can never produce a successful take. Same signatures, same grants as
-- 202609260001_verified_ad_bypass.sql; only the body changes.
create or replace function public.bypass_take_refill(p_request_id text, p_method text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_profile public.profiles%rowtype;
    v_settings public.app_settings%rowtype;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    if p_method = 'ad' then raise exception 'AD_REQUIRES_VERIFICATION'; end if;
    if p_method <> 'credits' then raise exception 'INVALID_METHOD'; end if;
    if p_request_id is null or length(p_request_id) = 0 then raise exception 'REQUEST_ID_REQUIRED'; end if;
    perform public.ensure_profile(null);

    if exists(select 1 from public.take_bypass_requests where request_id = p_request_id) then
        select * into v_profile from public.profiles where id = v_uid;
        return jsonb_build_object('ok', true, 'already_granted', true, 'take_balance', v_profile.take_balance);
    end if;

    select * into v_settings from public.app_settings where singleton;
    select * into v_profile from public.profiles where id = v_uid for update;
    if v_profile.banned_at is not null then raise exception 'ACCOUNT_BLOCKED'; end if;
    if exists (select 1 from public.banned_google_subs b where b.google_sub = public.linked_google_sub(v_uid)) then
        raise exception 'ACCOUNT_BLOCKED';
    end if;
    if v_settings.require_linked_identity_for_takes and public.linked_google_sub(v_uid) is null then
        raise exception 'IDENTITY_REQUIRED';
    end if;

    if v_profile.take_balance > 0 or v_profile.take_refill_at is null or v_profile.take_refill_at <= now() then
        raise exception 'NO_REFILL_IN_PROGRESS';
    end if;

    if v_profile.revenge_tickets < v_settings.take_bypass_credit_cost then
        raise exception 'INSUFFICIENT_CREDITS';
    end if;
    v_profile.revenge_tickets := v_profile.revenge_tickets - v_settings.take_bypass_credit_cost;
    insert into public.ticket_ledger(user_id, delta, reason, reference_id)
    values (v_uid, -v_settings.take_bypass_credit_cost, 'take_refill_bypass', p_request_id);

    insert into public.take_bypass_requests(request_id, user_id, method) values (p_request_id, v_uid, 'credits');

    update public.profiles set
        take_balance = 1,
        take_refill_at = null,
        revenge_tickets = v_profile.revenge_tickets
    where id = v_uid;

    return jsonb_build_object(
        'ok', true,
        'already_granted', false,
        'take_balance', 1,
        'method', 'credits',
        'ad_bypasses_remaining_today', greatest(0, v_settings.take_ad_bypass_daily_cap - case when v_profile.take_ad_bypasses_used_date = (now() at time zone 'utc')::date then v_profile.take_ad_bypasses_used_count else 0 end),
        'credits_remaining', v_profile.revenge_tickets
    );
end;
$$;
revoke all on function public.bypass_take_refill(text, text) from public, anon;
grant execute on function public.bypass_take_refill(text, text) to authenticated;

create or replace function public.grant_verified_ad_bypass(p_user_id uuid, p_request_id text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_profile public.profiles%rowtype;
    v_settings public.app_settings%rowtype;
    v_today date := (now() at time zone 'utc')::date;
begin
    if p_user_id is null then raise exception 'AUTH_REQUIRED'; end if;
    if p_request_id is null or length(p_request_id) = 0 then raise exception 'REQUEST_ID_REQUIRED'; end if;

    if exists(select 1 from public.take_bypass_requests where request_id = p_request_id) then
        select * into v_profile from public.profiles where id = p_user_id;
        return jsonb_build_object('ok', true, 'already_granted', true, 'take_balance', v_profile.take_balance);
    end if;

    select * into v_settings from public.app_settings where singleton;
    select * into v_profile from public.profiles where id = p_user_id for update;
    if not found then raise exception 'AUTH_REQUIRED'; end if;
    if v_profile.banned_at is not null then raise exception 'ACCOUNT_BLOCKED'; end if;
    if exists (select 1 from public.banned_google_subs b where b.google_sub = public.linked_google_sub(p_user_id)) then
        raise exception 'ACCOUNT_BLOCKED';
    end if;
    if v_settings.require_linked_identity_for_takes and public.linked_google_sub(p_user_id) is null then
        raise exception 'IDENTITY_REQUIRED';
    end if;

    if v_profile.take_balance > 0 or v_profile.take_refill_at is null or v_profile.take_refill_at <= now() then
        raise exception 'NO_REFILL_IN_PROGRESS';
    end if;

    if v_profile.take_ad_bypasses_used_date <> v_today then
        v_profile.take_ad_bypasses_used_date := v_today;
        v_profile.take_ad_bypasses_used_count := 0;
    end if;
    if v_profile.take_ad_bypasses_used_count >= v_settings.take_ad_bypass_daily_cap then
        raise exception 'AD_BYPASS_DAILY_CAP';
    end if;
    v_profile.take_ad_bypasses_used_count := v_profile.take_ad_bypasses_used_count + 1;

    insert into public.take_bypass_requests(request_id, user_id, method) values (p_request_id, p_user_id, 'ad');

    update public.profiles set
        take_balance = 1,
        take_refill_at = null,
        take_ad_bypasses_used_date = v_profile.take_ad_bypasses_used_date,
        take_ad_bypasses_used_count = v_profile.take_ad_bypasses_used_count
    where id = p_user_id;

    return jsonb_build_object(
        'ok', true,
        'already_granted', false,
        'take_balance', 1,
        'method', 'ad',
        'ad_bypasses_remaining_today', greatest(0, v_settings.take_ad_bypass_daily_cap - v_profile.take_ad_bypasses_used_count),
        'credits_remaining', v_profile.revenge_tickets
    );
end;
$$;
revoke all on function public.grant_verified_ad_bypass(uuid, text) from public, anon, authenticated;
grant execute on function public.grant_verified_ad_bypass(uuid, text) to service_role;

-- Surfaces the rollout flag and the CALLER's own linked-identity state, so
-- the web client can decide what to render without an extra round trip.
alter function public.get_one_state() rename to get_one_state_before_web_takeover;
revoke all on function public.get_one_state_before_web_takeover() from public, anon, authenticated;
create function public.get_one_state()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_state jsonb := public.get_one_state_before_web_takeover();
    v_uid uuid := auth.uid();
    v_settings public.app_settings%rowtype;
    v_google_linked boolean := false;
begin
    select * into v_settings from public.app_settings where singleton;
    if v_uid is not null then
        v_google_linked := public.linked_google_sub(v_uid) is not null;
    end if;
    return v_state || jsonb_build_object(
        'web_take_enabled', coalesce(v_settings.web_take_enabled, false),
        'require_linked_identity_for_takes', coalesce(v_settings.require_linked_identity_for_takes, false),
        'google_linked', v_google_linked
    );
end;
$$;
revoke all on function public.get_one_state() from public;
grant execute on function public.get_one_state() to anon, authenticated;

commit;
