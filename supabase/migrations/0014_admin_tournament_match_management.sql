-- 0014: administrator-controlled categories, game modes, tournament types,
-- tournaments and independent matches, plus atomic match registration.
--
-- Design notes
--  * Lookup tables use stable UUID identifiers. Tournaments and matches keep a
--    nullable foreign key to each lookup so renaming propagates automatically
--    without rewriting historical rows.
--  * Lookup rows can be deactivated/archived, never silently deleted while
--    referenced. New assignments require an active row; existing assignments
--    keep working and remain readable.
--  * All administrative writes go through SECURITY DEFINER RPCs guarded by
--    public.is_admin(). The tables have no INSERT/UPDATE/DELETE policies.
--  * Every admin RPC writes an immutable audit_logs row with old/new values.
--  * Match capacity is enforced by row locking inside register_for_match().
--    The client can never bypass it by calling PostgREST directly because the
--    match_registrations table has no INSERT policy.

-- ---------------------------------------------------------------------------
-- Lookup tables
-- ---------------------------------------------------------------------------

create table if not exists public.categories (
    id uuid primary key default gen_random_uuid(),
    slug text not null unique check (slug ~ '^[a-z0-9][a-z0-9-]{0,59}$'),
    name text not null check (char_length(name) between 2 and 60),
    description text not null default '',
    logo_url text,
    is_active boolean not null default true,
    is_archived boolean not null default false,
    display_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.game_modes (
    id uuid primary key default gen_random_uuid(),
    slug text not null unique check (slug ~ '^[a-z0-9][a-z0-9-]{0,59}$'),
    name text not null check (char_length(name) between 2 and 60),
    description text not null default '',
    logo_url text,
    is_active boolean not null default true,
    is_archived boolean not null default false,
    display_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.tournament_types (
    id uuid primary key default gen_random_uuid(),
    slug text not null unique check (slug ~ '^[a-z0-9][a-z0-9-]{0,59}$'),
    name text not null check (char_length(name) between 2 and 60),
    description text not null default '',
    logo_url text,
    is_active boolean not null default true,
    is_archived boolean not null default false,
    display_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists categories_order_idx on public.categories (display_order, name);
create index if not exists game_modes_order_idx on public.game_modes (display_order, name);
create index if not exists tournament_types_order_idx on public.tournament_types (display_order, name);

drop trigger if exists categories_updated_at on public.categories;
create trigger categories_updated_at before update on public.categories
    for each row execute function public.set_updated_at();

drop trigger if exists game_modes_updated_at on public.game_modes;
create trigger game_modes_updated_at before update on public.game_modes
    for each row execute function public.set_updated_at();

drop trigger if exists tournament_types_updated_at on public.tournament_types;
create trigger tournament_types_updated_at before update on public.tournament_types
    for each row execute function public.set_updated_at();

-- Seed the values that the old enum could express. These are only defaults:
-- administrators can add, rename, reorder and deactivate anything.
insert into public.categories (slug, name, description, display_order)
values
    ('battle-royale', 'Battle Royale', 'Classic battle royale lobbies.', 10),
    ('clash-squad', 'Clash Squad', 'Short 4v4 clash squad series.', 20),
    ('full-map', 'Full Map', 'Full-map matches with extended rotations.', 30),
    ('special-tournament', 'Special Tournament', 'Invitational and seasonal events.', 40),
    ('custom-tournament', 'Custom Tournament', 'Custom rooms configured by the admin team.', 50)
on conflict (slug) do nothing;

insert into public.game_modes (slug, name, description, display_order)
values
    ('solo', 'Solo', 'One player per slot.', 10),
    ('duo', 'Duo', 'Two players per team.', 20),
    ('squad', 'Squad', 'Four players per team.', 30),
    ('clash-squad', 'Clash Squad', 'Clash squad format.', 40)
on conflict (slug) do nothing;

insert into public.tournament_types (slug, name, description, display_order)
values
    ('free-entry', 'Free Entry', 'No entry fee.', 10),
    ('paid-entry', 'Paid Entry', 'Entry fee applies.', 20),
    ('practice', 'Practice', 'Practice lobbies and scrims.', 30),
    ('competitive', 'Competitive', 'Ranked competitive events.', 40),
    ('special-event', 'Special Event', 'Special events and collaborations.', 50)
on conflict (slug) do nothing;

-- ---------------------------------------------------------------------------
-- Tournament columns
-- ---------------------------------------------------------------------------

alter table public.tournaments
    add column if not exists category_id uuid references public.categories (id) on delete restrict,
    add column if not exists game_mode_id uuid references public.game_modes (id) on delete restrict,
    add column if not exists tournament_type_id uuid references public.tournament_types (id) on delete restrict,
    add column if not exists logo_url text,
    add column if not exists version text not null default '1.0',
    add column if not exists format text,
    add column if not exists per_kill_paise integer not null default 0 check (per_kill_paise >= 0),
    add column if not exists archived_at timestamptz;

create index if not exists tournaments_category_idx on public.tournaments (category_id);
create index if not exists tournaments_game_mode_idx on public.tournaments (game_mode_id);
create index if not exists tournaments_type_idx on public.tournaments (tournament_type_id);

-- Backfill the new foreign keys from the legacy enum values so existing
-- tournaments immediately have a stable category/mode/type relationship.
update public.tournaments t
set category_id = c.id
from public.categories c
where t.category_id is null
  and c.slug = case t.mode::text
        when 'solo' then 'battle-royale'
        when 'duo' then 'battle-royale'
        when 'squad' then 'battle-royale'
        when 'clash_squad' then 'clash-squad'
        else 'custom-tournament'
      end;

update public.tournaments t
set game_mode_id = g.id
from public.game_modes g
where t.game_mode_id is null
  and g.slug = replace(t.mode::text, '_', '-');

update public.tournaments t
set tournament_type_id = tt.id
from public.tournament_types tt
where t.tournament_type_id is null
  and tt.slug = case when t.entry_type = 'paid' then 'paid-entry' else 'free-entry' end;

-- ---------------------------------------------------------------------------
-- Match columns and independent-match support
-- ---------------------------------------------------------------------------

alter table public.matches alter column tournament_id drop not null;

alter table public.matches
    add column if not exists game text check (game is null or game in ('free_fire', 'bgmi', 'cod_mobile', 'valorant')),
    add column if not exists category_id uuid references public.categories (id) on delete restrict,
    add column if not exists game_mode_id uuid references public.game_modes (id) on delete restrict,
    add column if not exists tournament_type_id uuid references public.tournament_types (id) on delete restrict,
    add column if not exists match_number text,
    add column if not exists banner_url text,
    add column if not exists logo_url text,
    add column if not exists prize_pool_paise integer not null default 0 check (prize_pool_paise >= 0),
    add column if not exists per_kill_paise integer not null default 0 check (per_kill_paise >= 0),
    add column if not exists entry_fee_paise integer not null default 0 check (entry_fee_paise >= 0),
    add column if not exists version text not null default '1.0',
    add column if not exists format text,
    add column if not exists max_slots integer check (max_slots > 0),
    add column if not exists slots_filled integer not null default 0 check (slots_filled >= 0),
    add column if not exists registration_opens_at timestamptz,
    add column if not exists registration_closes_at timestamptz,
    add column if not exists is_published boolean not null default false,
    add column if not exists archived_at timestamptz;

alter table public.matches
    drop constraint if exists matches_capacity_check;
alter table public.matches
    add constraint matches_capacity_check check (max_slots is null or slots_filled <= max_slots);

alter table public.matches
    drop constraint if exists matches_registration_window_check;
alter table public.matches
    add constraint matches_registration_window_check check (
        registration_opens_at is null
        or registration_closes_at is null
        or registration_closes_at > registration_opens_at
    );

create index if not exists matches_published_idx on public.matches (is_published, status, scheduled_at);
create index if not exists matches_category_idx on public.matches (category_id);
create index if not exists matches_game_mode_idx on public.matches (game_mode_id);
create index if not exists matches_type_idx on public.matches (tournament_type_id);

update public.matches m
set game = t.game,
    category_id = coalesce(m.category_id, t.category_id),
    game_mode_id = coalesce(m.game_mode_id, t.game_mode_id),
    tournament_type_id = coalesce(m.tournament_type_id, t.tournament_type_id),
    is_published = true
from public.tournaments t
where m.tournament_id = t.id;

-- ---------------------------------------------------------------------------
-- Match-level registrations
-- ---------------------------------------------------------------------------

create table if not exists public.match_registrations (
    id uuid primary key default gen_random_uuid(),
    match_id uuid not null references public.matches (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    team_id uuid references public.teams (id) on delete set null,
    registration_type text not null default 'solo' check (registration_type in ('solo', 'team')),
    status public.registration_status not null default 'confirmed',
    slot_no integer check (slot_no > 0),
    created_at timestamptz not null default now(),
    cancelled_at timestamptz,
    unique (match_id, user_id)
);

create index if not exists match_registrations_match_status_idx
    on public.match_registrations (match_id, status);
create index if not exists match_registrations_user_idx
    on public.match_registrations (user_id, created_at desc);

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------

alter table public.categories enable row level security;
alter table public.game_modes enable row level security;
alter table public.tournament_types enable row level security;
alter table public.match_registrations enable row level security;

-- Reference data is public and non-sensitive. Keeping inactive/archived rows
-- readable is what lets an existing tournament keep displaying the name of a
-- category that was later deactivated.
drop policy if exists categories_select on public.categories;
create policy categories_select on public.categories for select using (true);

drop policy if exists game_modes_select on public.game_modes;
create policy game_modes_select on public.game_modes for select using (true);

drop policy if exists tournament_types_select on public.tournament_types;
create policy tournament_types_select on public.tournament_types for select using (true);

drop policy if exists match_registrations_select on public.match_registrations;
create policy match_registrations_select on public.match_registrations
    for select using (user_id = auth.uid() or public.is_admin());

grant select on public.categories, public.game_modes, public.tournament_types to anon, authenticated;
grant select on public.match_registrations to authenticated;

-- ---------------------------------------------------------------------------
-- Helpers (internal only)
-- ---------------------------------------------------------------------------

create or replace function public.admin_slugify(p_text text)
returns text
language sql
immutable
set search_path = public, pg_temp
as $$
    select trim(both '-' from lower(regexp_replace(coalesce(p_text, 'item'), '[^a-zA-Z0-9]+', '-', 'g')));
$$;

create or replace function public.require_admin()
returns uuid
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
declare
    v_uid uuid := auth.uid();
begin
    if v_uid is null then raise exception 'not_authenticated'; end if;
    if not public.is_admin() then raise exception 'not_admin'; end if;
    return v_uid;
end;
$$;

create or replace function public.admin_log(
    p_action text,
    p_entity_type text,
    p_entity_id uuid,
    p_metadata jsonb default '{}'::jsonb
)
returns void
language sql
security definer
set search_path = public, pg_temp
as $$
    insert into public.audit_logs (actor_id, action, entity_type, entity_id, metadata)
    values (public.require_admin(), p_action, p_entity_type, p_entity_id, p_metadata);
$$;

revoke all on function public.admin_slugify(text) from public, anon, authenticated;
revoke all on function public.require_admin() from public, anon, authenticated;
revoke all on function public.admin_log(text, text, uuid, jsonb) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- Lookup management RPCs
-- ---------------------------------------------------------------------------

create or replace function public.admin_upsert_category(
    p_id uuid default null,
    p_payload jsonb default '{}'::jsonb
)
returns public.categories
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_old public.categories;
    v_new public.categories;
    v_slug text;
begin
    perform public.require_admin();

    if p_id is null then
        if nullif(trim(p_payload->>'name'), '') is null then raise exception 'name_required'; end if;
        v_slug := coalesce(nullif(trim(p_payload->>'slug'), ''), public.admin_slugify(p_payload->>'name'));
        if exists (select 1 from public.categories where slug = v_slug) then raise exception 'slug_taken'; end if;
        insert into public.categories (slug, name, description, logo_url, display_order, is_active, is_archived)
        values (
            v_slug,
            trim(p_payload->>'name'),
            coalesce(p_payload->>'description', ''),
            nullif(p_payload->>'logo_url', ''),
            coalesce((p_payload->>'display_order')::integer, 0),
            coalesce((p_payload->>'is_active')::boolean, true),
            coalesce((p_payload->>'is_archived')::boolean, false)
        )
        returning * into v_new;
    else
        select * into v_old from public.categories where id = p_id for update;
        if not found then raise exception 'category_not_found'; end if;

        v_slug := v_old.slug;
        if nullif(trim(p_payload->>'slug'), '') is not null then
            v_slug := trim(p_payload->>'slug');
            if exists (select 1 from public.categories where slug = v_slug and id <> p_id) then
                raise exception 'slug_taken';
            end if;
        end if;

        update public.categories
        set slug = v_slug,
            name = coalesce(nullif(trim(p_payload->>'name'), ''), v_old.name),
            description = coalesce(p_payload->>'description', v_old.description),
            logo_url = case when p_payload ? 'logo_url' then nullif(p_payload->>'logo_url', '') else v_old.logo_url end,
            display_order = coalesce((p_payload->>'display_order')::integer, v_old.display_order),
            is_active = coalesce((p_payload->>'is_active')::boolean, v_old.is_active),
            is_archived = coalesce((p_payload->>'is_archived')::boolean, v_old.is_archived)
        where id = p_id
        returning * into v_new;
    end if;

    perform public.admin_log(
        case when p_id is null then 'category.create' else 'category.update' end,
        'category',
        v_new.id,
        jsonb_build_object(
            'old', case when p_id is null then null else to_jsonb(v_old) end,
            'new', to_jsonb(v_new)
        )
    );
    return v_new;
end;
$$;

create or replace function public.admin_delete_category(p_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_old public.categories;
begin
    perform public.require_admin();
    select * into v_old from public.categories where id = p_id for update;
    if not found then raise exception 'category_not_found'; end if;
    if exists (select 1 from public.tournaments where category_id = p_id)
        or exists (select 1 from public.matches where category_id = p_id) then
        raise exception 'category_in_use';
    end if;
    delete from public.categories where id = p_id;
    perform public.admin_log('category.delete', 'category', p_id, jsonb_build_object('old', to_jsonb(v_old)));
end;
$$;

create or replace function public.admin_upsert_game_mode(
    p_id uuid default null,
    p_payload jsonb default '{}'::jsonb
)
returns public.game_modes
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_old public.game_modes;
    v_new public.game_modes;
    v_slug text;
begin
    perform public.require_admin();

    if p_id is null then
        if nullif(trim(p_payload->>'name'), '') is null then raise exception 'name_required'; end if;
        v_slug := coalesce(nullif(trim(p_payload->>'slug'), ''), public.admin_slugify(p_payload->>'name'));
        if exists (select 1 from public.game_modes where slug = v_slug) then raise exception 'slug_taken'; end if;
        insert into public.game_modes (slug, name, description, logo_url, display_order, is_active, is_archived)
        values (
            v_slug,
            trim(p_payload->>'name'),
            coalesce(p_payload->>'description', ''),
            nullif(p_payload->>'logo_url', ''),
            coalesce((p_payload->>'display_order')::integer, 0),
            coalesce((p_payload->>'is_active')::boolean, true),
            coalesce((p_payload->>'is_archived')::boolean, false)
        )
        returning * into v_new;
    else
        select * into v_old from public.game_modes where id = p_id for update;
        if not found then raise exception 'game_mode_not_found'; end if;

        v_slug := v_old.slug;
        if nullif(trim(p_payload->>'slug'), '') is not null then
            v_slug := trim(p_payload->>'slug');
            if exists (select 1 from public.game_modes where slug = v_slug and id <> p_id) then
                raise exception 'slug_taken';
            end if;
        end if;

        update public.game_modes
        set slug = v_slug,
            name = coalesce(nullif(trim(p_payload->>'name'), ''), v_old.name),
            description = coalesce(p_payload->>'description', v_old.description),
            logo_url = case when p_payload ? 'logo_url' then nullif(p_payload->>'logo_url', '') else v_old.logo_url end,
            display_order = coalesce((p_payload->>'display_order')::integer, v_old.display_order),
            is_active = coalesce((p_payload->>'is_active')::boolean, v_old.is_active),
            is_archived = coalesce((p_payload->>'is_archived')::boolean, v_old.is_archived)
        where id = p_id
        returning * into v_new;
    end if;

    perform public.admin_log(
        case when p_id is null then 'game_mode.create' else 'game_mode.update' end,
        'game_mode',
        v_new.id,
        jsonb_build_object(
            'old', case when p_id is null then null else to_jsonb(v_old) end,
            'new', to_jsonb(v_new)
        )
    );
    return v_new;
end;
$$;

create or replace function public.admin_delete_game_mode(p_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_old public.game_modes;
begin
    perform public.require_admin();
    select * into v_old from public.game_modes where id = p_id for update;
    if not found then raise exception 'game_mode_not_found'; end if;
    if exists (select 1 from public.tournaments where game_mode_id = p_id)
        or exists (select 1 from public.matches where game_mode_id = p_id) then
        raise exception 'game_mode_in_use';
    end if;
    delete from public.game_modes where id = p_id;
    perform public.admin_log('game_mode.delete', 'game_mode', p_id, jsonb_build_object('old', to_jsonb(v_old)));
end;
$$;

create or replace function public.admin_upsert_tournament_type(
    p_id uuid default null,
    p_payload jsonb default '{}'::jsonb
)
returns public.tournament_types
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_old public.tournament_types;
    v_new public.tournament_types;
    v_slug text;
begin
    perform public.require_admin();

    if p_id is null then
        if nullif(trim(p_payload->>'name'), '') is null then raise exception 'name_required'; end if;
        v_slug := coalesce(nullif(trim(p_payload->>'slug'), ''), public.admin_slugify(p_payload->>'name'));
        if exists (select 1 from public.tournament_types where slug = v_slug) then raise exception 'slug_taken'; end if;
        insert into public.tournament_types (slug, name, description, logo_url, display_order, is_active, is_archived)
        values (
            v_slug,
            trim(p_payload->>'name'),
            coalesce(p_payload->>'description', ''),
            nullif(p_payload->>'logo_url', ''),
            coalesce((p_payload->>'display_order')::integer, 0),
            coalesce((p_payload->>'is_active')::boolean, true),
            coalesce((p_payload->>'is_archived')::boolean, false)
        )
        returning * into v_new;
    else
        select * into v_old from public.tournament_types where id = p_id for update;
        if not found then raise exception 'tournament_type_not_found'; end if;

        v_slug := v_old.slug;
        if nullif(trim(p_payload->>'slug'), '') is not null then
            v_slug := trim(p_payload->>'slug');
            if exists (select 1 from public.tournament_types where slug = v_slug and id <> p_id) then
                raise exception 'slug_taken';
            end if;
        end if;

        update public.tournament_types
        set slug = v_slug,
            name = coalesce(nullif(trim(p_payload->>'name'), ''), v_old.name),
            description = coalesce(p_payload->>'description', v_old.description),
            logo_url = case when p_payload ? 'logo_url' then nullif(p_payload->>'logo_url', '') else v_old.logo_url end,
            display_order = coalesce((p_payload->>'display_order')::integer, v_old.display_order),
            is_active = coalesce((p_payload->>'is_active')::boolean, v_old.is_active),
            is_archived = coalesce((p_payload->>'is_archived')::boolean, v_old.is_archived)
        where id = p_id
        returning * into v_new;
    end if;

    perform public.admin_log(
        case when p_id is null then 'tournament_type.create' else 'tournament_type.update' end,
        'tournament_type',
        v_new.id,
        jsonb_build_object(
            'old', case when p_id is null then null else to_jsonb(v_old) end,
            'new', to_jsonb(v_new)
        )
    );
    return v_new;
end;
$$;

create or replace function public.admin_delete_tournament_type(p_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_old public.tournament_types;
begin
    perform public.require_admin();
    select * into v_old from public.tournament_types where id = p_id for update;
    if not found then raise exception 'tournament_type_not_found'; end if;
    if exists (select 1 from public.tournaments where tournament_type_id = p_id)
        or exists (select 1 from public.matches where tournament_type_id = p_id) then
        raise exception 'tournament_type_in_use';
    end if;
    delete from public.tournament_types where id = p_id;
    perform public.admin_log('tournament_type.delete', 'tournament_type', p_id, jsonb_build_object('old', to_jsonb(v_old)));
end;
$$;

-- ---------------------------------------------------------------------------
-- Tournament management RPCs
-- ---------------------------------------------------------------------------

create or replace function public.admin_upsert_tournament(
    p_id uuid default null,
    p_payload jsonb default '{}'::jsonb
)
returns public.tournaments
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_old public.tournaments;
    v_new public.tournaments;
    v_slug text;
    v_mode_slug text;
    v_category uuid;
    v_game_mode uuid;
    v_type uuid;
begin
    perform public.require_admin();

    if p_id is null then
        if nullif(trim(p_payload->>'title'), '') is null then raise exception 'title_required'; end if;
        if nullif(trim(p_payload->>'starts_at'), '') is null then raise exception 'start_required'; end if;
        if nullif(trim(p_payload->>'registration_closes_at'), '') is null then raise exception 'registration_close_required'; end if;

        v_slug := coalesce(nullif(trim(p_payload->>'slug'), ''), public.admin_slugify(p_payload->>'title'));
        if v_slug is null or v_slug = '' then v_slug := 'tournament'; end if;
        if exists (select 1 from public.tournaments where slug = v_slug) then
            v_slug := v_slug || '-' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 6);
        end if;

        v_category := nullif(p_payload->>'category_id', '')::uuid;
        v_game_mode := nullif(p_payload->>'game_mode_id', '')::uuid;
        v_type := nullif(p_payload->>'tournament_type_id', '')::uuid;

        if v_category is not null and not exists (
            select 1 from public.categories where id = v_category and is_active and not is_archived
        ) then raise exception 'category_unavailable'; end if;
        if v_game_mode is not null and not exists (
            select 1 from public.game_modes where id = v_game_mode and is_active and not is_archived
        ) then raise exception 'game_mode_unavailable'; end if;
        if v_type is not null and not exists (
            select 1 from public.tournament_types where id = v_type and is_active and not is_archived
        ) then raise exception 'tournament_type_unavailable'; end if;

        insert into public.tournaments (
            slug, title, game, mode, entry_type, entry_fee_paise, prize_pool_paise,
            per_kill_paise, max_slots, min_team_size, max_team_size, status,
            registration_opens_at, registration_closes_at, starts_at, ends_at,
            banner_url, logo_url, description, map, region, is_featured,
            category_id, game_mode_id, tournament_type_id, version, format
        ) values (
            v_slug,
            trim(p_payload->>'title'),
            coalesce(nullif(p_payload->>'game', ''), 'free_fire'),
            coalesce(nullif(p_payload->>'mode', ''), 'squad')::public.game_mode,
            coalesce(nullif(p_payload->>'entry_type', ''), 'free')::public.entry_type,
            coalesce((p_payload->>'entry_fee_paise')::integer, 0),
            coalesce((p_payload->>'prize_pool_paise')::integer, 0),
            coalesce((p_payload->>'per_kill_paise')::integer, 0),
            coalesce((p_payload->>'max_slots')::integer, 64),
            coalesce((p_payload->>'min_team_size')::integer, 1),
            coalesce((p_payload->>'max_team_size')::integer, 1),
            coalesce(nullif(p_payload->>'status', ''), 'draft')::public.tournament_status,
            coalesce(nullif(p_payload->>'registration_opens_at', '')::timestamptz, now()),
            (p_payload->>'registration_closes_at')::timestamptz,
            (p_payload->>'starts_at')::timestamptz,
            nullif(p_payload->>'ends_at', '')::timestamptz,
            nullif(p_payload->>'banner_url', ''),
            nullif(p_payload->>'logo_url', ''),
            coalesce(p_payload->>'description', ''),
            nullif(p_payload->>'map', ''),
            coalesce(nullif(p_payload->>'region', ''), 'IN'),
            coalesce((p_payload->>'is_featured')::boolean, false),
            v_category,
            v_game_mode,
            v_type,
            coalesce(nullif(p_payload->>'version', ''), '1.0'),
            nullif(p_payload->>'format', '')
        )
        returning * into v_new;
    else
        select * into v_old from public.tournaments where id = p_id for update;
        if not found then raise exception 'tournament_not_found'; end if;

        v_new := v_old;

        if p_payload ? 'title' then v_new.title := trim(p_payload->>'title'); end if;
        if p_payload ? 'slug' and nullif(trim(p_payload->>'slug'), '') is not null then
            if exists (select 1 from public.tournaments where slug = trim(p_payload->>'slug') and id <> p_id) then
                raise exception 'slug_taken';
            end if;
            v_new.slug := trim(p_payload->>'slug');
        end if;
        if p_payload ? 'description' then v_new.description := coalesce(p_payload->>'description', ''); end if;
        if p_payload ? 'game' then v_new.game := p_payload->>'game'; end if;
        if p_payload ? 'mode' then v_new.mode := (p_payload->>'mode')::public.game_mode; end if;
        if p_payload ? 'entry_type' then v_new.entry_type := (p_payload->>'entry_type')::public.entry_type; end if;
        if p_payload ? 'entry_fee_paise' then v_new.entry_fee_paise := (p_payload->>'entry_fee_paise')::integer; end if;
        if p_payload ? 'prize_pool_paise' then v_new.prize_pool_paise := (p_payload->>'prize_pool_paise')::integer; end if;
        if p_payload ? 'per_kill_paise' then v_new.per_kill_paise := (p_payload->>'per_kill_paise')::integer; end if;
        if p_payload ? 'max_slots' then v_new.max_slots := (p_payload->>'max_slots')::integer; end if;
        if p_payload ? 'min_team_size' then v_new.min_team_size := (p_payload->>'min_team_size')::integer; end if;
        if p_payload ? 'max_team_size' then v_new.max_team_size := (p_payload->>'max_team_size')::integer; end if;
        if p_payload ? 'registration_opens_at' then
            v_new.registration_opens_at := (p_payload->>'registration_opens_at')::timestamptz;
        end if;
        if p_payload ? 'registration_closes_at' then
            v_new.registration_closes_at := (p_payload->>'registration_closes_at')::timestamptz;
        end if;
        if p_payload ? 'starts_at' then v_new.starts_at := (p_payload->>'starts_at')::timestamptz; end if;
        if p_payload ? 'ends_at' then v_new.ends_at := nullif(p_payload->>'ends_at', '')::timestamptz; end if;
        if p_payload ? 'banner_url' then v_new.banner_url := nullif(p_payload->>'banner_url', ''); end if;
        if p_payload ? 'logo_url' then v_new.logo_url := nullif(p_payload->>'logo_url', ''); end if;
        if p_payload ? 'map' then v_new.map := nullif(p_payload->>'map', ''); end if;
        if p_payload ? 'region' then v_new.region := p_payload->>'region'; end if;
        if p_payload ? 'is_featured' then v_new.is_featured := (p_payload->>'is_featured')::boolean; end if;
        if p_payload ? 'version' then v_new.version := coalesce(nullif(p_payload->>'version', ''), '1.0'); end if;
        if p_payload ? 'format' then v_new.format := nullif(p_payload->>'format', ''); end if;
        if p_payload ? 'status' then v_new.status := (p_payload->>'status')::public.tournament_status; end if;
        if p_payload ? 'archived_at' then v_new.archived_at := nullif(p_payload->>'archived_at', '')::timestamptz; end if;

        if p_payload ? 'category_id' then
            v_category := nullif(p_payload->>'category_id', '')::uuid;
            if v_category is not null
                and v_category is distinct from v_old.category_id
                and not exists (select 1 from public.categories where id = v_category and is_active and not is_archived) then
                raise exception 'category_unavailable';
            end if;
            v_new.category_id := v_category;
        end if;

        if p_payload ? 'game_mode_id' then
            v_game_mode := nullif(p_payload->>'game_mode_id', '')::uuid;
            if v_game_mode is not null
                and v_game_mode is distinct from v_old.game_mode_id
                and not exists (select 1 from public.game_modes where id = v_game_mode and is_active and not is_archived) then
                raise exception 'game_mode_unavailable';
            end if;
            v_new.game_mode_id := v_game_mode;
        end if;

        if p_payload ? 'tournament_type_id' then
            v_type := nullif(p_payload->>'tournament_type_id', '')::uuid;
            if v_type is not null
                and v_type is distinct from v_old.tournament_type_id
                and not exists (select 1 from public.tournament_types where id = v_type and is_active and not is_archived) then
                raise exception 'tournament_type_unavailable';
            end if;
            v_new.tournament_type_id := v_type;
        end if;

        if v_new.game_mode_id is not null then
            select slug into v_mode_slug from public.game_modes where id = v_new.game_mode_id;
            if v_mode_slug in ('solo', 'duo', 'squad', 'clash-squad') then
                v_new.mode := replace(v_mode_slug, '-', '_')::public.game_mode;
            end if;
        end if;

        if v_new.entry_fee_paise <= 0 then
            v_new.entry_type := 'free';
            v_new.entry_fee_paise := 0;
        else
            v_new.entry_type := 'paid';
        end if;

        if v_new.max_slots < v_new.slots_filled then
            raise exception 'capacity_below_registrations';
        end if;
        if v_new.registration_closes_at <= v_new.registration_opens_at then
            raise exception 'invalid_registration_window';
        end if;
        if v_new.starts_at < v_new.registration_closes_at then
            raise exception 'start_before_registration_close';
        end if;
        if v_new.ends_at is not null and v_new.ends_at < v_new.starts_at then
            raise exception 'end_before_start';
        end if;
        if v_new.status <> 'draft' and v_new.archived_at is not null then
            raise exception 'archived_tournament_must_be_draft';
        end if;

        update public.tournaments set
            slug = v_new.slug,
            title = v_new.title,
            game = v_new.game,
            mode = v_new.mode,
            entry_type = v_new.entry_type,
            entry_fee_paise = v_new.entry_fee_paise,
            prize_pool_paise = v_new.prize_pool_paise,
            per_kill_paise = v_new.per_kill_paise,
            max_slots = v_new.max_slots,
            min_team_size = v_new.min_team_size,
            max_team_size = v_new.max_team_size,
            status = v_new.status,
            registration_opens_at = v_new.registration_opens_at,
            registration_closes_at = v_new.registration_closes_at,
            starts_at = v_new.starts_at,
            ends_at = v_new.ends_at,
            banner_url = v_new.banner_url,
            logo_url = v_new.logo_url,
            description = v_new.description,
            map = v_new.map,
            region = v_new.region,
            is_featured = v_new.is_featured,
            category_id = v_new.category_id,
            game_mode_id = v_new.game_mode_id,
            tournament_type_id = v_new.tournament_type_id,
            version = v_new.version,
            format = v_new.format,
            archived_at = v_new.archived_at
        where id = p_id
        returning * into v_new;

        if p_payload ? 'rules' then
            delete from public.tournament_rules where tournament_id = p_id;
            insert into public.tournament_rules (tournament_id, position, title, body)
            select p_id, ord::integer, trim(item->>'title'), trim(item->>'body')
            from jsonb_array_elements(coalesce(p_payload->'rules', '[]'::jsonb)) with ordinality as t(item, ord)
            where nullif(trim(item->>'title'), '') is not null
              and nullif(trim(item->>'body'), '') is not null;
        end if;
    end if;

    perform public.admin_log(
        case when p_id is null then 'tournament.create' else 'tournament.update' end,
        'tournament',
        v_new.id,
        jsonb_build_object(
            'old', case when p_id is null then null else to_jsonb(v_old) end,
            'new', to_jsonb(v_new)
        )
    );
    return v_new;
end;
$$;

create or replace function public.admin_set_tournament_status(
    p_id uuid,
    p_status public.tournament_status
)
returns public.tournaments
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_old public.tournaments;
    v_new public.tournaments;
begin
    perform public.require_admin();
    select * into v_old from public.tournaments where id = p_id for update;
    if not found then raise exception 'tournament_not_found'; end if;
    if v_old.status = 'completed' and p_status <> 'completed' then
        raise exception 'completed_tournament_is_locked';
    end if;

    update public.tournaments set status = p_status where id = p_id returning * into v_new;
    perform public.admin_log(
        'tournament.status',
        'tournament',
        p_id,
        jsonb_build_object('old_status', v_old.status, 'new_status', v_new.status)
    );
    return v_new;
end;
$$;

create or replace function public.admin_delete_tournament(p_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_old public.tournaments;
begin
    perform public.require_admin();
    select * into v_old from public.tournaments where id = p_id for update;
    if not found then raise exception 'tournament_not_found'; end if;
    if exists (select 1 from public.registrations where tournament_id = p_id) then
        raise exception 'tournament_has_registrations';
    end if;
    if v_old.status <> 'draft' then raise exception 'only_draft_tournaments_can_be_deleted'; end if;
    delete from public.tournaments where id = p_id;
    perform public.admin_log('tournament.delete', 'tournament', p_id, jsonb_build_object('old', to_jsonb(v_old)));
end;
$$;

-- ---------------------------------------------------------------------------
-- Match management RPCs
-- ---------------------------------------------------------------------------

create or replace function public.admin_upsert_match(
    p_id uuid default null,
    p_payload jsonb default '{}'::jsonb,
    p_confirm_locked boolean default false
)
returns public.matches
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_old public.matches;
    v_new public.matches;
    v_t public.tournaments;
    v_category uuid;
    v_game_mode uuid;
    v_type uuid;
    v_locked boolean := false;
begin
    perform public.require_admin();

    if p_id is null then
        if nullif(trim(p_payload->>'name'), '') is null then raise exception 'name_required'; end if;
        if nullif(trim(p_payload->>'scheduled_at'), '') is null then raise exception 'schedule_required'; end if;

        if nullif(p_payload->>'tournament_id', '') is not null then
            select * into v_t from public.tournaments where id = (p_payload->>'tournament_id')::uuid;
            if not found then raise exception 'tournament_not_found'; end if;
        end if;

        v_category := coalesce(nullif(p_payload->>'category_id', '')::uuid, v_t.category_id);
        v_game_mode := coalesce(nullif(p_payload->>'game_mode_id', '')::uuid, v_t.game_mode_id);
        v_type := coalesce(nullif(p_payload->>'tournament_type_id', '')::uuid, v_t.tournament_type_id);

        if v_category is not null and not exists (
            select 1 from public.categories where id = v_category and is_active and not is_archived
        ) then raise exception 'category_unavailable'; end if;
        if v_game_mode is not null and not exists (
            select 1 from public.game_modes where id = v_game_mode and is_active and not is_archived
        ) then raise exception 'game_mode_unavailable'; end if;
        if v_type is not null and not exists (
            select 1 from public.tournament_types where id = v_type and is_active and not is_archived
        ) then raise exception 'tournament_type_unavailable'; end if;

        insert into public.matches (
            tournament_id, round, name, match_number, status,
            scheduled_at, check_in_opens_at, started_at, ended_at,
            map, mode, max_teams, game, category_id, game_mode_id, tournament_type_id,
            banner_url, logo_url, prize_pool_paise, per_kill_paise, entry_fee_paise,
            version, format, max_slots, registration_opens_at, registration_closes_at,
            is_published
        ) values (
            nullif(p_payload->>'tournament_id', '')::uuid,
            coalesce((p_payload->>'round')::integer, 1),
            trim(p_payload->>'name'),
            nullif(p_payload->>'match_number', ''),
            coalesce(nullif(p_payload->>'status', ''), 'scheduled')::public.match_status,
            (p_payload->>'scheduled_at')::timestamptz,
            nullif(p_payload->>'check_in_opens_at', '')::timestamptz,
            nullif(p_payload->>'started_at', '')::timestamptz,
            nullif(p_payload->>'ended_at', '')::timestamptz,
            nullif(p_payload->>'map', ''),
            nullif(p_payload->>'mode', ''),
            nullif(p_payload->>'max_teams', '')::integer,
            coalesce(nullif(p_payload->>'game', ''), v_t.game, 'free_fire'),
            v_category,
            v_game_mode,
            v_type,
            nullif(p_payload->>'banner_url', ''),
            nullif(p_payload->>'logo_url', ''),
            coalesce((p_payload->>'prize_pool_paise')::integer, 0),
            coalesce((p_payload->>'per_kill_paise')::integer, 0),
            coalesce((p_payload->>'entry_fee_paise')::integer, 0),
            coalesce(nullif(p_payload->>'version', ''), '1.0'),
            nullif(p_payload->>'format', ''),
            nullif(p_payload->>'max_slots', '')::integer,
            nullif(p_payload->>'registration_opens_at', '')::timestamptz,
            nullif(p_payload->>'registration_closes_at', '')::timestamptz,
            coalesce((p_payload->>'is_published')::boolean, false)
        )
        returning * into v_new;
    else
        select * into v_old from public.matches where id = p_id for update;
        if not found then raise exception 'match_not_found'; end if;

        v_new := v_old;

        if p_payload ? 'tournament_id' then
            v_new.tournament_id := nullif(p_payload->>'tournament_id', '')::uuid;
            if v_new.tournament_id is not null
                and not exists (select 1 from public.tournaments where id = v_new.tournament_id) then
                raise exception 'tournament_not_found';
            end if;
        end if;
        if p_payload ? 'name' then v_new.name := trim(p_payload->>'name'); end if;
        if p_payload ? 'match_number' then v_new.match_number := nullif(p_payload->>'match_number', ''); end if;
        if p_payload ? 'round' then v_new.round := (p_payload->>'round')::integer; end if;
        if p_payload ? 'game' then v_new.game := nullif(p_payload->>'game', ''); end if;
        if p_payload ? 'mode' then v_new.mode := nullif(p_payload->>'mode', ''); end if;
        if p_payload ? 'status' then v_new.status := (p_payload->>'status')::public.match_status; end if;
        if p_payload ? 'scheduled_at' then v_new.scheduled_at := (p_payload->>'scheduled_at')::timestamptz; end if;
        if p_payload ? 'check_in_opens_at' then v_new.check_in_opens_at := nullif(p_payload->>'check_in_opens_at', '')::timestamptz; end if;
        if p_payload ? 'started_at' then v_new.started_at := nullif(p_payload->>'started_at', '')::timestamptz; end if;
        if p_payload ? 'ended_at' then v_new.ended_at := nullif(p_payload->>'ended_at', '')::timestamptz; end if;
        if p_payload ? 'map' then v_new.map := nullif(p_payload->>'map', ''); end if;
        if p_payload ? 'max_teams' then v_new.max_teams := nullif(p_payload->>'max_teams', '')::integer; end if;
        if p_payload ? 'banner_url' then v_new.banner_url := nullif(p_payload->>'banner_url', ''); end if;
        if p_payload ? 'logo_url' then v_new.logo_url := nullif(p_payload->>'logo_url', ''); end if;
        if p_payload ? 'prize_pool_paise' then v_new.prize_pool_paise := (p_payload->>'prize_pool_paise')::integer; end if;
        if p_payload ? 'per_kill_paise' then v_new.per_kill_paise := (p_payload->>'per_kill_paise')::integer; end if;
        if p_payload ? 'entry_fee_paise' then v_new.entry_fee_paise := (p_payload->>'entry_fee_paise')::integer; end if;
        if p_payload ? 'version' then v_new.version := coalesce(nullif(p_payload->>'version', ''), '1.0'); end if;
        if p_payload ? 'format' then v_new.format := nullif(p_payload->>'format', ''); end if;
        if p_payload ? 'max_slots' then v_new.max_slots := nullif(p_payload->>'max_slots', '')::integer; end if;
        if p_payload ? 'registration_opens_at' then
            v_new.registration_opens_at := nullif(p_payload->>'registration_opens_at', '')::timestamptz;
        end if;
        if p_payload ? 'registration_closes_at' then
            v_new.registration_closes_at := nullif(p_payload->>'registration_closes_at', '')::timestamptz;
        end if;
        if p_payload ? 'is_published' then v_new.is_published := (p_payload->>'is_published')::boolean; end if;
        if p_payload ? 'archived_at' then v_new.archived_at := nullif(p_payload->>'archived_at', '')::timestamptz; end if;

        if p_payload ? 'category_id' then
            v_category := nullif(p_payload->>'category_id', '')::uuid;
            if v_category is not null
                and v_category is distinct from v_old.category_id
                and not exists (select 1 from public.categories where id = v_category and is_active and not is_archived) then
                raise exception 'category_unavailable';
            end if;
            v_new.category_id := v_category;
        end if;

        if p_payload ? 'game_mode_id' then
            v_game_mode := nullif(p_payload->>'game_mode_id', '')::uuid;
            if v_game_mode is not null
                and v_game_mode is distinct from v_old.game_mode_id
                and not exists (select 1 from public.game_modes where id = v_game_mode and is_active and not is_archived) then
                raise exception 'game_mode_unavailable';
            end if;
            v_new.game_mode_id := v_game_mode;
        end if;

        if p_payload ? 'tournament_type_id' then
            v_type := nullif(p_payload->>'tournament_type_id', '')::uuid;
            if v_type is not null
                and v_type is distinct from v_old.tournament_type_id
                and not exists (select 1 from public.tournament_types where id = v_type and is_active and not is_archived) then
                raise exception 'tournament_type_unavailable';
            end if;
            v_new.tournament_type_id := v_type;
        end if;

        if v_new.max_slots is not null and v_new.max_slots < v_new.slots_filled then
            raise exception 'capacity_below_registrations';
        end if;
        if v_new.registration_opens_at is not null and v_new.registration_closes_at is not null
            and v_new.registration_closes_at <= v_new.registration_opens_at then
            raise exception 'invalid_registration_window';
        end if;
        if v_new.ended_at is not null and v_new.ended_at < v_new.started_at then
            raise exception 'end_before_start';
        end if;

        v_locked := v_old.status in ('live', 'completed');
        if v_locked and not p_confirm_locked then
            if (p_payload ? 'scheduled_at' and v_new.scheduled_at is distinct from v_old.scheduled_at)
                or (p_payload ? 'prize_pool_paise' and v_new.prize_pool_paise is distinct from v_old.prize_pool_paise)
                or (p_payload ? 'per_kill_paise' and v_new.per_kill_paise is distinct from v_old.per_kill_paise)
                or (p_payload ? 'entry_fee_paise' and v_new.entry_fee_paise is distinct from v_old.entry_fee_paise)
                or (p_payload ? 'max_slots' and v_new.max_slots is distinct from v_old.max_slots)
                or (p_payload ? 'tournament_id' and v_new.tournament_id is distinct from v_old.tournament_id) then
                raise exception 'match_locked_confirmation_required';
            end if;
        end if;

        update public.matches set
            tournament_id = v_new.tournament_id,
            round = v_new.round,
            name = v_new.name,
            match_number = v_new.match_number,
            status = v_new.status,
            scheduled_at = v_new.scheduled_at,
            check_in_opens_at = v_new.check_in_opens_at,
            started_at = v_new.started_at,
            ended_at = v_new.ended_at,
            map = v_new.map,
            mode = v_new.mode,
            max_teams = v_new.max_teams,
            game = v_new.game,
            category_id = v_new.category_id,
            game_mode_id = v_new.game_mode_id,
            tournament_type_id = v_new.tournament_type_id,
            banner_url = v_new.banner_url,
            logo_url = v_new.logo_url,
            prize_pool_paise = v_new.prize_pool_paise,
            per_kill_paise = v_new.per_kill_paise,
            entry_fee_paise = v_new.entry_fee_paise,
            version = v_new.version,
            format = v_new.format,
            max_slots = v_new.max_slots,
            registration_opens_at = v_new.registration_opens_at,
            registration_closes_at = v_new.registration_closes_at,
            is_published = v_new.is_published,
            archived_at = v_new.archived_at
        where id = p_id
        returning * into v_new;
    end if;

    perform public.admin_log(
        case when p_id is null then 'match.create' else 'match.update' end,
        'match',
        v_new.id,
        jsonb_build_object(
            'old', case when p_id is null then null else to_jsonb(v_old) end,
            'new', to_jsonb(v_new),
            'confirmed_locked_edit', v_locked and p_confirm_locked
        )
    );
    return v_new;
end;
$$;

create or replace function public.admin_set_match_status(
    p_id uuid,
    p_status public.match_status
)
returns public.matches
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_old public.matches;
    v_new public.matches;
begin
    perform public.require_admin();
    select * into v_old from public.matches where id = p_id for update;
    if not found then raise exception 'match_not_found'; end if;
    if v_old.status = 'completed' and p_status <> 'completed' then
        raise exception 'completed_match_is_locked';
    end if;

    update public.matches set
        status = p_status,
        started_at = case when p_status = 'live' and started_at is null then now() else started_at end,
        ended_at = case when p_status = 'completed' and ended_at is null then now() else ended_at end
    where id = p_id
    returning * into v_new;

    perform public.admin_log(
        'match.status',
        'match',
        p_id,
        jsonb_build_object('old_status', v_old.status, 'new_status', v_new.status)
    );
    return v_new;
end;
$$;

create or replace function public.admin_set_match_published(
    p_id uuid,
    p_published boolean
)
returns public.matches
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_old public.matches;
    v_new public.matches;
begin
    perform public.require_admin();
    select * into v_old from public.matches where id = p_id for update;
    if not found then raise exception 'match_not_found'; end if;

    update public.matches set is_published = p_published where id = p_id returning * into v_new;
    perform public.admin_log(
        case when p_published then 'match.publish' else 'match.unpublish' end,
        'match',
        p_id,
        jsonb_build_object('old', v_old.is_published, 'new', v_new.is_published)
    );
    return v_new;
end;
$$;

create or replace function public.admin_duplicate_match(p_id uuid)
returns public.matches
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_old public.matches;
    v_new public.matches;
begin
    perform public.require_admin();
    select * into v_old from public.matches where id = p_id;
    if not found then raise exception 'match_not_found'; end if;

    insert into public.matches (
        tournament_id, round, name, match_number, status, scheduled_at,
        check_in_opens_at, game, category_id, game_mode_id, tournament_type_id,
        banner_url, logo_url, prize_pool_paise, per_kill_paise, entry_fee_paise,
        version, format, max_slots, registration_opens_at, registration_closes_at,
        is_published, map, mode, max_teams
    ) values (
        v_old.tournament_id, v_old.round, v_old.name || ' (copy)', v_old.match_number,
        'scheduled', v_old.scheduled_at,
        v_old.check_in_opens_at, v_old.game, v_old.category_id, v_old.game_mode_id,
        v_old.tournament_type_id, v_old.banner_url, v_old.logo_url,
        v_old.prize_pool_paise, v_old.per_kill_paise, v_old.entry_fee_paise,
        v_old.version, v_old.format, v_old.max_slots,
        v_old.registration_opens_at, v_old.registration_closes_at,
        false, v_old.map, v_old.mode, v_old.max_teams
    )
    returning * into v_new;

    perform public.admin_log(
        'match.duplicate',
        'match',
        v_new.id,
        jsonb_build_object('source_id', p_id, 'new', to_jsonb(v_new))
    );
    return v_new;
end;
$$;

create or replace function public.admin_delete_match(p_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_old public.matches;
begin
    perform public.require_admin();
    select * into v_old from public.matches where id = p_id for update;
    if not found then raise exception 'match_not_found'; end if;
    if exists (select 1 from public.match_registrations where match_id = p_id)
        or exists (select 1 from public.match_participants where match_id = p_id) then
        raise exception 'match_has_registrations';
    end if;
    delete from public.matches where id = p_id;
    perform public.admin_log('match.delete', 'match', p_id, jsonb_build_object('old', to_jsonb(v_old)));
end;
$$;

-- ---------------------------------------------------------------------------
-- Atomic match registration
-- ---------------------------------------------------------------------------

create or replace function public.register_for_match(
    p_match_id uuid,
    p_team_id uuid default null
)
returns public.match_registrations
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_uid uuid := auth.uid();
    v_m public.matches;
    v_t public.tournaments;
    v_reg public.match_registrations;
    v_game text;
    v_slot integer;
    v_is_team boolean := p_team_id is not null;
    v_member_count integer := 0;
    v_team_size integer := 1;
    v_existing boolean := false;
begin
    if v_uid is null then raise exception 'not_authenticated'; end if;

    -- Row lock serialises every concurrent registration for this match.
    select * into v_m from public.matches where id = p_match_id for update;
    if not found then raise exception 'match_not_found'; end if;
    if not v_m.is_published then raise exception 'match_not_available'; end if;
    if v_m.archived_at is not null then raise exception 'match_not_available'; end if;
    if v_m.status not in ('scheduled', 'check_in') then raise exception 'match_registration_closed'; end if;
    if v_m.registration_opens_at is not null and now() < v_m.registration_opens_at then
        raise exception 'registration_window_closed';
    end if;
    if v_m.registration_closes_at is not null and now() > v_m.registration_closes_at then
        raise exception 'registration_window_closed';
    end if;

    v_game := v_m.game;
    if v_game is null and v_m.tournament_id is not null then
        select * into v_t from public.tournaments where id = v_m.tournament_id;
        v_game := v_t.game;
        v_team_size := coalesce(v_t.max_team_size, 1);
    end if;

    if v_game is not null and not exists (
        select 1 from public.player_game_accounts where user_id = v_uid and game = v_game
    ) then
        raise exception 'game_account_required';
    end if;

    if v_is_team then
        if not exists (select 1 from public.teams where id = p_team_id) then
            raise exception 'team_not_found';
        end if;
        if not exists (select 1 from public.team_members where team_id = p_team_id and user_id = v_uid) then
            raise exception 'not_team_member';
        end if;
        select count(*) into v_member_count from public.team_members where team_id = p_team_id;
        if v_team_size > 1 and v_member_count > v_team_size then
            raise exception 'team_too_large';
        end if;
    end if;

    -- An existing active row is a duplicate, not an overbooking opportunity.
    select * into v_reg from public.match_registrations
    where match_id = p_match_id and user_id = v_uid
    for update;
    v_existing := found;

    if v_existing and v_reg.status in ('confirmed', 'waitlisted') then
        raise exception 'already_registered';
    end if;

    if v_m.max_slots is not null and v_m.slots_filled >= v_m.max_slots then
        raise exception 'match_full';
    end if;

    v_slot := coalesce(v_m.slots_filled, 0) + 1;
    update public.matches set slots_filled = coalesce(slots_filled, 0) + 1 where id = p_match_id;

    if v_existing then
        update public.match_registrations
        set status = 'confirmed',
            slot_no = v_slot,
            team_id = p_team_id,
            registration_type = case when v_is_team then 'team' else 'solo' end,
            cancelled_at = null,
            created_at = now()
        where id = v_reg.id
        returning * into v_reg;
    else
        insert into public.match_registrations (
            match_id, user_id, team_id, registration_type, status, slot_no
        ) values (
            p_match_id, v_uid, p_team_id,
            case when v_is_team then 'team' else 'solo' end,
            'confirmed', v_slot
        )
        returning * into v_reg;
    end if;

    perform public.notify(
        v_uid,
        'registration',
        'Match registration confirmed',
        v_m.name,
        jsonb_build_object('match_id', p_match_id, 'registration_id', v_reg.id)
    );

    return v_reg;
end;
$$;

create or replace function public.cancel_match_registration(p_registration_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_uid uuid := auth.uid();
    v_reg public.match_registrations;
    v_m public.matches;
begin
    if v_uid is null then raise exception 'not_authenticated'; end if;

    select * into v_reg from public.match_registrations where id = p_registration_id for update;
    if not found then raise exception 'match_registration_not_found'; end if;
    if v_reg.user_id <> v_uid and not public.is_admin() then raise exception 'not_allowed'; end if;
    if v_reg.status = 'cancelled' then return; end if;

    select * into v_m from public.matches where id = v_reg.match_id for update;

    update public.match_registrations
    set status = 'cancelled', cancelled_at = now()
    where id = v_reg.id;

    if v_reg.status = 'confirmed' then
        update public.matches
        set slots_filled = greatest(coalesce(slots_filled, 0) - 1, 0)
        where id = v_reg.match_id;
    end if;

    perform public.notify(
        v_uid,
        'registration',
        'Match registration cancelled',
        v_m.name,
        jsonb_build_object('match_id', v_reg.match_id, 'registration_id', v_reg.id)
    );
end;
$$;

-- Keep tournament cancellation consistent with match registrations: a player
-- who leaves the tournament also releases their match slots for that event.
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
    v_mr public.match_registrations;
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

    for v_mr in
        select mr.* from public.match_registrations mr
        join public.matches m on m.id = mr.match_id
        where m.tournament_id = v_t.id
          and mr.user_id = v_reg.user_id
          and mr.status = 'confirmed'
        for update of mr
    loop
        update public.match_registrations
        set status = 'cancelled', cancelled_at = now()
        where id = v_mr.id;
        update public.matches
        set slots_filled = greatest(coalesce(slots_filled, 0) - 1, 0)
        where id = v_mr.match_id;
    end loop;

    delete from public.match_participants where registration_id = v_reg.id;
end;
$$;

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------

grant execute on function public.is_admin() to anon;

grant execute on function public.admin_upsert_category(uuid, jsonb) to authenticated;
grant execute on function public.admin_delete_category(uuid) to authenticated;
grant execute on function public.admin_upsert_game_mode(uuid, jsonb) to authenticated;
grant execute on function public.admin_delete_game_mode(uuid) to authenticated;
grant execute on function public.admin_upsert_tournament_type(uuid, jsonb) to authenticated;
grant execute on function public.admin_delete_tournament_type(uuid) to authenticated;
grant execute on function public.admin_upsert_tournament(uuid, jsonb) to authenticated;
grant execute on function public.admin_set_tournament_status(uuid, public.tournament_status) to authenticated;
grant execute on function public.admin_delete_tournament(uuid) to authenticated;
grant execute on function public.admin_upsert_match(uuid, jsonb, boolean) to authenticated;
grant execute on function public.admin_set_match_status(uuid, public.match_status) to authenticated;
grant execute on function public.admin_set_match_published(uuid, boolean) to authenticated;
grant execute on function public.admin_duplicate_match(uuid) to authenticated;
grant execute on function public.admin_delete_match(uuid) to authenticated;
grant execute on function public.register_for_match(uuid, uuid) to authenticated;
grant execute on function public.cancel_match_registration(uuid) to authenticated;
