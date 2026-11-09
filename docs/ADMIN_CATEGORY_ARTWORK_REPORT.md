# Admin Category Artwork Report

Date: 2026-11-09
Branch: `feature/hmx-home-category-navigation`

## 1. What admins already control

The existing admin lookup editor and `admin_upsert_category` RPC remain the
authoritative write path. Admins can manage, per category:

- name, slug, description,
- display order,
- active and archived flags,
- square logo (`logo_url`),
- wide artwork (`image_url`).

No client-side duplicate fields were introduced. Matches keep their single
authoritative `category_id` foreign key; there is no copy of the category name
on the match row. Renaming a category therefore updates every card and screen.

## 2. Storage

Category images are stored in the existing public-read/admin-write
`category-logos` bucket under `categories/<category-id>/...`:

- logo: `logo-<timestamp>.<ext>`
- artwork: `artwork-<timestamp>.<ext>`

The existing 5 MB and JPG/PNG/WebP validation in `AdminRepository.upload()`
still applies. No service-role key is present in the client.

## 3. Display precedence

Both the Home tiles and the category hero use the same pure resolver:

```kotlin
categoryVisuals(imageUrl, logoUrl)
```

Precedence:

1. Admin wide artwork (`image_url`) when non-blank.
2. Admin square logo (`logo_url`) when non-blank.
3. A deterministic fallback:
   - known slugs get distinct icons (Battle Royale, Clash Squad, Full Map,
     Special Tournament, Custom Tournament),
   - any other category gets two-letter initials derived from its name.

Blank admin fields are treated as "not configured", so an empty string cannot
hide the fallback. A configured image always wins over a fallback, satisfying
the requirement that valid artwork is never replaced by a shared trophy.

## 4. Accessibility

Category tiles expose a content description of the form
`"<name> category"` (and `", unavailable"` if a non-active/archived row is ever
rendered). Artwork and logo images have their own descriptions. The category
name remains visible as text.

## 5. Tests

`CategoryFallbackTest` pins:

- known slug -> distinct glyph mapping,
- unknown slug -> initials, never the trophy,
- two-letter initials,
- configured artwork wins over logo, blank fields are ignored.

Admin CRUD itself remains covered by the existing server-side RPCs and RLS;
this change does not add a client write path.

## 6. Limitation

A production admin account was not available, so end-to-end admin UI editing
was not exercised against the live project. The write path is unchanged from
the previously verified admin implementation, and RLS/policies were re-checked
(see `docs/SUPABASE_SECURITY_VERIFICATION.md`).
