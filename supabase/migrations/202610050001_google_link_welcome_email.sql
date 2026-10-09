-- Sends a personalised welcome email the first time a player links a Google
-- account, confirming their identity (@HANDLE) is now protected. Reuses the
-- same Resend setup already deployed for send-tester-invite - no new secrets
-- needed, only a new Edge Function.
--
-- One-time, non-git step after this migration runs:
--   Deploy supabase/functions/send-google-welcome with env vars
--   MODERATION_HOOK_SECRET (same value already set for the other moderation
--   Edge Functions), RESEND_API_KEY, TESTER_FROM_EMAIL, TESTER_REPLY_TO
--   (same values as send-tester-invite - Edge Functions don't share env vars
--   with each other or the website automatically).

alter table public.profiles add column if not exists google_welcome_sent_at timestamptz;

create or replace function public.send_google_welcome_email(p_email text, p_handle text)
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
        url := 'https://ajkdzohnntrbkeqjskel.supabase.co/functions/v1/send-google-welcome',
        headers := jsonb_build_object('Content-Type', 'application/json', 'Authorization', 'Bearer ' || v_secret),
        body := jsonb_build_object('email', p_email, 'handle', p_handle)
    );
end;
$$;
revoke all on function public.send_google_welcome_email(text, text) from public, anon, authenticated;

-- Called by the app right after a successful Google link (not a restore -
-- restoring an already-linked identity hits the already_sent branch below
-- and stays silent, since that account was welcomed the first time it was
-- linked). Idempotent on google_welcome_sent_at, so a retried call, or the
-- app calling this after both the native and browser link paths, never
-- sends a second email.
create or replace function public.mark_google_linked(p_email text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_profile public.profiles%rowtype;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    if p_email is null or length(p_email) = 0 or position('@' in p_email) = 0 then
        raise exception 'EMAIL_REQUIRED';
    end if;

    select * into v_profile from public.profiles where id = v_uid for update;
    if not found then raise exception 'PROFILE_NOT_FOUND'; end if;
    if v_profile.google_welcome_sent_at is not null then
        return jsonb_build_object('ok', true, 'already_sent', true);
    end if;

    update public.profiles set google_welcome_sent_at = now() where id = v_uid;
    perform public.send_google_welcome_email(p_email, v_profile.handle);

    return jsonb_build_object('ok', true, 'already_sent', false);
end;
$$;
revoke all on function public.mark_google_linked(text) from public, anon;
grant execute on function public.mark_google_linked(text) to authenticated;
