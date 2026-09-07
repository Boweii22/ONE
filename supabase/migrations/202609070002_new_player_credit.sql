-- Give a new player one real chance to experience an instant cooldown skip
-- without making the purchasable currency feel disposable. Existing balances,
-- including purchased credits, are deliberately left untouched.
alter table public.profiles alter column revenge_tickets set default 1;
