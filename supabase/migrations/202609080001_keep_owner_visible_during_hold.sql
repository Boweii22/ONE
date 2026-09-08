-- A message under moderation review still has a real owner who can still be
-- challenged and dethroned. Masking their handle too made it impossible to
-- tell who to take the screen from. Only the app-wide kill switch (where no
-- takeover is possible at all) still shows the system placeholder owner.
create or replace function public.get_one_state()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_state jsonb := public.get_one_state_before_report_masking();
    v_uid uuid := auth.uid();
    v_reign_id uuid := nullif(v_state #>> '{reign,id}', '')::uuid;
    v_message_id text := v_state #>> '{reign,message,id}';
    v_personal_mask boolean := false;
    v_global_hold boolean := false;
    v_killed boolean := false;
begin
    select exists(select 1 from public.moderation_holds where reign_id = v_reign_id and resolved_at is null) into v_global_hold;
    select coalesce(global_kill_switch, false) into v_killed from public.app_settings where singleton;
    if v_uid is not null then
        select exists(select 1 from public.reports where reporter_id = v_uid and reign_id = v_reign_id) into v_personal_mask;
    end if;
    if v_killed then
        v_state := jsonb_set(v_state, '{reign,message}', jsonb_build_object('id', v_message_id, 'text', 'ONE IS PAUSED.', 'times_deployed', 0));
        v_state := jsonb_set(v_state, '{reign,owner}', jsonb_build_object('id', 'system', 'handle', '@ONE', 'city', '', 'country_code', '', 'verified', true, 'initials', '1'));
    elsif v_global_hold then
        -- The owner is left visible on purpose: they can still be challenged
        -- and dethroned while their message is under review.
        v_state := jsonb_set(v_state, '{reign,message}', jsonb_build_object('id', v_message_id, 'text', 'MESSAGE UNDER REVIEW.', 'times_deployed', 0));
    elsif v_personal_mask and not coalesce((v_state->>'current_content_blocked')::boolean, false) then
        v_state := jsonb_set(v_state, '{reign,message}', jsonb_build_object('id', v_message_id, 'text', 'CONTENT HIDDEN.', 'times_deployed', 0));
    end if;
    return v_state || jsonb_build_object('current_content_blocked', coalesce((v_state->>'current_content_blocked')::boolean, false) or v_personal_mask, 'moderation_hold', v_global_hold, 'global_kill_switch', v_killed);
end;
$$;
revoke all on function public.get_one_state() from public;
grant execute on function public.get_one_state() to anon, authenticated;
