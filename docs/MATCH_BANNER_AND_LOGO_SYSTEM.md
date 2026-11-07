# Match Banner and Logo System

Date: 2026-11-07
Migration: `0015_admin_artwork_storage`
Related: `0014_admin_tournament_match_management`
Files: `AdminRepository.kt`, `CommonUi.kt`, `MatchScreens.kt`

## Goal

Every match card and detail screen previously reused one image for both the
wide hero and the small badge, so artwork was cropped badly or absent. Matches
now have two independent images with fixed aspect ratios:

- **Banner** — a true 16:9 hero image (`matches.banner_url`).
- **Logo** — a 1:1 badge (`matches.logo_url`).

Tournaments gained the same separation: the existing 16:9 banner plus a new
1:1 `tournaments.logo_url`.

## Storage buckets

| Bucket | Public | Size limit | MIME types | Path |
| --- | --- | --- | --- | --- |
| `tournament-banners` | yes | existing | existing | `<tournament_id>/banner-<ts>.<ext>` |
| `category-logos` | yes | 2 MB | JPEG/PNG/WebP | `categories/<category_id>/logo-<ts>.<ext>` |
| `match-assets` | yes | 5 MB | JPEG/PNG/WebP | `matches/<match_id>/{banner,logo}-<ts>.<ext>` or `tournaments/<tournament_id>/logo-<ts>.<ext>` |

Uploads use the official Supabase Storage endpoint through the user's session.
The RLS policies check `public.is_admin()` on the request, so a player's token
cannot write even if it knows the path.

### Policies

`category-logos`:

- `category_logos_public_read`: `bucket_id = 'category-logos'`.
- `category_logos_admin_insert/update`: authenticated, first folder
  `categories`, `is_admin()`.
- `category_logos_admin_delete`: `is_admin()`.

`match-assets`:

- `match_assets_public_read`: `bucket_id = 'match-assets'`.
- `match_assets_admin_insert/update`: authenticated, first folder in
  (`matches`, `tournaments`), `is_admin()`.
- `match_assets_admin_delete`: `is_admin()`.

`tournament-banners`:

- Existing public read and admin insert retained.
- `banners_admin_update` / `banners_admin_delete` added so an admin can replace
  or remove a banner.

No bucket exposes a service-role key; the client only ever uses the
publishable key and the signed-in user's JWT.

## Client upload flow

`AdminRepository`:

```kotlin
suspend fun uploadMatchBanner(matchId: String, image: PickedImage): String =
    upload("match-assets", "matches/$matchId/banner-${System.currentTimeMillis()}.${ext}", image)

suspend fun uploadMatchLogo(matchId: String, image: PickedImage): String =
    upload("match-assets", "matches/$matchId/logo-${System.currentTimeMillis()}.${ext}", image)
```

`upload` posts the bytes with the correct `Content-Type`, then returns
`storage.publicUrl(bucket, path)`. The form stores that URL and the subsequent
`admin_upsert_match` persists it. If the upload fails, the old URL is kept and
the error is shown; there is no partial DB update.

The match form only enables the upload buttons after the row exists (a new
match must be saved first), because the object path includes the match id.

## Rendering

`MatchFeedCard` (`CommonUi.kt`):

```kotlin
Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
    if (!match.bannerUrl.isNullOrBlank()) {
        AsyncImage(model = match.bannerUrl, contentScale = ContentScale.Crop, ...)
    } else {
        // monochrome placeholder
    }
    if (match.isFull) StatusPill("MATCH FULL", filled = true)
    if (registered) StatusPill("Registered", filled = true)
    if (!match.logoUrl.isNullOrBlank()) {
        AsyncImage(
            model = match.logoUrl,
            contentScale = ContentScale.Crop,
            modifier = Modifier.align(Alignment.BottomStart).size(58.dp)
                .clip(HmxShapes.field).background(HmxColor.CanvasTint),
        )
    }
}
```

- The banner always occupies a 16:9 box; missing artwork shows a glass
  placeholder rather than collapsing the card.
- The logo is a separate square badge overlaying the bottom-left of the banner,
  clipped with the existing field shape.
- `ContentScale.Crop` is intentional for both; the fixed aspect boxes prevent
  the logo from being stretched by the banner ratio.
- Detail screens use the same two URLs with larger boxes and no duplication.

Tournament cards use `bannerUrl` at 16:9 and the new `logoUrl` as a badge, by
the same pattern.

## No new visual language

All frames use `HmxColor.CanvasTint`, `HmxShapes.field`, `HmxDimens` and the
existing glass surfaces. Light, Dark and System themes keep the monochrome
look; the images are the only colour.

## Failure modes

| Situation | Behaviour |
| --- | --- |
| No banner | 16:9 placeholder, card height unchanged |
| No logo | No badge; banner remains clean |
| Slow network | Coil placeholder; the rest of the card renders immediately |
| Upload rejected (non-admin) | RLS error surfaced; DB unchanged |
| Upload ok, DB save fails | Object orphaned, DB unchanged; next successful save replaces the URL |
| Deleted match | `on delete cascade` removes the match; storage object cleanup is not automatic |

> Storage objects are not automatically deleted when a match row is deleted.
> This is a known, documented limitation; a scheduled cleanup job is the
> recommended follow-up.

## Verification

| Test | Result |
| --- | --- |
| Buckets exist with correct public flag and limits | Verified in `storage.buckets` |
| Player insert into `match-assets` | Blocked by RLS (`is_admin()`) |
| Admin insert into `matches/<id>/banner-*` | Policy allows |
| Admin insert into a disallowed first folder | Blocked by `with check` |
| Existing `tournament-banners` upload still works | Policies retained |
| Android compile of upload paths | GitHub Actions (see test report) |

Physical upload UI testing needs an admin account and was not performed in this
environment.
