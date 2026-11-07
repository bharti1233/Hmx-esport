# HMX Arena — Complete Supabase Audit

Date: 2026-11-07  
Project: `hmxesport` (`https://ahsvxxhaowwhqermruhz.supabase.co`)  
Branch: `feature/hmx-arena-android-production`  
Method: live catalog queries through the Supabase MCP connection plus the
checked-in migrations `supabase/migrations/0001…0013`.

> All findings below were read from the live project, not from documentation.
> Destructive operations (drop table/column, data deletion, role changes) were
> **not** performed. Changes were limited to storage RLS policy correction and
> RPC limit clamping, both re-read after applying.

## 1. Live inventory

| Object | Count |
| --- | --- |
| Public base tables | 33 |
| Public RLS policies | 40 |
| Public functions | 29 |
| Public triggers | 16 (on 12 tables) |
| Public indexes | 76 |
| Public views | 1 (`public_profiles`) |
| Storage buckets | 4 |
| Extensions | `pg_stat_statements`, `pgcrypto`, `plpgsql`, `supabase_vault`, `uuid-ossp` |
| Auth users | 2 profiles, 1 registration, 6 tournaments, 5 matches |

Every public table has `relrowsecurity = true`. Three tables have RLS enabled
with **no policy** (`idempotency_keys`, `match_credentials`, `payment_events`),
which intentionally blocks all client access; they are written only by
`SECURITY DEFINER` functions / server-side paths.

## 2. Tables and policies

| Table | RLS | Policies | Client access |
| --- | --- | --- | --- |
| `announcements` | on | 1 | public SELECT where `is_published OR is_admin()` |
| `app_config` | on | 1 | public SELECT (feature flags, no secrets) |
| `audit_logs` | on | 1 | admin SELECT only; immutable by trigger |
| `financial_ledger` | on | 1 | own rows or admin; immutable by trigger |
| `idempotency_keys` | on | 0 | no client access |
| `match_credentials` | on | 0 | no client access; read via `get_match_credentials` |
| `match_participants` | on | 1 | public SELECT (no game UID column) |
| `match_results` | on | 1 | verified/disputed public, submitter, admin, participant |
| `matches` | on | 1 | public SELECT |
| `notifications` | on | 2 | own SELECT; own UPDATE (mark read) |
| `payment_events` | on | 0 | no client access; immutable |
| `payment_orders` | on | 1 | own or admin |
| `payout_records` | on | 1 | own or admin |
| `player_game_accounts` | on | 4 | own SELECT/INSERT/UPDATE/DELETE |
| `prize_allocations` | on | 1 | admin or owning registration |
| `profiles` | on | 2 | public non-deleted profile columns; own UPDATE |
| `referral_claims` | on | 1 | referrer, referred or admin |
| `refunds` | on | 1 | own or admin |
| `registration_members` | on | 1 | registration owner, team member or admin |
| `registrations` | on | 1 | own, admin, or member of the registering team |
| `reports` | on | 2 | own INSERT; own/admin SELECT |
| `result_disputes` | on | 1 | raiser or admin |
| `result_evidence` | on | 1 | uploader, admin, or match participant |
| `support_messages` | on | 2 | own SELECT; own INSERT into own ticket |
| `support_tickets` | on | 1 | own or admin |
| `team_invitations` | on | 2 | invited user, inviter or admin; owner DELETE |
| `team_members` | on | 1 | public SELECT |
| `teams` | on | 3 | public SELECT; owner UPDATE/DELETE |
| `tournament_eligibility` | on | 1 | public SELECT |
| `tournament_rules` | on | 1 | public SELECT |
| `tournaments` | on | 1 | non-draft public, admin sees drafts |
| `user_roles` | on | 1 | own roles or admin |
| `wallet_accounts` | on | 1 | own or admin |

### 2.1 Sensitive columns

- `profiles.email` and `profiles.phone` exist but are **not granted** to `anon`
  or `authenticated`; only the safe columns are (`id`, `username`,
  `display_name`, `avatar_url`, `bio`, `region`, `points`, `matches_played`,
  `wins`, `created_at`, `deleted_at`, plus `onboarding_completed` for the owner).
  This was verified from `information_schema.column_privileges`.
