-- ONE has one global message, so a report must hide that message for the
-- reporter without making the app unusable.  This migration also gives a small
-- team a safe server-side moderation queue and an emergency global hold.

alter table public.reports add column if not exists message_id uuid references public.messages(id);
alter table public.reports add column if not exists resolved_at timestamptz;
alter table public.reports add column if not exists resolution text check (resolution in ('removed', 'dismissed', 'suspended', 'banned'));
create unique index if not exists reports_one_per_reign_reporter
    on public.reports(reporter_id, reign_id);
create index if not exists reports_open_reign_idx on public.reports(reign_id, created_at desc) where status = 'open';

alter table public.profiles add column if not exists suspended_until timestamptz;
alter table public.profiles add column if not exists moderation_note text;

create or replace function public.enforce_take_account_status()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    if exists (select 1 from public.profiles where id = new.user_id and (banned_at is not null or suspended_until > now())) then
        raise exception 'ACCOUNT_SUSPENDED';
    end if;
    return new;
end;
$$;
drop trigger if exists take_requests_account_status on public.take_requests;
create trigger take_requests_account_status before insert on public.take_requests
for each row execute function public.enforce_take_account_status();

create table if not exists public.moderation_holds (
    reign_id uuid primary key references public.reigns(id) on delete cascade,
    reason text not null,
    created_at timestamptz not null default now(),
    resolved_at timestamptz,
    resolution text check (resolution in ('removed', 'dismissed', 'suspended', 'banned'))
);
alter table public.moderation_holds enable row level security;
revoke all on public.moderation_holds from public, anon, authenticated;

create or replace function public.report_one(p_reason text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_reign public.reigns%rowtype;
    v_reason text := upper(trim(coalesce(p_reason, '')));
    v_reporters integer := 0;
    v_global_hold boolean := false;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    if v_reason not in ('HATE OR HARASSMENT', 'THREAT OR VIOLENCE', 'PERSONAL INFORMATION', 'SCAM OR IMPERSONATION', 'OTHER') then
        raise exception 'INVALID_REPORT_REASON';
    end if;
    perform public.ensure_profile(null);
    if (select count(*) from public.reports where reporter_id = v_uid and created_at > now() - interval '1 hour') >= 8 then
        raise exception 'REPORT_RATE_LIMIT';
    end if;
    select * into v_reign from public.reigns where ended_at is null for update;
    if not found then raise exception 'NO_ACTIVE_REIGN'; end if;
    if v_reign.owner_id = v_uid then raise exception 'CANNOT_REPORT_YOURSELF'; end if;

    insert into public.reports(reporter_id, reign_id, message_id, reason)
    values (v_uid, v_reign.id, v_reign.message_id, v_reason)
    on conflict (reporter_id, reign_id) do nothing;

    select count(distinct reporter_id) into v_reporters
    from public.reports where reign_id = v_reign.id and status = 'open';
    v_global_hold := v_reason = 'THREAT OR VIOLENCE' or v_reporters >= 2;
    if v_global_hold then
        insert into public.moderation_holds(reign_id, reason)
        values (v_reign.id, case when v_reason = 'THREAT OR VIOLENCE' then 'THREAT OR VIOLENCE' else 'TWO UNIQUE REPORTS' end)
        on conflict (reign_id) do nothing;
    end if;
    return jsonb_build_object('ok', true, 'hidden_for_reporter', true, 'unique_reporters', v_reporters, 'globally_withheld', v_global_hold);
end;
$$;

-- Service-role-only function for a tiny moderation dashboard. It intentionally
-- is not granted to end users: use it from a protected staff console/function.
create or replace function public.resolve_moderation_report(p_reign_id uuid, p_action text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_action text := lower(trim(p_action));
    v_owner uuid;
begin
    if v_action not in ('remove', 'dismiss', 'suspend_24h', 'ban') then raise exception 'INVALID_MODERATION_ACTION'; end if;
    select owner_id into v_owner from public.reigns where id = p_reign_id for update;
    if v_owner is null then raise exception 'REIGN_NOT_FOUND'; end if;
    if v_action = 'dismiss' then
        update public.reports set status = 'dismissed', resolution = 'dismissed', resolved_at = now() where reign_id = p_reign_id and status = 'open';
        update public.moderation_holds set resolved_at = now(), resolution = 'dismissed' where reign_id = p_reign_id;
    else
        update public.reports set status = 'resolved', resolution = case when v_action = 'remove' then 'removed' when v_action = 'ban' then 'banned' else 'suspended' end, resolved_at = now() where reign_id = p_reign_id and status = 'open';
        update public.moderation_holds set resolved_at = now(), resolution = case when v_action = 'remove' then 'removed' when v_action = 'ban' then 'banned' else 'suspended' end where reign_id = p_reign_id;
        if v_action = 'suspend_24h' then update public.profiles set suspended_until = now() + interval '24 hours', moderation_note = 'moderation suspension' where id = v_owner; end if;
        if v_action = 'ban' then update public.profiles set banned_at = now(), moderation_note = 'moderation ban' where id = v_owner; end if;
    end if;
    return jsonb_build_object('ok', true, 'action', v_action);
end;
$$;

alter function public.get_one_state() rename to get_one_state_before_report_masking;
revoke all on function public.get_one_state_before_report_masking() from public, anon, authenticated;
create function public.get_one_state()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_state jsonb := public.get_one_state_before_report_masking();
    v_uid uuid := auth.uid();
    v_reign_id uuid := nullif(v_state #>> '{reign,id}', '')::uuid;
    v_message_id text := v_state #>> '{reign,message,id}';
    v_personal_mask boolean := false;
    v_global_hold boolean := false;
    v_killed boolean := false;
begin
    select exists(select 1 from public.moderation_holds where reign_id = v_reign_id and resolved_at is null) into v_global_hold;
    select coalesce(global_kill_switch, false) into v_killed from public.app_settings where singleton;
    if v_uid is not null then
        select exists(select 1 from public.reports where reporter_id = v_uid and reign_id = v_reign_id) into v_personal_mask;
    end if;
    if v_killed or v_global_hold then
        v_state := jsonb_set(v_state, '{reign,message}', jsonb_build_object('id', v_message_id, 'text', case when v_killed then 'ONE IS PAUSED.' else 'MESSAGE UNDER REVIEW.' end, 'times_deployed', 0));
        v_state := jsonb_set(v_state, '{reign,owner}', jsonb_build_object('id', 'system', 'handle', '@ONE', 'city', '', 'country_code', '', 'verified', true, 'initials', '1'));
    elsif v_personal_mask and not coalesce((v_state->>'current_content_blocked')::boolean, false) then
        v_state := jsonb_set(v_state, '{reign,message}', jsonb_build_object('id', v_message_id, 'text', 'CONTENT HIDDEN.', 'times_deployed', 0));
    end if;
    return v_state || jsonb_build_object('current_content_blocked', coalesce((v_state->>'current_content_blocked')::boolean, false) or v_personal_mask, 'moderation_hold', v_global_hold, 'global_kill_switch', v_killed);
end;
$$;

revoke all on function public.report_one(text) from public, anon;
revoke all on function public.resolve_moderation_report(uuid, text) from public, anon, authenticated;
revoke all on function public.get_one_state() from public;
grant execute on function public.report_one(text) to authenticated;
grant execute on function public.get_one_state() to anon, authenticated;
