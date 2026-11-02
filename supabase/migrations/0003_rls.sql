-- Row Level Security, public views and least-privilege grants.

-- ---------------------------------------------------------------------------
-- Public views (avoid exposing contact details and game UIDs)
-- ---------------------------------------------------------------------------

create or replace view public.public_profiles
with (security_invoker = false)
as
select id, username, display_name, avatar_url, bio, region, points, matches_played, wins, created_at
from public.profiles
where deleted_at is null;

create or replace view public.tournament_participants
with (security_invoker = false)
as
select r.tournament_id,
       r.id as registration_id,
       r.team_id,
       t.name as team_name,
       t.tag as team_tag,
       rm.ign,
       rm.is_captain,
       p.username,
       coalesce(nullif(p.display_name, ''), p.username) as display_name,
       p.avatar_url
from public.registrations r
join public.registration_members rm on rm.registration_id = r.id
left join public.teams t on t.id = r.team_id
left join public.profiles p on p.id = rm.user_id
where r.status = 'confirmed';

grant select on public.public_profiles to anon, authenticated;
grant select on public.tournament_participants to anon, authenticated;

-- ---------------------------------------------------------------------------
-- Invitations (RPC so the invited player is notified)
-- ---------------------------------------------------------------------------

create or replace function public.invite_team_member(p_team_id uuid, p_username text)
returns public.team_invitations
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_uid uuid := auth.uid();
    v_team public.teams;
    v_target public.profiles;
    v_inv public.team_invitations;
    v_size integer;
begin
    if v_uid is null then raise exception 'not_authenticated'; end if;
    select * into v_team from public.teams where id = p_team_id;
    if not found then raise exception 'team_not_found'; end if;
    if v_team.owner_id <> v_uid then raise exception 'not_team_owner'; end if;

    select * into v_target from public.profiles
    where lower(username) = lower(trim(p_username)) and deleted_at is null;
    if not found then raise exception 'player_not_found'; end if;
    if v_target.id = v_uid then raise exception 'cannot_invite_self'; end if;
    if exists (select 1 from public.team_members where user_id = v_target.id) then
        raise exception 'player_already_in_team';
    end if;

    select count(*) into v_size from public.team_members where team_id = p_team_id;
    if v_size >= 5 then raise exception 'team_full'; end if;

    insert into public.team_invitations (team_id, invited_user_id, invited_by)
    values (p_team_id, v_target.id, v_uid)
    returning * into v_inv;

    perform public.notify(v_target.id, 'account', 'Team invitation',
        v_team.name || ' invited you to join their team.',
        jsonb_build_object('team_id', p_team_id, 'invitation_id', v_inv.id));

    return v_inv;
end;
$$;

-- ---------------------------------------------------------------------------
-- Enable RLS
-- ---------------------------------------------------------------------------

alter table public.profiles enable row level security;
alter table public.player_game_accounts enable row level security;
alter table public.user_roles enable row level security;
alter table public.teams enable row level security;
alter table public.team_members enable row level security;
alter table public.team_invitations enable row level security;
alter table public.tournaments enable row level security;
alter table public.tournament_rules enable row level security;
alter table public.tournament_eligibility enable row level security;
alter table public.registrations enable row level security;
alter table public.registration_members enable row level security;
alter table public.matches enable row level security;
alter table public.match_participants enable row level security;
alter table public.match_credentials enable row level security;
alter table public.match_results enable row level security;
alter table public.result_evidence enable row level security;
alter table public.result_disputes enable row level security;
alter table public.wallet_accounts enable row level security;
alter table public.financial_ledger enable row level security;
alter table public.payment_orders enable row level security;
alter table public.payment_events enable row level security;
alter table public.refunds enable row level security;
alter table public.prize_allocations enable row level security;
alter table public.payout_records enable row level security;
alter table public.notifications enable row level security;
alter table public.announcements enable row level security;
alter table public.support_tickets enable row level security;
alter table public.support_messages enable row level security;
alter table public.reports enable row level security;
alter table public.audit_logs enable row level security;
alter table public.app_config enable row level security;
alter table public.idempotency_keys enable row level security;
alter table public.referral_claims enable row level security;

-- ---------------------------------------------------------------------------
-- Policies
-- ---------------------------------------------------------------------------

create policy profiles_select_own on public.profiles
    for select using (id = auth.uid() or public.is_admin());

create policy profiles_update_own on public.profiles
    for update using (id = auth.uid()) with check (id = auth.uid());

create policy game_accounts_select_own on public.player_game_accounts
    for select using (user_id = auth.uid() or public.is_admin());

create policy game_accounts_insert_own on public.player_game_accounts
    for insert with check (user_id = auth.uid());

create policy game_accounts_update_own on public.player_game_accounts
    for update using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy game_accounts_delete_own on public.player_game_accounts
    for delete using (user_id = auth.uid());

create policy user_roles_select on public.user_roles
    for select using (user_id = auth.uid() or public.is_admin());

create policy teams_select_all on public.teams
    for select using (true);

create policy teams_update_owner on public.teams
    for update using (owner_id = auth.uid() or public.is_admin())
    with check (owner_id = auth.uid() or public.is_admin());

create policy teams_delete_owner on public.teams
    for delete using (owner_id = auth.uid() or public.is_admin());

create policy team_members_select_all on public.team_members
    for select using (true);

create policy team_invitations_select on public.team_invitations
    for select using (
        invited_user_id = auth.uid()
        or invited_by = auth.uid()
        or public.is_admin()
    );

