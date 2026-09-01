begin;

select plan(5);

select has_table('public', 'reigns', 'reigns table exists');
select has_function('public', 'take_one', array['uuid', 'bigint', 'uuid'], 'atomic takeover function exists');
select has_function('public', 'get_one_state', array[]::text[], 'public state function exists');
select has_index('public', 'reigns', 'one_current_reign', 'only one current reign is enforced');
select ok((select count(*) = 1 from public.reigns where ended_at is null), 'exactly one live reign is seeded');

select * from finish();
rollback;