- `registration_members.game_uid` and `player_game_accounts.game_uid` are
  protected by RLS (owner/team/admin only). They are not exposed to anonymous
  callers; the public participant RPC deliberately omits `game_uid`.
- `match_credentials.room_password` is in a zero-policy table and is released
  only through `get_match_credentials(match_id)`, which checks the caller is a
  match participant.
- No service-role key or `SUPABASE_SERVICE_ROLE_KEY` exists in the Android
  source, `gradle.properties`, or the checked-in CI files. The client only uses
  the publishable key.

## 3. Functions

29 public functions. 26 are `SECURITY DEFINER`; the trusted ones pin
`search_path = public, pg_temp`. Only three are executable by `anon`:

| Function | `anon` EXECUTE | Reason |
| --- | --- | --- |
| `is_admin()` | yes | Referenced by public SELECT policies (`announcements`, `profiles`, `tournaments`); revoking it would break anonymous reads with `42501` |
| `is_match_participant(uuid, uuid)` | yes | Boolean predicate used in `match_results` policy for authenticated callers; does not return data |
| `get_leaderboard(...)` | yes | Public leaderboard; read-only aggregate |
| `get_tournament_participants(...)` | yes | Public participant projection; `game_uid` omitted |

All mutating RPCs (`register_for_tournament`, `submit_match_result`,
`claim_referral`, `verify_match_result`, `resolve_dispute`,
`set_match_credentials`, `verify_match_result`, `request_account_deletion`,
`complete_onboarding`, team RPCs, support RPCs) are executable by
`authenticated` only, and the admin-only ones are not executable by either
client role.

### 3.1 Fix applied — unbounded page size (migration 0013)

`get_leaderboard` and `get_tournament_participants` previously used
`limit greatest(p_limit, 0)`. An anonymous caller could request an arbitrarily
large page and force the server to sort/materialise the full result set.

Applied live and checked in:

```sql
limit least(greatest(p_limit, 0), 500) offset greatest(p_offset, 0)
```

The app requests 50 leaderboard rows and 100 participant rows, so 500 preserves
every legitimate page. Verified live in `pg_proc` after applying.

## 4. Triggers

16 triggers across 12 tables:

- `set_updated_at` on `app_config`, `match_credentials`, `matches`,
  `payment_orders`, `player_game_accounts`, `profiles`, `support_tickets`,
  `teams`, `tournaments`, `wallet_accounts`;
- `prevent_mutation` on `audit_logs`, `financial_ledger`, `payment_events`
  (UPDATE and DELETE), protecting financial/audit history from tampering;
- `handle_new_user` on `auth.users` creates the profile row after signup.

No trigger was found that writes to a table the client can update with elevated
privileges; all trigger functions pin their `search_path` where required.

## 5. Storage

4 buckets, all with size and MIME limits:

| Bucket | Public | Limit | MIME |
| --- | --- | --- | --- |
| `avatars` | yes | 2 MB | jpeg/png/webp |
| `team-logos` | yes | 2 MB | jpeg/png/webp |
| `tournament-banners` | yes | 5 MB | jpeg/png/webp |
| `result-evidence` | no | 10 MB | jpeg/png/webp |

### 5.1 Production defect found and fixed

Live `pg_policies` originally showed:

```sql
t.id::text = (storage.foldername(t.name))[1]
```

`name` inside the `teams` subquery resolves to **`teams.name`**, not the storage
object's path. The predicate could never match a real team id, so every logo
upload failed, and there was no delete policy.

Applied migration `0012_fix_team_logo_storage_policies.sql` (and re-applied the
same definition with the qualified column, because the first attempt reproduced
the unqualified-name bug):

```sql
t.id::text = (storage.foldername(objects.name))[1]
```

Verified live afterwards: insert, update and delete policies all reference
`storage.foldername(objects.name)`; the update `USING` clause is
`bucket_id = 'team-logos' and owner = auth.uid()`. Public read is unchanged.

`avatars` policies already used `storage.foldername(name)` at top level (no
subquery), so the unqualified name was correct there and was left untouched.

## 6. Indexes and constraints

76 indexes. 48 foreign keys, of which **22 have no covering index** (verified by
comparing each FK column tuple against `pg_index`):

