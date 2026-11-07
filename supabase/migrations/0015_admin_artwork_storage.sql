-- 0015: storage buckets for admin-managed tournament and match artwork.
--
--  * category-logos  (square 1:1, 2 MB)
--  * match-assets    (16:9 banner and 1:1 logo, 5 MB)
--
-- Existing `tournament-banners` is reused for tournament banners, but it only
-- had an INSERT policy. Add explicit admin UPDATE/DELETE so a banner can be
-- replaced or removed. Public reads stay unchanged.
--
-- Object paths used by the Android client:
--   category-logos/<category_id>/logo-<timestamp>.jpg
--   match-assets/matches/<match_id>/banner-<timestamp>.jpg
--   match-assets/matches/<match_id>/logo-<timestamp>.jpg
--   tournament-banners/<tournament_id>/banner-<timestamp>.jpg

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
    ('category-logos', 'category-logos', true, 2097152, array['image/jpeg', 'image/png', 'image/webp']),
    ('match-assets', 'match-assets', true, 5242880, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

-- category-logos ------------------------------------------------------------

drop policy if exists category_logos_public_read on storage.objects;
create policy category_logos_public_read on storage.objects
    for select using (bucket_id = 'category-logos');

drop policy if exists category_logos_admin_insert on storage.objects;
create policy category_logos_admin_insert on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'category-logos'
        and (storage.foldername(objects.name))[1] = 'categories'
        and public.is_admin()
    );

drop policy if exists category_logos_admin_update on storage.objects;
create policy category_logos_admin_update on storage.objects
    for update to authenticated
    using (bucket_id = 'category-logos' and public.is_admin())
    with check (
        bucket_id = 'category-logos'
        and (storage.foldername(objects.name))[1] = 'categories'
        and public.is_admin()
    );

drop policy if exists category_logos_admin_delete on storage.objects;
create policy category_logos_admin_delete on storage.objects
    for delete to authenticated
    using (bucket_id = 'category-logos' and public.is_admin());

-- match-assets --------------------------------------------------------------

drop policy if exists match_assets_public_read on storage.objects;
create policy match_assets_public_read on storage.objects
    for select using (bucket_id = 'match-assets');

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

drop policy if exists match_assets_admin_delete on storage.objects;
create policy match_assets_admin_delete on storage.objects
    for delete to authenticated
    using (bucket_id = 'match-assets' and public.is_admin());

-- tournament-banners: complete the admin lifecycle --------------------------

drop policy if exists banners_admin_update on storage.objects;
create policy banners_admin_update on storage.objects
    for update to authenticated
    using (bucket_id = 'tournament-banners' and public.is_admin())
    with check (bucket_id = 'tournament-banners' and public.is_admin());

drop policy if exists banners_admin_delete on storage.objects;
create policy banners_admin_delete on storage.objects
    for delete to authenticated
    using (bucket_id = 'tournament-banners' and public.is_admin());
