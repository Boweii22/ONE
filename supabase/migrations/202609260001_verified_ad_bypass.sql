-- Closes the ad trust hole: bypass_take_refill('ad', ...) used to accept the
-- client's word that an ad had been watched, so any signed-in caller could hit
-- the RPC directly and skip refills up to the daily cap without an ad.
--
-- Now:
--   * bypass_take_refill only serves 'credits'. 'ad' from a signed-in client is refused.
--   * grant_verified_ad_bypass(user, request) does the ad grant and is executable by
--     the service role only. Its sole caller is the redeem-ad-reward Edge Function,
--     which first spends one RevenueCat-granted ad-reward currency unit (granted by
--     RevenueCat only after AdMob server-side verification) before calling it.

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
