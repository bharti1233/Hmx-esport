-- RLS / privilege regression suite.
-- Run against a development database. Creates two throwaway auth users,
-- asserts the security contract, then removes them (cascade).

create temp table if not exists rls_results (
    n serial primary key,
    check_name text not null,
    outcome text not null,
    detail text
);
grant all on rls_results to authenticated, anon;
grant usage, select on sequence rls_results_n_seq to authenticated, anon;

insert into auth.users (id, instance_id, aud, role, email, raw_app_meta_data, raw_user_meta_data)
values
    ('11111111-1111-4111-8111-111111111111', '00000000-0000-0000-0000-000000000000',
     'authenticated', 'authenticated', 'rls-a@hmxarena.test',
     '{"provider":"email","providers":["email"]}'::jsonb, '{"username":"rls_alpha"}'::jsonb),
    ('22222222-2222-4222-8222-222222222222', '00000000-0000-0000-0000-000000000000',
     'authenticated', 'authenticated', 'rls-b@hmxarena.test',
     '{"provider":"email","providers":["email"]}'::jsonb, '{"username":"rls_beta"}'::jsonb)
on conflict (id) do nothing;

delete from public.registrations
where user_id in ('11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222');

insert into public.tournaments
    (slug, title, game, mode, entry_type, prize_pool_paise, max_slots, min_team_size, max_team_size,
     status, registration_opens_at, registration_closes_at, starts_at, description, map)
values
    ('rls-test-cup', 'RLS Test Cup', 'free_fire', 'solo', 'free', 0, 10, 1, 1,
     'registration_open', now() - interval '1 hour', now() + interval '1 day', now() + interval '2 days',
     'Throwaway tournament used by the RLS regression suite.', 'Bermuda')
on conflict (slug) do nothing;

insert into public.tournament_eligibility (tournament_id, min_level, min_points, requires_game_account, allowed_regions)
select id, 1, 0, true, array['IN']::text[] from public.tournaments where slug = 'rls-test-cup'
on conflict (tournament_id) do nothing;

insert into public.matches (tournament_id, round, name, status, scheduled_at, check_in_opens_at, map, mode, max_teams)
select t.id, 1, 'RLS Match', 'scheduled', t.starts_at, t.starts_at - interval '30 minutes', t.map, 'solo', 10
from public.tournaments t
where t.slug = 'rls-test-cup'
  and not exists (select 1 from public.matches m where m.tournament_id = t.id);

set role authenticated;
select set_config('request.jwt.claims', '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', false);

insert into rls_results (check_name, outcome)
select 'A reads own profile', case when count(*) = 1 then 'pass' else 'FAIL' end
from public.profiles where id = '11111111-1111-4111-8111-111111111111';

insert into rls_results (check_name, outcome)
select 'A reads B public username', case when count(*) = 1 then 'pass' else 'FAIL' end
from public.profiles where id = '22222222-2222-4222-8222-222222222222' and username = 'rls_beta';

do $$
declare
    v_email text;
begin
    begin
        select email into v_email from public.profiles
        where id = '22222222-2222-4222-8222-222222222222';
        insert into rls_results (check_name, outcome) values ('cross-user email read', 'FAIL: allowed');
    exception when insufficient_privilege then
        insert into rls_results (check_name, outcome) values ('cross-user email read', 'pass: denied');
    end;
end $$;

do $$
declare
    v_rows integer;
begin
    update public.profiles set display_name = 'hacked'
    where id = '22222222-2222-4222-8222-222222222222';
    get diagnostics v_rows = row_count;
    insert into rls_results (check_name, outcome)
    values ('A updates B profile', case when v_rows = 0 then 'pass: 0 rows' else 'FAIL: ' || v_rows end);

    update public.profiles set display_name = 'Alpha'
    where id = '11111111-1111-4111-8111-111111111111';
    get diagnostics v_rows = row_count;
    insert into rls_results (check_name, outcome)
    values ('A updates own profile', case when v_rows = 1 then 'pass' else 'FAIL: ' || v_rows end);
end $$;

do $$
begin
    begin
        insert into public.team_members (team_id, user_id, role)
        select id, '11111111-1111-4111-8111-111111111111', 'owner' from public.teams limit 1;
        insert into rls_results (check_name, outcome) values ('direct team_members insert', 'FAIL: allowed');
    exception when insufficient_privilege then
        insert into rls_results (check_name, outcome) values ('direct team_members insert', 'pass: denied');
    end;
end $$;

do $$
begin
    insert into public.player_game_accounts (user_id, game, ign, game_uid, level)
    values ('11111111-1111-4111-8111-111111111111', 'free_fire', 'AlphaFF', '900000001', 55)
    on conflict (user_id, game) do update set level = 55;
    insert into rls_results (check_name, outcome) values ('own game account upsert', 'pass');
end $$;

do $$
declare
    v_team public.teams;
begin
    begin
        v_team := public.create_team('Alpha Squad', 'ALPH', 'free_fire');
        insert into rls_results (check_name, outcome)
        values ('create_team RPC', case when v_team.id is not null then 'pass' else 'FAIL' end);
    exception when others then
        insert into rls_results (check_name, outcome) values ('create_team RPC', 'FAIL: ' || sqlerrm);
    end;
end $$;

do $$
declare
    v_reg public.registrations;
    v_count integer;
    v_match uuid;
