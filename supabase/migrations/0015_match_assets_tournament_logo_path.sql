-- 0015 (follow-up): allow tournament logos in the match-assets bucket
--
-- The initial match-assets policies only accepted `matches/<id>/...` objects.
-- Tournament logos reuse the same bucket under `tournaments/<id>/...`, so the
-- INSERT and UPDATE checks are widened to accept either first folder. The
-- delete policy needs no path check because it already requires is_admin().
--
-- 0015_admin_artwork_storage.sql contains the consolidated definition for
-- fresh installs; this file records the change that was applied to the live
-- project after the initial policy.

drop policy if exists match_assets_admin_insert on storage.objects;
create policy match_assets_admin_insert on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'match-assets'
        and (storage.foldername(objects.name))[1] in ('matches', 'tournaments')
        and public.is_admin()
    );

drop policy if exists match_assets_admin_update on storage.objects;
create policy match_assets_admin_update on storage.objects
    for update to authenticated
    using (bucket_id = 'match-assets' and public.is_admin())
    with check (
        bucket_id = 'match-assets'
        and (storage.foldername(objects.name))[1] in ('matches', 'tournaments')
        and public.is_admin()
    );
