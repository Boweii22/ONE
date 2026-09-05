-- Optional public profile photos (small re-encoded JPEGs), honest location,
-- complete current-reign reaction counts, and one Hall row per person.
alter table public.profiles add column if not exists photo_base64 text;
alter table public.profiles add column if not exists photo_version uuid;
alter table public.profiles add constraint profile_photo_size check (photo_base64 is null or length(photo_base64) <= 100000);

create or replace function public.update_profile_details(p_city text default '', p_country_code text default '')
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_uid uuid := auth.uid(); v_city text := trim(coalesce(p_city, '')); v_country text := upper(trim(coalesce(p_country_code, '')));
begin
  if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
  if length(v_city) > 60 or v_city ~ '[[:cntrl:]]' then raise exception 'INVALID_CITY'; end if;
  if v_country <> '' and (v_country !~ '^[A-Z]{2}$' or v_country = 'XX') then raise exception 'INVALID_COUNTRY'; end if;
  perform public.ensure_profile(null);
  if exists(select 1 from public.profiles where id = v_uid and banned_at is not null) then raise exception 'ACCOUNT_UNAVAILABLE'; end if;
  update public.profiles set city = v_city, country_code = v_country where id = v_uid;
  return jsonb_build_object('ok', true);
end $$;

create or replace function public.set_profile_photo(p_photo text default null)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_uid uuid := auth.uid(); v_bytes bytea;
begin
  if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
  perform public.ensure_profile(null);
  if exists(select 1 from public.profiles where id = v_uid and banned_at is not null) then raise exception 'ACCOUNT_UNAVAILABLE'; end if;
  if p_photo is not null then
    if length(p_photo) > 100000 or p_photo !~ '^[A-Za-z0-9+/=]+$' then raise exception 'INVALID_PHOTO'; end if;
    v_bytes := decode(p_photo, 'base64');
    if octet_length(v_bytes) < 4 or substring(v_bytes from 1 for 3) <> decode('ffd8ff', 'hex') then raise exception 'JPEG_REQUIRED'; end if;
  end if;
  update public.profiles set photo_base64 = p_photo, photo_version = case when p_photo is null then null else gen_random_uuid() end where id = v_uid;
  return jsonb_build_object('ok', true);
end $$;

create or replace function public.get_profile_photo(p_user_id uuid)
returns jsonb language sql stable security definer set search_path = '' as $$
  select jsonb_build_object('photo', (select photo_base64 from public.profiles where id = p_user_id and banned_at is null));
$$;
revoke all on function public.update_profile_details(text,text), public.set_profile_photo(text), public.get_profile_photo(uuid) from public, anon, authenticated;
grant execute on function public.update_profile_details(text,text), public.set_profile_photo(text) to authenticated;
grant execute on function public.get_profile_photo(uuid) to anon, authenticated;

