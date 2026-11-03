-- Fixes after the first RLS regression run:
--  * own contact details must not be readable by other signed-in users,
--  * demo schedule extended so seeded tournaments stay joinable,
--  * a stable live match with released credentials.

-- Profiles: remove the broad authenticated column grant. Own contact details
-- are returned only by public.my_profile().
revoke select (onboarding_completed, email, phone, referral_code, referred_by, deleted_at)
    on public.profiles from authenticated;

-- Keep the catalogue fresh relative to the current time.
update public.tournaments
set status = 'registration_open',
    registration_opens_at = now() - interval '1 day',
    registration_closes_at = now() + interval '10 days',
    starts_at = now() + interval '11 days'
where slug = 'hmx-pro-cup';

update public.tournaments
set status = 'registration_open',
    registration_opens_at = now() - interval '2 days',
    registration_closes_at = now() + interval '7 days',
    starts_at = now() + interval '8 days'
where slug in ('free-fire-daily-cup', 'solo-rush');

update public.tournaments
set status = 'registration_open',
    registration_opens_at = now() - interval '1 day',
    registration_closes_at = now() + interval '12 days',
    starts_at = now() + interval '13 days'
where slug = 'clash-squad-cup';

update public.tournaments
set status = 'live',
    registration_opens_at = now() - interval '6 days',
    registration_closes_at = now() - interval '2 days',
    starts_at = now() - interval '1 hour'
where slug = 'hmx-weekend-live';

update public.tournaments
set status = 'completed',
    registration_opens_at = now() - interval '20 days',
    registration_closes_at = now() - interval '14 days',
    starts_at = now() - interval '12 days'
where slug = 'hmx-launch-cup';

update public.matches m
set scheduled_at = t.starts_at,
    check_in_opens_at = t.starts_at - interval '30 minutes',
    status = case when t.status = 'live' then 'live'::public.match_status else 'scheduled'::public.match_status end,
    started_at = case when t.status = 'live' then t.starts_at else null end
from public.tournaments t
where t.id = m.tournament_id;

-- Release credentials for the live demo match (still gated by participation).
insert into public.match_credentials (match_id, room_id, room_password, released_at, visible_until)
select m.id, '846291', 'HMX2026', now() - interval '10 minutes', now() + interval '3 hours'
from public.matches m
join public.tournaments t on t.id = m.tournament_id
where t.slug = 'hmx-weekend-live'
on conflict (match_id) do update
set room_id = excluded.room_id,
    room_password = excluded.room_password,
    released_at = excluded.released_at,
    visible_until = excluded.visible_until;
