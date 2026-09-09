begin;

-- Enabled by default on Supabase projects; safe no-op if it already is.
create extension if not exists pg_net;

-- Fires the dethroned push the instant a reign ends, without depending on the
-- dashboard's Database Webhooks UI (which isn't available in every project's
-- Database section layout). The shared secret is a Postgres-level setting,
-- not a literal in this file, so it never lands in git:
--   alter database postgres set app.settings.takeover_hook_secret = '<same value as the onesignal-takeover function''s TAKEOVER_HOOK_SECRET>';
-- Run that once in the SQL Editor after this migration.
create or replace function public.notify_takeover() returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_secret text := current_setting('app.settings.takeover_hook_secret', true);
begin
    if new.previous_reign_id is null or v_secret is null or v_secret = '' then
        return new;
    end if;
    perform net.http_post(
        url := 'https://ajkdzohnntrbkeqjskel.supabase.co/functions/v1/onesignal-takeover',
        headers := jsonb_build_object('Content-Type', 'application/json', 'Authorization', 'Bearer ' || v_secret),
        body := jsonb_build_object(
            'record', jsonb_build_object(
                'id', new.id,
                'owner_id', new.owner_id,
                'previous_reign_id', new.previous_reign_id
            )
        )
    );
    return new;
end;
$$;

drop trigger if exists reigns_notify_takeover on public.reigns;
create trigger reigns_notify_takeover
after insert on public.reigns
for each row
execute function public.notify_takeover();

commit;
