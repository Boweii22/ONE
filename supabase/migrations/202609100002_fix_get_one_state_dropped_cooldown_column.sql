-- URGENT FIX: get_one_state() has been failing for every caller with
-- "column p.cooldown_until does not exist" since the take-balance migration
-- (202609090001) dropped profiles.cooldown_until. The break is inside an
-- intermediate layer of the get_one_state() rename chain -- originally
-- defined as get_one_state() in 202609050002_country_progressive_cooldowns.sql,
-- later renamed to get_one_state_before_report_masking by 202609070001 -- which
-- computed a cooldown_remaining_seconds field from that column. Every device
-- polling get_one_state() has been getting a hard 400 ever since, which is why
-- the app has been showing itself as offline: this is a genuine outage, not a
-- connectivity-detection bug.
--
-- cooldown_remaining_seconds is already dead weight: the current outermost
-- get_one_state() (202609090001) strips that key from the output unconditionally.
-- So this layer just needs to stop computing it, not replace it with anything.
create or replace function public.get_one_state_before_report_masking()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    return public.get_one_state_before_progressive();
end;
$$;
revoke all on function public.get_one_state_before_report_masking() from public, anon, authenticated;
