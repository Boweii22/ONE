-- Three honest counters for the rewarded-ad placement, no analytics stack:
--   offers_shown          distinct refill waits during which "WATCH AN AD" was on screen
--   ads_completed         verified ad redemptions (take_bypass_requests, method 'ad',
--                         which can only be written after RevenueCat-verified reward spend)
--   takeovers_within_5min completed ads followed by that user taking the screen inside 5 minutes
-- Read them in the SQL Editor: select * from public.ad_funnel_summary;

create table if not exists public.ad_offer_events (
    user_id uuid not null references public.profiles(id) on delete cascade,
    refill_at timestamptz not null,
    created_at timestamptz not null default now(),
    primary key (user_id, refill_at)
);
alter table public.ad_offer_events enable row level security;
revoke all on public.ad_offer_events from public, anon, authenticated;

-- Called by the app when the ad option appears. One row per refill wait, so
-- reopening the sheet during the same wait doesn't inflate the count.
create or replace function public.log_ad_offer_shown()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_profile public.profiles%rowtype;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    select * into v_profile from public.profiles where id = v_uid;
    if not found then return; end if;
    if v_profile.take_balance > 0 or v_profile.take_refill_at is null or v_profile.take_refill_at <= now() then
        return;
    end if;
    insert into public.ad_offer_events(user_id, refill_at)
    values (v_uid, v_profile.take_refill_at)
    on conflict do nothing;
end;
$$;
revoke all on function public.log_ad_offer_shown() from public, anon;
grant execute on function public.log_ad_offer_shown() to authenticated;

create or replace view public.ad_funnel_summary as
select
    (select count(*) from public.ad_offer_events) as offers_shown,
    (select count(*) from public.take_bypass_requests where method = 'ad') as ads_completed,
    (select count(*)
       from public.take_bypass_requests b
      where b.method = 'ad'
        and exists (
            select 1 from public.reigns r
             where r.owner_id = b.user_id
               and r.started_at >= b.created_at
               and r.started_at <= b.created_at + interval '5 minutes'
        )) as takeovers_within_5min;
revoke all on public.ad_funnel_summary from public, anon, authenticated;
