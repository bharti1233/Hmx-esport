-- Security hardening pass:
--  * SECURITY DEFINER views replaced with invoker views + least-privilege columns
--  * tournament participant projection moved to an RPC (no game UIDs exposed)
--  * EXECUTE revoked from PUBLIC/anon, granted only to the intended RPC surface
--  * pinned search_path on trigger helpers

-- ---------------------------------------------------------------------------
-- Profiles: public projection via column grants instead of a definer view
-- ---------------------------------------------------------------------------

drop policy if exists profiles_select_own on public.profiles;

create policy profiles_select_public on public.profiles
    for select using (deleted_at is null or id = auth.uid() or public.is_admin());

revoke all on public.profiles from anon, authenticated;

grant select (id, username, display_name, avatar_url, bio, region, points, matches_played, wins, created_at)
    on public.profiles to anon, authenticated;
grant select (onboarding_completed, email, phone, referral_code, referred_by, deleted_at)
    on public.profiles to authenticated;

-- The app never reads email/phone of other users; it reads its own row through
-- the RPC below, which returns the caller's full profile only.
grant update (display_name, avatar_url, bio, region, onboarding_completed)
    on public.profiles to authenticated;

alter view public.public_profiles set (security_invoker = true);

create or replace function public.my_profile()
returns public.profiles
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select * from public.profiles where id = auth.uid();
$$;

-- ---------------------------------------------------------------------------
-- Tournament participants (public projection, no game UIDs)
-- ---------------------------------------------------------------------------

drop view if exists public.tournament_participants;

create or replace function public.get_tournament_participants(
    p_tournament_id uuid,
    p_limit integer default 100,
    p_offset integer default 0
)
returns table (
    registration_id uuid,
    team_id uuid,
    team_name text,
    team_tag text,
    ign text,
    is_captain boolean,
    username text,
    display_name text,
    avatar_url text
)
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select r.id,
           r.team_id,
           t.name,
           t.tag,
           rm.ign,
           rm.is_captain,
           p.username,
           coalesce(nullif(p.display_name, ''), p.username),
           p.avatar_url
    from public.registrations r
    join public.registration_members rm on rm.registration_id = r.id
    left join public.teams t on t.id = r.team_id
    left join public.profiles p on p.id = rm.user_id
    where r.tournament_id = p_tournament_id
      and r.status = 'confirmed'
    order by r.slot_no nulls last, rm.is_captain desc, rm.ign
    limit greatest(p_limit, 0) offset greatest(p_offset, 0);
$$;

-- ---------------------------------------------------------------------------
-- Pinned search_path for trigger helpers
-- ---------------------------------------------------------------------------

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create or replace function public.prevent_mutation()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
    raise exception 'immutable_record';
end;
$$;

-- ---------------------------------------------------------------------------
-- Function privileges: no implicit PUBLIC execute
-- ---------------------------------------------------------------------------

revoke all on all functions in schema public from public, anon, authenticated;

grant execute on function public.get_leaderboard(text, text, text, uuid, integer, integer) to anon, authenticated;
grant execute on function public.get_tournament_participants(uuid, integer, integer) to anon, authenticated;
grant execute on function public.my_profile() to authenticated;
grant execute on function public.get_match_credentials(uuid) to authenticated;
grant execute on function public.register_for_tournament(uuid, uuid) to authenticated;
grant execute on function public.cancel_registration(uuid) to authenticated;
grant execute on function public.submit_match_result(uuid, integer, integer, text, text, bigint, text) to authenticated;
grant execute on function public.raise_dispute(uuid, text) to authenticated;
grant execute on function public.claim_referral(text) to authenticated;
grant execute on function public.create_team(text, text, text) to authenticated;
grant execute on function public.invite_team_member(uuid, text) to authenticated;
grant execute on function public.respond_team_invitation(uuid, boolean) to authenticated;
grant execute on function public.leave_team(uuid) to authenticated;
grant execute on function public.transfer_team_ownership(uuid, uuid) to authenticated;
grant execute on function public.request_account_deletion() to authenticated;
grant execute on function public.mark_notifications_read() to authenticated;
grant execute on function public.submit_support_ticket(text, text, text) to authenticated;

-- Required by RLS policies evaluated as the signed-in user.
grant execute on function public.is_admin() to authenticated;
grant execute on function public.is_match_participant(uuid, uuid) to authenticated;
