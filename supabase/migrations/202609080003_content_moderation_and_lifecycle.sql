-- Content rules, moderation tiering and message lifecycle.
--
-- submit_message keeps the fast deterministic regex reject (obvious links,
-- emails, phone numbers, slur list) as an immediate, specific rejection.
-- Everything else lands as 'reviewing' and is scored asynchronously by the
-- moderate-message edge function, which calls apply_message_moderation()
-- below to do the actual auto-approve / auto-reject / keep-queued decision
-- in one place. A brand new account's very first message is always forced
-- into the human queue once, regardless of score.

alter table public.messages add column if not exists deleted_at timestamptz;
alter table public.messages drop constraint if exists messages_status_check;
alter table public.messages add constraint messages_status_check
    check (status in ('approved', 'reviewing', 'rejected', 'revoked'));

alter table public.profiles add column if not exists first_message_reviewed boolean not null default false;

create table if not exists public.upheld_report_messages (
    id uuid primary key default gen_random_uuid(),
    message_id uuid references public.messages(id),
    message_text text not null,
    report_reason text not null,
    moderation_action text not null,
    created_at timestamptz not null default now()
);
alter table public.upheld_report_messages enable row level security;
revoke all on public.upheld_report_messages from public, anon, authenticated;

-- Delete rules: never-deployed messages are removed outright. Deployed
-- messages are soft-deleted (hidden, row kept) since reign history, Hall
-- entries and receipts reference them. Messages under review, or revoked
-- after an upheld report, cannot be deleted at all.
create or replace function public.delete_my_message(p_message_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_message public.messages%rowtype;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    select * into v_message from public.messages where id = p_message_id and author_id = v_uid for update;
    if not found then raise exception 'MESSAGE_NOT_FOUND'; end if;
    if v_message.status = 'reviewing' then raise exception 'MESSAGE_UNDER_REVIEW'; end if;
    if v_message.status = 'revoked' then raise exception 'MESSAGE_REVOKED'; end if;
    if v_message.deleted_at is not null then raise exception 'MESSAGE_ALREADY_DELETED'; end if;

    if v_message.times_deployed = 0 then
        delete from public.messages where id = p_message_id;
        return jsonb_build_object('ok', true, 'mode', 'removed');
    else
        update public.messages set deleted_at = now() where id = p_message_id;
        return jsonb_build_object('ok', true, 'mode', 'hidden');
    end if;
end;
$$;
revoke all on function public.delete_my_message(uuid) from public, anon;
grant execute on function public.delete_my_message(uuid) to authenticated;

-- Service-role only: the moderate-message edge function calls this with raw
-- category scores from the moderation model. All the threshold/queue/first-
-- message logic lives here so it isn't split between Deno and SQL.
create or replace function public.apply_message_moderation(p_message_id uuid, p_max_score numeric)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_message public.messages%rowtype;
    v_first_pending boolean;
    v_verdict text;
    v_reason text;
begin
    select * into v_message from public.messages where id = p_message_id for update;
    if not found then raise exception 'MESSAGE_NOT_FOUND'; end if;
    if v_message.status <> 'reviewing' then
        return jsonb_build_object('ok', true, 'skipped', true, 'status', v_message.status);
    end if;

    select not first_message_reviewed into v_first_pending from public.profiles where id = v_message.author_id;

    if p_max_score >= 0.8 then
        v_verdict := 'rejected';
        v_reason := 'Automatically flagged by our safety model.';
    elsif p_max_score < 0.2 and not coalesce(v_first_pending, false) then
        v_verdict := 'approved';
    else
        v_verdict := 'reviewing';
    end if;

    update public.messages
        set status = v_verdict,
            rejection_reason = case when v_verdict = 'rejected' then v_reason else rejection_reason end
        where id = p_message_id;

    if coalesce(v_first_pending, false) then
        update public.profiles set first_message_reviewed = true where id = v_message.author_id;
    end if;

    return jsonb_build_object('ok', true, 'verdict', v_verdict);
end;
$$;
revoke all on function public.apply_message_moderation(uuid, numeric) from public, anon, authenticated;

-- Service-role only, for resolving whatever's left sitting in the human
-- queue (ambiguous scores, or a forced first-message review).
create or replace function public.resolve_message_review(p_message_id uuid, p_action text, p_reason text default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_action text := lower(trim(p_action));
begin
    if v_action not in ('approve', 'reject') then raise exception 'INVALID_REVIEW_ACTION'; end if;
    update public.messages
        set status = case when v_action = 'approve' then 'approved' else 'rejected' end,
            rejection_reason = case when v_action = 'reject' then coalesce(p_reason, 'Did not pass manual review.') else rejection_reason end
        where id = p_message_id and status = 'reviewing';
    if not found then raise exception 'MESSAGE_NOT_IN_REVIEW'; end if;
    return jsonb_build_object('ok', true, 'action', v_action);
end;
$$;
revoke all on function public.resolve_message_review(uuid, text, text) from public, anon, authenticated;

-- A report being upheld means the message itself was wrong to publish, not
-- just that this reign should end: revoke it so it can never be redeployed,
-- and log it as training input for tightening the automatic filter.
create or replace function public.resolve_moderation_report(p_reign_id uuid, p_action text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_action text := lower(trim(p_action));
    v_owner uuid;
    v_message_id uuid;
    v_message_text text;
    v_report_reason text;
begin
    if v_action not in ('remove', 'dismiss', 'suspend_24h', 'ban') then raise exception 'INVALID_MODERATION_ACTION'; end if;
    select owner_id, message_id into v_owner, v_message_id from public.reigns where id = p_reign_id for update;
    if v_owner is null then raise exception 'REIGN_NOT_FOUND'; end if;
    if v_action = 'dismiss' then
        update public.reports set status = 'dismissed', resolution = 'dismissed', resolved_at = now() where reign_id = p_reign_id and status = 'open';
        update public.moderation_holds set resolved_at = now(), resolution = 'dismissed' where reign_id = p_reign_id;
    else
        update public.reports set status = 'resolved', resolution = case when v_action = 'remove' then 'removed' when v_action = 'ban' then 'banned' else 'suspended' end, resolved_at = now() where reign_id = p_reign_id and status = 'open';
        update public.moderation_holds set resolved_at = now(), resolution = case when v_action = 'remove' then 'removed' when v_action = 'ban' then 'banned' else 'suspended' end where reign_id = p_reign_id;
        if v_action = 'suspend_24h' then update public.profiles set suspended_until = now() + interval '24 hours', moderation_note = 'moderation suspension' where id = v_owner; end if;
        if v_action = 'ban' then update public.profiles set banned_at = now(), moderation_note = 'moderation ban' where id = v_owner; end if;

        select text into v_message_text from public.messages where id = v_message_id;
        select reason into v_report_reason from public.reports where reign_id = p_reign_id order by created_at asc limit 1;
        update public.messages set status = 'revoked' where id = v_message_id;
        insert into public.upheld_report_messages(message_id, message_text, report_reason, moderation_action)
        values (v_message_id, coalesce(v_message_text, ''), coalesce(v_report_reason, 'UNKNOWN'), v_action);
    end if;
    return jsonb_build_object('ok', true, 'action', v_action);
end;
$$;
revoke all on function public.resolve_moderation_report(uuid, text) from public, anon, authenticated;

-- submit_message: fast, deterministic, specific rejections up front; anything
-- else goes to 'reviewing' for the async moderation-model pass.
create or replace function public.submit_message(p_text text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_text text := upper(trim(p_text));
    v_status text;
    v_reason text;
    v_message public.messages%rowtype;
begin
    if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
    perform public.ensure_profile(null);
    if char_length(v_text) not between 1 and 90 then raise exception 'MESSAGE_MUST_BE_1_TO_90_CHARACTERS'; end if;
    if (select count(*) from public.messages where author_id = v_uid and created_at > now() - interval '1 day') >= 20 then
        raise exception 'DAILY_MESSAGE_LIMIT';
    end if;

    if v_text ~* '(https?://|www\.)' then
        v_status := 'rejected'; v_reason := 'That looks like a link.';
    elsif v_text ~* '[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}' then
        v_status := 'rejected'; v_reason := 'That looks like an email address.';
    elsif v_text ~* '\+?[0-9][0-9 ()-]{7,}' then
        v_status := 'rejected'; v_reason := 'That looks like a phone number.';
    elsif v_text ~* '(kill yourself|send money|crypto wallet|seed phrase|home address|kidnap|your organs)' then
        v_status := 'rejected'; v_reason := 'That looks like it violates the ONE community rules.';
    else
        v_status := 'reviewing'; v_reason := null;
    end if;

    insert into public.messages(author_id, text, status, rejection_reason)
    values (v_uid, v_text, v_status, v_reason)
    returning * into v_message;
    return jsonb_build_object(
        'id', v_message.id,
        'text', v_message.text,
        'status', v_message.status,
        'rejection_reason', v_message.rejection_reason,
        'created_at_ms', floor(extract(epoch from v_message.created_at) * 1000)::bigint
    );
end;
$$;

-- Refresh the messages array: include rejection_reason, and exclude
-- soft-deleted rows, without touching the older nested query it replaces.
alter function public.get_one_state() rename to get_one_state_before_content_lifecycle;
revoke all on function public.get_one_state_before_content_lifecycle() from public, anon, authenticated;
create function public.get_one_state()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_state jsonb := public.get_one_state_before_content_lifecycle();
    v_messages jsonb := '[]'::jsonb;
begin
    if v_uid is not null then
        select coalesce(jsonb_agg(jsonb_build_object(
            'id', m.id, 'text', m.text, 'status', m.status,
            'rejection_reason', m.rejection_reason,
            'created_at_ms', floor(extract(epoch from m.created_at) * 1000)::bigint,
            'times_deployed', m.times_deployed
        ) order by m.created_at desc), '[]'::jsonb)
        into v_messages
        from public.messages m
        where m.author_id = v_uid and m.deleted_at is null;
    end if;
    return v_state || jsonb_build_object('messages', v_messages);
end;
$$;
revoke all on function public.get_one_state() from public;
grant execute on function public.get_one_state() to anon, authenticated;
