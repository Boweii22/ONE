-- Replaces the cooldown/streak model with a refilling take balance.
--
-- Two dials, two different jobs (per design direction - do not conflate them):
--   take_balance_cap     - how often a player meets a wall at all
--   take_ladder_seconds  - how painful the wall is once reached
--
-- Balance never expires while full or partial - waiting costs nothing. A
-- refill timer only exists while balance = 0. The ladder position is a
-- separate value: it advances on the spend that drops balance back to zero
-- (never on a natural refill completing, so bypassing a wait can't itself
-- dodge the escalation - the next spend still advances the ladder exactly
-- the same way regardless of how the take was acquired), and resets to the
-- first step after take_ladder_reset_seconds of inactivity.
begin;

alter table public.app_settings
    add column if not exists take_balance_cap integer not null default 2 check (take_balance_cap between 1 and 10),
    add column if not exists take_ladder_seconds integer[] not null default '{60,180,480,1200,1800}',
    add column if not exists take_ladder_reset_seconds integer not null default 10800 check (take_ladder_reset_seconds between 60 and 604800),
    add column if not exists take_ad_bypass_daily_cap integer not null default 2 check (take_ad_bypass_daily_cap between 0 and 10),
    add column if not exists take_bypass_credit_cost integer not null default 1 check (take_bypass_credit_cost between 0 and 100);

alter table public.profiles
    add column if not exists take_balance integer not null default 2 check (take_balance >= 0),
    add column if not exists take_ladder_step integer not null default 0 check (take_ladder_step >= 0),
    add column if not exists take_refill_at timestamptz,
    add column if not exists take_ladder_touched_at timestamptz,
    add column if not exists take_ad_bypasses_used_date date not null default (now() at time zone 'utc')::date,
    add column if not exists take_ad_bypasses_used_count integer not null default 0;

-- Existing players get a clean reset onto the new system rather than trying
-- to reconstruct equivalent state from the retired cooldown_until/take_streak.
update public.profiles set take_balance = 2, take_ladder_step = 0, take_refill_at = null, take_ladder_touched_at = null;

alter table public.profiles
    drop column if exists cooldown_until,
    drop column if exists take_streak,
    drop column if exists ad_skip_available,
    drop column if exists ad_skips_granted_date,
    drop column if exists ad_skips_granted_count;

drop table if exists public.ad_skip_grants;

-- Needed by take_one below; the scheduled cleanup that actually uses this
-- column lives in a separate, lower-priority migration.
alter table public.take_requests add column if not exists expires_at timestamptz;

create table if not exists public.take_bypass_requests (
    request_id text primary key,
    user_id uuid not null references public.profiles(id),
    method text not null check (method in ('ad', 'credits')),
    created_at timestamptz not null default now()
);
alter table public.take_bypass_requests enable row level security;
revoke all on public.take_bypass_requests from public, anon, authenticated;

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

