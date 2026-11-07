# Admin Game Mode Management

Date: 2026-11-07
Migration: `0014_admin_tournament_match_management` (parts 1-5)
Grants migration: `0016_audit_logs_and_lookup_grants`
Table: `public.game_modes`

## Purpose

Game modes (Solo, Duo, Squad, Clash Squad, and any future mode) are database
rows. The admin can add, rename, reorder, deactivate and archive them without a
client release. Tournaments and matches reference the row by a stable UUID.

## Schema

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `slug` | text | Unique, lower-case kebab-case |
| `name` | text | 2-60 characters |
| `description` | text | Default empty |
| `logo_url` | text | Optional; the current UI does not upload mode logos |
| `is_active` | boolean | False blocks new assignment |
| `is_archived` | boolean | False-history-safe alternative to delete |
| `display_order` | integer | List ordering |
| `created_at`, `updated_at` | timestamptz | Trigger maintained |

Seed rows: `solo`, `duo`, `squad`, `clash-squad`.

## Legacy enum compatibility

The pre-existing `tournaments.mode` / `matches.mode` columns remain because
other SQL (eligibility, room and result logic, reports) still reads them. When
an admin assigns a mode whose slug is one of `solo`, `duo`, `squad` or
`clash-squad`, `admin_upsert_tournament` / `admin_upsert_match` normalises the
legacy column (`clash-squad` → `clash_squad`).

A custom mode (for example `battle-royale-trios`) keeps the legacy column at
its previous value but is still fully readable through the `game_mode_id`
relationship and the `gameMode` embedded object. This avoids breaking older
queries while allowing new modes.

## Relationships

- `tournaments.game_mode_id → game_modes(id)` (`on delete restrict`)
- `matches.game_mode_id → game_modes(id)` (`on delete restrict`)

A rename changes the display name everywhere immediately because the player
and admin queries embed the lookup row rather than storing the name. Old
registrations and results keep their FK and therefore the new display name.

Existing rows were backfilled from the legacy `mode` value
(`clash_squad` → `clash-squad`).

## Authorisation

- RLS enabled; `game_modes_select` allows read for everyone.
- `0016` revokes write grants from `anon` and `authenticated`.
- Writes only through `admin_upsert_game_mode` / `admin_delete_game_mode`,
  both `SECURITY DEFINER` and guarded by `require_admin()`.

## Admin RPCs

| RPC | Behaviour |
| --- | --- |
| `admin_upsert_game_mode(p_id, p_payload)` | Create/update with name, slug, description, logo, order, active, archived. Emits `game_mode.create` / `game_mode.update`. |
| `admin_delete_game_mode(p_id)` | Refuses with `game_mode_in_use` when tournaments or matches reference it, otherwise deletes. Emits `game_mode.delete`. |

Errors: `name_required`, `slug_taken`, `game_mode_not_found`,
`game_mode_in_use`, `game_mode_unavailable`.

## Deactivation semantics

- `is_active = false` or `is_archived = true` removes the row from the
  assignable list in the admin tournament and match forms.
- The currently assigned row stays selectable and is labelled
  `(inactive)` / `(archived)`.
- Existing tournaments and matches are not modified. Their cards continue to
  show the stored name.
- Re-activating a row makes it assignable again.

## Android UI

Settings → Admin console → Game modes
(`Routes.adminLookup("game-modes")`).

The shared lookup screen provides list, create, edit, activate/archive and
delete. Game modes do not use the logo picker in the current UI
(`lookupSupportsLogo` is true only for categories).

Tournament and match forms show a **Game mode** dropdown populated from
`AdminRepository.gameModes()`.

## Audit

Each write writes `game_mode.create|update|delete` to `audit_logs` with
`actor_id`, `entity_type = 'game_mode'`, `entity_id` and `{old,new}` metadata.
Only admins can read the log.

## Verification

| Test | Result |
| --- | --- |
| Mode rows seeded and readable by players (`game_modes_select`) | Passed |
| Create/update through admin RPC | Passed |
| Rename reflected through the tournament embed | Passed (shared lookup test) |
| Direct `UPDATE` by a player after 0016 | Blocked (`permission denied`) |
| Delete while referenced | Blocked (`game_mode_in_use`, migration test suite) |
| Non-admin RPC call | Blocked (`not_admin`) |
