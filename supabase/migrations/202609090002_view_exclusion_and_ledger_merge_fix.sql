-- Fix 1: views must not accrue while a message is masked pending review.
--
-- heartbeat() has recorded a view for any active reign unconditionally since
-- the very first migration - it predates moderation_holds entirely and was
-- never updated when report-masking shipped. A reign under an unresolved
-- hold has its message masked for everyone (see get_one_state's v_global_hold
-- branch), so counting views for it means the Hall can be climbed on content
-- nobody can actually read. Verified views are the number this product is
-- implicitly selling as proof of an audience - it must only count people who
-- could actually see the message.
begin;

create or replace function public.heartbeat(p_source text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_reign public.reigns%rowtype;
    v_under_review boolean := false;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    if p_source not in ('app', 'web') then raise exception 'INVALID_SOURCE'; end if;
    perform public.ensure_profile(null);
    select * into v_reign from public.reigns where ended_at is null;
    insert into public.heartbeats(viewer_id, source, current_reign_id, seen_at)
    values (v_uid, p_source, v_reign.id, now())
    on conflict (viewer_id) do update set source = excluded.source, current_reign_id = excluded.current_reign_id, seen_at = excluded.seen_at;

    select exists(select 1 from public.moderation_holds where reign_id = v_reign.id and resolved_at is null) into v_under_review;
    if not v_under_review then
        insert into public.view_events(reign_id, viewer_id, source)
        values (v_reign.id, v_uid, p_source) on conflict do nothing;
    end if;

    return jsonb_build_object(
        'watchers', (select count(*) from public.heartbeats where seen_at > now() - interval '20 seconds'),
        'views', (select count(*) from public.view_events where reign_id = v_reign.id)
    );
end;
$$;
revoke all on function public.heartbeat(text) from public, anon;
grant execute on function public.heartbeat(text) to authenticated;

-- Fix 2: credit balance can diverge from the ledger after a handle recovery
-- merge. In normal play the invariant holds - every spend and grant writes a
-- matching ledger row in the same transaction. Recovery broke it: the
-- surviving balance was set via greatest(a, b) while the old account's
-- ledger rows were separately reassigned (not merged) onto the survivor, so
-- the ledger's sum stopped matching the balance it was supposed to explain.
--
-- The fix keeps the existing greatest() policy (a merge is meant to be
-- generous - the player keeps whichever balance was higher, never loses
-- tickets for having two accounts) but makes the ledger the source of truth
-- for what that balance actually is: after reassigning both histories onto
-- one user, write an explicit adjustment row for whatever gap remains
-- between the combined ledger and the intended balance, then assert the
-- invariant holds before returning.
create or replace function public.recover_identity(p_handle text, p_code text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_target_handle text := '@' || upper(regexp_replace(trim(p_handle), '[^A-Za-z0-9_]', '', 'g'));
    v_code text := upper(regexp_replace(p_code, '[^A-Fa-f0-9]', '', 'g'));
    v_old public.profiles%rowtype;
    v_current public.profiles%rowtype;
    v_intended_balance integer;
    v_ledger_sum integer;
    v_adjustment integer;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    if char_length(v_target_handle) not between 4 and 19 or char_length(v_code) <> 12 then
        raise exception 'RECOVERY_CODE_INVALID';
    end if;
    perform public.ensure_profile(null);
    perform pg_advisory_xact_lock(771042);
    select * into v_old from public.profiles where handle = v_target_handle for update;
    if not found then raise exception 'RECOVERY_NOT_AVAILABLE'; end if;
    if v_old.id = v_uid then return jsonb_build_object('ok', true, 'handle', v_old.handle); end if;
    if v_old.id in ('00000000-0000-0000-0000-000000000001'::uuid, '00000000-0000-0000-0000-000000000004'::uuid) then
        raise exception 'RECOVERY_NOT_AVAILABLE';
    end if;
    if not exists (
        select 1 from private.identity_recovery_codes
        where user_id = v_old.id and crypt(v_code, code_digest) = code_digest
    ) then raise exception 'RECOVERY_CODE_INVALID'; end if;

    select * into v_current from public.profiles where id = v_uid for update;

    -- Merge old activity into the authenticated replacement account. Existing
    -- activity on this new account remains, while tickets never get duplicated.
    update public.messages set author_id = v_uid where author_id = v_old.id;
    update public.reigns set owner_id = v_uid where owner_id = v_old.id;
    update public.ticket_ledger set user_id = v_uid where user_id = v_old.id;
    update public.purchase_events set user_id = v_uid where user_id = v_old.id;
    update public.take_requests set user_id = v_uid where user_id = v_old.id;
    update public.reactions set user_id = v_uid where user_id = v_old.id;
    update public.reports set reporter_id = v_uid where reporter_id = v_old.id;
    update public.app_feedback set user_id = v_uid where user_id = v_old.id;
    delete from public.view_events old_view
    where old_view.viewer_id = v_old.id
      and exists (select 1 from public.view_events current_view where current_view.viewer_id = v_uid and current_view.reign_id = old_view.reign_id);
    update public.view_events set viewer_id = v_uid where viewer_id = v_old.id;
    delete from public.heartbeats where viewer_id = v_uid;
    update public.heartbeats set viewer_id = v_uid where viewer_id = v_old.id;
    delete from public.blocked_profiles where blocker_id = v_old.id or blocked_id = v_old.id;
    delete from private.identity_recovery_codes where user_id = v_uid;
    update private.identity_recovery_codes set user_id = v_uid where user_id = v_old.id;

    -- Every ledger row from both accounts now lives under v_uid. The balance
    -- this merge intends to grant is the better of the two prior balances,
    -- not their sum - so write an adjustment row for the gap between that
    -- intended balance and what the now-combined ledger actually sums to.
    v_intended_balance := greatest(v_current.revenge_tickets, v_old.revenge_tickets);
    select coalesce(sum(delta), 0) into v_ledger_sum from public.ticket_ledger where user_id = v_uid;
    v_adjustment := v_intended_balance - v_ledger_sum;
    if v_adjustment <> 0 then
        insert into public.ticket_ledger(user_id, delta, reason, reference_id)
        values (v_uid, v_adjustment, 'identity_recovery_merge_adjustment', v_old.id::text);
    end if;

    -- Release the unique handle before attaching it to the authenticated account.
    update public.profiles
    set handle = '@RECOVER_' || left(replace(v_old.id::text, '-', ''), 6), initials = 'RC'
    where id = v_old.id;
    update public.profiles
    set handle = v_old.handle,
        initials = v_old.initials,
        city = v_old.city,
        country_code = v_old.country_code,
        verified = v_old.verified,
        revenge_tickets = v_intended_balance,
        last_take_at = coalesce(v_old.last_take_at, v_current.last_take_at)
    where id = v_uid;
    delete from public.profiles where id = v_old.id;
    delete from auth.users where id = v_old.id;

    select coalesce(sum(delta), 0) into v_ledger_sum from public.ticket_ledger where user_id = v_uid;
    if v_ledger_sum <> v_intended_balance then
        raise exception 'LEDGER_INVARIANT_VIOLATED';
    end if;

    return jsonb_build_object('ok', true, 'handle', v_old.handle);
end;
$$;
revoke all on function public.recover_identity(text, text) from public, anon;
grant execute on function public.recover_identity(text, text) to authenticated;

commit;
