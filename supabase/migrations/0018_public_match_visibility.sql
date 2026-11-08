-- 0018: make public match visibility a row-level guarantee.
--
-- Before this migration matches_select was `using (true)`, so any caller with
-- the anon key could read unpublished or archived matches by guessing an id.
-- The client feed already filtered is_published, but security must not depend
-- on every future caller remembering that filter.
--
-- Active match registrations also become first-class match participation for
-- private resources (room credentials, result visibility). Tournament
-- participation keeps working exactly as before.

drop policy if exists matches_select on public.matches;
create policy matches_select on public.matches
    for select using (
        public.is_admin()
        or (is_published = true and archived_at is null)
    );

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
    )
    or exists (
        select 1
        from public.match_registrations mr
        where mr.match_id = p_match_id
          and mr.user_id = p_user_id
          and mr.status = 'confirmed'
    );
$$;

-- Re-assert the existing read grants; no write grants are added.
grant select on public.matches to anon, authenticated;
grant execute on function public.is_match_participant(uuid, uuid) to anon, authenticated;
