begin;
select plan(4);

insert into public.profiles(id, handle, initials, revenge_tickets)
values
  ('10000000-0000-0000-0000-000000000001', '@RACER_A', 'RA', 3),
  ('10000000-0000-0000-0000-000000000002', '@RACER_B', 'RB', 3);

insert into public.messages(id, author_id, text, status)
values
  ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'RACER A WON.', 'approved'),
  ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', 'RACER B WON.', 'approved');

select set_config('request.jwt.claims', '{"sub":"10000000-0000-0000-0000-000000000001","role":"authenticated"}', true);
create temporary table race_result as
select public.take_one(
  '20000000-0000-0000-0000-000000000001',
  (select sequence from public.reigns where ended_at is null),
  '30000000-0000-0000-0000-000000000001'
) result;

select ok((select (result->>'ok')::boolean from race_result), 'first racer atomically takes ONE');
select is((select owner_id from public.reigns where ended_at is null), '10000000-0000-0000-0000-000000000001'::uuid, 'first racer is the only owner');

select set_config('request.jwt.claims', '{"sub":"10000000-0000-0000-0000-000000000002","role":"authenticated"}', true);
select is(
  (public.take_one(
    '20000000-0000-0000-0000-000000000002',
    (select expected_sequence from public.take_requests where id = '30000000-0000-0000-0000-000000000001'),
    '30000000-0000-0000-0000-000000000002'
  )->>'code'),
  'STALE_REIGN',
  'second racer loses against the stale sequence'
);
select is((select revenge_tickets from public.profiles where id = '10000000-0000-0000-0000-000000000002'), 3, 'stale-race loser spends no ticket');

select * from finish();
rollback;
