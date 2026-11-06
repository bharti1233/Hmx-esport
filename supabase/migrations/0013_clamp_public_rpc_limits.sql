-- 0013: clamp the public leaderboard/participant page size.
--
-- Both RPCs are executable by anonymous callers and previously accepted any
-- positive `p_limit`, so a crafted request could force the server to sort and
-- materialise the full result set. The app asks for 50 leaderboard rows and
-- 100 participants; 500 is a generous ceiling that preserves every legitimate
-- page while bounding the work per request.

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
as $function$
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
        limit least(greatest(p_limit, 0), 500) offset greatest(p_offset, 0);
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
        limit least(greatest(p_limit, 0), 500) offset greatest(p_offset, 0);
    end if;
end;
$function$;

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
    limit least(greatest(p_limit, 0), 500) offset greatest(p_offset, 0);
$$;