-- Older app releases remain supported; the new five controls use their own keys.
alter table public.reactions drop constraint if exists reactions_reaction_check;
alter table public.reactions add constraint reactions_reaction_check check (reaction in ('THIEF','TOO SLOW','TAKE IT BACK','RESPECT','LOL','FIRE','100','WATCH','ROCKET'));
create or replace function public.react_to_one(p_reaction text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_uid uuid := auth.uid(); v_reign_id uuid; v_key text := upper(trim(p_reaction));
begin
  if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
  if v_key is null or v_key not in ('THIEF','TOO SLOW','TAKE IT BACK','RESPECT','LOL','FIRE','100','WATCH','ROCKET') then raise exception 'INVALID_REACTION'; end if;
  perform public.ensure_profile(null);
  -- Lock the profile so concurrent taps cannot bypass the rate limit.
  perform 1 from public.profiles where id = v_uid and banned_at is null for update;
  if not found then raise exception 'ACCOUNT_UNAVAILABLE'; end if;
  if (select count(*) from public.reactions where user_id = v_uid and created_at > now() - interval '10 seconds') >= 3 then raise exception 'REACTION_RATE_LIMIT'; end if;
  select id into v_reign_id from public.reigns where ended_at is null;
  insert into public.reactions(reign_id,user_id,reaction) values(v_reign_id,v_uid,v_key);
  return jsonb_build_object('ok', true);
end $$;
revoke all on function public.react_to_one(text) from public, anon;
grant execute on function public.react_to_one(text) to authenticated;

-- Retain the existing block/content filtering rather than replacing it.
alter function public.get_one_state() rename to get_one_state_before_profile_stats;
revoke all on function public.get_one_state_before_profile_stats() from public, anon, authenticated;
create function public.get_one_state()
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare v_state jsonb := public.get_one_state_before_profile_stats(); v_uid uuid := auth.uid(); v_owner uuid; v_reign uuid; v_photo uuid; v_counts jsonb;
begin
  v_owner := nullif(v_state #>> '{reign,owner,id}', '')::uuid;
  v_reign := nullif(v_state #>> '{reign,id}', '')::uuid;
  if not coalesce((v_state->>'current_content_blocked')::boolean,false) then
    select photo_version into v_photo from public.profiles where id = v_owner and banned_at is null;
    v_state := jsonb_set(v_state, '{reign,owner,photo_version}', coalesce(to_jsonb(v_photo),'null'::jsonb));
  end if;
  if v_uid is not null and v_state->'current_user' <> 'null'::jsonb then
    select photo_version into v_photo from public.profiles where id = v_uid;
    v_state := jsonb_set(v_state, '{current_user,photo_version}', coalesce(to_jsonb(v_photo),'null'::jsonb));
  end if;
  select coalesce(jsonb_object_agg(key, amount), '{}'::jsonb) into v_counts from (
    select case reaction when 'THIEF' then 'FIRE' when 'TOO SLOW' then 'WATCH' when 'TAKE IT BACK' then 'ROCKET' when 'LOL' then '100' else reaction end as key, count(*)::integer as amount
    from public.reactions rx where reign_id = v_reign
      and not exists(select 1 from public.blocked_profiles b where b.blocker_id = v_uid and b.blocked_id = rx.user_id)
    group by 1
  ) counts;
  v_state := v_state || jsonb_build_object('reaction_counts', jsonb_build_object('FIRE',0,'RESPECT',0,'100',0,'WATCH',0,'ROCKET',0) || v_counts);
  if v_uid is not null then
    v_state := v_state || jsonb_build_object('user_stats', (
      select jsonb_build_object('takeovers',count(*),'longest_reign_seconds',coalesce(max(greatest(0,extract(epoch from (coalesce(ended_at,now())-started_at))))::integer,0),
        'verified_views',(select count(*) from public.view_events ve join public.reigns rv on rv.id=ve.reign_id where rv.owner_id=v_uid))
      from public.reigns where owner_id=v_uid
    ));
  end if;
  return v_state;
end $$;
revoke all on function public.get_one_state() from public;
grant execute on function public.get_one_state() to anon,authenticated;

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
  ), ranked as (
    select row_number() over(order by s.reign_seconds desc,s.takeovers desc,s.first_reign,s.owner_id)::integer rank,
      s.*,p.handle,p.initials,p.city,p.country_code,p.verified,p.photo_version,coalesce(v.verified_views,0) verified_views
    from stats s join public.profiles p on p.id=s.owner_id left join views v on v.owner_id=s.owner_id
  )
  select coalesce(jsonb_agg(to_jsonb(item) order by item.rank),'[]'::jsonb) into v_result from (
    select rank,owner_id,handle as owner,initials,city,country_code,verified,photo_version,reign_seconds,takeovers,verified_views,''::text message
    from ranked where rank <= least(greatest(coalesce(p_limit,100),1),100) or owner_id=auth.uid() order by rank
  ) item;
  return jsonb_build_object('period',p_period,'entries',v_result);
end $$;
revoke all on function public.get_hall(text,integer) from public;
grant execute on function public.get_hall(text,integer) to anon,authenticated;
