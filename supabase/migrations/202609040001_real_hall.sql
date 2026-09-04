-- Real, ranked Hall datasets for the Android Today / All Time controls.
create or replace function public.get_hall(
    p_period text default 'today',
    p_limit integer default 100
)
returns jsonb
language plpgsql
security definer
set search_path = ''
stable
as $$
declare
    v_limit integer := least(greatest(coalesce(p_limit, 100), 1), 100);
    v_start timestamptz := case
        when lower(coalesce(p_period, 'today')) = 'today'
            then date_trunc('day', now() at time zone 'UTC') at time zone 'UTC'
        else null
    end;
begin
    return jsonb_build_object(
        'period', case when v_start is null then 'all_time' else 'today' end,
        'generated_at_ms', floor(extract(epoch from now()) * 1000)::bigint,
        'entries', coalesce((
            select jsonb_agg(
                jsonb_build_object(
                    'rank', ranked.rank,
                    'owner', ranked.handle,
                    'owner_id', ranked.owner_id,
                    'initials', ranked.initials,
                    'city', ranked.city,
                    'country_code', ranked.country_code,
                    'verified', ranked.verified,
                    'message', ranked.message,
                    'reign_seconds', ranked.reign_seconds,
                    'verified_views', ranked.verified_views
                ) order by ranked.rank
            )
            from (
                select
                    row_number() over (order by extract(epoch from (coalesce(r.ended_at, now()) - r.started_at)) desc, r.started_at asc)::integer as rank,
                    p.handle,
                    p.id as owner_id,
                    p.initials,
                    p.city,
                    p.country_code,
                    p.verified,
                    m.text as message,
                    extract(epoch from (coalesce(r.ended_at, now()) - r.started_at))::integer as reign_seconds,
                    (select count(*) from public.view_events ve where ve.reign_id = r.id)::integer as verified_views
                from public.reigns r
                join public.profiles p on p.id = r.owner_id
                join public.messages m on m.id = r.message_id
                where v_start is null or r.started_at >= v_start
                order by reign_seconds desc, r.started_at asc
                limit v_limit
            ) ranked
        ), '[]'::jsonb)
    );
end;
$$;

revoke execute on function public.get_hall(text, integer) from public;
grant execute on function public.get_hall(text, integer) to anon, authenticated;
