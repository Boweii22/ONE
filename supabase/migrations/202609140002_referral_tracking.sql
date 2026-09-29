-- Lightweight referral tracking: a small named set of people (e.g. 5 friends)
-- each get a unique link like oneis.live/?ref=alex. When someone signs up
-- for closed testing through that link, it's attributed to that code. A
-- staff-only leaderboard shows who's actually bringing people in.
--
-- Scoped to website signups, not app installs - tracking real Play Store
-- installs per referrer would need Google's separate Install Referrer API
-- wired into the Android app, which is a bigger, separate piece of work.
-- Signups are the real bottleneck during closed testing anyway.

create table if not exists public.referral_codes (
    code text primary key check (code ~ '^[a-z0-9-]{2,32}$'),
    label text not null check (char_length(label) between 1 and 80),
    created_at timestamptz not null default now()
);
alter table public.referral_codes enable row level security;
revoke all on public.referral_codes from public, anon, authenticated;

alter table public.tester_interest add column if not exists referral_code text references public.referral_codes(code);

-- Widen the existing signup check constraint to allow (but not require) a
-- referral code, keeping every other rule exactly as it was.
alter table public.tester_interest drop constraint if exists tester_interest_referral_length;
alter table public.tester_interest add constraint tester_interest_referral_length
    check (char_length(coalesce(referral_code, '')) <= 32);

-- Staff-only: create a new named referral code, e.g. create_referral_code('alex', 'Alex').
create or replace function public.create_referral_code(p_code text, p_label text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_code text := lower(trim(p_code));
begin
    if not coalesce((select is_staff from public.profiles where id = auth.uid()), false) then
        raise exception 'STAFF_ONLY';
    end if;
    insert into public.referral_codes(code, label) values (v_code, trim(p_label));
    return jsonb_build_object('ok', true, 'code', v_code);
end;
$$;
revoke all on function public.create_referral_code(text, text) from public, anon, authenticated;
grant execute on function public.create_referral_code(text, text) to authenticated;

-- Staff-only: every code with its signup count, most first. Codes with zero
-- signups still show up, so a freshly created link reads as "0", not missing.
create or replace function public.referral_leaderboard()
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
    select coalesce(jsonb_agg(item order by (item->>'signups')::integer desc, item->>'label' asc), '[]'::jsonb)
    into v_result
    from (
        select jsonb_build_object(
            'code', c.code,
            'label', c.label,
            'signups', (select count(*) from public.tester_interest t where t.referral_code = c.code),
            'created_at_ms', floor(extract(epoch from c.created_at) * 1000)::bigint
        ) as item
        from public.referral_codes c
    ) sub;
    return v_result;
end;
$$;
revoke all on function public.referral_leaderboard() from public, anon;
grant execute on function public.referral_leaderboard() to authenticated;
