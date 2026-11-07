# Tournament Security Audit

Date: 2026-11-07
Scope: categories, game modes, tournament types, tournaments, matches,
match registrations, artwork storage and audit logging.
Migrations: `0014`, `0015`, `0016`, `0017`

## 1. Threat model

| Actor | Capability | Goal |
| --- | --- | --- |
| Anonymous caller | Has the publishable key | Read public events, attempt admin RPCs |
| Signed-in player | Valid JWT, no role | Register/cancel own entries, tamper with admin data |
| Signed-in admin | Row in `user_roles` with `admin`/`moderator` | Manage events |
| Leaked key | Publishable only | Nothing beyond anon; no service role exists client-side |

The client is never trusted. Hiding the admin entry point is a usability
measure; every write is re-authorised in Postgres.

## 2. Server-side authorisation

`public.is_admin()`:

```sql
select exists (
  select 1 from public.user_roles
  where user_id = auth.uid() and role in ('admin', 'moderator')
);
```

- `STABLE SECURITY DEFINER`, `search_path = public, pg_temp`.
- Reads `user_roles` directly, so a client boolean or header cannot forge it.
- Returns `false` for anon (no `auth.uid()`).

`public.require_admin()`:

```sql
if v_uid is null then raise exception 'not_authenticated'; end if;
if not public.is_admin() then raise exception 'not_admin'; end if;
return v_uid;
```

Every administrative RPC begins with `perform public.require_admin()`.
`admin_slugify`, `require_admin` and `admin_log` have EXECUTE revoked from
`public`, `anon` and `authenticated`, so they cannot be called through
PostgREST. `admin_log` is only reachable from inside the definer functions.

### Grant hardening (`0017`)

Postgres grants EXECUTE to `PUBLIC` on new functions by default, which made the
new admin RPCs callable by `anon` (they still failed at `require_admin`, but the
Supabase advisor flags that pattern). `0017` revokes `PUBLIC`/`anon`/`authenticated`
and re-grants to `authenticated` and `service_role` for:

`admin_upsert_category`, `admin_delete_category`,
`admin_upsert_game_mode`, `admin_delete_game_mode`,
`admin_upsert_tournament_type`, `admin_delete_tournament_type`,
`admin_upsert_tournament`, `admin_set_tournament_status`,
`admin_delete_tournament`, `admin_upsert_match`, `admin_set_match_status`,
`admin_set_match_published`, `admin_duplicate_match`, `admin_delete_match`,
`register_for_match`, `cancel_match_registration`.

`is_admin()` intentionally remains executable by `anon`; it returns only the
caller's own membership and is needed to decide whether to show the admin
entry.

Verified after `0017`:

| Caller | Admin RPC | Match RPC |
| --- | --- | --- |
| `anon` | `permission denied for function` | `permission denied for function` |
| `authenticated` admin | succeeds | succeeds |
| `authenticated` player | `not_admin` | own registration only |

## 3. RLS and grants matrix

| Table | RLS | Policies | `anon` grants | `authenticated` grants |
| --- | --- | --- | --- | --- |
| `categories` | on | `categories_select` `using(true)` | SELECT | SELECT |
| `game_modes` | on | `game_modes_select` `using(true)` | SELECT | SELECT |
| `tournament_types` | on | `tournament_types_select` `using(true)` | SELECT | SELECT |
| `match_registrations` | on | `match_registrations_select` `user_id = auth.uid() or is_admin()` | SELECT (no rows pass) | SELECT |
| `tournaments` | on | `tournaments_select` `status <> 'draft' or is_admin()` | SELECT | SELECT |
| `matches` | on | `matches_select` `using(true)` (published filter is in the feed query) | SELECT | SELECT |
| `audit_logs` | on | `audit_logs_admin` `is_admin()` | none | SELECT (RLS limits rows) |

There are **no** INSERT/UPDATE/DELETE policies on any of these tables. Writes
are only possible through the definer RPCs.

`0016` revoked the Supabase default writes that remained on the lookup tables
and `match_registrations`, then re-granted only SELECT. Before `0016` a player
was already blocked by RLS; after it they are blocked by the grant as well.

> Note: `match_registrations` has a SELECT grant for `anon`, but the only policy
> compares `user_id = auth.uid()` (NULL for anon) or `is_admin()` (false), so no
> rows are visible. The grant is harmless but could be removed later.

## 4. Direct-write tests

Executed with `set local role authenticated` and a real player JWT claim,
inside rolled-back transactions:

| Attempt | Result |
| --- | --- |
| `UPDATE matches` | `permission denied for table matches` |
| `DELETE FROM tournaments` | `permission denied for table tournaments` |
| `INSERT INTO match_registrations` | `new row violates row-level security policy` |
| `INSERT INTO categories` | `permission denied for table categories` |
| `UPDATE game_modes` | `permission denied for table game_modes` |
| `SELECT audit_logs` as player | 0 rows |

