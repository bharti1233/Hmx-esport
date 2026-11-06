-- 0012: fix team logo storage policies.
--
-- Production metadata for `team_logos_owner_insert` / `team_logos_owner_update`
-- compared `t.id::text` with `(storage.foldername(t.name))[1]`. That applies
-- `storage.foldername` to the *team name column* instead of the uploaded
-- object's path, so the check can never match a real team id and every logo
-- upload fails. The delete path was also missing entirely.
--
-- Careful: inside the `exists (select ... from public.teams t)` subquery an
-- unqualified `name` resolves to `teams.name`, not `storage.objects.name`.
-- The object path must therefore be written as `objects.name`; migration 0004
-- used the unqualified form and silently recreated the same defect.
--
-- The app uploads to `<team_id>/logo-<timestamp>.jpg` (see TeamRepository), so
-- the first path segment must be compared to the team id, and the caller must
-- be that team's owner. Anonymous/public reads are unaffected.

drop policy if exists team_logos_owner_insert on storage.objects;
create policy team_logos_owner_insert on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'team-logos'
        and exists (
            select 1 from public.teams t
            where t.id::text = (storage.foldername(objects.name))[1]
              and t.owner_id = auth.uid()
        )
    );

drop policy if exists team_logos_owner_update on storage.objects;
create policy team_logos_owner_update on storage.objects
    for update to authenticated
    using (
        bucket_id = 'team-logos'
        and owner = auth.uid()
    )
    with check (
        bucket_id = 'team-logos'
        and exists (
            select 1 from public.teams t
            where t.id::text = (storage.foldername(objects.name))[1]
              and t.owner_id = auth.uid()
        )
    );

drop policy if exists team_logos_owner_delete on storage.objects;
create policy team_logos_owner_delete on storage.objects
    for delete to authenticated
    using (
        bucket_id = 'team-logos'
        and exists (
            select 1 from public.teams t
            where t.id::text = (storage.foldername(objects.name))[1]
              and t.owner_id = auth.uid()
        )
    );
