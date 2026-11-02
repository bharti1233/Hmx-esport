-- HMX Arena server-side business logic, invariants and triggers.

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select exists (
        select 1 from public.user_roles
        where user_id = auth.uid() and role in ('admin', 'moderator')
    );
$$;

create or replace function public.flag(p_key text)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select coalesce((select (value)::boolean from public.app_config where key = p_key), false);
$$;

create or replace function public.notify(
    p_user uuid,
    p_type public.notification_type,
    p_title text,
    p_body text,
    p_data jsonb default '{}'::jsonb
)
returns void
language sql
security definer
set search_path = public, pg_temp
as $$
    insert into public.notifications (user_id, type, title, body, data)
    values (p_user, p_type, p_title, p_body, p_data);
$$;

-- ---------------------------------------------------------------------------
-- New auth user -> profile + wallet + player role
-- ---------------------------------------------------------------------------

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    base_username text;
    final_username text;
    suffix integer := 0;
    new_code text;
begin
    base_username := coalesce(
        nullif(regexp_replace(new.raw_user_meta_data ->> 'username', '[^A-Za-z0-9_]', '', 'g'), ''),
        'player'
    );
    if char_length(base_username) < 3 then
        base_username := base_username || 'hmx';
    end if;
    base_username := left(base_username, 20);
    final_username := base_username;

    while exists (select 1 from public.profiles p where lower(p.username) = lower(final_username)) loop
        suffix := suffix + 1;
        final_username := base_username || suffix::text;
    end loop;

    loop
        new_code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));
        exit when not exists (select 1 from public.profiles p where p.referral_code = new_code);
    end loop;

    insert into public.profiles (id, username, display_name, email, phone, referral_code)
    values (
        new.id,
        final_username,
        coalesce(nullif(new.raw_user_meta_data ->> 'display_name', ''), final_username),
        new.email,
        new.phone,
        new_code
    );

    insert into public.wallet_accounts (user_id) values (new.id) on conflict do nothing;
    insert into public.user_roles (user_id, role) values (new.id, 'player') on conflict do nothing;

    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------
-- Teams
-- ---------------------------------------------------------------------------

create or replace function public.create_team(p_name text, p_tag text, p_game text default 'free_fire')
returns public.teams
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_team public.teams;
    v_uid uuid := auth.uid();
    v_existing uuid;
begin
    if v_uid is null then raise exception 'not_authenticated'; end if;
    if p_name is null or char_length(trim(p_name)) < 3 then raise exception 'invalid_team_name'; end if;
    if p_tag is null or char_length(trim(p_tag)) < 2 then raise exception 'invalid_team_tag'; end if;
    if p_game is null or p_game not in ('free_fire', 'bgmi', 'cod_mobile', 'valorant') then raise exception 'invalid_game'; end if;

    select team_id into v_existing from public.team_members where user_id = v_uid;
    if v_existing is not null then raise exception 'already_in_team'; end if;

    insert into public.teams (name, tag, game, owner_id)
    values (trim(p_name), upper(trim(p_tag)), p_game, v_uid)
    returning * into v_team;

    insert into public.team_members (team_id, user_id, role) values (v_team.id, v_uid, 'owner');

    insert into public.audit_logs (actor_id, action, entity_type, entity_id)
    values (v_uid, 'team.create', 'team', v_team.id);

    return v_team;
end;
$$;

create or replace function public.respond_team_invitation(p_invitation_id uuid, p_accept boolean)
returns public.team_invitations
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_inv public.team_invitations;
    v_uid uuid := auth.uid();
    v_size integer;
