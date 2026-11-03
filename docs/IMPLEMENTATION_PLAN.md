# Implementation Plan

## Phase 0 — Audit and scaffold (done)

- Inspect empty repo, reference image and constraints.
- Choose Gradle/AGP/Kotlin/Compose versions.
- Add CI workflow: unit tests → lint → debug APK → artifact upload.
- Add Hilt application, manifest, launcher icon, splash theme.

## Phase 1 — Backend (done)

1. `0001_core_schema.sql`: enums, tables, indexes, constraints.
2. `0002_functions.sql`: signup trigger, teams, registration, credentials,
   results, disputes, leaderboard, referrals, deletion, support, notifications.
3. `0003_rls.sql`: enable RLS, policies, grants.
4. `0004_storage_seed.sql`: buckets, flags, tournaments, rules, announcements.
5. `0005_security_hardening.sql`: search paths, grant tightening.
6. `0006_audit_integrity.sql`: immutable audit log, FK behaviour.
7. `0007_profile_privacy_and_schedule.sql`: private contact columns, schedule
   refresh relative to `now()`, live match credentials.
8. `0008_support_replies.sql`: player replies to own tickets.
9. Run `supabase/tests/rls_test.sql` until all checks pass.

## Phase 2 — Data layer (done)

- Transport models with Kotlin Serialization.
- Retrofit interfaces for Auth, PostgREST, Storage.
- OkHttp auth interceptor + blocking refresh authenticator.
- `SessionManager` (memory access token, DataStore refresh state).
- `RemoteDataSource` helpers + error normalisation.
- Repositories: Auth, Player, Tournament (+ Room cache), Team.
- Hilt network/database modules.

## Phase 3 — Design system (done)

- Single light colour scheme, type scale, shapes.
- Glass surface, scaffold, top bar, bottom bar.
- Buttons, icon buttons, chips, section headers, stat cells, action rows.
- Shared states: loading, error, notice, success, empty, confirm dialog.

## Phase 4 — Screens (done)

1. Auth: welcome, login, signup, verify email, forgot password, recovery reset.
2. Onboarding: complete profile, game account.
3. Shell: five tabs + glass bottom bar.
4. Home and discovery: featured/live lists, announcements, notifications,
   search, player profile.
5. Tournaments: catalogue, detail, rules, participants, schedule, register,
   my registrations, tournament leaderboard.
6. Matches: list, detail, credentials, results, submit, dispute.
7. Teams: overview, create, invite, invitations, manage.
8. Wallet, transactions, referral.
9. Profile: view, edit, game accounts.
10. Settings: account, support list/form/thread, help, about, legal.

## Phase 5 — Verification (in progress)

- JVM tests for validation, formatting and registration rules.
- Compile + lint + package through GitHub Actions.
- RLS regression suite against the live project.
- Fix failures, rerun until green.
- Capture debug APK artifact.

## Phase 6 — Documentation and delivery (in progress)

- Twelve documents in `docs/`.
- Final audit mapping requirements to evidence.
- Final test report with CI run ids.
- Merge feature branch to `main` when checks are green.

## Sequencing notes

- The screen layer was written before the first CI compile; this was a
  deliberate trade-off to avoid local builds on a low-RAM device. Compile
  errors are fixed in CI iterations.
- Paid features are schema-only. No UI work will be done until compliance
  approval and a gateway decision exist.
- Push notifications require a Firebase project and backend keys; deferred.
