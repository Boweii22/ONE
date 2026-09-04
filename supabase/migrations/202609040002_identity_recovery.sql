-- Anonymous identities need an explicit recovery path. Codes are stored only as
-- bcrypt digests; the plaintext is returned once to the account holder.
create table if not exists private.identity_recovery_codes (
    user_id uuid primary key references public.profiles(id) on delete cascade,
    code_digest text not null,
    issued_at timestamptz not null default now()
);

create or replace function public.create_recovery_code()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_code text;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    perform public.ensure_profile(null);
    if exists (select 1 from private.identity_recovery_codes where user_id = v_uid) then
        raise exception 'RECOVERY_CODE_ALREADY_CREATED';
    end if;

    v_code := upper(encode(gen_random_bytes(6), 'hex'));
    insert into private.identity_recovery_codes(user_id, code_digest)
    values (v_uid, crypt(v_code, gen_salt('bf')));

    return jsonb_build_object(
        'recovery_code', 'ONE-' || substr(v_code, 1, 4) || '-' || substr(v_code, 5, 4) || '-' || substr(v_code, 9, 4)
    );
end;
$$;

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
        revenge_tickets = greatest(v_current.revenge_tickets, v_old.revenge_tickets),
        last_take_at = coalesce(v_old.last_take_at, v_current.last_take_at)
    where id = v_uid;
    delete from public.profiles where id = v_old.id;
    delete from auth.users where id = v_old.id;

    return jsonb_build_object('ok', true, 'handle', v_old.handle);
end;
$$;

-- A forgotten alias can only be reclaimed automatically when it was an unused
-- anonymous shell, has no recovery code, and has been inactive for two weeks.
-- Accounts with any real contribution must be recovered by their private code.
create or replace function public.reclaim_unclaimed_handle(p_handle text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_handle text := '@' || upper(regexp_replace(trim(p_handle), '[^A-Za-z0-9_]', '', 'g'));
    v_old_id uuid;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    perform public.ensure_profile(null);
    perform pg_advisory_xact_lock(771043);
    select p.id into v_old_id
    from public.profiles p
    left join public.heartbeats h on h.viewer_id = p.id
    where p.handle = v_handle
      and p.id <> v_uid
      and p.created_at < now() - interval '14 days'
      and coalesce(h.seen_at, p.created_at) < now() - interval '14 days'
      and not exists (select 1 from private.identity_recovery_codes c where c.user_id = p.id)
      and not exists (select 1 from public.reigns r where r.owner_id = p.id)
      and not exists (select 1 from public.ticket_ledger t where t.user_id = p.id)
      and not exists (select 1 from public.purchase_events e where e.user_id = p.id)
      and not exists (select 1 from public.reactions rx where rx.user_id = p.id)
      and not exists (select 1 from public.reports rp where rp.reporter_id = p.id)
      and not exists (select 1 from public.app_feedback af where af.user_id = p.id)
      and not exists (select 1 from public.messages m where m.author_id = p.id and m.text not in ('I TOOK ONE.', 'TOO SLOW.'));
    if v_old_id is null then raise exception 'RECOVERY_NOT_AVAILABLE'; end if;

    delete from public.messages where author_id = v_old_id;
    delete from public.view_events where viewer_id = v_old_id;
    delete from public.heartbeats where viewer_id = v_old_id;
    delete from public.profiles where id = v_old_id;
    delete from auth.users where id = v_old_id;
    update public.profiles set handle = v_handle, initials = left(replace(v_handle, '@', ''), 2) where id = v_uid;
    return jsonb_build_object('ok', true, 'handle', v_handle);
end;
$$;

revoke execute on function public.create_recovery_code() from public, anon;
revoke execute on function public.recover_identity(text, text) from public, anon;
revoke execute on function public.reclaim_unclaimed_handle(text) from public, anon;
grant execute on function public.create_recovery_code() to authenticated;
grant execute on function public.recover_identity(text, text) to authenticated;
grant execute on function public.reclaim_unclaimed_handle(text) to authenticated;
