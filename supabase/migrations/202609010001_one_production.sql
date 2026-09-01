create extension if not exists pgcrypto;
create schema if not exists private;

create table public.profiles (
    id uuid primary key,
    handle text not null unique,
    city text not null default 'EARTH',
    country_code text not null default 'XX',
    initials text not null default 'ON',
    verified boolean not null default false,
    revenge_tickets integer not null default 3 check (revenge_tickets >= 0),
    last_take_at timestamptz,
    banned_at timestamptz,
    created_at timestamptz not null default now()
);

create table public.messages (
    id uuid primary key default gen_random_uuid(),
    author_id uuid not null references public.profiles(id),
    text text not null check (char_length(text) between 1 and 90),
    status text not null default 'reviewing' check (status in ('approved', 'reviewing', 'rejected')),
    rejection_reason text,
    echoed_from_message_id uuid references public.messages(id),
    times_deployed integer not null default 0,
    created_at timestamptz not null default now()
);

create table public.reigns (
    id uuid primary key default gen_random_uuid(),
    sequence bigint generated always as identity unique,
    owner_id uuid not null references public.profiles(id),
    message_id uuid not null references public.messages(id),
    previous_reign_id uuid references public.reigns(id),
    started_at timestamptz not null default now(),
    protected_until timestamptz not null default now(),
    ended_at timestamptz,
    used_revenge_ticket boolean not null default false,
    palette text not null default 'ACID' check (palette in ('ACID', 'COBALT', 'ORANGE', 'MAGENTA', 'ICE'))
);

create unique index one_current_reign on public.reigns ((ended_at is null)) where ended_at is null;
create index reigns_started_at_idx on public.reigns (started_at desc);

create table public.take_requests (
    id uuid primary key,
    user_id uuid not null,
    expected_sequence bigint not null,
    status text not null check (status in ('started', 'committed', 'rejected')),
    result jsonb,
    created_at timestamptz not null default now()
);

create table public.ticket_ledger (
    id bigint generated always as identity primary key,
    user_id uuid not null references public.profiles(id),
    delta integer not null,
    reason text not null,
    reference_id text not null,
    created_at timestamptz not null default now(),
    unique (reason, reference_id)
);

create table public.purchase_events (
    event_id text primary key,
    user_id uuid not null,
    product_id text not null,
    tickets integer not null check (tickets > 0),
    raw_event jsonb not null,
    processed_at timestamptz not null default now()
);

create table public.view_events (
    reign_id uuid not null references public.reigns(id) on delete cascade,
    viewer_id uuid not null,
    source text not null check (source in ('app', 'web')),
    qualified_at timestamptz not null default now(),
    primary key (reign_id, viewer_id)
);

create table public.heartbeats (
    viewer_id uuid primary key,
    source text not null check (source in ('app', 'web')),
    current_reign_id uuid references public.reigns(id) on delete cascade,
    seen_at timestamptz not null default now()
);

create table public.reactions (
    id bigint generated always as identity primary key,
    reign_id uuid not null references public.reigns(id) on delete cascade,
    user_id uuid not null references public.profiles(id),
    reaction text not null check (reaction in ('THIEF', 'TOO SLOW', 'TAKE IT BACK', 'RESPECT', 'LOL')),
    created_at timestamptz not null default now()
);
create index reactions_reign_created_idx on public.reactions (reign_id, created_at desc);

create table public.reports (
    id uuid primary key default gen_random_uuid(),
    reporter_id uuid not null,
    reign_id uuid not null references public.reigns(id),
    reason text not null,
    status text not null default 'open' check (status in ('open', 'resolved', 'dismissed')),
    created_at timestamptz not null default now()
);

create table public.app_settings (
    singleton boolean primary key default true check (singleton),
    cooldown_seconds integer not null default 30 check (cooldown_seconds between 0 and 3600),
    protection_seconds integer not null default 3 check (protection_seconds between 0 and 60),
    global_kill_switch boolean not null default false,
    updated_at timestamptz not null default now()
);
insert into public.app_settings(singleton) values (true) on conflict do nothing;

alter table public.profiles enable row level security;
alter table public.messages enable row level security;
alter table public.reigns enable row level security;
alter table public.take_requests enable row level security;
alter table public.ticket_ledger enable row level security;
alter table public.purchase_events enable row level security;
alter table public.view_events enable row level security;
alter table public.heartbeats enable row level security;
alter table public.reactions enable row level security;
alter table public.reports enable row level security;
alter table public.app_settings enable row level security;

revoke all on all tables in schema public from anon, authenticated;
grant usage on schema public to anon, authenticated;

