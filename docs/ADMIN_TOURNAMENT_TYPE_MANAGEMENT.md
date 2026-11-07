# Admin Tournament Type Management

Date: 2026-11-07
Migration: `0014_admin_tournament_match_management` (parts 1-5)
Grants migration: `0016_audit_logs_and_lookup_grants`
Table: `public.tournament_types`

## Purpose

Tournament types describe the commercial/competitive shape of an event
(Free Entry, Paid Entry, Practice, Competitive, Special Event, and future
types). They are database rows referenced by tournaments and matches, so the
label is editable without changing registrations or results.

## Schema

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `slug` | text | Unique kebab-case |
| `name` | text | 2-60 characters |
| `description` | text | Default empty |
| `logo_url` | text | Optional |
| `is_active` | boolean | False blocks new assignment |
| `is_archived` | boolean | History-preserving alternative to delete |
| `display_order` | integer | List ordering |
| `created_at`, `updated_at` | timestamptz | Trigger maintained |

Seed rows: `free-entry`, `paid-entry`, `practice`, `competitive`,
`special-event`.

## Relationship to entry fees

`tournament_types` is descriptive. The authoritative paid/free signal remains
`tournaments.entry_type` and `entry_fee_paise`, because registration and
wallet logic already depend on them:

- `entry_fee_paise <= 0` forces `entry_type = 'free'`.
- `entry_fee_paise > 0` forces `entry_type = 'paid'`.

The admin UI does not silently rewrite the selected tournament type when the
fee changes; both fields are independently editable and visible. This avoids
mis-labelling a free practice event as paid (or the reverse) while still
letting the admin choose the type explicitly.

Existing tournaments were backfilled to `paid-entry` or `free-entry` from the
legacy `entry_type`.

## Relationships

- `tournaments.tournament_type_id → tournament_types(id)` (`on delete restrict`)
- `matches.tournament_type_id → tournament_types(id)` (`on delete restrict`)

Renaming propagates immediately because the client embeds
`tournamentType:tournament_types(...)` in the tournament and match selects.
Existing registrations are untouched.

## Authorisation

- RLS enabled; `tournament_types_select` allows read for everyone.
- `0016` revokes write grants from `anon` and `authenticated`.
- Writes only through `admin_upsert_tournament_type` /
  `admin_delete_tournament_type`, `SECURITY DEFINER`, guarded by
  `require_admin()`.

## Admin RPCs

| RPC | Behaviour |
| --- | --- |
| `admin_upsert_tournament_type(p_id, p_payload)` | Create/update; emits `tournament_type.create` / `tournament_type.update` |
| `admin_delete_tournament_type(p_id)` | Refuses with `tournament_type_in_use` while referenced; emits `tournament_type.delete` |

Errors: `name_required`, `slug_taken`, `tournament_type_not_found`,
`tournament_type_in_use`, `tournament_type_unavailable`.

## Android UI

Settings → Admin console → Tournament types
(`Routes.adminLookup("tournament-types")`).

The shared lookup screen supports list, create, edit, active/archived and
delete. Tournament and match forms show a **Tournament type** dropdown fed by
`AdminRepository.tournamentTypes()`.

## Audit

Writes appear in `audit_logs` as `tournament_type.create|update|delete` with
the actor, entity id and old/new JSON. The log is admin-only.

## Verification

| Test | Result |
| --- | --- |
| Seed rows readable by players | Passed |
| Create/update through admin RPC | Passed |
| Delete while referenced | Blocked (`tournament_type_in_use`) |
| Non-admin RPC call | Blocked (`not_admin`) |
| Direct write by a player | Blocked by RLS/grants |
| Rename propagation | Passed through the shared embedded lookup |
