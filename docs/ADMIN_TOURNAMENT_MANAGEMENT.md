# Admin Tournament Management

Date: 2026-11-07
Migration: `0014_admin_tournament_match_management` (parts 1-5)
Related: `0015_admin_artwork_storage`, `0016_audit_logs_and_lookup_grants`
Files: `AdminRepository.kt`, `AdminScreens.kt`, `AdminFormScreens.kt`

## Purpose

Administrators can create and maintain tournaments entirely from the app:
draft → registration open → registration closed → live → completed, plus
archival. Every field the player sees is editable, and every write is
authorised and audited on the server.

## Where it lives

Settings → Admin console → Tournaments
(`Routes.adminTournaments`). The entry point is only shown when
`AdminRepository.isAdmin()` returns `true` from the server-side `is_admin()`
RPC; it is not a local preference.

The tournament list shows title, status, game, category, mode, capacity and
schedule, with per-row actions: edit, change status, publish/archive, delete.

## Editable fields

| Field | Column | Validation |
| --- | --- | --- |
| Title | `title` | required |
| Slug | `slug` | unique, auto-derived from title, collision suffix |
| Description | `description` | free text |
| Game | `game` | `free_fire`, `bgmi`, `cod_mobile`, `valorant` |
| Category | `category_id` | active, non-archived lookup |
| Game mode | `game_mode_id` | active, non-archived lookup |
| Tournament type | `tournament_type_id` | active, non-archived lookup |
| Entry fee | `entry_fee_paise` | ≥ 0; drives `entry_type` |
| Prize pool | `prize_pool_paise` | ≥ 0 |
| Per-kill reward | `per_kill_paise` | ≥ 0 |
| Max slots | `max_slots` | ≥ current `slots_filled` |
| Team size | `min_team_size`, `max_team_size` | integers |
| Status | `status` | enum, transition-checked |
| Registration window | `registration_opens_at`, `registration_closes_at` | close after open, start after close |
| Starts / ends | `starts_at`, `ends_at` | end after start |
| Banner | `banner_url` | uploaded to `tournament-banners` |
| Logo | `logo_url` | uploaded to `match-assets/tournaments/<id>/` |
| Map | `map` | free text |
| Region | `region` | default `IN` |
| Featured | `is_featured` | boolean |
| Version / format | `version`, `format` | free text |
| Rules | `tournament_rules` | array of `{title, body}` |
| Archive | `archived_at` | only while draft |

## Server RPCs

All are `SECURITY DEFINER`, `search_path = public, pg_temp`, and start with
`perform public.require_admin()` (which raises `not_authenticated` /
`not_admin`).

| RPC | Effect |
| --- | --- |
| `admin_upsert_tournament(p_id, p_payload)` | Create (`p_id` null) or update. Returns the full row. |
| `admin_set_tournament_status(p_id, p_status)` | Status transition; records old/new status. |
| `admin_delete_tournament(p_id)` | Only draft tournaments with no registrations. |

`admin_upsert_tournament` guarantees:

- `title_required`, `start_required`, `registration_close_required` on create.
- A friendly unique slug; explicit `slug_taken` when changed by hand.
- `category_unavailable` / `game_mode_unavailable` /
  `tournament_type_unavailable` when a new assignment is inactive or archived.
  Keeping the existing value is always allowed, so deactivating a lookup never
  breaks a live event.
- Legacy `mode` is synchronised for the known mode slugs so older SQL keeps
  working.
- `entry_fee_paise <= 0` forces `entry_type = 'free'`; otherwise `paid`.
- `capacity_below_registrations` if `max_slots < slots_filled`.
- `invalid_registration_window`, `start_before_registration_close`,
  `end_before_start`, `archived_tournament_must_be_draft`.
- Rules are replaced atomically only when the payload includes `rules`.

## Status lifecycle

```
draft ──► registration_open ──► registration_closed ──► live ──► completed
  │              │                      │                 │
  └──────────────┴──────────────────────┴─────────────────┘
                    (any non-completed status)
```

- `completed` is terminal: `admin_set_tournament_status` raises
  `completed_tournament_is_locked` for any move away from it.
- `admin_delete_tournament` refuses when registrations exist
  (`tournament_has_registrations`) and when the status is not `draft`
  (`only_draft_tournaments_can_be_deleted`).
- Archiving is a soft delete: set `archived_at` while still draft, keeping
  history and foreign keys.

## Artwork

- Banner → `tournament-banners/<tournament_id>/banner-<timestamp>.<ext>`
  (public read, admin write, complete update/delete lifecycle).
- Logo → `match-assets/tournaments/<tournament_id>/logo-<timestamp>.<ext>`.
- The upload happens first; the returned public URL is then saved through
  `admin_upsert_tournament`, so a failed DB write leaves the old URL intact.

## Audit

Every create, update, status change and delete inserts an `audit_logs` row:

- `action`: `tournament.create`, `tournament.update`, `tournament.status`,
  `tournament.delete`
- `entity_type = 'tournament'`, `entity_id = tournaments.id`
- `metadata`: `{ old, new }` snapshots (status changes store
  `old_status`/`new_status`)
- `actor_id = auth.uid()`, `created_at`

Admins read the log from the Audit screen; non-admins see zero rows.

## Android form behaviour

`AdminTournamentEditScreen`:

- Loads the lookup lists and, when editing, the existing tournament.
- Uses date/time pickers for all four schedule fields; `ends_at` is optional.
- Shows the currently selected lookup even if it was later deactivated.
- Price inputs are entered in rupees and converted to paise in the payload.
- The banner/logo pickers upload through `AdminRepository` and fill the URL
  field with the returned public URL.
- Save is disabled while in flight; the server error key is mapped by
  `hmxMessage()` and shown inline.
- After success the list refreshes from the server; no optimistic row is kept.

## Verification

Executed in rolled-back transactions against the live project unless noted:

| Test | Result |
| --- | --- |
| Create tournament with a category FK | Passed |
| Category rename visible through the tournament relationship | Passed |
| Create with an inactive category | Blocked (`category_unavailable`) |
| Lower `max_slots` below registrations | Blocked (`capacity_below_registrations`) |
| Reversed registration window | Blocked (`invalid_registration_window`) |
| Move a completed tournament back to live | Blocked (`completed_tournament_is_locked`) |
| Delete a tournament with registrations | Blocked (`tournament_has_registrations`) |
| Non-admin RPC call | Blocked (`not_admin`) |
| Direct player `UPDATE` / `DELETE` | Blocked (`permission denied`) |
| `tournament.create` / `.update` audit rows | Passed |
| Android compile of the form | GitHub Actions (see `TOURNAMENT_TEST_REPORT.md`) |

No device end-to-end run was possible; there is no admin account in the live
project yet. Granting an admin role is documented separately and is
intentionally not automated.
