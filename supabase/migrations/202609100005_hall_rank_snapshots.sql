-- Storage layer for future rank-movement arrows. Not wired to the UI yet --
-- HallRow still renders the static muted dash on purpose (see the comment on
-- RankMovement() in OneApp.kt) until enough days of history exist. This has
-- a time dependency nothing else does: every day this table doesn't exist is
-- a day of history that can never be recovered, so it goes in now.
--
-- Snapshots the ALL-TIME leaderboard only (no per-day filter) because rank
-- movement will only ever be shown on the ALL TIME tab -- movement within a
-- single day is meaningless, and TODAY's ranking resets every day anyway.
create table if not exists public.hall_rank_snapshots (
    snapshot_date date not null,
    owner_id uuid not null references public.profiles(id),
    rank integer not null,
    reign_seconds integer not null,
    takeovers integer not null,
    verified_views integer not null,
    created_at timestamptz not null default now(),
    primary key (snapshot_date, owner_id)
);
alter table public.hall_rank_snapshots enable row level security;
revoke all on public.hall_rank_snapshots from public, anon, authenticated;
create index if not exists hall_rank_snapshots_owner_idx on public.hall_rank_snapshots (owner_id, snapshot_date desc);

create or replace function public.snapshot_hall_ranks()
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    with eligible as (
        select r.* from public.reigns r join public.profiles p on p.id = r.owner_id
        where p.banned_at is null
          and r.owner_id not in ('00000000-0000-0000-0000-000000000001'::uuid, '00000000-0000-0000-0000-000000000004'::uuid)
    ), stats as (
        select owner_id,
            count(*)::integer as takeovers,
            max(greatest(0, extract(epoch from (coalesce(ended_at, now()) - started_at))))::integer as reign_seconds,
            min(started_at) as first_reign
        from eligible
        group by owner_id
    ), views as (
        select e.owner_id, count(*)::integer as verified_views
        from eligible e join public.view_events ve on ve.reign_id = e.id
        group by e.owner_id
    ), ranked as (
        select row_number() over (order by s.reign_seconds desc, s.takeovers desc, s.first_reign, s.owner_id)::integer as rank,
            s.owner_id, s.reign_seconds, s.takeovers, coalesce(v.verified_views, 0) as verified_views
        from stats s left join views v on v.owner_id = s.owner_id
    )
    insert into public.hall_rank_snapshots (snapshot_date, owner_id, rank, reign_seconds, takeovers, verified_views)
    select current_date, owner_id, rank, reign_seconds, takeovers, verified_views from ranked
    on conflict (snapshot_date, owner_id) do update set
        rank = excluded.rank,
        reign_seconds = excluded.reign_seconds,
        takeovers = excluded.takeovers,
        verified_views = excluded.verified_views;
end;
$$;
revoke all on function public.snapshot_hall_ranks() from public, anon, authenticated;

-- One-time, optional, same caveat as cleanup_expired_take_requests in
-- 202609090003 -- I can't verify pg_cron is enabled on your project without
-- risking a failed command, so run this yourself in the SQL editor once:
--   create extension if not exists pg_cron;
--   select cron.schedule('hall-rank-daily-snapshot', '5 0 * * *', $$select public.snapshot_hall_ranks();$$);
-- Until that's scheduled, no history accumulates -- consider also running
-- `select public.snapshot_hall_ranks();` once manually right after this
-- migration so today counts as day one instead of waiting for tomorrow.