begin
    begin
        v_reg := public.register_for_tournament(
            (select id from public.tournaments where slug = 'rls-test-cup'), null);
        select count(*) into v_count from public.registrations
        where user_id = '11111111-1111-4111-8111-111111111111'
          and tournament_id = (select id from public.tournaments where slug = 'rls-test-cup');
        insert into rls_results (check_name, outcome)
        values ('register solo tournament', case when v_count = 1 then 'pass' else 'FAIL' end);

        select mp.match_id into v_match
        from public.match_participants mp
        join public.registrations r on r.id = mp.registration_id
        where r.user_id = '11111111-1111-4111-8111-111111111111'
        limit 1;
        insert into rls_results (check_name, outcome)
        values ('match participant assigned', case when v_match is not null then 'pass' else 'FAIL' end);
    exception when others then
        insert into rls_results (check_name, outcome)
        values ('register solo tournament', 'FAIL: ' || sqlerrm);
    end;
end $$;

do $$
declare
    v_rows integer;
begin
    begin
        insert into public.registrations (tournament_id, user_id, registration_type)
        values ((select id from public.tournaments limit 1),
                '11111111-1111-4111-8111-111111111111', 'solo');
        insert into rls_results (check_name, outcome) values ('direct registration insert', 'FAIL: allowed');
    exception when insufficient_privilege then
        insert into rls_results (check_name, outcome) values ('direct registration insert', 'pass: denied');
    end;

    select count(*) into v_rows from public.registrations
    where user_id = '22222222-2222-4222-8222-222222222222';
    insert into rls_results (check_name, outcome)
    values ('A cannot list B registrations', case when v_rows = 0 then 'pass' else 'FAIL: ' || v_rows end);

    begin
        select count(*) into v_rows from public.match_credentials;
        insert into rls_results (check_name, outcome)
        values ('direct match_credentials read', case when v_rows = 0 then 'pass: no rows' else 'FAIL: ' || v_rows end);
    exception when insufficient_privilege then
        insert into rls_results (check_name, outcome) values ('direct match_credentials read', 'pass: denied');
    end;
end $$;

do $$
begin
    begin
        update public.wallet_accounts set balance_paise = 99999999
        where user_id = '11111111-1111-4111-8111-111111111111';
        insert into rls_results (check_name, outcome) values ('wallet balance tamper', 'FAIL: allowed');
    exception when insufficient_privilege then
        insert into rls_results (check_name, outcome) values ('wallet balance tamper', 'pass: denied');
    end;
end $$;

reset role;

insert into public.match_credentials (match_id, room_id, room_password, released_at)
select mp.match_id, 'RM-778812', 'hmx-pass', now() - interval '1 minute'
from public.match_participants mp
join public.registrations r on r.id = mp.registration_id
where r.user_id = '11111111-1111-4111-8111-111111111111'
on conflict (match_id) do nothing;

set role authenticated;
select set_config('request.jwt.claims', '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', false);

do $$
declare
    v_room text;
    v_match uuid;
begin
    begin
        select mp.match_id into v_match
        from public.match_participants mp
        join public.registrations r on r.id = mp.registration_id
        where r.user_id = '11111111-1111-4111-8111-111111111111' limit 1;
        select room_id into v_room from public.get_match_credentials(v_match);
        insert into rls_results (check_name, outcome)
        values ('participant reads room credentials', case when v_room = 'RM-778812' then 'pass' else 'FAIL' end);
    exception when others then
        insert into rls_results (check_name, outcome) values ('participant reads room credentials', 'FAIL: ' || sqlerrm);
    end;
end $$;

select set_config('request.jwt.claims', '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', false);

do $$
declare
    v_match uuid;
begin
    begin
        select mp.match_id into v_match
        from public.match_participants mp
        join public.registrations r on r.id = mp.registration_id
        where r.user_id = '11111111-1111-4111-8111-111111111111' limit 1;
        perform * from public.get_match_credentials(v_match);
        insert into rls_results (check_name, outcome) values ('non-participant room credentials', 'FAIL: allowed');
    exception when others then
        insert into rls_results (check_name, outcome)
        values ('non-participant room credentials', 'pass: ' || sqlerrm);
    end;
end $$;

reset role;
set role anon;
select set_config('request.jwt.claims', '{}', false);

insert into rls_results (check_name, outcome)
select 'anon reads public leaderboard', case when count(*) >= 0 then 'pass' else 'FAIL' end
from public.get_leaderboard('player', 'all', null, null, 10, 0);

do $$
begin
    begin
        perform public.register_for_tournament((select id from public.tournaments limit 1), null);
        insert into rls_results (check_name, outcome) values ('anon register_for_tournament', 'FAIL: allowed');
    exception when insufficient_privilege then
        insert into rls_results (check_name, outcome) values ('anon register_for_tournament', 'pass: denied');
    when others then
        insert into rls_results (check_name, outcome) values ('anon register_for_tournament', 'pass: ' || sqlerrm);
    end;
end $$;

reset role;

delete from public.match_credentials
where match_id in (
    select mp.match_id from public.match_participants mp
    join public.registrations r on r.id = mp.registration_id
    where r.user_id in ('11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222')
);

delete from public.tournaments where slug = 'rls-test-cup';

delete from auth.users
where id in ('11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222');

select check_name, outcome, detail from rls_results order by n;
