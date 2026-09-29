-- Two-step tester onboarding: signing up on the site sends an immediate
-- welcome email (no Play link - they aren't on the tester list yet), and
-- gets a row here. A staff member manually adds them to the Play Console
-- tester list at their own pace, then hits Approve in the moderation
-- console's new Pending Testers queue - that's what actually sends the
-- second email with the real install link, right when it will work.
--
-- One-time, non-git step after this migration runs, same pattern as the
-- other moderation pushes:
--   Deploy supabase/functions/send-tester-invite with env vars
--   MODERATION_HOOK_SECRET (reuse the same value already set for the other
--   moderation edge functions), RESEND_API_KEY, TESTER_FROM_EMAIL,
--   TESTER_REPLY_TO and TESTER_PLAY_URL (same values as the website's own
--   env vars - Edge Functions and the website are separate deployments and
--   don't share env vars automatically).

alter table public.tester_interest add column if not exists approved_by uuid references public.profiles(id);

create or replace function public.send_tester_invite_email(p_email text, p_name text, p_device text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_secret text;
begin
    select value into v_secret from public.app_secrets where key = 'moderation_hook_secret';
    if v_secret is null or v_secret = '' then return; end if;
    perform net.http_post(
        url := 'https://ajkdzohnntrbkeqjskel.supabase.co/functions/v1/send-tester-invite',
        headers := jsonb_build_object('Content-Type', 'application/json', 'Authorization', 'Bearer ' || v_secret),
        body := jsonb_build_object('email', p_email, 'name', p_name, 'device', p_device)
    );
end;
$$;
revoke all on function public.send_tester_invite_email(text, text, text) from public, anon, authenticated;

-- The queue: everyone who signed up and hasn't been approved yet, oldest first.
create or replace function public.pending_testers_queue()
returns jsonb
language plpgsql
security definer
set search_path = ''
stable
as $$
declare
    v_result jsonb;
begin
    if not coalesce((select is_staff from public.profiles where id = auth.uid()), false) then
        raise exception 'STAFF_ONLY';
    end if;
    select coalesce(jsonb_agg(item order by (item->>'created_at_ms')::bigint asc), '[]'::jsonb)
    into v_result
    from (
        select jsonb_build_object(
            'id', t.id,
            'email', t.email,
            'name', t.name,
            'device', t.device,
            'created_at_ms', floor(extract(epoch from t.created_at) * 1000)::bigint
        ) as item
        from public.tester_interest t
        where t.contacted_at is null
    ) sub;
    return v_result;
end;
$$;
revoke all on function public.pending_testers_queue() from public, anon;
grant execute on function public.pending_testers_queue() to authenticated;

-- Staff-only: marks a signup as contacted and fires the real invite email
-- with the Play Store opt-in link. Idempotent - approving twice sends only
-- the one email from the first call.
create or replace function public.approve_tester_invite(p_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_row public.tester_interest%rowtype;
begin
    if not coalesce((select is_staff from public.profiles where id = v_uid), false) then
        raise exception 'STAFF_ONLY';
    end if;
    select * into v_row from public.tester_interest where id = p_id for update;
    if not found then raise exception 'SIGNUP_NOT_FOUND'; end if;
    if v_row.contacted_at is not null then
        return jsonb_build_object('ok', true, 'already_approved', true);
    end if;

    update public.tester_interest set contacted_at = now(), approved_by = v_uid where id = p_id;
    perform public.send_tester_invite_email(v_row.email, coalesce(v_row.name, ''), v_row.device);

    return jsonb_build_object('ok', true, 'already_approved', false);
end;
$$;
revoke all on function public.approve_tester_invite(uuid) from public, anon, authenticated;
grant execute on function public.approve_tester_invite(uuid) to authenticated;