create or replace function private.safe_message(candidate text)
returns boolean
language sql
immutable
set search_path = ''
as $$
    select
        char_length(trim(candidate)) between 1 and 90
        and candidate !~* '(https?://|www\.|@[a-z0-9._%+-]+\.[a-z]{2,}|\+?[0-9][0-9 ()-]{7,})'
        and candidate !~* '(kill yourself|send money|crypto wallet|seed phrase|home address|kidnap|your organs)';
$$;

create or replace function public.ensure_profile(p_handle text default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_handle text;
    v_profile public.profiles%rowtype;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    v_handle := upper(coalesce(nullif(regexp_replace(p_handle, '[^A-Za-z0-9_]', '', 'g'), ''), 'PLAYER_' || substr(replace(v_uid::text, '-', ''), 1, 6)));
    v_handle := '@' || left(v_handle, 20);

    insert into public.profiles(id, handle, initials)
    values (v_uid, v_handle, left(replace(v_handle, '@', ''), 2))
    on conflict (id) do nothing;

    insert into public.messages(author_id, text, status)
    select v_uid, starter.text, 'approved'
    from (values ('I TOOK ONE.'), ('TOO SLOW.')) as starter(text)
    where not exists (select 1 from public.messages m where m.author_id = v_uid);

    select * into v_profile from public.profiles where id = v_uid;
    return jsonb_build_object('id', v_profile.id, 'handle', v_profile.handle, 'tickets', v_profile.revenge_tickets);
end;
$$;

create or replace function public.update_handle(p_handle text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_handle text;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    v_handle := upper(regexp_replace(trim(p_handle), '[^A-Za-z0-9_]', '', 'g'));
    if char_length(v_handle) not between 3 and 18 then raise exception 'HANDLE_MUST_BE_3_TO_18_CHARACTERS'; end if;
    update public.profiles
    set handle = '@' || v_handle, initials = left(v_handle, 2)
    where id = v_uid;
    return jsonb_build_object('handle', '@' || v_handle);
exception when unique_violation then
    raise exception 'HANDLE_ALREADY_TAKEN';
end;
$$;

create or replace function public.submit_message(p_text text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_text text := upper(trim(p_text));
    v_status text;
    v_message public.messages%rowtype;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    perform public.ensure_profile(null);
    if char_length(v_text) not between 1 and 90 then raise exception 'MESSAGE_MUST_BE_1_TO_90_CHARACTERS'; end if;
    if (select count(*) from public.messages where author_id = v_uid and created_at > now() - interval '1 day') >= 20 then
        raise exception 'DAILY_MESSAGE_LIMIT';
    end if;
    v_status := case when private.safe_message(v_text) then 'approved' else 'reviewing' end;
    insert into public.messages(author_id, text, status)
    values (v_uid, v_text, v_status)
    returning * into v_message;
    return jsonb_build_object(
        'id', v_message.id,
        'text', v_message.text,
        'status', v_message.status,
        'created_at_ms', floor(extract(epoch from v_message.created_at) * 1000)::bigint
    );
end;
$$;

create or replace function public.echo_current_message()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_current public.reigns%rowtype;
    v_source public.messages%rowtype;
    v_echo public.messages%rowtype;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    perform public.ensure_profile(null);
    select * into v_current from public.reigns where ended_at is null;
    select * into v_source from public.messages where id = v_current.message_id;
    insert into public.messages(author_id, text, status, echoed_from_message_id)
    values (v_uid, v_source.text, 'approved', v_source.id)
    returning * into v_echo;
    return jsonb_build_object('id', v_echo.id, 'text', v_echo.text, 'status', v_echo.status, 'echoed_from', v_source.author_id);
end;
$$;

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
    if v_profile.last_take_at is not null then
        v_remaining := greatest(0, ceil(extract(epoch from (v_profile.last_take_at + make_interval(secs => v_settings.cooldown_seconds) - now())))::integer);
    end if;
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
    update public.profiles set last_take_at = now() where id = v_uid;

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

create or replace function public.heartbeat(p_source text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_reign public.reigns%rowtype;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    if p_source not in ('app', 'web') then raise exception 'INVALID_SOURCE'; end if;
    perform public.ensure_profile(null);
    select * into v_reign from public.reigns where ended_at is null;
    insert into public.heartbeats(viewer_id, source, current_reign_id, seen_at)
    values (v_uid, p_source, v_reign.id, now())
    on conflict (viewer_id) do update set source = excluded.source, current_reign_id = excluded.current_reign_id, seen_at = excluded.seen_at;
    insert into public.view_events(reign_id, viewer_id, source)
    values (v_reign.id, v_uid, p_source) on conflict do nothing;
    return jsonb_build_object(
        'watchers', (select count(*) from public.heartbeats where seen_at > now() - interval '20 seconds'),
        'views', (select count(*) from public.view_events where reign_id = v_reign.id)
    );
end;
$$;

create or replace function public.react_to_one(p_reaction text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_reign_id uuid;
    v_normalized text := upper(trim(p_reaction));
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    if v_normalized not in ('THIEF', 'TOO SLOW', 'TAKE IT BACK', 'RESPECT', 'LOL') then raise exception 'INVALID_REACTION'; end if;
    perform public.ensure_profile(null);
    if (select count(*) from public.reactions where user_id = v_uid and created_at > now() - interval '10 seconds') >= 3 then
        raise exception 'REACTION_RATE_LIMIT';
    end if;
    select id into v_reign_id from public.reigns where ended_at is null;
    insert into public.reactions(reign_id, user_id, reaction) values (v_reign_id, v_uid, v_normalized);
    return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.report_one(p_reason text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_reign_id uuid;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    select id into v_reign_id from public.reigns where ended_at is null;
    insert into public.reports(reporter_id, reign_id, reason) values (v_uid, v_reign_id, left(trim(p_reason), 120));
    return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.get_one_state()
returns jsonb
language plpgsql
security definer
set search_path = ''
stable
as $$
declare
    v_uid uuid := auth.uid();
    v_reign public.reigns%rowtype;
    v_owner public.profiles%rowtype;
    v_message public.messages%rowtype;
    v_profile public.profiles%rowtype;
    v_settings public.app_settings%rowtype;
    v_cooldown integer := 0;
begin
    select * into v_reign from public.reigns where ended_at is null;
    select * into v_owner from public.profiles where id = v_reign.owner_id;
    select * into v_message from public.messages where id = v_reign.message_id;
    select * into v_settings from public.app_settings where singleton;
    if v_uid is not null then
        select * into v_profile from public.profiles where id = v_uid;
        if found and v_profile.last_take_at is not null then
            v_cooldown := greatest(0, ceil(extract(epoch from (v_profile.last_take_at + make_interval(secs => v_settings.cooldown_seconds) - now())))::integer);
        end if;
    end if;

    return jsonb_build_object(
        'server_time_ms', floor(extract(epoch from now()) * 1000)::bigint,
        'current_user_id', v_uid,
        'current_user', case when v_profile.id is null then null else jsonb_build_object(
            'id', v_profile.id,
            'handle', v_profile.handle,
            'city', v_profile.city,
            'country_code', v_profile.country_code,
            'verified', v_profile.verified,
            'initials', v_profile.initials
        ) end,
        'connected', true,
        'reign', jsonb_build_object(
            'id', v_reign.id,
            'sequence', v_reign.sequence,
            'started_at_ms', floor(extract(epoch from v_reign.started_at) * 1000)::bigint,
            'protected_until_ms', floor(extract(epoch from v_reign.protected_until) * 1000)::bigint,
            'palette', v_reign.palette,
            'used_revenge_ticket', v_reign.used_revenge_ticket,
            'owner', jsonb_build_object('id', v_owner.id, 'handle', v_owner.handle, 'city', v_owner.city, 'country_code', v_owner.country_code, 'verified', v_owner.verified, 'initials', v_owner.initials),
            'message', jsonb_build_object('id', v_message.id, 'text', v_message.text, 'times_deployed', v_message.times_deployed, 'echoed_from_message_id', v_message.echoed_from_message_id)
        ),
        'app_views', (select count(*) from public.view_events where reign_id = v_reign.id and source = 'app'),
        'web_views', (select count(*) from public.view_events where reign_id = v_reign.id and source = 'web'),
        'live_watchers', (select count(*) from public.heartbeats where seen_at > now() - interval '20 seconds'),
        'revenge_tickets', coalesce(v_profile.revenge_tickets, 0),
        'cooldown_remaining_seconds', v_cooldown,
        'takeovers_today', (select count(*) from public.reigns where started_at >= date_trunc('day', now() at time zone 'UTC')),
        'messages', coalesce((
            select jsonb_agg(jsonb_build_object('id', m.id, 'text', m.text, 'status', m.status, 'created_at_ms', floor(extract(epoch from m.created_at) * 1000)::bigint, 'times_deployed', m.times_deployed) order by m.created_at desc)
            from public.messages m where m.author_id = v_uid
        ), '[]'::jsonb),
        'activity', coalesce((
            select jsonb_agg(x.item order by x.started_at desc) from (
                select r.started_at, jsonb_build_object('sequence', r.sequence, 'owner', p.handle, 'message', m.text, 'started_at_ms', floor(extract(epoch from r.started_at) * 1000)::bigint, 'used_ticket', r.used_revenge_ticket) item
                from public.reigns r join public.profiles p on p.id = r.owner_id join public.messages m on m.id = r.message_id
                order by r.started_at desc limit 12
            ) x
        ), '[]'::jsonb),
        'reactions', coalesce((
            select jsonb_agg(x.item order by x.created_at desc) from (
                select rx.created_at, jsonb_build_object('reaction', rx.reaction, 'handle', p.handle, 'created_at_ms', floor(extract(epoch from rx.created_at) * 1000)::bigint) item
                from public.reactions rx join public.profiles p on p.id = rx.user_id
                where rx.reign_id = v_reign.id order by rx.created_at desc limit 12
            ) x
        ), '[]'::jsonb),
        'hall', coalesce((
            select jsonb_agg(x.item order by x.reign_seconds desc) from (
                select extract(epoch from (coalesce(r.ended_at, now()) - r.started_at))::integer reign_seconds,
                    jsonb_build_object('owner', p.handle, 'owner_id', p.id, 'initials', p.initials, 'city', p.city, 'country_code', p.country_code, 'message', m.text, 'reign_seconds', extract(epoch from (coalesce(r.ended_at, now()) - r.started_at))::integer, 'verified_views', (select count(*) from public.view_events ve where ve.reign_id = r.id)) item
                from public.reigns r join public.profiles p on p.id = r.owner_id join public.messages m on m.id = r.message_id
                where r.started_at >= date_trunc('day', now() at time zone 'UTC')
                order by reign_seconds desc limit 10
            ) x
        ), '[]'::jsonb)
    );
end;
$$;

create or replace function public.grant_revenge_tickets(
    p_event_id text,
    p_user_id uuid,
    p_product_id text,
    p_tickets integer,
    p_raw_event jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
    if p_tickets <= 0 or p_tickets > 10000 then raise exception 'INVALID_TICKET_GRANT'; end if;
    insert into public.purchase_events(event_id, user_id, product_id, tickets, raw_event)
    values (p_event_id, p_user_id, p_product_id, p_tickets, p_raw_event)
    on conflict (event_id) do nothing;
    if not found then return jsonb_build_object('ok', true, 'duplicate', true); end if;
    perform public.ensure_service_profile(p_user_id);
    update public.profiles set revenge_tickets = revenge_tickets + p_tickets where id = p_user_id;
    insert into public.ticket_ledger(user_id, delta, reason, reference_id)
    values (p_user_id, p_tickets, 'revenuecat_purchase', p_event_id);
    return jsonb_build_object('ok', true, 'granted', p_tickets);
end;
$$;

create or replace function public.ensure_service_profile(p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.profiles(id, handle, initials)
    values (p_user_id, '@PLAYER_' || upper(substr(replace(p_user_id::text, '-', ''), 1, 6)), 'PL')
    on conflict (id) do nothing;
end;
$$;

revoke execute on all functions in schema public from public;
revoke execute on all functions in schema public from anon, authenticated;
grant execute on function public.get_one_state() to anon, authenticated;
grant execute on function public.ensure_profile(text) to authenticated;
grant execute on function public.update_handle(text) to authenticated;
grant execute on function public.submit_message(text) to authenticated;
grant execute on function public.echo_current_message() to authenticated;
grant execute on function public.take_one(uuid, bigint, uuid) to authenticated;
grant execute on function public.heartbeat(text) to authenticated;
grant execute on function public.react_to_one(text) to authenticated;
grant execute on function public.report_one(text) to authenticated;
revoke execute on function public.grant_revenge_tickets(text, uuid, text, integer, jsonb) from public, anon, authenticated;
revoke execute on function public.ensure_service_profile(uuid) from public, anon, authenticated;
grant execute on function public.grant_revenge_tickets(text, uuid, text, integer, jsonb) to service_role;

insert into public.profiles(id, handle, city, country_code, initials, verified, revenge_tickets)
values ('00000000-0000-0000-0000-000000000001', '@ONE', 'THE INTERNET', 'XX', '1', true, 0)
on conflict do nothing;

insert into public.messages(id, author_id, text, status)
values ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'TAKE THIS SCREEN.', 'approved')
on conflict do nothing;

insert into public.reigns(id, owner_id, message_id, protected_until, palette)
select '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', now(), 'ACID'
where not exists (select 1 from public.reigns where ended_at is null);

do $$
begin
    if not exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
        create publication supabase_realtime;
    end if;
    alter publication supabase_realtime add table public.reigns;
    alter publication supabase_realtime add table public.reactions;
exception when duplicate_object then null;
end $$;
