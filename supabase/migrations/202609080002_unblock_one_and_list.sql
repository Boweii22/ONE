-- Blocking previously only supported clearing every block at once. Someone who
-- blocked several people and wants to undo just one had no way to do that
-- short of re-blocking the others afterward. Add a per-person unblock, and
-- return the actual list of blocked accounts so the client can show it.
create or replace function public.unblock_one(p_blocked_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_deleted integer;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    delete from public.blocked_profiles where blocker_id = v_uid and blocked_id = p_blocked_id;
    get diagnostics v_deleted = row_count;
    return jsonb_build_object('ok', true, 'unblocked', v_deleted > 0);
end;
$$;
revoke all on function public.unblock_one(uuid) from public, anon;
grant execute on function public.unblock_one(uuid) to authenticated;

alter function public.get_one_state() rename to get_one_state_before_blocklist;
revoke all on function public.get_one_state_before_blocklist() from public, anon, authenticated;
create function public.get_one_state()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_state jsonb := public.get_one_state_before_blocklist();
    v_blocked jsonb := '[]'::jsonb;
begin
    if v_uid is not null then
        select coalesce(jsonb_agg(jsonb_build_object('id', p.id, 'handle', p.handle) order by b.created_at desc), '[]'::jsonb)
        into v_blocked
        from public.blocked_profiles b
        join public.profiles p on p.id = b.blocked_id
        where b.blocker_id = v_uid;
    end if;
    return v_state || jsonb_build_object('blocked_accounts', v_blocked);
end;
$$;
revoke all on function public.get_one_state() from public;
grant execute on function public.get_one_state() to anon, authenticated;
