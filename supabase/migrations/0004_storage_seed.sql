-- Storage buckets, access policies and initial catalogue data.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
    ('avatars', 'avatars', true, 2097152, array['image/jpeg', 'image/png', 'image/webp']),
    ('team-logos', 'team-logos', true, 2097152, array['image/jpeg', 'image/png', 'image/webp']),
    ('tournament-banners', 'tournament-banners', true, 5242880, array['image/jpeg', 'image/png', 'image/webp']),
    ('result-evidence', 'result-evidence', false, 10485760, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

-- avatars: public read, owner write under <uid>/...
drop policy if exists avatars_public_read on storage.objects;
create policy avatars_public_read on storage.objects
    for select using (bucket_id = 'avatars');

drop policy if exists avatars_owner_insert on storage.objects;
create policy avatars_owner_insert on storage.objects
    for insert to authenticated
    with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists avatars_owner_update on storage.objects;
create policy avatars_owner_update on storage.objects
    for update to authenticated
    using (bucket_id = 'avatars' and owner = auth.uid())
    with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists avatars_owner_delete on storage.objects;
create policy avatars_owner_delete on storage.objects
    for delete to authenticated
    using (bucket_id = 'avatars' and owner = auth.uid());

-- team logos: public read, team owner write under <team_id>/...
drop policy if exists team_logos_public_read on storage.objects;
create policy team_logos_public_read on storage.objects
    for select using (bucket_id = 'team-logos');

drop policy if exists team_logos_owner_insert on storage.objects;
create policy team_logos_owner_insert on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'team-logos'
        and exists (
            select 1 from public.teams t
            where t.id::text = (storage.foldername(name))[1] and t.owner_id = auth.uid()
        )
    );

drop policy if exists team_logos_owner_update on storage.objects;
create policy team_logos_owner_update on storage.objects
    for update to authenticated
    using (bucket_id = 'team-logos' and owner = auth.uid())
    with check (
        bucket_id = 'team-logos'
        and exists (
            select 1 from public.teams t
            where t.id::text = (storage.foldername(name))[1] and t.owner_id = auth.uid()
        )
    );

-- tournament banners: public read, admin write
drop policy if exists banners_public_read on storage.objects;
create policy banners_public_read on storage.objects
    for select using (bucket_id = 'tournament-banners');

drop policy if exists banners_admin_insert on storage.objects;
create policy banners_admin_insert on storage.objects
    for insert to authenticated with check (bucket_id = 'tournament-banners' and public.is_admin());

-- result evidence: private, owner upload, participants/admin read
drop policy if exists evidence_owner_insert on storage.objects;
create policy evidence_owner_insert on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'result-evidence'
        and (storage.foldername(name))[1] = auth.uid()::text
    );

drop policy if exists evidence_read on storage.objects;
create policy evidence_read on storage.objects
    for select to authenticated
    using (
        bucket_id = 'result-evidence'
        and (
            owner = auth.uid()
            or public.is_admin()
            or exists (
                select 1
                from public.result_evidence e
                join public.match_results r on r.id = e.result_id
                where e.storage_path = storage.objects.name
                  and public.is_match_participant(r.match_id, auth.uid())
            )
        )
    );

drop policy if exists evidence_owner_delete on storage.objects;
create policy evidence_owner_delete on storage.objects
    for delete to authenticated
    using (bucket_id = 'result-evidence' and owner = auth.uid());

-- ---------------------------------------------------------------------------
-- Feature flags (paid features default to OFF until approved)
-- ---------------------------------------------------------------------------

insert into public.app_config (key, value, description) values
    ('free_tournaments_enabled', 'true'::jsonb, 'Free entry tournaments'),
    ('paid_tournaments_enabled', 'false'::jsonb, 'Paid entry tournaments (requires compliance approval)'),
    ('payment_orders_enabled', 'false'::jsonb, 'Payment order creation'),
    ('refunds_enabled', 'false'::jsonb, 'Refund processing'),
    ('payouts_enabled', 'false'::jsonb, 'Prize payouts'),
    ('referrals_enabled', 'true'::jsonb, 'Referral code sharing (rewards require server verification)'),
    ('waitlist_enabled', 'true'::jsonb, 'Waitlist when a tournament is full'),
    ('support_email', '"support@hmxarena.app"'::jsonb, 'Public support address'),
    ('terms_version', '"2026-01"'::jsonb, 'Current legal document version')
on conflict (key) do nothing;

-- ---------------------------------------------------------------------------
-- Tournament catalogue
-- ---------------------------------------------------------------------------

