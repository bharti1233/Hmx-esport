-- HMX Arena core schema.
-- UUID primary keys, foreign keys, checks, indexes, timestamps.

create type public.user_role as enum ('player', 'moderator', 'admin');
create type public.team_role as enum ('owner', 'member');
create type public.invitation_status as enum ('pending', 'accepted', 'declined', 'cancelled');
create type public.tournament_status as enum ('draft', 'registration_open', 'registration_closed', 'live', 'completed', 'cancelled');
create type public.entry_type as enum ('free', 'paid');
create type public.game_mode as enum ('solo', 'duo', 'squad', 'clash_squad');
create type public.registration_status as enum ('confirmed', 'waitlisted', 'cancelled', 'rejected');
create type public.match_status as enum ('scheduled', 'check_in', 'live', 'completed', 'cancelled');
create type public.result_status as enum ('pending', 'verified', 'rejected', 'disputed');
create type public.dispute_status as enum ('open', 'under_review', 'resolved', 'rejected');
create type public.ticket_status as enum ('open', 'in_progress', 'resolved', 'closed');
create type public.notification_type as enum ('registration', 'tournament', 'match', 'result', 'dispute', 'account', 'system');
create type public.ledger_entry_type as enum ('credit', 'debit', 'hold', 'release', 'adjustment');

-- ---------------------------------------------------------------------------
-- Identity
-- ---------------------------------------------------------------------------

create table public.profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    username text not null check (char_length(username) between 3 and 24 and username ~ '^[A-Za-z0-9_]+$'),
    display_name text not null default '' check (char_length(display_name) <= 40),
    email text,
    phone text,
    avatar_url text,
    bio text check (char_length(bio) <= 240),
    region text not null default 'IN' check (char_length(region) <= 8),
    referral_code text not null unique,
    referred_by uuid references public.profiles (id) on delete set null,
    points integer not null default 0 check (points >= 0),
    matches_played integer not null default 0 check (matches_played >= 0),
    wins integer not null default 0 check (wins >= 0),
    onboarding_completed boolean not null default false,
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index profiles_username_lower_idx on public.profiles (lower(username));
create index profiles_referral_code_idx on public.profiles (referral_code);
create index profiles_points_idx on public.profiles (points desc);

create table public.player_game_accounts (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles (id) on delete cascade,
    game text not null default 'free_fire' check (game in ('free_fire', 'bgmi', 'cod_mobile', 'valorant')),
    ign text not null check (char_length(ign) between 2 and 24),
    game_uid text not null check (char_length(game_uid) between 4 and 24),
    level integer check (level between 1 and 100),
    verified boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (user_id, game),
    unique (game, game_uid)
);

create table public.user_roles (
    user_id uuid not null references public.profiles (id) on delete cascade,
    role public.user_role not null default 'player',
    granted_by uuid references public.profiles (id) on delete set null,
    granted_at timestamptz not null default now(),
    primary key (user_id, role)
);

-- ---------------------------------------------------------------------------
-- Teams
-- ---------------------------------------------------------------------------

