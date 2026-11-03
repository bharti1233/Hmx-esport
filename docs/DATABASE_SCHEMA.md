# Database Schema

Project: `ahsvxxhaowwhqermruhz` (Supabase / PostgreSQL)

## 1. Migrations

| File | Contents |
| --- | --- |
| `0001_core_schema.sql` | Enums, 33 tables, indexes, constraints. |
| `0002_functions.sql` | Business RPCs, triggers, helper functions. |
| `0003_rls.sql` | RLS enablement, policies, grants. |
| `0004_storage_seed.sql` | Buckets, app flags, demo catalogue, rules, announcements, staff roles. |
| `0005_security_hardening.sql` | Function `search_path`, grant tightening, privilege fixes. |
| `0006_audit_integrity.sql` | Audit immutability, FK behaviour for deleted users. |
| `0007_profile_privacy_and_schedule.sql` | Contact-column revocation, relative schedule refresh, live credentials. |
| `0008_support_replies.sql` | Player insert policy/grant for support messages. |

## 2. Enums

`user_role`, `game_title`, `tournament_mode`, `entry_type`, `tournament_status`,
`registration_status`, `registration_type`, `match_status`, `result_status`,
`dispute_status`, `ticket_status`, `notification_type`, `ledger_type`,
`payment_status`, `refund_status`, `payout_status`, `referral_status`.

## 3. Tables

### Identity
- `profiles` — one row per `auth.users` row. Public fields: username (unique),
  display name, avatar, bio, region, points, matches/wins, onboarding flag,
  referral code. Private: email, phone, referred_by, deleted_at.
- `player_game_accounts` — Free Fire IGN, UID, level, verified flag.
- `user_roles` — admin/staff assignment (read-only to clients).

### Teams
- `teams` — name, tag, game, logo, owner.
- `team_members` — roster with captain flag.
- `team_invitations` — invitee, inviter, status, expiry.

### Competition
- `tournaments` — slug, title, mode, entry type/fee, prize pool, slots,
  status, schedule, banner, map, region, featured flag.
- `tournament_rules` — ordered rule text.
- `tournament_eligibility` — min level/points, game account requirement,
  allowed regions.
- `registrations` — solo/team entry, status, slot number.
- `registration_members` — snapshot of roster for a team registration.
- `matches` — round, schedule, status, map, mode.
- `match_participants` — registration ↔ match.
- `match_credentials` — room ID/password, release timestamp.
- `match_results` — placement, kills, points, evidence note, verify state.
- `result_evidence` — private storage object metadata.
- `result_disputes` — reason, status, resolution.

### Money (disabled flows)
- `wallet_accounts` — balance/reserved in paise.
- `financial_ledger` — immutable double-entry style entries.
- `payment_orders`, `payment_events` — gateway order lifecycle.
- `refunds`, `prize_allocations`, `payout_records`.
- RLS allows owners to read; inserts happen only through service role/RPCs.

### Communication
- `notifications` — per-user in-app alerts, read state.
- `announcements` — published editorial posts.
- `support_tickets` — category, subject, status, owner.
- `support_messages` — thread messages; players may insert on their own ticket.
- `reports` — player reports (owner read, insert only).

### Platform
- `app_config` — jsonb flags (`free_tournaments_enabled`, `paid_*`,
  `referrals_enabled`, `waitlist_enabled`, `support_email`, `terms_version`).
- `audit_logs` — append-only actor/action/entity trail; rows cannot be updated
  or deleted by clients.
- `idempotency_keys` — protects mutating RPCs from duplicate submissions.
- `referral_claims` — referrer/referred/code/status/points.

## 4. Key functions

| Function | Purpose |
| --- | --- |
| `handle_new_user()` | Creates profile + wallet + referral code on signup. |
| `create_team`, `invite_team_member`, `respond_team_invitation` | Team lifecycle. |
| `leave_team`, `transfer_team_ownership` | Ownership-safe roster changes. |
| `register_for_tournament(tournament, team)` | Validates flags, eligibility, capacity, duplicates, roster; allocates slot. |
| `cancel_registration` | Cancels and frees capacity. |
| `get_match_credentials(match)` | Returns room details only to participants. |
| `submit_match_result(...)` | Upserts player result, enforces window, stores evidence pointer. |
| `verify_match_result` | Staff verification and points recalculation. |
| `raise_dispute`, `resolve_dispute` | Dispute lifecycle. |
| `get_leaderboard(scope, period, ...)` | Global and tournament boards. |
| `claim_referral(code)` | 7-day window, self-referral and duplicates blocked. |
| `request_account_deletion` | Soft-deletes profile and anonymises identity. |
| `mark_notifications_read` | Bulk read state. |
| `submit_support_ticket(category, subject, body)` | Creates ticket + first message. |
| `notify(...)` | Internal notification writer. |
| `flag(key)` | Reads `app_config`. |
| `is_admin()` | Role check used by RLS. |

## 5. RLS model

- Every public table has RLS enabled.
- `profiles`: public columns readable by anyone; contact columns revoked from
  `authenticated` and only returned by `my_profile()`.
- `teams`/`team_members`/`tournaments`/`announcements`: public read.
- `registrations`: owner, team members, and admins.
- `match_credentials`: participants and admins only; the app uses the RPC.
- `result_evidence`: uploader, dispute parties and admins; bucket is private.
- `wallet_*`, `payment_*`, `refunds`, `prizes`, `payouts`, `notifications`,
  `support_*`, `reports`, `referral_claims`: owner (and admin) only.
- `audit_logs`: admin read, no client writes.
- RPCs are `security definer` with fixed `search_path` and explicit grants.

## 6. Storage buckets

| Bucket | Visibility | Purpose |
| --- | --- | --- |
| `avatars` | public read, owner write | Profile photos |
| `team-logos` | public read, owner write | Team emblems |
| `tournament-banners` | public read, staff write | Catalogue art |
| `result-evidence` | private | Screenshots for result disputes |

## 7. Seed data

Free tournaments with schedules relative to `now()`:

- `clash-squad-cup`, `free-fire-daily-cup`, `solo-rush`, `hmx-pro-cup` — open.
- `hmx-weekend-live` — live, credentials `846291` / `HMX2026`.
- `hmx-launch-cup` — completed with verified results.

Also seeded: rules, eligibility, announcements, app flags, staff roles.
No fabricated `slots_filled` values: capacity is computed from registrations.
