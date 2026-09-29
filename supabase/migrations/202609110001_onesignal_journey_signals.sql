-- Feeds OneSignal Journeys: until now nothing set a tag or fired a custom
-- event, so a Journey would have had no state to branch on. times_dethroned
-- is tracked authoritatively here (not read back from OneSignal and
-- incremented there) because that would need an extra GET before every
-- write and would be exposed to double-counting on any retry; incrementing
-- our own row in the same transaction that ends the reign is atomic and
-- exactly-once by construction, so the edge function only ever relays an
-- already-correct number outward.
alter table public.profiles add column if not exists times_dethroned integer not null default 0;

-- Reuses the exact trigger that already fires the dethroned push (any reign
-- ending funnels through this one INSERT, whether from a natural takeover,
-- a moderation Remove/Ban, or account deletion), so the tag/event and the
-- notification are computed from the same data in the same execution and
-- can't drift apart.
create or replace function public.notify_takeover() returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_secret text;
    v_previous_owner uuid;
    v_times_dethroned integer;
begin
    if new.previous_reign_id is null then
        return new;
    end if;

    select value into v_secret from public.app_secrets where key = 'takeover_hook_secret';
    if v_secret is null or v_secret = '' then
        return new;
    end if;

    select owner_id into v_previous_owner from public.reigns where id = new.previous_reign_id;
    if v_previous_owner is not null and v_previous_owner not in ('00000000-0000-0000-0000-000000000001'::uuid, '00000000-0000-0000-0000-000000000004'::uuid) then
        update public.profiles set times_dethroned = times_dethroned + 1
        where id = v_previous_owner
        returning times_dethroned into v_times_dethroned;
    end if;

    perform net.http_post(
        url := 'https://ajkdzohnntrbkeqjskel.supabase.co/functions/v1/onesignal-takeover',
        headers := jsonb_build_object('Content-Type', 'application/json', 'Authorization', 'Bearer ' || v_secret),
        body := jsonb_build_object(
            'record', jsonb_build_object(
                'id', new.id,
                'owner_id', new.owner_id,
                'previous_reign_id', new.previous_reign_id
            ),
            'times_dethroned', v_times_dethroned
        )
    );
    return new;
end;
$$;
