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
      and exists (select 1 from auth.users u where u.id = p.id and u.is_anonymous)
      and not exists (select 1 from auth.identities i where i.user_id = p.id)
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