create table public.teams (
    id uuid primary key default gen_random_uuid(),
    name text not null check (char_length(name) between 3 and 24),
    tag text not null check (char_length(tag) between 2 and 5),
    game text not null default 'free_fire' check (game in ('free_fire', 'bgmi', 'cod_mobile', 'valorant')),
    owner_id uuid not null references public.profiles (id) on delete cascade,
    logo_url text,
    region text not null default 'IN' check (char_length(region) <= 8),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index teams_game_name_lower_idx on public.teams (game, lower(name));

create table public.team_members (
    team_id uuid not null references public.teams (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    role public.team_role not null default 'member',
    joined_at timestamptz not null default now(),
    primary key (team_id, user_id),
    unique (user_id)
);

create index team_members_team_idx on public.team_members (team_id);

create table public.team_invitations (
    id uuid primary key default gen_random_uuid(),
    team_id uuid not null references public.teams (id) on delete cascade,
    invited_user_id uuid not null references public.profiles (id) on delete cascade,
    invited_by uuid not null references public.profiles (id) on delete cascade,
    status public.invitation_status not null default 'pending',
    created_at timestamptz not null default now(),
    responded_at timestamptz
);

create unique index team_invitations_pending_idx
    on public.team_invitations (team_id, invited_user_id)
    where status = 'pending';

create index team_invitations_user_idx on public.team_invitations (invited_user_id, status);

-- ---------------------------------------------------------------------------
-- Tournaments
-- ---------------------------------------------------------------------------

create table public.tournaments (
    id uuid primary key default gen_random_uuid(),
    slug text not null unique,
    title text not null check (char_length(title) between 3 and 80),
    game text not null default 'free_fire' check (game in ('free_fire', 'bgmi', 'cod_mobile', 'valorant')),
    mode public.game_mode not null default 'squad',
    entry_type public.entry_type not null default 'free',
    entry_fee_paise integer not null default 0 check (entry_fee_paise >= 0),
    prize_pool_paise integer not null default 0 check (prize_pool_paise >= 0),
    max_slots integer not null check (max_slots > 0),
    slots_filled integer not null default 0 check (slots_filled >= 0),
    min_team_size integer not null default 1 check (min_team_size >= 1),
    max_team_size integer not null default 1 check (max_team_size >= 1),
    status public.tournament_status not null default 'draft',
    registration_opens_at timestamptz not null default now(),
    registration_closes_at timestamptz not null,
    starts_at timestamptz not null,
    ends_at timestamptz,
    banner_url text,
    description text not null default '',
    map text,
    region text not null default 'IN' check (char_length(region) <= 8),
    is_featured boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint tournaments_team_size_check check (max_team_size >= min_team_size),
    constraint tournaments_registration_window_check check (registration_closes_at > registration_opens_at),
    constraint tournaments_start_after_registration_check check (starts_at >= registration_closes_at),
    constraint tournaments_slots_check check (slots_filled <= max_slots),
    constraint tournaments_paid_fee_check check (entry_type = 'free' or entry_fee_paise > 0)
);

create index tournaments_status_start_idx on public.tournaments (status, starts_at);
create index tournaments_game_idx on public.tournaments (game);

create table public.tournament_rules (
    id uuid primary key default gen_random_uuid(),
    tournament_id uuid not null references public.tournaments (id) on delete cascade,
    position integer not null default 1 check (position > 0),
    title text not null check (char_length(title) between 1 and 80),
    body text not null check (char_length(body) between 1 and 2000),
    unique (tournament_id, position)
);

create table public.tournament_eligibility (
    tournament_id uuid primary key references public.tournaments (id) on delete cascade,
    min_level integer check (min_level between 1 and 100),
    min_points integer not null default 0 check (min_points >= 0),
    requires_game_account boolean not null default true,
    allowed_regions text[] not null default array['IN']::text[]
);

create table public.registrations (
    id uuid primary key default gen_random_uuid(),
    tournament_id uuid not null references public.tournaments (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    team_id uuid references public.teams (id) on delete set null,
    registration_type text not null check (registration_type in ('solo', 'team')),
    status public.registration_status not null default 'confirmed',
    slot_no integer check (slot_no > 0),
    created_at timestamptz not null default now(),
    cancelled_at timestamptz,
    unique (tournament_id, user_id)
);

create index registrations_user_idx on public.registrations (user_id, created_at desc);
create index registrations_tournament_status_idx on public.registrations (tournament_id, status);

create table public.registration_members (
    id uuid primary key default gen_random_uuid(),
    registration_id uuid not null references public.registrations (id) on delete cascade,
    user_id uuid references public.profiles (id) on delete set null,
    ign text not null check (char_length(ign) between 2 and 24),
    game_uid text not null check (char_length(game_uid) between 4 and 24),
    is_captain boolean not null default false,
    created_at timestamptz not null default now(),
    unique (registration_id, game_uid)
);

create index registration_members_registration_idx on public.registration_members (registration_id);

-- ---------------------------------------------------------------------------
-- Matches
-- ---------------------------------------------------------------------------

create table public.matches (
    id uuid primary key default gen_random_uuid(),
    tournament_id uuid not null references public.tournaments (id) on delete cascade,
    round integer not null default 1 check (round > 0),
    name text not null default 'Match',
    status public.match_status not null default 'scheduled',
    scheduled_at timestamptz not null,
    check_in_opens_at timestamptz,
    started_at timestamptz,
    ended_at timestamptz,
    map text,
    mode text,
    max_teams integer check (max_teams > 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index matches_tournament_idx on public.matches (tournament_id, scheduled_at);
create index matches_status_idx on public.matches (status, scheduled_at);

create table public.match_participants (
    id uuid primary key default gen_random_uuid(),
    match_id uuid not null references public.matches (id) on delete cascade,
    registration_id uuid not null references public.registrations (id) on delete cascade,
    team_id uuid references public.teams (id) on delete set null,
    slot integer check (slot > 0),
    placement integer check (placement > 0),
    kills integer not null default 0 check (kills >= 0),
    points integer not null default 0,
    status text not null default 'assigned',
    created_at timestamptz not null default now(),
    unique (match_id, registration_id)
);

create index match_participants_match_idx on public.match_participants (match_id);
create index match_participants_registration_idx on public.match_participants (registration_id);

-- Never exposed by a SELECT policy: read only through get_match_credentials().
create table public.match_credentials (
    match_id uuid primary key references public.matches (id) on delete cascade,
    room_id text not null check (char_length(room_id) between 1 and 40),
    room_password text not null check (char_length(room_password) between 1 and 40),
    released_at timestamptz not null default now(),
    visible_until timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.match_results (
    id uuid primary key default gen_random_uuid(),
    match_id uuid not null references public.matches (id) on delete cascade,
    registration_id uuid not null references public.registrations (id) on delete cascade,
    submitted_by uuid not null references public.profiles (id) on delete cascade,
    placement integer not null check (placement > 0),
    kills integer not null default 0 check (kills >= 0),
    points integer not null default 0 check (points >= 0),
    status public.result_status not null default 'pending',
    notes text check (char_length(notes) <= 1000),
    verified_by uuid references public.profiles (id) on delete set null,
    verified_at timestamptz,
    created_at timestamptz not null default now(),
    unique (match_id, registration_id)
);

create index match_results_match_idx on public.match_results (match_id);
create index match_results_registration_idx on public.match_results (registration_id);

create table public.result_evidence (
    id uuid primary key default gen_random_uuid(),
    result_id uuid not null references public.match_results (id) on delete cascade,
    storage_path text not null,
    mime_type text not null check (mime_type in ('image/jpeg', 'image/png', 'image/webp')),
    size_bytes bigint not null check (size_bytes > 0 and size_bytes <= 10485760),
    uploaded_by uuid not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now()
);

create index result_evidence_result_idx on public.result_evidence (result_id);

create table public.result_disputes (
    id uuid primary key default gen_random_uuid(),
    result_id uuid not null references public.match_results (id) on delete cascade,
    raised_by uuid not null references public.profiles (id) on delete cascade,
    reason text not null check (char_length(reason) between 10 and 1000),
    status public.dispute_status not null default 'open',
    resolution text check (char_length(resolution) <= 1000),
    created_at timestamptz not null default now(),
    resolved_at timestamptz
);

create index result_disputes_result_idx on public.result_disputes (result_id);
create index result_disputes_status_idx on public.result_disputes (status);

-- ---------------------------------------------------------------------------
-- Financial architecture (disabled behind server flags until approved)
-- ---------------------------------------------------------------------------

create table public.wallet_accounts (
    user_id uuid primary key references public.profiles (id) on delete cascade,
    balance_paise bigint not null default 0 check (balance_paise >= 0),
    reserved_paise bigint not null default 0 check (reserved_paise >= 0),
    currency text not null default 'INR' check (currency = 'INR'),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.financial_ledger (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles (id) on delete cascade,
    entry_type public.ledger_entry_type not null,
    amount_paise bigint not null check (amount_paise <> 0),
    balance_after_paise bigint not null check (balance_after_paise >= 0),
    reference_type text,
    reference_id uuid,
    description text not null default '',
    idempotency_key text unique,
    created_at timestamptz not null default now()
);

create index financial_ledger_user_idx on public.financial_ledger (user_id, created_at desc);

create table public.payment_orders (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles (id) on delete cascade,
    amount_paise bigint not null check (amount_paise > 0),
    currency text not null default 'INR' check (currency = 'INR'),
    provider text not null default 'none',
    provider_order_id text,
    status text not null default 'created' check (status in ('created', 'pending', 'paid', 'failed', 'cancelled', 'refunded')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (provider, provider_order_id)
);

create table public.payment_events (
    id uuid primary key default gen_random_uuid(),
    provider text not null,
    event_id text not null unique,
    order_id uuid references public.payment_orders (id) on delete set null,
    payload jsonb not null default '{}'::jsonb,
    processed_at timestamptz,
    created_at timestamptz not null default now()
);

create table public.refunds (
    id uuid primary key default gen_random_uuid(),
    order_id uuid not null references public.payment_orders (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    amount_paise bigint not null check (amount_paise > 0),
    status text not null default 'requested' check (status in ('requested', 'approved', 'processed', 'rejected')),
    reason text check (char_length(reason) <= 500),
    created_at timestamptz not null default now(),
    processed_at timestamptz
);

create table public.prize_allocations (
    id uuid primary key default gen_random_uuid(),
    tournament_id uuid not null references public.tournaments (id) on delete cascade,
    registration_id uuid references public.registrations (id) on delete set null,
    rank integer not null check (rank > 0),
    amount_paise bigint not null check (amount_paise >= 0),
    status text not null default 'pending' check (status in ('pending', 'approved', 'paid', 'cancelled')),
    created_at timestamptz not null default now()
);

create table public.payout_records (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles (id) on delete cascade,
    amount_paise bigint not null check (amount_paise > 0),
    status text not null default 'pending' check (status in ('pending', 'processing', 'paid', 'failed')),
    provider_reference text,
    created_at timestamptz not null default now(),
    processed_at timestamptz
);

-- ---------------------------------------------------------------------------
-- Platform
-- ---------------------------------------------------------------------------

create table public.notifications (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles (id) on delete cascade,
    type public.notification_type not null default 'system',
    title text not null check (char_length(title) between 1 and 120),
    body text not null default '' check (char_length(body) <= 500),
    data jsonb not null default '{}'::jsonb,
    read_at timestamptz,
    created_at timestamptz not null default now()
);

create index notifications_user_created_idx on public.notifications (user_id, created_at desc);
create index notifications_unread_idx on public.notifications (user_id) where read_at is null;

create table public.announcements (
    id uuid primary key default gen_random_uuid(),
    title text not null check (char_length(title) between 1 and 120),
    body text not null check (char_length(body) between 1 and 2000),
    image_url text,
    is_published boolean not null default false,
    published_at timestamptz,
    created_at timestamptz not null default now()
);

create index announcements_published_idx on public.announcements (published_at desc) where is_published;

create table public.support_tickets (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles (id) on delete cascade,
    category text not null check (category in ('account', 'tournament', 'payment', 'technical', 'report', 'other')),
    subject text not null check (char_length(subject) between 3 and 120),
    status public.ticket_status not null default 'open',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index support_tickets_user_idx on public.support_tickets (user_id, created_at desc);

create table public.support_messages (
    id uuid primary key default gen_random_uuid(),
    ticket_id uuid not null references public.support_tickets (id) on delete cascade,
    sender_id uuid not null references public.profiles (id) on delete cascade,
    body text not null check (char_length(body) between 1 and 4000),
    created_at timestamptz not null default now()
);

create index support_messages_ticket_idx on public.support_messages (ticket_id, created_at);

create table public.reports (
    id uuid primary key default gen_random_uuid(),
    reporter_id uuid not null references public.profiles (id) on delete cascade,
    category text not null check (category in ('cheating', 'abuse', 'spam', 'impersonation', 'other')),
    target_type text not null check (target_type in ('user', 'team', 'match', 'result', 'tournament')),
    target_id uuid,
    description text not null check (char_length(description) between 10 and 2000),
    status text not null default 'open' check (status in ('open', 'reviewing', 'resolved', 'dismissed')),
    created_at timestamptz not null default now()
);

create index reports_status_idx on public.reports (status, created_at desc);

create table public.audit_logs (
    id uuid primary key default gen_random_uuid(),
    actor_id uuid references public.profiles (id) on delete set null,
    action text not null,
    entity_type text not null,
    entity_id uuid,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index audit_logs_entity_idx on public.audit_logs (entity_type, entity_id);

create table public.app_config (
    key text primary key,
    value jsonb not null,
    description text not null default '',
    updated_at timestamptz not null default now()
);

create table public.idempotency_keys (
    key text primary key,
    user_id uuid references public.profiles (id) on delete cascade,
    scope text not null,
    response jsonb,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null default now() + interval '24 hours'
);

create table public.referral_claims (
    id uuid primary key default gen_random_uuid(),
    referrer_id uuid not null references public.profiles (id) on delete cascade,
    referred_id uuid not null unique references public.profiles (id) on delete cascade,
    code text not null,
    status text not null default 'pending' check (status in ('pending', 'qualified', 'rejected')),
    reward_points integer not null default 0 check (reward_points >= 0),
    created_at timestamptz not null default now(),
    verified_at timestamptz
);
