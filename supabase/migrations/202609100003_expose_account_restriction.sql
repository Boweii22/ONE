-- Exposes the CALLER's own ban/suspension status (not the reign owner's) so
-- the client can explain why taking is blocked. Deliberately does not gate
-- viewing at all: a banned/suspended account can keep watching Live/Hall/
-- Words normally, exactly like anyone else -- only the take action itself
-- checks this, same as the server already enforces server-side.
alter function public.get_one_state() rename to get_one_state_before_account_restriction;
revoke all on function public.get_one_state_before_account_restriction() from public, anon, authenticated;

create function public.get_one_state()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_state jsonb := public.get_one_state_before_account_restriction();
    v_uid uuid := auth.uid();
    v_banned_at timestamptz;
    v_suspended_until timestamptz;
    v_note text;
    v_offense_count integer;
begin
    if v_uid is not null then
        select banned_at, suspended_until, moderation_note, ban_offense_count
        into v_banned_at, v_suspended_until, v_note, v_offense_count
        from public.profiles where id = v_uid;

        if v_banned_at is not null then
            v_state := v_state || jsonb_build_object(
                'account_restriction', 'banned',
                'account_restriction_reason', v_note,
                'account_offense_count', coalesce(v_offense_count, 0)
            );
        elsif v_suspended_until is not null and v_suspended_until > now() then
            v_state := v_state || jsonb_build_object(
                'account_restriction', 'suspended',
                'account_restriction_reason', v_note,
                'account_suspended_until_ms', floor(extract(epoch from v_suspended_until) * 1000)::bigint,
                'account_offense_count', coalesce(v_offense_count, 0)
            );
        end if;
    end if;
    return v_state;
end;
$$;
revoke all on function public.get_one_state() from public;
grant execute on function public.get_one_state() to anon, authenticated;