## 5. Atomicity and abuse resistance

- `register_for_match` locks the match row `FOR UPDATE` before the capacity
  check and increment. Concurrent requests serialise; the last-slot race is
  impossible.
- Duplicate registration is rejected (`already_registered`) before the counter
  changes.
- `cancel_match_registration` and `cancel_registration` lock the registration
  and are idempotent; counters use `greatest(x - 1, 0)`.
- Ownership is checked inside the RPC (`v_reg.user_id <> v_uid and not
  is_admin()` → `not_allowed`), so a guessed registration id is useless.
- Checks are inside the transaction, so a failed validation rolls back any
  earlier change.
- `admin_upsert_match` refuses sensitive edits on live/completed matches unless
  the caller explicitly passes `p_confirm_locked`, preventing accidental
  rewrites of results already in play.

## 6. Storage security

| Bucket | Public read | Write |
| --- | --- | --- |
| `tournament-banners` | yes | `is_admin()`, insert/update/delete |
| `category-logos` | yes | `is_admin()`, first folder `categories` |
| `match-assets` | yes | `is_admin()`, first folder `matches` or `tournaments` |

- Uploads use the user's JWT; no service-role key is shipped.
- Size and MIME limits are enforced by the bucket configuration.
- Players cannot overwrite another team's artwork because `is_admin()` is
  required for every write.
- Objects are public by design (event artwork). No PII is stored there.

## 7. Audit logging

`audit_logs` is append-only in practice: the table has no write grant for
`authenticated` or `anon`, and the only writer is `public.admin_log` inside
definer functions. Each row records `actor_id`, `action`, `entity_type`,
`entity_id`, `metadata` (`old`/`new`), and `created_at`.

Actions covered: `category.*`, `game_mode.*`, `tournament_type.*`,
`tournament.create/update/status/delete`, `match.create/update/status/publish/
unpublish/duplicate/delete`.

Only `is_admin()` callers read the log (`audit_logs_admin` policy). `0016`
added the missing table-level SELECT grant that previously caused even admins
to receive `permission denied`.

## 8. Client behaviour

- `AdminRepository.isAdmin()` calls the `is_admin` RPC; the Settings entry is
  rendered only when it returns `true`.
- The admin screens are still protected server-side; navigating to an admin
  route with a player token produces `not_admin` errors, not data.
- No admin flag is persisted in preferences or passed in mutable headers.
- Cancellation and registration update the UI only after the RPC succeeds.

## 9. Supabase advisor review

After `0016`/`0017` the remaining relevant findings are:

| Finding | Status |
| --- | --- |
| `anon_security_definer_function_executable` for the 14 admin RPCs | **Resolved by `0017`** |
| Same for `register_for_match` / `cancel_match_registration` | **Resolved by `0017`** |
| `get_leaderboard`, `get_tournament_participants`, `is_match_participant`, `is_admin` callable by anon | Pre-existing, intentional public read/identity helpers; not changed in this scope |
| `rls_enabled_no_policy` on `idempotency_keys`, `match_credentials`, `payment_events` | Pre-existing deny-all tables; intentionally inaccessible |
| Leaked password protection disabled | Pre-existing auth configuration issue; still a launch blocker |

## 10. Residual risks and operations

1. **No admin account exists in production.** `user_roles` currently contains
   only player rows. Granting admin is intentionally manual and must be done
   with a reviewed statement, for example:

   ```sql
   insert into public.user_roles (user_id, role)
   values ('<auth-user-uuid>', 'admin')
   on conflict do nothing;
   ```

   Never ship this as an app-accessible RPC, or any player could self-promote.
2. **Storage orphan cleanup** is not automatic when a match/tournament row is
   deleted. Add a scheduled job if storage growth matters.
3. **Audit retention** is unbounded. Add a retention/export policy before scale.
4. **`completed` locks** prevent status rollback but do not freeze every
   column; only the sensitive fields listed in `admin_upsert_match` require
   confirmation. Broader immutability can be added if needed.
5. **Match feed RLS** exposes all match rows to signed-in players; unpublished
   matches are filtered by the client query. If unpublished data is sensitive,
   add `is_published or is_admin()` to `matches_select`.

## 11. Verdict

The feature satisfies the security requirements: authorisation is decided on
the server from `user_roles`, RLS and table grants block direct writes, all
administrative writes go through definer RPCs with a fixed `search_path`, audit
rows are written for every mutation and readable only by admins, and storage
writes require `is_admin()`. The remaining items are operational (admin
provisioning, leaked-password protection, storage cleanup) rather than code
defects in this change set.
