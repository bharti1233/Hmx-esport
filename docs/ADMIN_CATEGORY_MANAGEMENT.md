# Admin Category Management

Date: 2026-11-07
Migration: `0014_admin_tournament_match_management` (parts 1-5)
Grants migration: `0016_audit_logs_and_lookup_grants`
Table: `public.categories`

## Purpose

Categories are now first-class database records instead of a fixed enum or a
hardcoded list in the client. The same rows feed tournament cards, match cards,
the tournament details screen and the admin form.

## Schema

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key, `gen_random_uuid()` |
| `slug` | text | Unique, lower-case `^[a-z0-9][a-z0-9-]{0,59}$` |
| `name` | text | 2-60 characters |
| `description` | text | Default empty |
| `logo_url` | text | Public URL of the uploaded 1:1 logo |
| `is_active` | boolean | Blocks new assignment when false |
| `is_archived` | boolean | Preserves history; also blocks new assignment |
| `display_order` | integer | Ordering in admin and player lists |
| `created_at`, `updated_at` | timestamptz | `updated_at` maintained by `set_updated_at()` |

Seed rows: Battle Royale, Clash Squad, Full Map, Special Tournament, Custom
Tournament. They are ordinary rows and can be renamed, reordered, deactivated
or deleted when unused.

## Relationships

`tournaments.category_id` and `matches.category_id` reference `categories(id)`
with `on delete restrict`. A category that is still referenced cannot be
deleted; the admin must deactivate or archive it so existing events keep their
stable foreign key and history.

Existing tournaments were backfilled from the legacy `mode` value, and matches
inherited their tournament's category/mode/type.

## Authorisation

- `categories` has RLS enabled.
- The only policy is `categories_select` (`for select using (true)`), so both
  players and admins can read the catalogue.
- `0016` revokes `insert/update/delete/truncate/references/trigger` from `anon`
  and `authenticated`; direct writes are rejected by both grants and RLS.
- All writes go through `SECURITY DEFINER` RPCs guarded by
  `public.require_admin()` (which calls `is_admin()`).

## Admin RPCs

| RPC | Behaviour |
| --- | --- |
| `admin_upsert_category(p_id uuid, p_payload jsonb)` | Creates when `p_id` is null, otherwise updates. Slug is derived from the name when omitted. Emits `category.create` / `category.update` with old/new JSON. |
| `admin_delete_category(p_id uuid)` | Refuses (`category_in_use`) when any tournament or match references the row, otherwise deletes and emits `category.delete`. |

Validation and error keys:

- `name_required`, `slug_taken`, `category_unavailable`, `category_not_found`,
  `category_in_use`.
- `is_active = false` or `is_archived = true` on an existing row is always
  allowed; it only prevents *new* assignments.
- An update may keep the currently assigned category even if it was
  deactivated after the event was created.

## Android UI

Settings → Admin console → Categories
(`Routes.adminLookup("categories")`).

The lookup screen supports:

- list with logo thumbnail, slug, display order, active/archived pills;
- create and edit (name, slug, description, order, active, archived);
- 1:1 logo upload after the row exists;
- delete behind a confirmation dialog, with the in-use error surfaced.

The tournament and match forms only offer assignable rows
(`isActive && !isArchived`) plus the row already selected on that record.
Inactive and archived rows are labelled in the dropdown.

## Storage

Logos use the public `category-logos` bucket at
`categories/<category_id>/logo-<timestamp>.<ext>`.
`category_logos_public_read` allows reads; insert/update/delete require
`is_admin()` and, for writes, the first folder must be `categories`.
Size limit is 5 MB per upload and MIME types are restricted to JPG/PNG/WebP.

## Audit

Every create, update and delete writes an `audit_logs` row with:

- `actor_id` = `auth.uid()`,
- `action` = `category.create` / `category.update` / `category.delete`,
- `entity_type` = `category`,
- `entity_id` = the category id,
- `metadata` = `{ old, new }` JSON,
- `created_at`.

The audit log is readable only by admins (`audit_logs_admin` policy) and
`authenticated` now has the table-level `SELECT` grant added by `0016`.

## Verification

Executed in a rolled-back transaction:

| Test | Result |
| --- | --- |
| Create category through `admin_upsert_category` | Passed |
| Rename category and read the name through the tournament's FK | Passed |
| Player direct insert into `categories` | Blocked (`permission denied` after 0016) |
| Non-admin admin RPC call | Blocked (`not_admin`) |
| `category.create` audit row written with actor and new values | Passed |
| Delete referenced category | Blocked (`category_in_use`, from the migration test suite) |
