create table public.tester_interest (
    id uuid primary key default gen_random_uuid(),
    email text not null,
    name text,
    device text not null default 'Android phone',
    source text not null default 'one_public_spectator',
    created_at timestamptz not null default now(),
    contacted_at timestamptz,
    unique (email)
);

alter table public.tester_interest enable row level security;

create policy "public can request closed-test access"
on public.tester_interest
for insert
to anon, authenticated
with check (
    char_length(email) between 5 and 254
    and email ~* '^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$'
    and char_length(coalesce(name, '')) <= 80
    and char_length(device) <= 80
    and source = 'one_public_spectator'
);

revoke all on public.tester_interest from public;
grant insert on public.tester_interest to anon, authenticated;
