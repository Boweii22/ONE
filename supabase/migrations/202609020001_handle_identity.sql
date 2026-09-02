create table if not exists private.reserved_handles (
    handle text primary key check (handle = upper(handle) and handle !~ '[^A-Z0-9_]'),
    reason text not null default 'protected identity',
    created_at timestamptz not null default now()
);

insert into private.reserved_handles(handle, reason)
values
    ('ONE', 'product identity'),
    ('ADMIN', 'staff identity'),
    ('ADMINISTRATOR', 'staff identity'),
    ('MOD', 'staff identity'),
    ('MODERATOR', 'staff identity'),
    ('SUPPORT', 'staff identity'),
    ('OFFICIAL', 'staff identity'),
    ('REVENUECAT', 'partner identity'),
    ('SUPABASE', 'partner identity'),
    ('ONESIGNAL', 'partner identity'),
    ('GOOGLE', 'brand identity'),
    ('GOOGLEPLAY', 'brand identity'),
    ('ANDROID', 'brand identity'),
    ('MICROSOFT', 'brand identity'),
    ('APPLE', 'brand identity'),
    ('META', 'brand identity'),
    ('FACEBOOK', 'brand identity'),
    ('INSTAGRAM', 'brand identity'),
    ('WHATSAPP', 'brand identity'),
    ('OPENAI', 'brand identity'),
    ('CHATGPT', 'brand identity'),
    ('TWITTER', 'brand identity'),
    ('TIKTOK', 'brand identity'),
    ('YOUTUBE', 'brand identity'),
    ('SPACEX', 'brand identity'),
    ('TESLA', 'brand identity'),
    ('ELONMUSK', 'public figure identity'),
    ('ELON_MUSK', 'public figure identity'),
    ('MRBEAST', 'public figure identity'),
    ('NIKE', 'brand identity'),
    ('ADIDAS', 'brand identity')
on conflict (handle) do nothing;

create or replace function private.enforce_handle_policy()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_normalized text := upper(regexp_replace(trim(new.handle), '^@', ''));
begin
    if exists (
        select 1
        from private.reserved_handles
        where handle = v_normalized
    ) then
        raise exception 'HANDLE_RESERVED';
    end if;
    return new;
end;
$$;

drop trigger if exists enforce_handle_policy on public.profiles;
create trigger enforce_handle_policy
before insert or update of handle on public.profiles
for each row execute function private.enforce_handle_policy();
