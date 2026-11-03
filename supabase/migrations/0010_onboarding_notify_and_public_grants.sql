-- 0010: onboarding notification payload + anonymous policy helpers.
--
-- Two live defects surfaced while verifying the onboarding repair:
--
-- 1) public.notifications.data is NOT NULL (default '{}'::jsonb), but
--    complete_onboarding() passed NULL to public.notify(). The whole RPC
--    transaction rolled back with 23502 and the client still saw a permission
--    style failure. notify() now normalises NULL to '{}' so no caller can
--    reintroduce the bug.
--
-- 2) Policies on anon-readable tables (profiles, tournaments, matches,
--    match_results, result_evidence) call public.is_admin() or
--    public.is_match_participant(), but EXECUTE was only granted to
--    authenticated. Anonymous catalogue reads therefore failed with
--    42501 "permission denied for function is_admin". Both helpers are
--    SECURITY DEFINER, STABLE and evaluate auth.uid(); with a NULL uid they
--    return false, so granting EXECUTE to anon is safe and does not widen
--    data access.

-- Keep the exact signature and default so CREATE OR REPLACE is valid.
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
    values (p_user, p_type, p_title, p_body, coalesce(p_data, '{}'::jsonb));
$$;

-- notify() is an internal helper; only server-side roles may call it.
revoke all on function public.notify(uuid, public.notification_type, text, text, jsonb)
    from public, anon, authenticated;
grant execute on function public.notify(uuid, public.notification_type, text, text, jsonb)
    to service_role;

-- Recreate the onboarding RPC with a valid notification payload.
create or replace function public.complete_onboarding(
    p_display_name text,
    p_bio text default null,
    p_region text default 'IN',
    p_game_account_id uuid default null,
    p_ign text default null,
    p_game_uid text default null,
    p_level integer default null
)
returns public.profiles
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_uid uuid := auth.uid();
    v_ign text;
    v_game_uid text;
    v_level integer;
    v_region text;
    v_profile public.profiles;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    -- Profile validation (server-side authority, mirrors client rules).
    if p_display_name is null or length(p_display_name) < 2 or length(p_display_name) > 30 then
        raise exception 'invalid_display_name';
    end if;
    if p_bio is not null and length(p_bio) > 160 then
        raise exception 'invalid_bio';
    end if;
    v_region := upper(coalesce(p_region, 'IN'));
    if length(v_region) not between 2 and 8 then
        raise exception 'invalid_region';
    end if;

    -- Game account validation (optional; fresh sign-ups always send it).
    if p_ign is not null or p_game_uid is not null then
        v_ign := btrim(coalesce(p_ign, ''));
        v_game_uid := btrim(coalesce(p_game_uid, ''));
        v_level := p_level;
        if v_ign = '' then v_ign := null; end if;
        if v_game_uid = '' then v_game_uid := null; end if;
        if v_ign is not null and length(v_ign) not between 2 and 24 then
            raise exception 'invalid_ign';
        end if;
        if v_game_uid is not null and length(v_game_uid) not between 4 and 24 then
            raise exception 'invalid_game_uid';
        end if;
        if v_level is not null and v_level not between 1 and 100 then
            raise exception 'invalid_level';
        end if;
        if (v_game_uid is null) <> (v_ign is null) then
            raise exception 'invalid_game_identity';
        end if;

        if p_game_account_id is null then
            insert into public.player_game_accounts
                (user_id, game, ign, game_uid, level)
            values
                (v_uid, 'free_fire', v_ign, v_game_uid, v_level);
        else
            update public.player_game_accounts
            set ign = coalesce(v_ign, ign),
                game_uid = coalesce(v_game_uid, game_uid),
                level = v_level
            where id = p_game_account_id
              and user_id = v_uid;
            if not found then
                -- Cross-user or stale id: rejected by ownership check.
                raise exception 'invalid_game_identity';
            end if;
        end if;
    end if;

    update public.profiles
    set display_name = p_display_name,
        bio = p_bio,
        region = v_region,
        onboarding_completed = true
    where id = v_uid
    returning * into v_profile;

    if not found then
        raise exception 'profile_not_found';
    end if;

    perform public.notify(
        v_uid,
        'account',
        'Welcome to HMX Arena',
        'Your player profile is complete. Good luck in the arena.',
        '{}'::jsonb
    );
    return v_profile;
end;
$$;

revoke all on function public.complete_onboarding(text, text, text, uuid, text, text, integer)
    from public, anon;
grant execute on function public.complete_onboarding(text, text, text, uuid, text, text, integer)
    to authenticated;

-- RLS helpers referenced by policies on anon-readable tables. They are safe
-- for anon: auth.uid() is NULL, so each returns false.
grant execute on function public.is_admin() to anon;
grant execute on function public.is_match_participant(uuid, uuid) to anon;
