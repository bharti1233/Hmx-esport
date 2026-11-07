# Admin Match Management

Date: 2026-11-07
Migration: `0014_admin_tournament_match_management` (parts 1-5)
Related: `0015_admin_artwork_storage`, `0016_audit_logs_and_lookup_grants`
Files: `AdminRepository.kt`, `AdminScreens.kt`, `AdminFormScreens.kt`

## Purpose

Matches are independent, admin-managed events. A match may belong to a
tournament or stand alone (`matches.tournament_id` is now nullable). Each match
has its own banner, logo, rewards, schedule, capacity, registration window and
publication state, so a tournament can host several matches without
duplicating those settings.

## Where it lives

Settings → Admin console → Matches (`Routes.adminMatches`).

The list shows each match with its 16:9 banner, 1:1 logo, status, tournament,
game/category/mode, rewards and capacity. Row actions: edit, duplicate,
publish/unpublish, change status, delete.

## Schema additions

| Column | Type | Notes |
| --- | --- | --- |
| `tournament_id` | uuid | nullable; `on delete` behaviour unchanged |
| `game` | text | `free_fire`, `bgmi`, `cod_mobile`, `valorant` |
| `category_id` | uuid | FK `categories` restrict |
| `game_mode_id` | uuid | FK `game_modes` restrict |
| `tournament_type_id` | uuid | FK `tournament_types` restrict |
| `match_number` | text | free label |
| `banner_url` | text | 16:9 upload |
| `logo_url` | text | 1:1 upload |
| `prize_pool_paise` | integer | ≥ 0 |
| `per_kill_paise` | integer | ≥ 0 |
| `entry_fee_paise` | integer | ≥ 0 |
| `version` | text | default `1.0` |
| `format` | text | free text |
| `max_slots` | integer | > 0 |
| `slots_filled` | integer | ≥ 0, ≤ `max_slots` |
| `registration_opens_at` | timestamptz | |
| `registration_closes_at` | timestamptz | > open |
| `is_published` | boolean | default false |
| `archived_at` | timestamptz | soft archive |

Constraints:

- `matches_capacity_check`: `slots_filled <= max_slots`.
- `matches_registration_window_check`: close after open.
- Index `matches_published_idx (is_published, status, scheduled_at)` powers the
  player feed; one index per lookup.

Existing matches were backfilled with `game`, category/mode/type from their
tournament and `is_published = true` so they stayed visible.

## Server RPCs

All `SECURITY DEFINER`, guarded by `require_admin()`.

| RPC | Effect |
| --- | --- |
| `admin_upsert_match(p_id, p_payload, p_confirm_locked)` | Create/update. `p_confirm_locked` must be true for sensitive edits on live/completed matches. |
| `admin_set_match_status(p_id, p_status)` | Status transition; stamps `started_at`/`ended_at`. |
| `admin_set_match_published(p_id, p_published)` | Publish/unpublish. |
| `admin_duplicate_match(p_id)` | Copies configuration into a new unpublished `scheduled` match named "(copy)". |
| `admin_delete_match(p_id)` | Refuses while registrations or participants exist. |

### Create rules

- `name_required`, `schedule_required`.
- If a `tournament_id` is supplied it must exist (`tournament_not_found`).
- Category/mode/type default from the tournament when omitted, otherwise must
  be active and non-archived.
- `game` defaults to the tournament game, then `free_fire`.

### Update rules

- `match_not_found` for an unknown id.
- `category_unavailable`, `game_mode_unavailable`,
  `tournament_type_unavailable` for new assignments to inactive rows.
- `capacity_below_registrations` if `max_slots < slots_filled`.
- `invalid_registration_window`, `end_before_start`.
- **Locked-match confirmation**: when the stored status is `live` or
  `completed`, changes to `scheduled_at`, `prize_pool_paise`,
  `per_kill_paise`, `entry_fee_paise`, `max_slots` or `tournament_id` require
  `p_confirm_locked = true`; otherwise `match_locked_confirmation_required`.
  The audit metadata records `confirmed_locked_edit`.
- Status move away from `completed` is rejected
  (`completed_match_is_locked`).
- Delete is refused while `match_registrations` or `match_participants` rows
  exist (`match_has_registrations`).

## Match lifecycle

```
scheduled ──► check_in ──► live ──► completed
     │            │           │
     └────────────┴───────────┘  (scheduled/check_in cancellable)
```

Registration (`register_for_match`) is only allowed while the match is
`published`, not archived, `scheduled`/`check_in`, and inside the registration
window. `completed` is terminal.

## Android form

`AdminMatchEditScreen` includes:

- tournament selector (optional), name, match number, round;
- game, category, game mode, tournament type;
- prize pool, per-kill, entry fee, version, format;
- max slots, registration open/close, scheduled/check-in/start/end times;
- map, banner (16:9) and logo (1:1) uploaders, published toggle;
- a confirmation dialog when the match is live/completed and a sensitive field
  changed, which resends with `p_confirm_locked = true`.

The list uses the same `MatchFeedCard` visual language as the player feed, so
the admin sees the card exactly as it will appear.

## Audit

`match.create`, `match.update`, `match.status`, `match.publish`,
`match.unpublish`, `match.duplicate`, `match.delete`, each with the actor,
entity id and old/new JSON (status changes use old/new status).

## Verification

| Test | Result |
| --- | --- |
| Create a standalone match | Passed |
| Create a match inside a tournament (inherits lookups) | Passed |
| Register a player, then cancel, slot restored | Passed |
| Duplicate match is unpublished and `scheduled` | Passed (migration suite) |
| Locked-match edit without confirmation | Blocked (`match_locked_confirmation_required`) |
| Locked-match edit with confirmation | Passed |
| Delete with registrations | Blocked (`match_has_registrations`) |
| Non-admin RPC call | Blocked (`not_admin`) |
| Direct player `UPDATE`/`DELETE` | Blocked by grants/RLS |
| `match.create`/`.update` audit rows | Passed |
| Android compile | GitHub Actions (see `TOURNAMENT_TEST_REPORT.md`) |
