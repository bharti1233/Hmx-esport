-- 0019: admin-managed content for the Liquid Glass redesign.
--
-- This migration is additive. It does not change any existing row, policy or
-- RPC signature, and it does not introduce a client write path.
--
--  * Lookup tables gain `image_url` for wide category artwork, separate from
--    the existing square `logo_url`. Admins keep writing through SECURITY
--    DEFINER RPCs; the tables still have no INSERT/UPDATE/DELETE policy.
--  * `promotional_banners` holds the Home carousel records. Public reads see
--    only active, in-window rows. Admins manage them through RPCs.
--  * `announcements` gains admin CRUD RPCs. The table itself keeps its
--    published-or-admin SELECT policy and no client write grants.
--
-- Storage decisions (no new bucket):
--  * Promotional banner images reuse `tournament-banners`, path
--    `banners/<banner_id>/banner-<timestamp>.<ext>`. That bucket is already
--    public-read/admin-write.
--  * Category artwork reuses `category-logos`, path
--    `categories/<category_id>/artwork-<timestamp>.<ext>`. The bucket limit is
--    raised from 2 MB to 5 MB so a wide artwork and a square logo can both be
--    stored at production quality.

-- ---------------------------------------------------------------------------
-- Lookup artwork
-- ---------------------------------------------------------------------------

alter table public.categories add column if not exists image_url text;
alter table public.game_modes add column if not exists image_url text;
alter table public.tournament_types add column if not exists image_url text;

comment on column public.categories.image_url is
    'Wide category artwork used by the Home/Arena discovery grid. logo_url stays the square badge.';
comment on column public.game_modes.image_url is
    'Optional wide artwork for game-mode discovery.';
comment on column public.tournament_types.image_url is
    'Optional wide artwork for tournament-type discovery.';

-- ---------------------------------------------------------------------------
-- Promotional banners
-- ---------------------------------------------------------------------------

create table if not exists public.promotional_banners (
    id uuid primary key default gen_random_uuid(),
    title text not null check (char_length(title) between 1 and 120),
    subtitle text not null default '' check (char_length(subtitle) <= 240),
    image_url text,
    destination text check (destination is null or char_length(destination) <= 500),
    display_order integer not null default 0,
    is_active boolean not null default true,
    starts_at timestamptz,
    ends_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint promotional_banners_window_check check (
        ends_at is null or starts_at is null or ends_at > starts_at
    )
);

create index if not exists promotional_banners_order_idx
    on public.promotional_banners (display_order, created_at desc);

create index if not exists promotional_banners_active_idx
    on public.promotional_banners (display_order)
    where is_active;

drop trigger if exists promotional_banners_updated_at on public.promotional_banners;
create trigger promotional_banners_updated_at before update on public.promotional_banners
    for each row execute function public.set_updated_at();

alter table public.promotional_banners enable row level security;

drop policy if exists promotional_banners_select on public.promotional_banners;
create policy promotional_banners_select on public.promotional_banners
    for select using (
        public.is_admin()
        or (
            is_active = true
            and (starts_at is null or starts_at <= now())
            and (ends_at is null or ends_at > now())
        )
    );

revoke all on public.promotional_banners from anon, authenticated;
grant select on public.promotional_banners to anon, authenticated;

-- ---------------------------------------------------------------------------
-- Lookup artwork RPC
-- ---------------------------------------------------------------------------

