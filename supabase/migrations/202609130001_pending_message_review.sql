-- Fixes a real gap: a message that lands in 'reviewing' (no OPENAI_API_KEY
-- configured, an ambiguous moderation score, or simply an account's very
-- first-ever message, which is always forced into human review once) had
-- no way to ever leave that status. The moderation console only ever
-- managed reported/live reigns (resolve_moderation_report) - there was no
-- queue and no RPC for pending, not-yet-published messages at all. This
-- adds that queue, a staff approve/reject RPC, and an author-facing push
-- so someone waiting on a decision actually finds out.
--
-- One-time, non-git step after this migration runs, same pattern as the
-- report-resolved push (202609100001):
--   Deploy supabase/functions/onesignal-message-decision with env vars
--   MODERATION_HOOK_SECRET (reuse the same value you set for
--   onesignal-report-resolved), ONESIGNAL_APP_ID and ONESIGNAL_REST_API_KEY.
--   No new app_secrets row needed - this reuses the existing
--   'moderation_hook_secret' row from 202609100001_moderation_console.sql.

alter table public.moderation_audit_log drop constraint if exists moderation_audit_log_action_check;
alter table public.moderation_audit_log add constraint moderation_audit_log_action_check
    check (action in ('dismiss', 'remove', 'ban', 'kill_switch_enabled', 'kill_switch_disabled', 'approve_message', 'reject_message'));

create or replace function public.notify_author_message_decision(p_author_id uuid, p_message_text text, p_verdict text, p_reason text)
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
        url := 'https://ajkdzohnntrbkeqjskel.supabase.co/functions/v1/onesignal-message-decision',
        headers := jsonb_build_object('Content-Type', 'application/json', 'Authorization', 'Bearer ' || v_secret),
        body := jsonb_build_object(
            'author_id', p_author_id,
            'message_text', p_message_text,
            'verdict', p_verdict,
            'reason', p_reason
        )
    );
end;
$$;
revoke all on function public.notify_author_message_decision(uuid, text, text, text) from public, anon, authenticated;

-- The queue: every message still waiting on a human, oldest first.
create or replace function public.pending_messages_queue()
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
            'message_id', m.id,
            'text', m.text,
            'created_at_ms', floor(extract(epoch from m.created_at) * 1000)::bigint,
            'author_id', m.author_id,
            'author_handle', p.handle,
            'is_authors_first_review', not coalesce(p.first_message_reviewed, false)
        ) as item
        from public.messages m
        join public.profiles p on p.id = m.author_id
        where m.status = 'reviewing'
    ) sub;
    return v_result;
end;
$$;
revoke all on function public.pending_messages_queue() from public, anon;
grant execute on function public.pending_messages_queue() to authenticated;

-- Staff approve/reject for a pending message. Also clears the author's
-- first-review lock if it was still set, so this account's future
-- submissions can flow through the automatic path normally instead of
-- being forced into this same queue forever.
create or replace function public.moderate_pending_message(p_message_id uuid, p_verdict text, p_reason text default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_verdict text := lower(trim(p_verdict));
    v_message public.messages%rowtype;
begin
    if not coalesce((select is_staff from public.profiles where id = v_uid), false) then
        raise exception 'STAFF_ONLY';
    end if;
    if v_verdict not in ('approved', 'rejected') then raise exception 'INVALID_VERDICT'; end if;

    select * into v_message from public.messages where id = p_message_id for update;
    if not found then raise exception 'MESSAGE_NOT_FOUND'; end if;
    if v_message.status <> 'reviewing' then raise exception 'MESSAGE_NOT_PENDING'; end if;

    update public.messages
        set status = v_verdict,
            rejection_reason = case when v_verdict = 'rejected' then coalesce(p_reason, 'Reviewed and not approved.') else rejection_reason end
        where id = p_message_id;

    update public.profiles set first_message_reviewed = true
        where id = v_message.author_id and first_message_reviewed = false;

    insert into public.moderation_audit_log(actor_id, action, message_id, target_user_id, detail)
    values (v_uid, case when v_verdict = 'approved' then 'approve_message' else 'reject_message' end, p_message_id, v_message.author_id, jsonb_build_object('reason', p_reason));

    perform public.notify_author_message_decision(v_message.author_id, v_message.text, v_verdict, p_reason);

    return jsonb_build_object('ok', true, 'verdict', v_verdict);
end;
$$;
revoke all on function public.moderate_pending_message(uuid, text, text) from public, anon, authenticated;
grant execute on function public.moderate_pending_message(uuid, text, text) to authenticated;
