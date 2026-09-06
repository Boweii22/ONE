-- Rewarded-ad cooldown skip: a separate, non-transferable grant, never ONE Credits or wallet balance.
-- See ECONOMY.md: an ad grants one immediate ad skip, capped at three per UTC day, consumed only by
-- the reserved takeover attempt. There is no server-side AdMob/RevenueCat SSV bridge wired up yet;
-- this RPC is the redemption endpoint that bridge will call once it exists (MONETIZATION-SETUP.md).
begin;

alter table public.profiles
    add column if not exists ad_skip_available boolean not null default false,
    add column if not exists ad_skips_granted_date date not null default (now() at time zone 'utc')::date,
    add column if not exists ad_skips_granted_count integer not null default 0;

create table if not exists public.ad_skip_grants (
    request_id text primary key,
    user_id uuid not null references public.profiles(id),
    created_at timestamptz not null default now()
);
alter table public.ad_skip_grants enable row level security;

create or replace function public.grant_ad_skip(p_request_id text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
    v_uid uuid := auth.uid();
    v_profile public.profiles%rowtype;
    v_today date := (now() at time zone 'utc')::date;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    if p_request_id is null or length(p_request_id) = 0 then raise exception 'REQUEST_ID_REQUIRED'; end if;

    select * into v_profile from public.profiles where id = v_uid for update;
    if not found or v_profile.banned_at is not null then raise exception 'ACCOUNT_UNAVAILABLE'; end if;

    if exists(select 1 from public.ad_skip_grants where request_id = p_request_id) then
        select * into v_profile from public.profiles where id = v_uid;
        return jsonb_build_object(
            'ok', true,
            'already_granted', true,
            'ad_skip_available', v_profile.ad_skip_available,
            'ad_skips_remaining_today', greatest(0, 3 - case when v_profile.ad_skips_granted_date = v_today then v_profile.ad_skips_granted_count else 0 end)
        );
    end if;

    if v_profile.ad_skips_granted_date <> v_today then
        update public.profiles set ad_skips_granted_date = v_today, ad_skips_granted_count = 0 where id = v_uid;
        v_profile.ad_skips_granted_count := 0;
    end if;

    if v_profile.ad_skips_granted_count >= 3 then
        raise exception 'AD_SKIP_DAILY_CAP';
    end if;

    insert into public.ad_skip_grants(request_id, user_id) values (p_request_id, v_uid);
    update public.profiles
        set ad_skip_available = true, ad_skips_granted_count = ad_skips_granted_count + 1
        where id = v_uid;

    return jsonb_build_object(
        'ok', true,
        'already_granted', false,
        'ad_skip_available', true,
        'ad_skips_remaining_today', greatest(0, 3 - (v_profile.ad_skips_granted_count + 1))
    );
end $$;
revoke all on function public.grant_ad_skip(text) from public, anon;
grant execute on function public.grant_ad_skip(text) to authenticated;

-- take_one: allow a granted ad skip to substitute for a ONE Credit during cooldown, consumed only
-- on a successful reserved attempt (never merely for opening the challenge sheet).
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
    v_remaining integer := 0;
    v_spent_ticket boolean := false;
    v_used_ad_skip boolean := false;
    v_result jsonb;
    v_palette text;
    v_streak integer;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    perform public.ensure_profile(null);

    select result into v_result from public.take_requests where id = p_request_id and user_id = v_uid;
    if found and v_result is not null then return v_result; end if;

    begin
        insert into public.take_requests(id, user_id, expected_sequence, status)
        values (p_request_id, v_uid, p_expected_sequence, 'started');
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
    v_remaining := case when v_profile.last_take_at > now() - make_interval(secs => v_settings.idle_reset_seconds)
        then greatest(0, ceil(extract(epoch from (v_profile.cooldown_until - now())))::integer) else 0 end;
    if v_remaining > 0 then
        if v_profile.ad_skip_available then
            update public.profiles set ad_skip_available = false where id = v_uid;
            v_used_ad_skip := true;
        elsif v_profile.revenge_tickets < 1 then
            v_result := jsonb_build_object('ok', false, 'code', 'COOLDOWN', 'message', 'Free steal recharges in ' || v_remaining || 's.', 'cooldown_seconds', v_remaining);
            update public.take_requests set status = 'rejected', result = v_result where id = p_request_id;
            return v_result;
        else
            update public.profiles set revenge_tickets = revenge_tickets - 1 where id = v_uid;
            insert into public.ticket_ledger(user_id, delta, reason, reference_id)
            values (v_uid, -1, 'cooldown_skip', p_request_id::text);
            v_spent_ticket := true;
        end if;
    end if;

    update public.reigns set ended_at = now() where id = v_current.id;
    v_palette := (array['ACID','COBALT','ORANGE','MAGENTA','ICE'])[((v_current.sequence % 5) + 1)::integer];
    insert into public.reigns(owner_id, message_id, previous_reign_id, protected_until, used_revenge_ticket, palette)
    values (v_uid, v_message.id, v_current.id, now() + make_interval(secs => v_settings.protection_seconds), v_spent_ticket, v_palette)
    returning * into v_new;

    update public.messages set times_deployed = times_deployed + 1 where id = v_message.id;
    v_streak := case when v_profile.last_take_at > now() - make_interval(secs => v_settings.idle_reset_seconds)
        then v_profile.take_streak + 1 else 1 end;
    update public.profiles set last_take_at = now(), take_streak = v_streak,
      cooldown_until = now() + make_interval(secs => case
        when v_streak < v_settings.intro_free_takes then 0
        when v_streak = v_settings.intro_free_takes then v_settings.cooldown_first_seconds
        when v_streak = v_settings.intro_free_takes + 1 then v_settings.cooldown_second_seconds
        else v_settings.cooldown_max_seconds end)
    where id = v_uid;

    v_result := jsonb_build_object(
        'ok', true,
        'reign_id', v_new.id,
        'sequence', v_new.sequence,
        'previous_owner_id', v_current.owner_id,
        'used_revenge_ticket', v_spent_ticket,
        'used_ad_skip', v_used_ad_skip,
        'started_at_ms', floor(extract(epoch from v_new.started_at) * 1000)::bigint
    );
    update public.take_requests set status = 'committed', result = v_result where id = p_request_id;
    return v_result;
end;
$$;

alter function public.get_one_state() rename to get_one_state_before_ad_skip;
revoke all on function public.get_one_state_before_ad_skip() from public, anon, authenticated;
create function public.get_one_state() returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare
    v_state jsonb := public.get_one_state_before_ad_skip();
    v_available boolean := false;
    v_remaining integer := 3;
begin
    select ad_skip_available,
        greatest(0, 3 - case when ad_skips_granted_date = (now() at time zone 'utc')::date then ad_skips_granted_count else 0 end)
    into v_available, v_remaining
    from public.profiles where id = auth.uid();
    return v_state || jsonb_build_object('ad_skip_available', coalesce(v_available, false), 'ad_skips_remaining_today', coalesce(v_remaining, 3));
end $$;
revoke all on function public.get_one_state() from public;
grant execute on function public.get_one_state() to anon, authenticated;

commit;
