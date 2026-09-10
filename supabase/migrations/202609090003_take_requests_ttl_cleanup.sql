-- Housekeeping: take_requests rows left in a non-committed state by a
-- crashed or abandoned request were never cleaned up - the only deletion was
-- tied to full account deletion. expires_at already exists (added by
-- 202609090001, set to now() + 10 minutes on every insert); this adds the
-- actual cleanup. Kept in its own migration, separate from the take-balance
-- rework and the two correctness fixes, since pg_cron may need enabling via
-- the dashboard rather than SQL - a failure here shouldn't be able to roll
-- back anything user-facing.
begin;

create or replace function public.cleanup_expired_take_requests()
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    delete from public.take_requests
    where status <> 'committed'
      and expires_at is not null
      and expires_at < now();
end;
$$;
revoke all on function public.cleanup_expired_take_requests() from public, anon, authenticated;

commit;

-- Run once, separately, only if the pg_cron extension is available on this
-- project (Database -> Extensions in the dashboard, or try the line below -
-- if it errors with a permissions issue, enable pg_cron from the dashboard
-- first, then re-run just this block):
--
-- create extension if not exists pg_cron;
-- select cron.schedule('cleanup-expired-take-requests', '*/15 * * * *', $$select public.cleanup_expired_take_requests();$$);
