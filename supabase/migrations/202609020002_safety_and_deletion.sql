create table public.blocked_profiles (
    blocker_id uuid not null references public.profiles(id) on delete cascade,
    blocked_id uuid not null references public.profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (blocker_id, blocked_id),
    check (blocker_id <> blocked_id)
);

alter table public.blocked_profiles enable row level security;
revoke all on public.blocked_profiles from public, anon, authenticated;

insert into public.profiles(id, handle, city, country_code, initials, verified, revenge_tickets)
values ('00000000-0000-0000-0000-000000000004', '@DELETED', 'REMOVED', 'XX', '--', false, 0)
on conflict do nothing;

insert into public.messages(id, author_id, text, status)
values (
    '00000000-0000-0000-0000-000000000005',
    '00000000-0000-0000-0000-000000000004',
    'ACCOUNT DELETED.',
    'approved'
)
on conflict do nothing;

create or replace function public.block_current_owner()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_owner_id uuid;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    perform public.ensure_profile(null);
    select owner_id into v_owner_id from public.reigns where ended_at is null;
    if v_owner_id is null then raise exception 'NO_CURRENT_OWNER'; end if;
    if v_owner_id = v_uid then raise exception 'CANNOT_BLOCK_YOURSELF'; end if;
    if v_owner_id in (
        '00000000-0000-0000-0000-000000000001'::uuid,
        '00000000-0000-0000-0000-000000000004'::uuid
    ) then
        raise exception 'CANNOT_BLOCK_ONE';
    end if;

    insert into public.blocked_profiles(blocker_id, blocked_id)
    values (v_uid, v_owner_id)
    on conflict do nothing;

    return jsonb_build_object('ok', true, 'blocked_id', v_owner_id);
end;
$$;

create or replace function public.unblock_all()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_count integer;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    delete from public.blocked_profiles where blocker_id = v_uid;
    get diagnostics v_count = row_count;
    return jsonb_build_object('ok', true, 'unblocked', v_count);
end;
$$;

alter function public.get_one_state() rename to get_one_state_unfiltered;

create function public.get_one_state()
returns jsonb
language plpgsql
security definer
set search_path = ''
stable
as $$
declare
    v_uid uuid := auth.uid();
    v_state jsonb := public.get_one_state_unfiltered();
    v_owner_id uuid;
    v_is_blocked boolean := false;
    v_filtered jsonb;
begin
    if v_uid is null then
        return v_state || jsonb_build_object('current_content_blocked', false, 'blocked_count', 0);
    end if;

    v_owner_id := nullif(v_state #>> '{reign,owner,id}', '')::uuid;
    select exists(
        select 1 from public.blocked_profiles
        where blocker_id = v_uid and blocked_id = v_owner_id
    ) into v_is_blocked;

    v_state := v_state || jsonb_build_object(
        'current_content_blocked', v_is_blocked,
        'blocked_count', (select count(*) from public.blocked_profiles where blocker_id = v_uid)
    );

    if v_is_blocked then
        v_state := jsonb_set(
            v_state,
            '{reign,owner}',
            jsonb_build_object(
                'id', v_owner_id,
                'handle', '@BLOCKED',
                'city', 'HIDDEN',
                'country_code', 'XX',
                'verified', false,
                'initials', '--'
            )
        );
        v_state := jsonb_set(
            v_state,
            '{reign,message}',
            jsonb_build_object(
                'id', v_state #>> '{reign,message,id}',
                'text', 'CONTENT BLOCKED.',
                'times_deployed', 0,
                'echoed_from_message_id', null
            )
        );
    end if;

    select coalesce(jsonb_agg(item), '[]'::jsonb) into v_filtered
    from jsonb_array_elements(coalesce(v_state->'hall', '[]'::jsonb)) item
    where not exists (
        select 1 from public.blocked_profiles b
        where b.blocker_id = v_uid and b.blocked_id = nullif(item->>'owner_id', '')::uuid
    );
    v_state := jsonb_set(v_state, '{hall}', v_filtered);

    select coalesce(jsonb_agg(item), '[]'::jsonb) into v_filtered
    from jsonb_array_elements(coalesce(v_state->'activity', '[]'::jsonb)) item
    where not exists (
        select 1
        from public.blocked_profiles b
        join public.profiles p on p.id = b.blocked_id
        where b.blocker_id = v_uid and p.handle = item->>'owner'
    );
    v_state := jsonb_set(v_state, '{activity}', v_filtered);

    select coalesce(jsonb_agg(item), '[]'::jsonb) into v_filtered
    from jsonb_array_elements(coalesce(v_state->'reactions', '[]'::jsonb)) item
    where not exists (
        select 1
        from public.blocked_profiles b
        join public.profiles p on p.id = b.blocked_id
        where b.blocker_id = v_uid and p.handle = item->>'handle'
    );
    v_state := jsonb_set(v_state, '{reactions}', v_filtered);

    return v_state;
end;
$$;

create or replace function public.delete_my_account()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_current public.reigns%rowtype;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    if v_uid in (
        '00000000-0000-0000-0000-000000000001'::uuid,
        '00000000-0000-0000-0000-000000000004'::uuid
    ) then raise exception 'SYSTEM_ACCOUNT'; end if;

    perform pg_advisory_xact_lock(771041);
    select * into v_current from public.reigns where ended_at is null for update;

    if v_current.owner_id = v_uid then
        update public.reigns set ended_at = now() where id = v_current.id;
        insert into public.reigns(owner_id, message_id, previous_reign_id, protected_until, palette)
        values (
            '00000000-0000-0000-0000-000000000001',
            '00000000-0000-0000-0000-000000000002',
            v_current.id,
            now(),
            'ACID'
        );
    end if;

    delete from public.reports
    where reporter_id = v_uid
       or reign_id in (select id from public.reigns where owner_id = v_uid);

    update public.messages
    set echoed_from_message_id = null
    where echoed_from_message_id in (select id from public.messages where author_id = v_uid);

    update public.reigns
    set owner_id = '00000000-0000-0000-0000-000000000004',
        message_id = '00000000-0000-0000-0000-000000000005'
    where owner_id = v_uid
       or message_id in (select id from public.messages where author_id = v_uid);

    delete from public.reactions where user_id = v_uid;
    delete from public.blocked_profiles where blocker_id = v_uid or blocked_id = v_uid;
    delete from public.ticket_ledger where user_id = v_uid;
    delete from public.take_requests where user_id = v_uid;
    delete from public.purchase_events where user_id = v_uid;
    delete from public.view_events where viewer_id = v_uid;
    delete from public.heartbeats where viewer_id = v_uid;
    delete from public.messages where author_id = v_uid;
    delete from public.profiles where id = v_uid;
    delete from auth.users where id = v_uid;

    return jsonb_build_object('ok', true);
end;
$$;

revoke execute on function public.get_one_state_unfiltered() from public, anon, authenticated;
revoke execute on function public.block_current_owner() from public, anon;
revoke execute on function public.unblock_all() from public, anon;
revoke execute on function public.delete_my_account() from public, anon;
grant execute on function public.get_one_state() to anon, authenticated;
grant execute on function public.block_current_owner() to authenticated;
grant execute on function public.unblock_all() to authenticated;
grant execute on function public.delete_my_account() to authenticated;
