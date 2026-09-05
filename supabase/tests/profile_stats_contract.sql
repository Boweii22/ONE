-- Run after 202609050001 in the SQL editor. Everything rolls back.
begin;
do $$
declare v_uid uuid := gen_random_uuid(); v_state jsonb; v_hall jsonb; v_bad boolean := false;
begin
  insert into public.profiles(id,handle,initials) values(v_uid,'@QA_' || left(replace(v_uid::text,'-',''),10),'QA');
  perform set_config('request.jwt.claim.sub',v_uid::text,true);
  perform public.update_profile_details('London','GB');
  if not exists(select 1 from public.profiles where id=v_uid and city='London' and country_code='GB') then raise exception 'profile update failed'; end if;
  begin
    perform public.set_profile_photo('not-a-photo');
  exception when others then v_bad := true;
  end;
  if not v_bad then raise exception 'invalid image accepted'; end if;
  perform public.set_profile_photo(null);
  v_state := public.get_one_state();
  if v_state #>> '{user_stats,takeovers}' <> '0' then raise exception 'new account has fake takeovers'; end if;
  if not (v_state->'reaction_counts' ?& array['FIRE','RESPECT','100','WATCH','ROCKET']) then raise exception 'reaction totals missing'; end if;
  v_hall := public.get_hall('all_time',100);
  if exists(select item->>'owner_id' from jsonb_array_elements(v_hall->'entries') item group by 1 having count(*)>1) then raise exception 'duplicate Hall owner'; end if;
end $$;
rollback;