insert into public.tournaments
    (slug, title, game, mode, entry_type, entry_fee_paise, prize_pool_paise, max_slots,
     min_team_size, max_team_size, status, registration_opens_at, registration_closes_at,
     starts_at, banner_url, description, map, is_featured)
values
    ('hmx-pro-cup', 'HMX Pro Cup', 'free_fire', 'squad', 'free', 0, 1000000, 25, 4, 4,
     'registration_open', now() - interval '1 day', now() + interval '1 day', now() + interval '2 days',
     null, 'The flagship HMX Arena squad cup. Four players, three matches, one champion.', 'Bermuda', true),
    ('free-fire-daily-cup', 'Free Fire Daily Cup', 'free_fire', 'squad', 'free', 0, 100000, 48, 4, 4,
     'registration_open', now() - interval '2 days', now() + interval '6 hours', now() + interval '1 day',
     null, 'Daily open squad tournament. Check in early, room details drop before the match.', 'Purgatory', true),
    ('solo-rush', 'Solo Rush', 'free_fire', 'solo', 'free', 0, 200000, 50, 1, 1,
     'registration_open', now() - interval '3 days', now() + interval '12 hours', now() + interval '2 days',
     null, 'Solo survival showdown. Every kill counts toward the leaderboard.', 'Kalahari', false),
    ('clash-squad-cup', 'Clash Squad Cup', 'free_fire', 'clash_squad', 'free', 0, 500000, 64, 4, 4,
     'registration_open', now() - interval '1 day', now() + interval '2 days', now() + interval '3 days',
     null, 'Fast 4v4 clash squad bracket. Bring your best four.', 'Bermuda Remastered', false),
    ('hmx-weekend-live', 'HMX Weekend Live', 'free_fire', 'squad', 'free', 0, 300000, 16, 4, 4,
     'live', now() - interval '6 days', now() - interval '2 days', now() - interval '2 hours',
     null, 'Invitational weekend series, live now.', 'Bermuda', false),
    ('hmx-launch-cup', 'HMX Launch Cup', 'free_fire', 'squad', 'free', 0, 400000, 16, 4, 4,
     'completed', now() - interval '20 days', now() - interval '14 days', now() - interval '12 days',
     null, 'Our opening tournament. Results are final.', 'Bermuda', false)
on conflict (slug) do nothing;

insert into public.tournament_eligibility (tournament_id, min_level, min_points, requires_game_account, allowed_regions)
select id, 1, 0, true, array['IN']::text[] from public.tournaments
on conflict (tournament_id) do nothing;

insert into public.tournament_rules (tournament_id, position, title, body)
select t.id, v.position, v.title, v.body
from public.tournaments t
cross join (values
    (1, 'Fair play', 'Any use of hacks, mods, emulators or teaming is an instant disqualification.'),
    (2, 'Roster lock', 'Your registered roster is final. Substitutes must be listed before registration closes.'),
    (3, 'Check-in', 'Check in inside the match lobby before the countdown ends. Late teams forfeit their slot.'),
    (4, 'Evidence', 'Screenshot your final placement and submit it with your result. Unverified results cannot be scored.'),
    (5, 'Sportsmanship', 'Abusive behaviour in chat or voice results in a warning, then removal.')
) as v(position, title, body)
on conflict (tournament_id, position) do nothing;

insert into public.matches (tournament_id, round, name, status, scheduled_at, check_in_opens_at, map, mode, max_teams)
select t.id, 1, 'Match 1', 'scheduled', t.starts_at, t.starts_at - interval '30 minutes', t.map, t.mode::text, t.max_slots
from public.tournaments t
where t.status in ('registration_open', 'live')
  and not exists (select 1 from public.matches m where m.tournament_id = t.id);

-- ---------------------------------------------------------------------------
-- Announcements
-- ---------------------------------------------------------------------------

insert into public.announcements (title, body, is_published, published_at)
select v.title, v.body, true, now() - v.age
from (values
    ('Welcome to HMX Arena', 'Free Fire tournaments, verified results and a fair leaderboard. Create your profile and join your first cup.', interval '2 days'),
    ('Room details before every match', 'Room ID and password are released inside the match lobby. Only registered participants can see them.', interval '1 day'),
    ('Payment features are coming later', 'Paid entries, wallet top-ups and payouts stay disabled until compliance approval. Free tournaments are unaffected.', interval '6 hours')
) as v(title, body, age)
where not exists (select 1 from public.announcements a where a.title = v.title);
