# Changelog

## Unreleased — HMX Arena Android

### Added

- Full Jetpack Compose application with a white "Liquid Glass" design system.
- 46 user-facing destinations: auth, onboarding, five tabs, tournament
  detail/subscreens, matches, teams, wallet, referrals, profile, settings,
  support and legal.
- Session bootstrap with memory access token and DataStore refresh state.
- Password-recovery deep link handling (`hmxarena://auth/reset`) for fragment
  and token-hash links.
- Onboarding gate driven by `my_profile()`.
- Hilt dependency injection, Room tournament cache, Retrofit/OkHttp/Supabase
  data layer with Kotlin Serialization.
- Support ticket threads with player replies.
- Referral claim flow with server-side rules.
- JVM tests for validation, formatting and registration rules.
- Twelve project documents in `docs/`.

### Backend

- `0001_core_schema.sql` — 33 tables, enums, indexes, constraints.
- `0002_functions.sql` — business RPCs and triggers.
- `0003_rls.sql` — RLS policies, public views, grants.
- `0004_storage_seed.sql` — buckets, flags, tournament catalogue, rules,
  announcements.
- `0005_security_hardening.sql` — search paths, grant tightening, participant
  RPC.
- `0006_audit_integrity.sql` — immutable audit log and FK behaviour.
- `0007_profile_privacy_and_schedule.sql` — private contact columns, refreshed
  demo schedule, live credentials.
- `0008_support_replies.sql` — player reply policy/grant for support messages.
- RLS regression suite (`supabase/tests/rls_test.sql`), 18/18 passing.

### Changed

- Replaced the initial placeholder `MainActivity` with the real root gate.
- Moved theme ownership to `core.designsystem`; removed `ui.theme`.
- Removed the unused `SettingsRepository`.
- Removed the dead `game-account?accountId=` route; game accounts are edited in
  a dialog on the list screen.
- Introduced `RegistrationRules` so eligibility is unit-tested outside Compose.

### Fixed

- PostgREST `in.()` filter template.
- Glass component import paths and trailing-lambda parameter order.
- `copyToClipboard` composable misuse in click handlers.
- Avatar upload result handling.
- Missing `contentOrNull` import.
- Tournament participants loading through the correct repository.

### Security

- Publishable Supabase key only; no service-role key in the client.
- Contact columns revoked from `authenticated`; `my_profile()` for own data.
- Evidence bucket private; credentials only via participant-checked RPC.
- `allowBackup=false`; refresh token in private DataStore.
- Paid feature flags default `false`.

### Known limitations

- No push notifications/FCM, realtime, SMS OTP, payments, refunds or payouts.
- Email templates and the `hmxarena://auth/reset` redirect must be configured
  in the Supabase dashboard before links work end to end.
- No instrumented UI tests.
- Staff-side support replies require dashboard/service-role operation.