create policy team_invitations_delete_owner on public.team_invitations
    for delete using (
        invited_by = auth.uid()
        or public.is_admin()
    );

create policy tournaments_select on public.tournaments
    for select using (status <> 'draft' or public.is_admin());

create policy tournament_rules_select on public.tournament_rules
    for select using (true);

create policy tournament_eligibility_select on public.tournament_eligibility
    for select using (true);

create policy registrations_select on public.registrations
    for select using (
        user_id = auth.uid()
        or public.is_admin()
        or (
            team_id is not null
            and exists (
                select 1 from public.team_members tm
                where tm.team_id = registrations.team_id and tm.user_id = auth.uid()
            )
        )
    );

create policy registration_members_select on public.registration_members
    for select using (
        exists (
            select 1 from public.registrations r
            where r.id = registration_members.registration_id
              and (
                  r.user_id = auth.uid()
                  or public.is_admin()
                  or exists (
                      select 1 from public.team_members tm
                      where tm.team_id = r.team_id and tm.user_id = auth.uid()
                  )
              )
        )
    );

create policy matches_select on public.matches
    for select using (true);

create policy match_participants_select on public.match_participants
    for select using (true);

-- match_credentials: intentionally no policy. Reads happen only through
-- public.get_match_credentials(), which verifies participation and release time.

create policy match_results_select on public.match_results
    for select using (
        status in ('verified', 'disputed')
        or submitted_by = auth.uid()
        or public.is_admin()
        or public.is_match_participant(match_id, auth.uid())
    );

create policy result_evidence_select on public.result_evidence
    for select using (
        uploaded_by = auth.uid()
        or public.is_admin()
        or exists (
            select 1 from public.match_results r
            where r.id = result_evidence.result_id
              and public.is_match_participant(r.match_id, auth.uid())
        )
    );

create policy result_disputes_select on public.result_disputes
    for select using (raised_by = auth.uid() or public.is_admin());

create policy wallet_select_own on public.wallet_accounts
    for select using (user_id = auth.uid() or public.is_admin());

create policy ledger_select_own on public.financial_ledger
    for select using (user_id = auth.uid() or public.is_admin());

create policy payment_orders_select_own on public.payment_orders
    for select using (user_id = auth.uid() or public.is_admin());

create policy refunds_select_own on public.refunds
    for select using (user_id = auth.uid() or public.is_admin());

create policy prize_allocations_select on public.prize_allocations
    for select using (
        public.is_admin()
        or exists (
            select 1 from public.registrations r
            where r.id = prize_allocations.registration_id and r.user_id = auth.uid()
        )
    );

create policy payout_records_select_own on public.payout_records
    for select using (user_id = auth.uid() or public.is_admin());

create policy notifications_select_own on public.notifications
    for select using (user_id = auth.uid() or public.is_admin());

create policy notifications_update_own on public.notifications
    for update using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy announcements_select on public.announcements
    for select using (is_published or public.is_admin());

create policy support_tickets_select_own on public.support_tickets
    for select using (user_id = auth.uid() or public.is_admin());

create policy support_messages_select_own on public.support_messages
    for select using (
        public.is_admin()
        or exists (
            select 1 from public.support_tickets t
            where t.id = support_messages.ticket_id and t.user_id = auth.uid()
        )
    );

create policy reports_select_own on public.reports
    for select using (reporter_id = auth.uid() or public.is_admin());

create policy reports_insert_own on public.reports
    for insert with check (reporter_id = auth.uid());

create policy audit_logs_admin on public.audit_logs
    for select using (public.is_admin());

create policy app_config_select on public.app_config
    for select using (true);

create policy referral_claims_select on public.referral_claims
    for select using (referrer_id = auth.uid() or referred_id = auth.uid() or public.is_admin());

-- payment_events, idempotency_keys: no policies (server-side only).

-- ---------------------------------------------------------------------------
-- Least-privilege grants (RLS is not a substitute for table privileges)
-- ---------------------------------------------------------------------------

revoke all on all tables in schema public from anon, authenticated;

grant select on public.public_profiles,
    public.tournament_participants,
    public.tournaments,
    public.tournament_rules,
    public.tournament_eligibility,
    public.announcements,
    public.app_config,
    public.teams,
    public.team_members,
    public.matches,
    public.match_participants
to anon, authenticated;

grant select on public.profiles,
    public.player_game_accounts,
    public.user_roles,
    public.team_invitations,
    public.registrations,
    public.registration_members,
    public.match_results,
    public.result_evidence,
    public.result_disputes,
    public.notifications,
    public.support_tickets,
    public.support_messages,
    public.reports,
    public.referral_claims,
    public.wallet_accounts,
    public.financial_ledger,
    public.payment_orders,
    public.refunds,
    public.prize_allocations,
    public.payout_records
to authenticated;

grant update (display_name, avatar_url, bio, region, onboarding_completed)
    on public.profiles to authenticated;

grant insert (user_id, game, ign, game_uid, level)
    on public.player_game_accounts to authenticated;
grant update (ign, game_uid, level)
    on public.player_game_accounts to authenticated;
grant delete on public.player_game_accounts to authenticated;

grant update (name, tag, logo_url)
    on public.teams to authenticated;
grant delete on public.teams to authenticated;

grant update (read_at)
    on public.notifications to authenticated;

grant insert on public.reports to authenticated;

revoke all on all functions in schema public from anon;

grant execute on function public.get_leaderboard(text, text, text, uuid, integer, integer) to anon, authenticated;
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
grant execute on function public.is_admin() to authenticated;
grant execute on function public.is_match_participant(uuid, uuid) to authenticated;
