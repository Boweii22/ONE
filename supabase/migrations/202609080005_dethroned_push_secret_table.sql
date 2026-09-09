begin;

-- ALTER DATABASE ... SET is superuser-only on Supabase's hosted Postgres, so the
-- shared secret can't live in a custom GUC. A small locked-down table works with
-- ordinary insert/select privileges instead.
create table if not exists public.app_secrets (
    key text primary key,
    value text not null,
    updated_at timestamptz not null default now()
);

alter table public.app_secrets enable row level security;
revoke all on public.app_secrets from public, anon, authenticated;

create or replace function public.notify_takeover() returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_secret text;
begin
    if new.previous_reign_id is null then
        return new;
    end if;

    select value into v_secret from public.app_secrets where key = 'takeover_hook_secret';
    if v_secret is null or v_secret = '' then
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

commit;
