-- Moderation console: a staff-only web queue so an upheld report can be
-- resolved in minutes from a phone, not by hand-writing SQL in the Supabase
-- dashboard. Every admin RPC checks profiles.is_staff itself (not just a
-- hidden URL), and every action is written to moderation_audit_log.
--
-- Reporter-notification pushes need two one-time, non-git steps after this
-- migration runs, mirroring the dethroned-push setup in 202609080005:
--   1. Deploy supabase/functions/onesignal-report-resolved with env vars
--      MODERATION_HOOK_SECRET (any random string you choose),
--      ONESIGNAL_APP_ID and ONESIGNAL_REST_API_KEY.
--   2. In the SQL editor:
--      insert into public.app_secrets(key, value) values
--        ('moderation_hook_secret', '<same value as MODERATION_HOOK_SECRET>')
--      on conflict (key) do update set value = excluded.value;
--   Until step 2 is done, resolve_moderation_report() still works -- it just
--   silently skips the push (see notify_reporters_report_upheld's early return).

alter table public.profiles add column if not exists is_staff boolean not null default false;
alter table public.profiles add column if not exists ban_offense_count integer not null default 0;

-- One-time bootstrap: grants the developer's own account staff access, keyed
-- off the Google account email on this machine. No-op (0 rows) if this isn't
-- the email linked to your ONE account's Google Sign-In -- in that case, run
-- manually once instead:
--   update public.profiles set is_staff = true where id = '<your user id>';
update public.profiles set is_staff = true
where id = (select id from auth.users where email = 'tombribowei01@gmail.com');

create table if not exists public.moderation_audit_log (
    id uuid primary key default gen_random_uuid(),
    actor_id uuid not null references public.profiles(id),
    action text not null check (action in ('dismiss', 'remove', 'ban', 'kill_switch_enabled', 'kill_switch_disabled')),
    reign_id uuid,
    target_user_id uuid,
    message_id uuid,
    detail jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);
alter table public.moderation_audit_log enable row level security;
revoke all on public.moderation_audit_log from public, anon, authenticated;

-- Reuses the app_secrets/net.http_post pattern already established for the
-- dethroned push (202609080005) instead of inventing a new transport.
create or replace function public.notify_reporters_report_upheld(p_reign_id uuid, p_message_text text, p_reason text, p_reporter_ids uuid[])
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_secret text;
begin
    if p_reporter_ids is null or array_length(p_reporter_ids, 1) is null then return; end if;
    select value into v_secret from public.app_secrets where key = 'moderation_hook_secret';
    if v_secret is null or v_secret = '' then return; end if;
    perform net.http_post(
        url := 'https://ajkdzohnntrbkeqjskel.supabase.co/functions/v1/onesignal-report-resolved',
        headers := jsonb_build_object('Content-Type', 'application/json', 'Authorization', 'Bearer ' || v_secret),
        body := jsonb_build_object(
            'reign_id', p_reign_id,
            'message_text', p_message_text,
            'reason', p_reason,
            'reporter_ids', to_jsonb(p_reporter_ids)
        )
    );
end;
$$;
revoke all on function public.notify_reporters_report_upheld(uuid, text, text, uuid[]) from public, anon, authenticated;

create or replace function public.moderation_whoami()
returns jsonb
language plpgsql
security definer
set search_path = ''
stable
as $$
declare
    v_uid uuid := auth.uid();
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    return jsonb_build_object(
        'user_id', v_uid,
        'is_staff', coalesce((select is_staff from public.profiles where id = v_uid), false),
        'handle', (select handle from public.profiles where id = v_uid)
    );
end;
$$;
revoke all on function public.moderation_whoami() from public, anon;
grant execute on function public.moderation_whoami() to authenticated;

-- The queue: one row per reign that still has an open report, newest first.
create or replace function public.moderation_queue()
returns jsonb
language plpgsql
security definer
set search_path = ''
stable
as $$
declare
    v_result jsonb;
begin
    if not coalesce((select is_staff from public.profiles where id = auth.uid()), false) then
        raise exception 'STAFF_ONLY';
    end if;
    select coalesce(jsonb_agg(item order by (item->>'reign_started_at_ms')::bigint desc), '[]'::jsonb)
    into v_result
    from (
        select jsonb_build_object(
            'reign_id', r.id,
            'sequence', r.sequence,
            'reign_started_at_ms', floor(extract(epoch from r.started_at) * 1000)::bigint,
            'owner_id', r.owner_id,
            'owner_handle', o.handle,
            'message_id', r.message_id,
            'message_text', m.text,
            'reasons', (select coalesce(jsonb_agg(distinct rep.reason), '[]'::jsonb) from public.reports rep where rep.reign_id = r.id and rep.status = 'open'),
            'unique_reporters', (select count(distinct rep.reporter_id) from public.reports rep where rep.reign_id = r.id and rep.status = 'open'),
            'reporter_handles', (select coalesce(jsonb_agg(distinct rp.handle), '[]'::jsonb) from public.reports rep join public.profiles rp on rp.id = rep.reporter_id where rep.reign_id = r.id and rep.status = 'open'),
            'auto_pulled', exists(select 1 from public.moderation_holds h where h.reign_id = r.id),
            'hold_active', exists(select 1 from public.moderation_holds h where h.reign_id = r.id and h.resolved_at is null)
        ) as item
        from public.reigns r
        join public.profiles o on o.id = r.owner_id
        join public.messages m on m.id = r.message_id
        where exists (select 1 from public.reports rep where rep.reign_id = r.id and rep.status = 'open')
    ) sub;
    return v_result;
end;
$$;
revoke all on function public.moderation_queue() from public, anon;
grant execute on function public.moderation_queue() to authenticated;

create or replace function public.moderation_set_kill_switch(p_enabled boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
begin
    if not coalesce((select is_staff from public.profiles where id = v_uid), false) then
        raise exception 'STAFF_ONLY';
    end if;
    update public.app_settings set global_kill_switch = p_enabled, updated_at = now() where singleton;
    insert into public.moderation_audit_log(actor_id, action, detail)
    values (v_uid, case when p_enabled then 'kill_switch_enabled' else 'kill_switch_disabled' end, jsonb_build_object('enabled', p_enabled));
    return jsonb_build_object('ok', true, 'global_kill_switch', p_enabled);
end;
$$;
revoke all on function public.moderation_set_kill_switch(boolean) from public, anon;
grant execute on function public.moderation_set_kill_switch(boolean) to authenticated;

-- Dismiss / Remove / Ban, staff-gated, audited. Replaces the old
-- remove/dismiss/suspend_24h/ban action set: Ban now decides suspend-vs-
-- permanent itself from profiles.ban_offense_count, so the console only ever
-- shows three buttons.
alter function public.resolve_moderation_report(uuid, text) rename to resolve_moderation_report_before_console;
revoke all on function public.resolve_moderation_report_before_console(uuid, text) from public, anon, authenticated;

create function public.resolve_moderation_report(p_reign_id uuid, p_action text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_action text := lower(trim(p_action));
    v_reign public.reigns%rowtype;
    v_message_id uuid;
    v_message_text text;
    v_report_reason text;
    v_reporter_ids uuid[];
    v_is_current boolean;
    v_ban_stage text;
    v_offense_count integer;
begin
    if not coalesce((select is_staff from public.profiles where id = v_uid), false) then
        raise exception 'STAFF_ONLY';
    end if;
    if v_action not in ('dismiss', 'remove', 'ban') then raise exception 'INVALID_MODERATION_ACTION'; end if;

    select * into v_reign from public.reigns where id = p_reign_id for update;
    if not found then raise exception 'REIGN_NOT_FOUND'; end if;
    v_message_id := v_reign.message_id;

    if not exists (select 1 from public.reports where reign_id = p_reign_id and status = 'open') then
        raise exception 'NO_OPEN_REPORTS';
    end if;

    select array_agg(distinct reporter_id) into v_reporter_ids from public.reports where reign_id = p_reign_id and status = 'open';
    select reason into v_report_reason from public.reports where reign_id = p_reign_id and status = 'open' order by created_at asc limit 1;

    if v_action = 'dismiss' then
        update public.reports set status = 'dismissed', resolution = 'dismissed', resolved_at = now() where reign_id = p_reign_id and status = 'open';
        update public.moderation_holds set resolved_at = now(), resolution = 'dismissed' where reign_id = p_reign_id and resolved_at is null;

        insert into public.moderation_audit_log(actor_id, action, reign_id, target_user_id, message_id, detail)
        values (v_uid, 'dismiss', p_reign_id, v_reign.owner_id, v_message_id, jsonb_build_object('unique_reporters', coalesce(array_length(v_reporter_ids, 1), 0)));

        return jsonb_build_object('ok', true, 'action', v_action);
    end if;

    -- remove / ban: the message can never be redeployed, and if this reign is
    -- still the live one, end it and hand the screen to the neutral system
    -- reign -- the same pattern delete_my_account() uses so there is never a
    -- moment with zero active reigns.
    perform pg_advisory_xact_lock(771041);
    select (ended_at is null) into v_is_current from public.reigns where id = p_reign_id;

    update public.reports set status = 'resolved', resolution = case when v_action = 'ban' then 'banned' else 'removed' end, resolved_at = now() where reign_id = p_reign_id and status = 'open';
    update public.moderation_holds set resolved_at = now(), resolution = case when v_action = 'ban' then 'banned' else 'removed' end where reign_id = p_reign_id and resolved_at is null;

    select text into v_message_text from public.messages where id = v_message_id;
    update public.messages set status = 'revoked' where id = v_message_id;
    insert into public.upheld_report_messages(message_id, message_text, report_reason, moderation_action)
    values (v_message_id, coalesce(v_message_text, ''), coalesce(v_report_reason, 'UNKNOWN'), v_action);

    if v_is_current then
        update public.reigns set ended_at = now() where id = p_reign_id;
        insert into public.reigns(owner_id, message_id, previous_reign_id, protected_until, palette)
        values (
            '00000000-0000-0000-0000-000000000001',
            '00000000-0000-0000-0000-000000000002',
            p_reign_id,
            now(),
            'ACID'
        );
    end if;

    if v_action = 'ban' and v_reign.owner_id <> '00000000-0000-0000-0000-000000000001' then
        select ban_offense_count into v_offense_count from public.profiles where id = v_reign.owner_id for update;
        if coalesce(v_offense_count, 0) >= 1 then
            update public.profiles set banned_at = now(), ban_offense_count = ban_offense_count + 1, moderation_note = 'moderation ban (permanent)' where id = v_reign.owner_id;
            v_ban_stage := 'permanent';
        else
            update public.profiles set suspended_until = now() + interval '24 hours', ban_offense_count = ban_offense_count + 1, moderation_note = 'moderation ban (24h)' where id = v_reign.owner_id;
            v_ban_stage := '24h';
        end if;
    end if;

    insert into public.moderation_audit_log(actor_id, action, reign_id, target_user_id, message_id, detail)
    values (v_uid, v_action, p_reign_id, v_reign.owner_id, v_message_id, jsonb_build_object('unique_reporters', coalesce(array_length(v_reporter_ids, 1), 0), 'reign_ended', v_is_current, 'ban_stage', v_ban_stage));

    perform public.notify_reporters_report_upheld(p_reign_id, v_message_text, coalesce(v_report_reason, 'UNKNOWN'), v_reporter_ids);

    return jsonb_build_object('ok', true, 'action', v_action, 'ban_stage', v_ban_stage, 'reign_ended', v_is_current);
end;
$$;
revoke all on function public.resolve_moderation_report(uuid, text) from public, anon, authenticated;
grant execute on function public.resolve_moderation_report(uuid, text) to authenticated;