`idempotency_keys.user_id`, `match_participants.team_id`,
`match_results.submitted_by`, `match_results.verified_by`,
`payment_events.order_id`, `payment_orders.user_id`, `payout_records.user_id`,
`prize_allocations.registration_id`, `prize_allocations.tournament_id`,
`profiles.referred_by`, `referral_claims.referrer_id`, `refunds.order_id`,
`refunds.user_id`, `registration_members.user_id`,
`registrations.team_id`, `reports.reporter_id`,
`result_disputes.raised_by`, `result_evidence.uploaded_by`,
`support_messages.sender_id`, `team_invitations.invited_by`,
`teams.owner_id`, `user_roles.granted_by` (`match_credentials` is covered).

The app's live tables are tiny (largest is `profiles` at 96 kB, 6 tournaments,
5 matches), so none of these currently causes a slow query. They matter as
volume grows, especially for DELETE cascades. Adding indexes is safe but was left
for the next migration to keep this audit's production change set minimal.

### 6.1 Query performance

- `pg_stat_statements` is installed.
- All row counts are small (0–30 rows per table); no query is currently
  data-bound.
- The two public RPCs are `STABLE` and bounded after migration 0013.
- Advisor finding `auth_rls_initplan` (30 policies) suggests wrapping
  `auth.uid()` in `(select auth.uid())`; this is a scale optimisation and was
  deliberately not rewritten across 30 production policies in this pass.

## 7. Auth

- Email/password signup with email confirmation; the client handles
  `needsVerification`.
- Password recovery uses the `hmxarena://auth/reset` deep link with a strict
  parser and a verified `/auth/v1/user` call.
- Refresh tokens are persisted; access tokens are memory-only.
- Sign-out calls `/auth/v1/logout` and clears DataStore.
- **Leaked-password protection is disabled** in the hosted Auth settings. This is
  a dashboard/Management API setting; it cannot be safely enabled from SQL and
  was not changed. It is the top remaining backend recommendation.

## 8. Advisors (live)

| Advisor | Count | Assessment |
| --- | --- | --- |
| `rls_enabled_no_policy` | 3 | Intentional deny-all for server-only tables |
| `security_definer_view` | 0 | `public_profiles` is `security_invoker` |
| anon-executable `SECURITY DEFINER` | 4 | Predicates + bounded public reads, assessed above |
| `auth_rls_initplan` | 30 | Scale optimisation, backlog |
| unindexed foreign keys | 22 | Backlog, no current impact |
| leaked-password protection | 1 | Hosted Auth setting, backlog |

No advisor finding indicated a missing RLS policy on a client table, a
`SECURITY DEFINER` view, or a publicly exposed secret.

## 9. Migrations

Checked in: `0001_core_schema`, `0002_functions`, `0003_rls`,
`0004_storage_seed`, `0005_security_hardening`,
`0006_audit_integrity`, `0007_profile_privacy_and_schedule`,
`0008_support_replies`, `0009_onboarding_rpc`,
`0010_onboarding_notify_and_public_grants`,
`0011_public_profiles_deleted_at`, `0012_fix_team_logo_storage_policies`,
`0013_clamp_public_rpc_limits`.

Previously applied migrations are visible through `list_migrations`; 0012 and
0013 were applied during this audit. No migration was rolled back and no table
or row was destroyed.

## 10. Verification status

| Item | Status |
| --- | --- |
| Table/column/RLS inventory | Verified live |
| Storage policy fix | Applied and re-read live |
| RPC limit clamp | Applied and re-read live |
| Advisor review | Verified live |
| Row-level behaviour tests | A regression script exists (`supabase/tests/rls_test.sql`) but was **not executed**; it needs a development database |
| Backup/restore drill | Not performed (would require project-level access) |

## 11. Recommended next backend phase

1. Enable leaked-password protection in Supabase Auth.
2. Add the 22 missing FK indexes in one non-destructive migration.
3. Rewrite `auth.uid()` policies as `(select auth.uid())` in a reviewed batch.
4. Add pgTAP or SQL impersonation tests for the top 10 policies.
5. Add a `match_credentials` visibility test proving non-participants get zero
   rows from `get_match_credentials`.
