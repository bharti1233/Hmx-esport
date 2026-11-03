-- Atomic onboarding completion.
--
-- Defect fixed: the player-setup screen performed three sequential writes
-- (game-account insert, profile PATCH, onboarding flag PATCH). The profile
-- PATCH failed for users because PostgREST's return=representation triggers a
-- SELECT * on `profiles`, whose contact columns are revoked from
-- `authenticated` (0007). Authenticated users cannot SELECT * on profiles by
-- design; only my_profile() returns the full row. The failure surfaced to the
-- client as "You do not have permission to do that." (42501).
--
-- This function performs the whole onboarding write set in one transaction
-- with auth.uid() ownership verification, validation and an audit-friendly
-- error contract. It never exposes service-role credentials and does not
-- weaken RLS: ownership checks use auth.uid().

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

    -- Game account validation (optional in function contract; the client
    -- always provides it for a fresh sign-up).
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

-- Cross-user spoofing stays dead: p_game_account_id is always scoped by
-- user_id = auth.uid(), and the profile write is scoped to auth.uid().
revoke all on function public.complete_onboarding(text, text, text, uuid, text, text, integer) from public, anon;
grant execute on function public.complete_onboarding(text, text, text, uuid, text, text, integer)
    to authenticated;