begin
    if v_uid is null then raise exception 'not_authenticated'; end if;

    select * into v_inv from public.team_invitations
    where id = p_invitation_id and invited_user_id = v_uid and status = 'pending'
    for update;
    if not found then raise exception 'invitation_not_found'; end if;

    if p_accept then
        if exists (select 1 from public.team_members where user_id = v_uid) then
            raise exception 'already_in_team';
        end if;
        select count(*) into v_size from public.team_members where team_id = v_inv.team_id;
        if v_size >= 5 then raise exception 'team_full'; end if;

        insert into public.team_members (team_id, user_id, role) values (v_inv.team_id, v_uid, 'member');
        update public.team_invitations set status = 'accepted', responded_at = now() where id = v_inv.id returning * into v_inv;
        perform public.notify(v_inv.invited_by, 'account', 'Team invitation accepted', 'A player joined your team.', jsonb_build_object('team_id', v_inv.team_id));
    else
        update public.team_invitations set status = 'declined', responded_at = now() where id = v_inv.id returning * into v_inv;
    end if;

    return v_inv;
end;
$$;

create or replace function public.leave_team(p_team_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_uid uuid := auth.uid();
    v_owner uuid;
begin
    if v_uid is null then raise exception 'not_authenticated'; end if;
    select owner_id into v_owner from public.teams where id = p_team_id;
    if v_owner is null then raise exception 'team_not_found'; end if;
    if v_owner = v_uid then raise exception 'owner_must_transfer'; end if;
    delete from public.team_members where team_id = p_team_id and user_id = v_uid;
end;
$$;

create or replace function public.transfer_team_ownership(p_team_id uuid, p_new_owner uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_uid uuid := auth.uid();
begin
    if v_uid is null then raise exception 'not_authenticated'; end if;
    if not exists (select 1 from public.teams where id = p_team_id and owner_id = v_uid) then
        raise exception 'not_team_owner';
    end if;
    if not exists (select 1 from public.team_members where team_id = p_team_id and user_id = p_new_owner) then
        raise exception 'target_not_member';
    end if;
    update public.teams set owner_id = p_new_owner where id = p_team_id;
    update public.team_members set role = 'member' where team_id = p_team_id and user_id = v_uid;
    update public.team_members set role = 'owner' where team_id = p_team_id and user_id = p_new_owner;
    insert into public.audit_logs (actor_id, action, entity_type, entity_id, metadata)
    values (v_uid, 'team.transfer', 'team', p_team_id, jsonb_build_object('new_owner', p_new_owner));
end;
$$;

-- ---------------------------------------------------------------------------
-- Tournament registration (atomic)
-- ---------------------------------------------------------------------------

create or replace function public.register_for_tournament(
    p_tournament_id uuid,
    p_team_id uuid default null
)
returns public.registrations
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_uid uuid := auth.uid();
    v_t public.tournaments;
    v_elig public.tournament_eligibility;
    v_profile public.profiles;
    v_reg public.registrations;
    v_count integer;
    v_slot integer;
    v_status public.registration_status;
    v_is_team boolean := p_team_id is not null;
    v_member_count integer := 0;
begin
    if v_uid is null then raise exception 'not_authenticated'; end if;

    select * into v_t from public.tournaments where id = p_tournament_id for update;
    if not found then raise exception 'tournament_not_found'; end if;
    if v_t.status <> 'registration_open' then raise exception 'registration_closed'; end if;
    if now() < v_t.registration_opens_at or now() > v_t.registration_closes_at then
        raise exception 'registration_window_closed';
    end if;

    if v_t.entry_type = 'paid' and not public.flag('paid_tournaments_enabled') then
        raise exception 'paid_tournaments_disabled';
    end if;

    select * into v_profile from public.profiles where id = v_uid;
    if v_profile.deleted_at is not null then raise exception 'account_deleted'; end if;

    select * into v_elig from public.tournament_eligibility where tournament_id = v_t.id;
    if found then
        if v_elig.min_points > 0 and v_profile.points < v_elig.min_points then
            raise exception 'eligibility_points';
        end if;
        if v_elig.min_level is not null and v_elig.min_level > 0 and not exists (
            select 1 from public.player_game_accounts
            where user_id = v_uid and game = v_t.game and coalesce(level, 0) >= v_elig.min_level
        ) then
            raise exception 'eligibility_level';
        end if;
        if v_elig.requires_game_account and not exists (
            select 1 from public.player_game_accounts where user_id = v_uid and game = v_t.game
        ) then
            raise exception 'game_account_required';
        end if;
        if v_elig.allowed_regions is not null and not (v_profile.region = any (v_elig.allowed_regions)) then
            raise exception 'eligibility_region';
        end if;
    end if;

    if v_is_team then
        if v_t.min_team_size <= 1 then raise exception 'solo_only_tournament'; end if;
        if not exists (select 1 from public.teams where id = p_team_id and game = v_t.game) then
            raise exception 'team_game_mismatch';
        end if;
        if not exists (select 1 from public.team_members where team_id = p_team_id and user_id = v_uid) then
            raise exception 'not_team_member';
        end if;
        select count(*) into v_member_count
        from public.team_members tm
        join public.player_game_accounts pga on pga.user_id = tm.user_id and pga.game = v_t.game
        where tm.team_id = p_team_id;
        if v_member_count < v_t.min_team_size then raise exception 'team_too_small'; end if;
        if v_member_count > v_t.max_team_size then raise exception 'team_too_large'; end if;
    else
        if v_t.min_team_size > 1 then raise exception 'team_required'; end if;
        if not exists (
            select 1 from public.player_game_accounts where user_id = v_uid and game = v_t.game
        ) then
            raise exception 'game_account_required';
        end if;
        v_member_count := 1;
    end if;

    if v_t.slots_filled < v_t.max_slots then
        v_status := 'confirmed';
        v_slot := v_t.slots_filled + 1;
        update public.tournaments set slots_filled = slots_filled + 1 where id = v_t.id;
    elsif public.flag('waitlist_enabled') then
        v_status := 'waitlisted';
        v_slot := null;
    else
        raise exception 'tournament_full';
    end if;

    if exists (select 1 from public.registrations where tournament_id = v_t.id and user_id = v_uid) then
        update public.registrations
        set status = v_status, slot_no = v_slot, team_id = p_team_id,
            registration_type = case when v_is_team then 'team' else 'solo' end,
            cancelled_at = null, created_at = now()
        where tournament_id = v_t.id and user_id = v_uid
        returning * into v_reg;
        delete from public.registration_members where registration_id = v_reg.id;
    else
        insert into public.registrations (tournament_id, user_id, team_id, registration_type, status, slot_no)
        values (v_t.id, v_uid, p_team_id, case when v_is_team then 'team' else 'solo' end, v_status, v_slot)
        returning * into v_reg;
    end if;

    if v_is_team then
        insert into public.registration_members (registration_id, user_id, ign, game_uid, is_captain)
        select v_reg.id, tm.user_id, pga.ign, pga.game_uid, (tm.user_id = v_uid)
        from public.team_members tm
        join public.player_game_accounts pga on pga.user_id = tm.user_id and pga.game = v_t.game
        where tm.team_id = p_team_id;
    else
        insert into public.registration_members (registration_id, user_id, ign, game_uid, is_captain)
        select v_reg.id, v_uid, pga.ign, pga.game_uid, true
        from public.player_game_accounts pga
        where pga.user_id = v_uid and pga.game = v_t.game
        limit 1;
    end if;

    if v_status = 'confirmed' then
        insert into public.match_participants (match_id, registration_id, team_id, slot)
        select m.id,
               v_reg.id,
               v_reg.team_id,
               coalesce((select max(mp.slot) from public.match_participants mp where mp.match_id = m.id), 0) + 1
        from public.matches m
        where m.tournament_id = v_t.id
          and m.status = 'scheduled'
          and (m.max_teams is null or (select count(*) from public.match_participants mp where mp.match_id = m.id) < m.max_teams)
        order by m.scheduled_at
        limit 1;
    end if;

    perform public.notify(
        v_uid,
        'registration',
        case when v_status = 'confirmed' then 'Registration confirmed' else 'Added to waitlist' end,
        v_t.title,
        jsonb_build_object('tournament_id', v_t.id, 'registration_id', v_reg.id, 'status', v_status)
    );

    return v_reg;
end;
$$;

create or replace function public.cancel_registration(p_registration_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_uid uuid := auth.uid();
    v_reg public.registrations;
    v_t public.tournaments;
    v_next public.registrations;
    v_promoted_slot integer;
begin
    if v_uid is null then raise exception 'not_authenticated'; end if;
    select * into v_reg from public.registrations where id = p_registration_id for update;
    if not found then raise exception 'registration_not_found'; end if;
    if v_reg.user_id <> v_uid and not public.is_admin() then raise exception 'not_allowed'; end if;
    if v_reg.status = 'cancelled' then return; end if;

    select * into v_t from public.tournaments where id = v_reg.tournament_id for update;

    update public.registrations set status = 'cancelled', cancelled_at = now() where id = v_reg.id;

    if v_reg.status = 'confirmed' and v_t.status in ('registration_open', 'registration_closed') then
        update public.tournaments set slots_filled = greatest(slots_filled - 1, 0) where id = v_t.id;

        select * into v_next from public.registrations
        where tournament_id = v_t.id and status = 'waitlisted'
        order by created_at
        limit 1
        for update;

        if found then
            v_promoted_slot := v_t.slots_filled;
            update public.registrations set status = 'confirmed', slot_no = v_promoted_slot where id = v_next.id;
            update public.tournaments set slots_filled = slots_filled + 1 where id = v_t.id;
            insert into public.match_participants (match_id, registration_id, team_id, slot)
            select m.id, v_next.id, v_next.team_id,
                   coalesce((select max(mp.slot) from public.match_participants mp where mp.match_id = m.id), 0) + 1
            from public.matches m
            where m.tournament_id = v_t.id and m.status = 'scheduled'
            order by m.scheduled_at limit 1;
            perform public.notify(v_next.user_id, 'registration', 'You are in!', 'A slot opened up in ' || v_t.title,
                jsonb_build_object('tournament_id', v_t.id, 'registration_id', v_next.id));
        end if;
    end if;

    delete from public.match_participants where registration_id = v_reg.id;
end;
$$;

-- ---------------------------------------------------------------------------
-- Match credentials, results and disputes
-- ---------------------------------------------------------------------------

create or replace function public.is_match_participant(p_match_id uuid, p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select exists (
        select 1
        from public.match_participants mp
        join public.registrations r on r.id = mp.registration_id
        where mp.match_id = p_match_id
          and (
              r.user_id = p_user_id
              or exists (
                  select 1 from public.registration_members rm
                  where rm.registration_id = r.id and rm.user_id = p_user_id
              )
          )
    );
$$;

create or replace function public.get_match_credentials(p_match_id uuid)
returns table (room_id text, room_password text, released_at timestamptz, visible_until timestamptz)
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
declare
    v_uid uuid := auth.uid();
begin
    if v_uid is null then raise exception 'not_authenticated'; end if;
    if not public.is_match_participant(p_match_id, v_uid) then
        raise exception 'not_match_participant';
    end if;
    return query
        select c.room_id, c.room_password, c.released_at, c.visible_until
        from public.match_credentials c
        where c.match_id = p_match_id
          and now() >= c.released_at
          and (c.visible_until is null or now() <= c.visible_until);
end;
$$;

create or replace function public.submit_match_result(
    p_match_id uuid,
    p_placement integer,
    p_kills integer,
    p_evidence_path text,
    p_evidence_mime text,
    p_evidence_size bigint,
    p_notes text default null
)
returns public.match_results
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_uid uuid := auth.uid();
    v_reg_id uuid;
    v_result public.match_results;
begin
    if v_uid is null then raise exception 'not_authenticated'; end if;
    if p_placement < 1 then raise exception 'invalid_placement'; end if;
    if p_kills < 0 then raise exception 'invalid_kills'; end if;
    if p_evidence_path is null or p_evidence_path not like v_uid::text || '/%' then
        raise exception 'invalid_evidence_path';
    end if;

    select mp.registration_id into v_reg_id
    from public.match_participants mp
    join public.registrations r on r.id = mp.registration_id
    where mp.match_id = p_match_id
      and (r.user_id = v_uid or exists (
          select 1 from public.registration_members rm
          where rm.registration_id = r.id and rm.user_id = v_uid
      ))
    limit 1;

    if v_reg_id is null then raise exception 'not_match_participant'; end if;

    insert into public.match_results (match_id, registration_id, submitted_by, placement, kills, points, notes)
    values (p_match_id, v_reg_id, v_uid, p_placement, p_kills, 0, p_notes)
    on conflict (match_id, registration_id) do update
        set placement = excluded.placement,
            kills = excluded.kills,
            notes = excluded.notes,
            submitted_by = excluded.submitted_by,
            status = 'pending',
            verified_by = null,
            verified_at = null
        where public.match_results.status in ('pending', 'rejected')
    returning * into v_result;

    if not found then raise exception 'result_already_finalized'; end if;

    insert into public.result_evidence (result_id, storage_path, mime_type, size_bytes, uploaded_by)
    values (v_result.id, p_evidence_path, p_evidence_mime, p_evidence_size, v_uid);

    perform public.notify(v_uid, 'result', 'Result submitted', 'Your result is awaiting verification.',
        jsonb_build_object('match_id', p_match_id, 'result_id', v_result.id));

    return v_result;
end;
$$;

create or replace function public.recalculate_player_stats()
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    -- ponytail: full-table recompute; swap for incremental triggers if the player base grows past ~50k.
    update public.profiles set points = 0, matches_played = 0, wins = 0;

    with verified as (
        select rr.points, rr.placement, rr.registration_id
        from public.match_results rr
        where rr.status = 'verified'
    ),
    attributed as (
        select rm.user_id as user_id, v.points, v.placement
        from verified v
        join public.registration_members rm on rm.registration_id = v.registration_id
        where rm.user_id is not null
        union all
        select r.user_id, v.points, v.placement
        from verified v
        join public.registrations r on r.id = v.registration_id
        where not exists (
            select 1 from public.registration_members m where m.registration_id = v.registration_id
        )
    )
    update public.profiles p
    set points = agg.points,
        matches_played = agg.matches,
        wins = agg.wins,
        updated_at = now()
    from (
        select user_id,
               coalesce(sum(points), 0)::integer as points,
               count(*)::integer as matches,
               count(*) filter (where placement = 1)::integer as wins
        from attributed
        group by user_id
    ) agg
    where p.id = agg.user_id;
end;
$$;

create or replace function public.verify_match_result(
    p_result_id uuid,
    p_status public.result_status,
    p_points integer default 0,
    p_notes text default null
)
returns public.match_results
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_result public.match_results;
    v_match uuid;
begin
    if not public.is_admin() then raise exception 'not_admin'; end if;
    if p_status not in ('verified', 'rejected') then raise exception 'invalid_status'; end if;

    update public.match_results
    set status = p_status,
        points = greatest(p_points, 0),
        notes = coalesce(p_notes, notes),
        verified_by = auth.uid(),
        verified_at = now()
    where id = p_result_id
    returning * into v_result;

    if not found then raise exception 'result_not_found'; end if;
    v_match := v_result.match_id;

    update public.match_participants
    set points = v_result.points,
        placement = v_result.placement,
        kills = v_result.kills,
        status = p_status::text
    where match_id = v_match and registration_id = v_result.registration_id;

    if not exists (select 1 from public.match_results where match_id = v_match and status = 'pending') then
        update public.matches set status = 'completed', ended_at = now() where id = v_match;
    end if;

    perform public.recalculate_player_stats();

    perform public.notify(v_result.submitted_by, 'result', 'Result reviewed',
        case when p_status = 'verified' then 'Your result has been verified.' else 'Your result was rejected.' end,
        jsonb_build_object('match_id', v_match, 'result_id', p_result_id, 'status', p_status));

    insert into public.audit_logs (actor_id, action, entity_type, entity_id, metadata)
    values (auth.uid(), 'result.verify', 'match_result', p_result_id,
            jsonb_build_object('status', p_status, 'points', p_points));

    return v_result;
end;
$$;

create or replace function public.raise_dispute(p_result_id uuid, p_reason text)
returns public.result_disputes
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_uid uuid := auth.uid();
    v_result public.match_results;
    v_dispute public.result_disputes;
begin
    if v_uid is null then raise exception 'not_authenticated'; end if;
    if p_reason is null or char_length(trim(p_reason)) < 10 then raise exception 'invalid_reason'; end if;

    select * into v_result from public.match_results where id = p_result_id;
    if not found then raise exception 'result_not_found'; end if;
    if not public.is_match_participant(v_result.match_id, v_uid) then raise exception 'not_match_participant'; end if;
    if v_result.status = 'verified' then raise exception 'result_finalized'; end if;

    insert into public.result_disputes (result_id, raised_by, reason)
    values (p_result_id, v_uid, trim(p_reason))
    returning * into v_dispute;

    update public.match_results set status = 'disputed' where id = p_result_id;
    perform public.notify(v_result.submitted_by, 'dispute', 'Result disputed',
        'A dispute was raised on a match result.',
        jsonb_build_object('result_id', p_result_id, 'dispute_id', v_dispute.id));

    return v_dispute;
end;
$$;

create or replace function public.resolve_dispute(p_dispute_id uuid, p_status public.dispute_status, p_resolution text)
returns public.result_disputes
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_dispute public.result_disputes;
begin
    if not public.is_admin() then raise exception 'not_admin'; end if;
    if p_status not in ('resolved', 'rejected') then raise exception 'invalid_status'; end if;

    update public.result_disputes
    set status = p_status, resolution = p_resolution, resolved_at = now()
    where id = p_dispute_id
    returning * into v_dispute;

    if not found then raise exception 'dispute_not_found'; end if;

    if p_status = 'resolved' then
        update public.match_results set status = 'pending' where id = v_dispute.result_id;
    end if;

    perform public.notify(v_dispute.raised_by, 'dispute', 'Dispute updated',
        coalesce(p_resolution, 'Your dispute has been reviewed.'),
        jsonb_build_object('dispute_id', p_dispute_id, 'status', p_status));

    return v_dispute;
end;
$$;

create or replace function public.set_match_credentials(
    p_match_id uuid,
    p_room_id text,
    p_room_password text,
    p_released_at timestamptz default now(),
    p_visible_until timestamptz default null
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if not public.is_admin() then raise exception 'not_admin'; end if;
    insert into public.match_credentials (match_id, room_id, room_password, released_at, visible_until)
    values (p_match_id, p_room_id, p_room_password, p_released_at, p_visible_until)
    on conflict (match_id) do update
        set room_id = excluded.room_id,
            room_password = excluded.room_password,
            released_at = excluded.released_at,
            visible_until = excluded.visible_until,
            updated_at = now();

    perform public.notify(
        r.user_id, 'match', 'Room credentials released', 'Match room ID and password are now available.',
        jsonb_build_object('match_id', p_match_id)
    )
    from public.registrations r
    join public.match_participants mp on mp.registration_id = r.id
    where mp.match_id = p_match_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- Leaderboards
-- ---------------------------------------------------------------------------

create or replace function public.get_leaderboard(
    p_scope text default 'player',
    p_period text default 'all',
    p_game text default null,
    p_tournament_id uuid default null,
    p_limit integer default 50,
    p_offset integer default 0
)
returns table (
    rank_no bigint,
    entity_id uuid,
    name text,
    avatar_url text,
    subtitle text,
    points bigint,
    matches bigint,
    wins bigint
)
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
begin
    if p_scope = 'team' then
        return query
        with source as (
            select rr.points, rr.placement, mp.team_id
            from public.match_results rr
            join public.matches m on m.id = rr.match_id
            join public.tournaments t on t.id = m.tournament_id
            join public.match_participants mp
              on mp.match_id = rr.match_id and mp.registration_id = rr.registration_id
            where rr.status = 'verified'
              and mp.team_id is not null
              and (p_game is null or t.game = p_game)
              and (p_tournament_id is null or t.id = p_tournament_id)
              and (p_period <> 'monthly' or rr.created_at >= date_trunc('month', now()))
        )
        select row_number() over (order by sum(s.points) desc, count(*) desc, tm.name),
               tm.id,
               tm.name,
               tm.logo_url,
               tm.tag,
               sum(s.points)::bigint,
               count(*)::bigint,
               count(*) filter (where s.placement = 1)::bigint
        from source s
        join public.teams tm on tm.id = s.team_id
        group by tm.id, tm.name, tm.logo_url, tm.tag
        order by sum(s.points) desc, count(*) desc, tm.name
        limit greatest(p_limit, 0) offset greatest(p_offset, 0);
    else
        return query
        with source as (
            select rr.points, rr.placement, rr.registration_id
            from public.match_results rr
            join public.matches m on m.id = rr.match_id
            join public.tournaments t on t.id = m.tournament_id
            where rr.status = 'verified'
              and (p_game is null or t.game = p_game)
              and (p_tournament_id is null or t.id = p_tournament_id)
              and (p_period <> 'monthly' or rr.created_at >= date_trunc('month', now()))
        ),
        attributed as (
            select rm.user_id, s.points, s.placement
            from source s
            join public.registration_members rm on rm.registration_id = s.registration_id
            where rm.user_id is not null
            union all
            select r.user_id, s.points, s.placement
            from source s
            join public.registrations r on r.id = s.registration_id
            where not exists (
                select 1 from public.registration_members m2 where m2.registration_id = s.registration_id
            )
        )
        select row_number() over (order by sum(a.points) desc, count(*) desc, p.username),
               p.id,
               p.username,
               p.avatar_url,
               coalesce(nullif(p.display_name, ''), p.username),
               sum(a.points)::bigint,
               count(*)::bigint,
               count(*) filter (where a.placement = 1)::bigint
        from attributed a
        join public.profiles p on p.id = a.user_id
        where p.deleted_at is null
        group by p.id, p.username, p.avatar_url, p.display_name
        order by sum(a.points) desc, count(*) desc, p.username
        limit greatest(p_limit, 0) offset greatest(p_offset, 0);
    end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- Referrals, account, notifications, support
-- ---------------------------------------------------------------------------

create or replace function public.claim_referral(p_code text)
returns public.referral_claims
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_uid uuid := auth.uid();
    v_referrer public.profiles;
    v_me public.profiles;
    v_claim public.referral_claims;
begin
    if v_uid is null then raise exception 'not_authenticated'; end if;
    if not public.flag('referrals_enabled') then raise exception 'referrals_disabled'; end if;

    select * into v_me from public.profiles where id = v_uid;
    if not found then raise exception 'profile_not_found'; end if;
    if v_me.referred_by is not null then raise exception 'already_referred'; end if;
    if v_me.created_at < now() - interval '7 days' then raise exception 'referral_window_expired'; end if;

    select * into v_referrer from public.profiles where referral_code = upper(trim(p_code)) and deleted_at is null;
    if not found then raise exception 'invalid_referral_code'; end if;
    if v_referrer.id = v_uid then raise exception 'self_referral'; end if;

    update public.profiles set referred_by = v_referrer.id where id = v_uid;
    insert into public.referral_claims (referrer_id, referred_id, code, status)
    values (v_referrer.id, v_uid, v_referrer.referral_code, 'pending')
    returning * into v_claim;

    perform public.notify(v_referrer.id, 'account', 'New referral', 'Someone joined with your referral code.',
        jsonb_build_object('referred_id', v_uid));

    return v_claim;
end;
$$;

create or replace function public.request_account_deletion()
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_uid uuid := auth.uid();
begin
    if v_uid is null then raise exception 'not_authenticated'; end if;
    update public.profiles
    set deleted_at = now(),
        username = 'deleted_' || left(replace(v_uid::text, '-', ''), 8),
        display_name = 'Deleted player',
        avatar_url = null,
        bio = null,
        email = null,
        phone = null,
        referral_code = upper(left(replace(gen_random_uuid()::text, '-', ''), 8)),
        updated_at = now()
    where id = v_uid;

    insert into public.audit_logs (actor_id, action, entity_type, entity_id)
    values (v_uid, 'account.delete_requested', 'profile', v_uid);
end;
$$;

create or replace function public.mark_notifications_read()
returns void
language sql
security definer
set search_path = public, pg_temp
as $$
    update public.notifications set read_at = now() where user_id = auth.uid() and read_at is null;
$$;

create or replace function public.submit_support_ticket(
    p_category text,
    p_subject text,
    p_body text
)
returns public.support_tickets
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_ticket public.support_tickets;
begin
    if auth.uid() is null then raise exception 'not_authenticated'; end if;
    if p_category not in ('account', 'tournament', 'payment', 'technical', 'report', 'other') then
        raise exception 'invalid_category';
    end if;
    if p_subject is null or char_length(trim(p_subject)) < 3 then raise exception 'invalid_subject'; end if;
    if p_body is null or char_length(trim(p_body)) < 5 then raise exception 'invalid_message'; end if;

    insert into public.support_tickets (user_id, category, subject)
    values (auth.uid(), p_category, trim(p_subject))
    returning * into v_ticket;

    insert into public.support_messages (ticket_id, sender_id, body)
    values (v_ticket.id, auth.uid(), trim(p_body));

    return v_ticket;
end;
$$;

-- ---------------------------------------------------------------------------
-- Updated-at triggers
-- ---------------------------------------------------------------------------

do $$
declare
    t text;
begin
    foreach t in array array[
        'profiles', 'player_game_accounts', 'teams', 'tournaments', 'matches',
        'match_credentials', 'wallet_accounts', 'payment_orders', 'support_tickets', 'app_config'
    ] loop
        execute format('drop trigger if exists %I on public.%I', t || '_updated_at', t);
        execute format('create trigger %I before update on public.%I for each row execute function public.set_updated_at()', t || '_updated_at', t);
    end loop;
end;
$$;

-- ---------------------------------------------------------------------------
-- Immutable audit + financial rows
-- ---------------------------------------------------------------------------

create or replace function public.prevent_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'immutable_record';
end;
$$;

drop trigger if exists financial_ledger_immutable on public.financial_ledger;
create trigger financial_ledger_immutable
    before update or delete on public.financial_ledger
    for each row execute function public.prevent_mutation();

drop trigger if exists audit_logs_immutable on public.audit_logs;
create trigger audit_logs_immutable
    before update or delete on public.audit_logs
    for each row execute function public.prevent_mutation();

drop trigger if exists payment_events_immutable on public.payment_events;
create trigger payment_events_immutable
    before update or delete on public.payment_events
    for each row execute function public.prevent_mutation();

-- ---------------------------------------------------------------------------
-- Realtime publication
-- ---------------------------------------------------------------------------

do $$
begin
    begin
        alter publication supabase_realtime add table public.notifications;
    exception when duplicate_object then null;
    end;
    begin
        alter publication supabase_realtime add table public.tournaments;
    exception when duplicate_object then null;
    end;
    begin
        alter publication supabase_realtime add table public.matches;
    exception when duplicate_object then null;
    end;
    begin
        alter publication supabase_realtime add table public.registrations;
    exception when duplicate_object then null;
    end;
    begin
        alter publication supabase_realtime add table public.match_results;
    exception when duplicate_object then null;
    end;
    begin
        alter publication supabase_realtime add table public.announcements;
    exception when duplicate_object then null;
    end;
end;
$$;