create or replace function public.admin_set_lookup_image(
    p_kind text,
    p_id uuid,
    p_image_url text
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_old jsonb;
    v_new jsonb;
begin
    perform public.require_admin();

    if p_kind = 'category' then
        select to_jsonb(c) into v_old from public.categories c where c.id = p_id for update;
        if v_old is null then raise exception 'category_not_found'; end if;
        update public.categories
        set image_url = nullif(trim(p_image_url), '')
        where id = p_id
        returning to_jsonb(categories) into v_new;
    elsif p_kind = 'game_mode' then
        select to_jsonb(g) into v_old from public.game_modes g where g.id = p_id for update;
        if v_old is null then raise exception 'game_mode_not_found'; end if;
        update public.game_modes
        set image_url = nullif(trim(p_image_url), '')
        where id = p_id
        returning to_jsonb(game_modes) into v_new;
    elsif p_kind = 'tournament_type' then
        select to_jsonb(t) into v_old from public.tournament_types t where t.id = p_id for update;
        if v_old is null then raise exception 'tournament_type_not_found'; end if;
        update public.tournament_types
        set image_url = nullif(trim(p_image_url), '')
        where id = p_id
        returning to_jsonb(tournament_types) into v_new;
    else
        raise exception 'invalid_lookup_kind';
    end if;

    perform public.admin_log(
        p_kind || '.image',
        p_kind,
        p_id,
        jsonb_build_object('old', v_old, 'new', v_new)
    );
end;
$$;

-- ---------------------------------------------------------------------------
-- Promotional banner RPCs
-- ---------------------------------------------------------------------------

create or replace function public.admin_upsert_banner(
    p_id uuid default null,
    p_payload jsonb default '{}'::jsonb
)
returns public.promotional_banners
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_old public.promotional_banners;
    v_new public.promotional_banners;
begin
    perform public.require_admin();

    if p_id is null then
        if nullif(trim(p_payload->>'title'), '') is null then raise exception 'title_required'; end if;
        insert into public.promotional_banners (
            title, subtitle, image_url, destination, display_order,
            is_active, starts_at, ends_at
        )
        values (
            trim(p_payload->>'title'),
            coalesce(p_payload->>'subtitle', ''),
            nullif(trim(p_payload->>'image_url'), ''),
            nullif(trim(p_payload->>'destination'), ''),
            coalesce((p_payload->>'display_order')::integer, 0),
            coalesce((p_payload->>'is_active')::boolean, true),
            nullif(p_payload->>'starts_at', '')::timestamptz,
            nullif(p_payload->>'ends_at', '')::timestamptz
        )
        returning * into v_new;
    else
        select * into v_old from public.promotional_banners where id = p_id for update;
        if not found then raise exception 'banner_not_found'; end if;

        update public.promotional_banners
        set title = coalesce(nullif(trim(p_payload->>'title'), ''), v_old.title),
            subtitle = coalesce(p_payload->>'subtitle', v_old.subtitle),
            image_url = case
                when p_payload ? 'image_url' then nullif(trim(p_payload->>'image_url'), '')
                else v_old.image_url
            end,
            destination = case
                when p_payload ? 'destination' then nullif(trim(p_payload->>'destination'), '')
                else v_old.destination
            end,
            display_order = coalesce((p_payload->>'display_order')::integer, v_old.display_order),
            is_active = coalesce((p_payload->>'is_active')::boolean, v_old.is_active),
            starts_at = case
                when p_payload ? 'starts_at' then nullif(p_payload->>'starts_at', '')::timestamptz
                else v_old.starts_at
            end,
            ends_at = case
                when p_payload ? 'ends_at' then nullif(p_payload->>'ends_at', '')::timestamptz
                else v_old.ends_at
            end
        where id = p_id
        returning * into v_new;
    end if;

    perform public.admin_log(
        case when p_id is null then 'banner.create' else 'banner.update' end,
        'promotional_banner',
        v_new.id,
        jsonb_build_object(
            'old', case when p_id is null then null else to_jsonb(v_old) end,
            'new', to_jsonb(v_new)
        )
    );
    return v_new;
end;
$$;

create or replace function public.admin_delete_banner(p_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_old public.promotional_banners;
begin
    perform public.require_admin();
    select * into v_old from public.promotional_banners where id = p_id for update;
    if not found then raise exception 'banner_not_found'; end if;
    delete from public.promotional_banners where id = p_id;
    perform public.admin_log(
        'banner.delete',
        'promotional_banner',
        p_id,
        jsonb_build_object('old', to_jsonb(v_old))
    );
end;
$$;

-- ---------------------------------------------------------------------------
-- Announcement RPCs
-- ---------------------------------------------------------------------------

create or replace function public.admin_upsert_announcement(
    p_id uuid default null,
    p_payload jsonb default '{}'::jsonb
)
returns public.announcements
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_old public.announcements;
    v_new public.announcements;
    v_published boolean;
begin
    perform public.require_admin();

    if p_id is null then
        if nullif(trim(p_payload->>'title'), '') is null then raise exception 'title_required'; end if;
        if nullif(trim(p_payload->>'body'), '') is null then raise exception 'body_required'; end if;
        v_published := coalesce((p_payload->>'is_published')::boolean, false);
        insert into public.announcements (title, body, image_url, is_published, published_at)
        values (
            trim(p_payload->>'title'),
            trim(p_payload->>'body'),
            nullif(trim(p_payload->>'image_url'), ''),
            v_published,
            case when v_published then now() else null end
        )
        returning * into v_new;
    else
        select * into v_old from public.announcements where id = p_id for update;
        if not found then raise exception 'announcement_not_found'; end if;

        v_published := coalesce((p_payload->>'is_published')::boolean, v_old.is_published);

        update public.announcements
        set title = coalesce(nullif(trim(p_payload->>'title'), ''), v_old.title),
            body = coalesce(nullif(trim(p_payload->>'body'), ''), v_old.body),
            image_url = case
                when p_payload ? 'image_url' then nullif(trim(p_payload->>'image_url'), '')
                else v_old.image_url
            end,
            is_published = v_published,
            published_at = case
                when v_published and not v_old.is_published then now()
                when v_published then coalesce(v_old.published_at, now())
                else null
            end
        where id = p_id
        returning * into v_new;
    end if;

    perform public.admin_log(
        case when p_id is null then 'announcement.create' else 'announcement.update' end,
        'announcement',
        v_new.id,
        jsonb_build_object(
            'old', case when p_id is null then null else to_jsonb(v_old) end,
            'new', to_jsonb(v_new)
        )
    );
    return v_new;
end;
$$;

create or replace function public.admin_delete_announcement(p_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_old public.announcements;
begin
    perform public.require_admin();
    select * into v_old from public.announcements where id = p_id for update;
    if not found then raise exception 'announcement_not_found'; end if;
    delete from public.announcements where id = p_id;
    perform public.admin_log(
        'announcement.delete',
        'announcement',
        p_id,
        jsonb_build_object('old', to_jsonb(v_old))
    );
end;
$$;

-- ---------------------------------------------------------------------------
-- Storage limits
-- ---------------------------------------------------------------------------

update storage.buckets
set file_size_limit = 5242880
where id = 'category-logos';

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------

revoke all on function public.admin_set_lookup_image(text, uuid, text) from public, anon;
revoke all on function public.admin_upsert_banner(uuid, jsonb) from public, anon;
revoke all on function public.admin_delete_banner(uuid) from public, anon;
revoke all on function public.admin_upsert_announcement(uuid, jsonb) from public, anon;
revoke all on function public.admin_delete_announcement(uuid) from public, anon;

grant execute on function public.admin_set_lookup_image(text, uuid, text) to authenticated;
grant execute on function public.admin_upsert_banner(uuid, jsonb) to authenticated;
grant execute on function public.admin_delete_banner(uuid) to authenticated;
grant execute on function public.admin_upsert_announcement(uuid, jsonb) to authenticated;
grant execute on function public.admin_delete_announcement(uuid) to authenticated;
