-- Keep historical rows; count one vote per person/type and prevent new duplicates.
begin;
create or replace function public.react_to_one(p_reaction text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_uid uuid := auth.uid(); v_reign uuid; v_key text := upper(trim(p_reaction));
begin
  if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
  v_key := case v_key when 'THIEF' then 'FIRE' when 'TOO SLOW' then 'WATCH' when 'TAKE IT BACK' then 'ROCKET' when 'LOL' then '100' else v_key end;
  if v_key is null or v_key not in ('FIRE','RESPECT','100','WATCH','ROCKET') then raise exception 'INVALID_REACTION'; end if;
  perform public.ensure_profile(null);
  perform 1 from public.profiles where id=v_uid and banned_at is null for update;
  if not found then raise exception 'ACCOUNT_UNAVAILABLE'; end if;
  select id into v_reign from public.reigns where ended_at is null;
  if v_reign is null then raise exception 'NO_ACTIVE_REIGN'; end if;
  if exists(select 1 from public.reactions where user_id=v_uid and reign_id=v_reign and
    case reaction when 'THIEF' then 'FIRE' when 'TOO SLOW' then 'WATCH' when 'TAKE IT BACK' then 'ROCKET' when 'LOL' then '100' else reaction end = v_key) then
    return jsonb_build_object('ok',true,'already_reacted',true);
  end if;
  insert into public.reactions(reign_id,user_id,reaction) values(v_reign,v_uid,v_key);
  return jsonb_build_object('ok',true,'already_reacted',false);
end $$;
revoke all on function public.react_to_one(text) from public, anon;
grant execute on function public.react_to_one(text) to authenticated;

do $$ begin
  if to_regprocedure('public.get_one_state_before_unique_reactions()') is null then
    alter function public.get_one_state() rename to get_one_state_before_unique_reactions;
  end if;
end $$;
revoke all on function public.get_one_state_before_unique_reactions() from public, anon, authenticated;
create or replace function public.get_one_state() returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare v_state jsonb := public.get_one_state_before_unique_reactions(); v_reign uuid := nullif(v_state #>> '{reign,id}','')::uuid; v_counts jsonb; v_mine jsonb;
begin
  with votes as (
    select distinct user_id, case reaction when 'THIEF' then 'FIRE' when 'TOO SLOW' then 'WATCH' when 'TAKE IT BACK' then 'ROCKET' when 'LOL' then '100' else reaction end as key
    from public.reactions r where reign_id=v_reign
      and not exists(select 1 from public.blocked_profiles b where b.blocker_id=auth.uid() and b.blocked_id=r.user_id)
  ), counts as (select key,count(*) amount from votes group by key)
  select coalesce(jsonb_object_agg(key,amount),'{}'::jsonb) into v_counts from counts;
  select coalesce(jsonb_agg(distinct case reaction when 'THIEF' then 'FIRE' when 'TOO SLOW' then 'WATCH' when 'TAKE IT BACK' then 'ROCKET' when 'LOL' then '100' else reaction end),'[]'::jsonb)
    into v_mine from public.reactions where reign_id=v_reign and user_id=auth.uid();
  return v_state || jsonb_build_object('my_reactions',v_mine,'reaction_counts',jsonb_build_object('FIRE',0,'RESPECT',0,'100',0,'WATCH',0,'ROCKET',0)||v_counts);
end $$;
revoke all on function public.get_one_state() from public;
grant execute on function public.get_one_state() to anon, authenticated;
commit;
