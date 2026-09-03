create table public.app_feedback (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles(id) on delete cascade,
    category text not null check (category in ('BUG', 'IDEA', 'GAMEPLAY', 'OTHER')),
    body text not null check (char_length(body) between 3 and 1000),
    status text not null default 'new' check (status in ('new', 'reviewing', 'resolved')),
    created_at timestamptz not null default now()
);

create index app_feedback_created_at_idx on public.app_feedback (created_at desc);
create index app_feedback_status_idx on public.app_feedback (status, created_at desc);

alter table public.app_feedback enable row level security;
revoke all on public.app_feedback from public, anon, authenticated;

create or replace function public.submit_feedback(p_category text, p_text text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_category text := upper(trim(p_category));
    v_text text := trim(p_text);
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    perform public.ensure_profile(null);
    if v_category not in ('BUG', 'IDEA', 'GAMEPLAY', 'OTHER') then
        raise exception 'INVALID_FEEDBACK_CATEGORY';
    end if;
    if char_length(v_text) not between 3 and 1000 then
        raise exception 'FEEDBACK_MUST_BE_3_TO_1000_CHARACTERS';
    end if;
    if (select count(*) from public.app_feedback where user_id = v_uid and created_at > now() - interval '1 hour') >= 8 then
        raise exception 'FEEDBACK_RATE_LIMIT';
    end if;

    insert into public.app_feedback(user_id, category, body)
    values (v_uid, v_category, v_text);

    return jsonb_build_object('ok', true);
end;
$$;

revoke execute on function public.submit_feedback(text, text) from public, anon;
grant execute on function public.submit_feedback(text, text) to authenticated;
