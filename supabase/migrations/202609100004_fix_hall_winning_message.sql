-- get_hall() hardcoded message to ''::text, so the winning-message card has
-- never had anything real to show. Fixed by tracking which specific reign
-- produced each owner's best (longest) reign_seconds within the period, then
-- joining that reign's actual message text in.
create or replace function public.get_hall(p_period text default 'today', p_limit integer default 100)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare v_start timestamptz; v_result jsonb;
begin
  if lower(coalesce(p_period,'today')) not in ('today','all_time') then raise exception 'INVALID_PERIOD'; end if;
  v_start := case when lower(coalesce(p_period,'today'))='today' then date_trunc('day',now() at time zone 'UTC') at time zone 'UTC' else null end;
  with eligible as (
    select r.* from public.reigns r join public.profiles p on p.id=r.owner_id
    where (v_start is null or r.started_at >= v_start) and p.banned_at is null
      and r.owner_id not in ('00000000-0000-0000-0000-000000000001'::uuid,'00000000-0000-0000-0000-000000000004'::uuid)
      and not exists(select 1 from public.blocked_profiles b where b.blocker_id=auth.uid() and b.blocked_id=r.owner_id)
  ), stats as (
    select owner_id, count(*)::integer takeovers, max(greatest(0,extract(epoch from(coalesce(ended_at,now())-started_at))))::integer reign_seconds, min(started_at) first_reign
    from eligible group by owner_id
  ), views as (
    select e.owner_id,count(*)::integer verified_views from eligible e join public.view_events ve on ve.reign_id=e.id group by e.owner_id
  ), best_reign as (
    -- The one reign per owner that actually produced their reign_seconds
    -- above, so the champion card can quote the message that earned it.
    select distinct on (owner_id) owner_id, message_id
    from eligible
    order by owner_id, greatest(0, extract(epoch from (coalesce(ended_at, now()) - started_at))) desc, started_at asc
  ), ranked as (
    select row_number() over(order by s.reign_seconds desc,s.takeovers desc,s.first_reign,s.owner_id)::integer rank,
      s.*,p.handle,p.initials,p.city,p.country_code,p.verified,p.photo_version,coalesce(v.verified_views,0) verified_views,
      coalesce(m.text, '') as message
    from stats s
      join public.profiles p on p.id=s.owner_id
      left join views v on v.owner_id=s.owner_id
      left join best_reign br on br.owner_id = s.owner_id
      left join public.messages m on m.id = br.message_id
  )
  select coalesce(jsonb_agg(to_jsonb(item) order by item.rank),'[]'::jsonb) into v_result from (
    select rank,owner_id,handle as owner,initials,city,country_code,verified,photo_version,reign_seconds,takeovers,verified_views,message
    from ranked where rank <= least(greatest(coalesce(p_limit,100),1),100) or owner_id=auth.uid() order by rank
  ) item;
  return jsonb_build_object('period',p_period,'entries',v_result);
end $$;
revoke all on function public.get_hall(text,integer) from public;
grant execute on function public.get_hall(text,integer) to anon,authenticated;