-- Bypasses a running refill timer: watching a rewarded ad (capped per day,
-- resets at UTC midnight) or spending credits (cost configurable). Either
-- grants exactly one take immediately. Deliberately does not touch the
-- ladder itself - the next spend advances it the same way a natural refill's
-- next spend would, so a bypass shortens the wait without shortening the
-- escalation.
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
    v_today date := (now() at time zone 'utc')::date;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    if p_method not in ('ad', 'credits') then raise exception 'INVALID_METHOD'; end if;
    if p_request_id is null or length(p_request_id) = 0 then raise exception 'REQUEST_ID_REQUIRED'; end if;
    perform public.ensure_profile(null);

    if exists(select 1 from public.take_bypass_requests where request_id = p_request_id) then
        select * into v_profile from public.profiles where id = v_uid;
        return jsonb_build_object('ok', true, 'already_granted', true, 'take_balance', v_profile.take_balance);
    end if;

    select * into v_settings from public.app_settings where singleton;
    select * into v_profile from public.profiles where id = v_uid for update;
    if v_profile.banned_at is not null then raise exception 'ACCOUNT_BLOCKED'; end if;

    if v_profile.take_balance > 0 or v_profile.take_refill_at is null or v_profile.take_refill_at <= now() then
        raise exception 'NO_REFILL_IN_PROGRESS';
    end if;

    if p_method = 'ad' then
        if v_profile.take_ad_bypasses_used_date <> v_today then
            v_profile.take_ad_bypasses_used_date := v_today;
            v_profile.take_ad_bypasses_used_count := 0;
        end if;
        if v_profile.take_ad_bypasses_used_count >= v_settings.take_ad_bypass_daily_cap then
            raise exception 'AD_BYPASS_DAILY_CAP';
        end if;
        v_profile.take_ad_bypasses_used_count := v_profile.take_ad_bypasses_used_count + 1;
    else
        if v_profile.revenge_tickets < v_settings.take_bypass_credit_cost then
            raise exception 'INSUFFICIENT_CREDITS';
        end if;
        v_profile.revenge_tickets := v_profile.revenge_tickets - v_settings.take_bypass_credit_cost;
        insert into public.ticket_ledger(user_id, delta, reason, reference_id)
        values (v_uid, -v_settings.take_bypass_credit_cost, 'take_refill_bypass', p_request_id);
    end if;

    insert into public.take_bypass_requests(request_id, user_id, method) values (p_request_id, v_uid, p_method);

    update public.profiles set
        take_balance = 1,
        take_refill_at = null,
        revenge_tickets = v_profile.revenge_tickets,
        take_ad_bypasses_used_date = v_profile.take_ad_bypasses_used_date,
        take_ad_bypasses_used_count = v_profile.take_ad_bypasses_used_count
    where id = v_uid;

    return jsonb_build_object(
        'ok', true,
        'already_granted', false,
        'take_balance', 1,
        'method', p_method,
        'ad_bypasses_remaining_today', greatest(0, v_settings.take_ad_bypass_daily_cap - v_profile.take_ad_bypasses_used_count),
        'credits_remaining', v_profile.revenge_tickets
    );
end;
$$;
revoke all on function public.bypass_take_refill(text, text) from public, anon;
grant execute on function public.bypass_take_refill(text, text) to authenticated;

-- get_one_state: replace the retired cooldown/ad-skip fields with the take
-- balance the client actually needs to render (balance, cap, live refill
-- countdown, remaining ad bypasses today, and the credit cost of a bypass -
-- all tunable in app_settings and readable here without an app update).
alter function public.get_one_state() rename to get_one_state_before_take_balance;
revoke all on function public.get_one_state_before_take_balance() from public, anon, authenticated;
create or replace function public.get_one_state() returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare
    v_state jsonb := public.get_one_state_before_take_balance();
    v_uid uuid := auth.uid();
    v_profile public.profiles%rowtype;
    v_settings public.app_settings%rowtype;
    v_balance integer := 0;
    v_refill_seconds integer := 0;
    v_ad_remaining integer := 0;
    v_today date := (now() at time zone 'utc')::date;
begin
    select * into v_settings from public.app_settings where singleton;
    if v_uid is not null then
        select * into v_profile from public.profiles where id = v_uid;
        if found then
            v_balance := v_profile.take_balance;
            if v_balance <= 0 and v_profile.take_refill_at is not null then
                v_refill_seconds := greatest(0, ceil(extract(epoch from (v_profile.take_refill_at - now())))::integer);
            end if;
            v_ad_remaining := greatest(0, v_settings.take_ad_bypass_daily_cap -
                case when v_profile.take_ad_bypasses_used_date = v_today then v_profile.take_ad_bypasses_used_count else 0 end);
        end if;
    end if;
    return (v_state - 'cooldown_remaining_seconds' - 'ad_skip_available' - 'ad_skips_remaining_today') || jsonb_build_object(
        'take_balance', v_balance,
        'take_balance_cap', v_settings.take_balance_cap,
        'take_refill_seconds', v_refill_seconds,
        'take_ad_bypasses_remaining_today', v_ad_remaining,
        'take_bypass_credit_cost', v_settings.take_bypass_credit_cost
    );
end $$;
revoke all on function public.get_one_state() from public;
grant execute on function public.get_one_state() to anon, authenticated;

commit;
