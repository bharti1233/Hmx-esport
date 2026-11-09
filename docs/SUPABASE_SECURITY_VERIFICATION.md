# Supabase Security Verification

Date: 2026-11-09
Project: `ahsvxxhaowwhqermruhz` (`https://ahsvxxhaowwhqermruhz.supabase.co`)
Branch: `feature/hmx-home-category-navigation`

## 1. What changed server-side

One additive migration was added and applied:

`supabase/migrations/0021_category_match_feed_index.sql`

It creates two partial indexes for the category feed:

- `matches_category_feed_idx (category_id, status, scheduled_at)`
  where `is_published and archived_at is null`
- `matches_category_completed_idx (category_id, scheduled_at desc)`
  where `is_published and archived_at is null and status = 'completed'`

**No policy, grant, RPC, column, data row or storage rule was changed.** The
indexes only accelerate the existing authenticated/public filters.

## 2. Row Level Security (verified live)

| Table | Policy | Verified definition |
| --- | --- | --- |
| `matches` | `matches_select` | `is_admin() OR (is_published = true AND archived_at IS NULL)` |
| `match_registrations` | `match_registrations_select` | `user_id = auth.uid() OR is_admin()` |
| `categories` | `categories_select` | `true` (public lookup data) |
| `game_modes` | `game_modes_select` | `true` (public lookup data) |
| `tournament_types` | `tournament_types_select` | `true` (public lookup data) |

No INSERT/UPDATE/DELETE policy exists on these tables.

## 3. Table privileges (verified live)

| Check | Result |
| --- | --- |
| `anon` INSERT/UPDATE `categories` | false / false |
| `authenticated` INSERT/UPDATE `categories` | false / false |
| `anon` INSERT `matches` | false |
| `authenticated` UPDATE `matches` | false |
| `anon` / `authenticated` INSERT `match_registrations` | false / false |

Only `SELECT` is granted to `anon`/`authenticated` on the public read tables.
All writes go through admin RPCs or player RPCs that re-check ownership/admin
role and write audit rows.

## 4. Category and match reads from the client

- The client uses the publishable key from `gradle.properties`
  (`HMX_SUPABASE_KEY`), never a service-role key. Repository grep found no
  service-role usage; the only mention is a comment warning not to add one.
- The category match query always sends `is_published=eq.true` and
  `archived_at=is.null`, in addition to RLS. Draft/archived rows are therefore
  excluded twice.
- The count request uses the same filters through `HEAD` + `count=exact`; it
  never widens visibility to compute a total.
- Category identity is the `categories.id` UUID. There is no client-supplied
  category name used in a filter.

## 5. Admin RPC authority

Verified with `has_function_privilege`:

| Function | SECURITY DEFINER | anon EXECUTE | authenticated EXECUTE |
| --- | --- | --- | --- |
| `admin_upsert_category` | true | false | true |
| `admin_set_lookup_image` | true | false | true |
| `register_for_match` | true | false | true |
| `cancel_match_registration` | true | false | true |

Admin functions call `require_admin()`/`is_admin()` internally; the
`authenticated` grant is necessary for the call to reach that check, and all
admin writes are audit-logged by the existing implementation.

## 6. Registration safety

The category screen only navigates to the existing match detail/registration
flow. It does not add a new registration write path. Capacity, duplicate
registration, cancellation and row locking remain enforced by
`register_for_match` / `cancel_match_registration` (`SELECT ... FOR UPDATE`,
`match_full`, `already_registered`, `registration_closed`).

## 7. Advisors

Supabase security advisors were re-run after the migration. No new finding was
introduced for `matches`, `categories`, `game_modes` or `tournament_types`.
Existing project-wide warnings are unchanged and pre-date this work (for
example, intentionally public lookup tables, existing `SECURITY DEFINER`
functions, and other tables outside this feature).

## 8. Limitations

- No production admin account was available; admin UI writes were not executed
  end-to-end against the live project.
- No physical device/emulator was available. Verification is based on live SQL,
  live PostgREST requests, repository inspection and CI compilation/tests.
