-- Country-only profiles and server-owned progressive cooldowns.
alter table public.profiles add column if not exists take_streak integer not null default 0;
alter table public.profiles add column if not exists cooldown_until timestamptz;
alter table public.app_settings
 add column if not exists intro_free_takes integer not null default 3 check(intro_free_takes between 2 and 3),
 add column if not exists idle_reset_seconds integer not null default 1800 check(idle_reset_seconds between 300 and 86400),
 add column if not exists cooldown_first_seconds integer not null default 90 check(cooldown_first_seconds between 0 and 3600),
 add column if not exists cooldown_second_seconds integer not null default 240 check(cooldown_second_seconds between 0 and 3600),
 add column if not exists cooldown_max_seconds integer not null default 720 check(cooldown_max_seconds between 0 and 3600),
 add column if not exists reaction_limit_per_10s integer not null default 20 check(reaction_limit_per_10s between 5 and 100),
 add column if not exists ad_skips_daily_limit integer not null default 3 check(ad_skips_daily_limit between 0 and 3);
-- Remove city exposure, including writes from older app versions.
create or replace function public.coarse_profile_country() returns trigger
language plpgsql set search_path = '' as $$
begin
 new.city := '';
 if new.country_code = 'XX' then new.country_code := ''; end if;
 return new;
end $$;
create trigger coarse_profile_country before insert or update on public.profiles
for each row execute function public.coarse_profile_country();
update public.profiles set city = '';
create or replace function public.react_to_one(p_reaction text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_uid uuid := auth.uid(); v_reign_id uuid; v_key text := upper(trim(p_reaction));
begin
  if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
  if v_key is null or v_key not in ('THIEF','TOO SLOW','TAKE IT BACK','RESPECT','LOL','FIRE','100','WATCH','ROCKET') then raise exception 'INVALID_REACTION'; end if;
  perform public.ensure_profile(null);
  -- Lock the profile so concurrent taps cannot bypass the rate limit.
  perform 1 from public.profiles where id = v_uid and banned_at is null for update;
  if not found then raise exception 'ACCOUNT_UNAVAILABLE'; end if;
  if (select count(*) from public.reactions where user_id = v_uid and created_at > now() - interval '10 seconds') >= (select reaction_limit_per_10s from public.app_settings where singleton) then raise exception 'REACTION_RATE_LIMIT'; end if;
  select id into v_reign_id from public.reigns where ended_at is null;
  insert into public.reactions(reign_id,user_id,reaction) values(v_reign_id,v_uid,v_key);
  return jsonb_build_object('ok', true);
end $$;
revoke all on function public.react_to_one(text) from public, anon;
grant execute on function public.react_to_one(text) to authenticated;


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
        if v_profile.revenge_tickets < 1 then
            v_result := jsonb_build_object('ok', false, 'code', 'COOLDOWN', 'message', 'Free steal recharges in ' || v_remaining || 's.', 'cooldown_seconds', v_remaining);
            update public.take_requests set status = 'rejected', result = v_result where id = p_request_id;
            return v_result;
        end if;
        update public.profiles set revenge_tickets = revenge_tickets - 1 where id = v_uid;
        insert into public.ticket_ledger(user_id, delta, reason, reference_id)
        values (v_uid, -1, 'cooldown_skip', p_request_id::text);
        v_spent_ticket := true;
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
        'started_at_ms', floor(extract(epoch from v_new.started_at) * 1000)::bigint
    );
    update public.take_requests set status = 'committed', result = v_result where id = p_request_id;
    return v_result;
end;
$$;


alter function public.get_one_state() rename to get_one_state_before_progressive;
revoke all on function public.get_one_state_before_progressive() from public, anon, authenticated;
create function public.get_one_state() returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare v_state jsonb := public.get_one_state_before_progressive(); v_seconds integer := 0;
begin
 select case when p.last_take_at > now() - make_interval(secs => s.idle_reset_seconds)
    then greatest(0,ceil(extract(epoch from(p.cooldown_until-now())))::integer) else 0 end
 into v_seconds from public.profiles p cross join public.app_settings s where p.id=auth.uid() and s.singleton;
 return v_state || jsonb_build_object('cooldown_remaining_seconds',coalesce(v_seconds,0));
end $$;
revoke all on function public.get_one_state() from public;
grant execute on function public.get_one_state() to anon,authenticated;
-- Stop issuance, but keep old code recovery available for existing owners during migration.
revoke execute on function public.create_recovery_code() from public, anon, authenticated;
