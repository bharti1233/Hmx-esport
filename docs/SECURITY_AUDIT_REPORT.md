# HMX Arena — Security Audit Report

Date: 2026-11-07  
Branch: `feature/hmx-arena-android-production`  
Commit: `16bf24e`  
Scope: Android client, session handling, deep links, Supabase Auth, RLS,
storage policies, RPC surface, CI secrets.

## 1. Executive summary

| Severity | Found | Fixed | Open |
| --- | --- | --- | --- |
| Critical | 2 | 2 | 0 |
| High | 3 | 3 | 0 |
| Medium | 4 | 3 | 1 |
| Low / informational | 6 | 2 | 4 |

The two critical issues were a **deep-link session-injection flaw** and a
**team-logo storage policy that made uploads impossible while granting no
ownership check on delete**. Both are fixed. The largest remaining item is
**leaked-password protection being disabled** in Supabase Auth, which requires a
hosted dashboard/Management API change rather than SQL.

## 2. Critical findings (fixed)

### S-01 — Any `hmxarena://` URI could replace the session (CVSS ~8.8)

**Before.** `HmxRoot` treated any `hmxarena://` intent as a recovery link and
saved whatever `access_token`/`refresh_token` it contained. A malicious app on
the device could `startActivity` with a crafted URI and swap the player's
session, or a phishing link could persist an attacker-controlled session.

**Fix.** `core/util/RecoveryLinkParser.kt` now requires:

- scheme exactly `hmxarena`;
- host exactly `auth`;
- path `/reset` (or empty);
- `type=recovery`;
- either a non-blank `token_hash`, **or** a non-blank access/refresh pair whose
  two values differ (a real pair can never be identical).

The raw intent is consumed once, and the link is queued until the connectivity
gate is ready. `RecoveryLinkParserTest` covers wrong scheme, host and path,
missing/wrong type, forged identical pair, percent-encoding, blank input, and
fragment-over-query precedence.

### S-02 — Team logo storage policies used the wrong `name` column

**Before (live).** `team_logos_owner_insert`/`_update` compared
`t.id::text` with `(storage.foldername(t.name))[1]`. Inside the `teams`
subquery, `name` resolves to `teams.name`, not the uploaded object's path, so the
predicate always failed; no authenticated user could upload a logo. There was no
delete policy at all, so owners could not remove a logo either.

**Fix.** Migration `0012` (applied live and checked in) uses the qualified
`(storage.foldername(objects.name))[1]` and adds an owner delete policy. Public
read remains unchanged.

**Residual.** The object is public-read by bucket design (team logos are shown on
public team cards). The size (2 MB) and MIME allow-list (`jpeg/png/webp`) are
enforced by the bucket.

## 3. High findings (fixed)

### S-03 — Access token persisted to disk

`SessionManager.persist()` wrote the short-lived `access_token` to DataStore.
Any process with root/backup access could read a bearer token.

**Fix.** The access token is memory-only. `persist()` writes only the refresh
token, user id, email and onboarding flag. `bootstrap()` deletes any legacy
`access_token` key. This keeps "stay signed in" while shrinking the on-disk
credential to the refresh token (which is revocable and rotates).

### S-04 — A backend outage signed the player out

Any non-2xx refresh response called `clear()`. A 500/429/timeout therefore
destroyed the refresh token — a denial-of-service against the account.

**Fix.** Only `400` and `401` clear the session. `5xx`, `429`, timeouts and I/O
errors keep the refresh token for a later retry.

### S-05 — Recovery session could be attached to the wrong user

`restoreFromRecovery` saved tokens without verifying them and without updating
the in-memory user id, so the app could show player A's data while holding
player B's session.

**Fix.** `restoreFromRecovery` is now `suspend`, calls `GET /auth/v1/user` with the
new token, and only then stores the verified id/email. A failure returns `false`
and the UI shows "invalid or expired link".

## 4. Medium findings

### S-06 — Public RPC page size was unbounded (fixed)

`get_leaderboard` and `get_tournament_participants` were anonymous-executable
and accepted any positive `p_limit`, allowing a cheap client to force a full
sort/materialisation. Migration `0013` clamps to
`least(greatest(p_limit, 0), 500)`. Verified live.

### S-07 — `team_members` and `matches`/`match_participants` are public-read (accepted)

`team_members_select_all` is `qual = true`, so anonymous callers can read team
rosters (user ids + team ids). The app's public team pages need this. No email,
phone or game UID is in `team_members`; the ids are already visible as team
owners and public profiles. `matches` and `match_participants` contain only
bracket/slot/score data. Accepted as product design, but worth revisiting if
rosters become private.

### S-08 — No certificate pinning (accepted for now)

The client trusts the platform CA store. Pinning Supabase would break on
certificate rotation and would require a remote config escape hatch. The
connection is HTTPS and the publishable key is not a secret; pinning is a
next-phase hardening option, not a release blocker.

### S-09 — `allowBackup="false"` but DataStore is not additionally encrypted (open)

Backup is disabled in the manifest, and app-private DataStore is protected by
the OS sandbox. The refresh token is not encrypted with the Android Keystore.
For a device with root or a debuggable production build this matters; the app
does not ship a debuggable release (`isMinifyEnabled = true`,
`debuggable` only in debug). Encrypting DataStore with a Keystore key is a
reasonable next step but not required for the current threat model.

## 5. Low / informational

| ID | Finding | Status |
| --- | --- | --- |
| S-10 | Debug-only `VisualTestActivity` is exported | Debug manifest only; absent from release |
| S-11 | Custom scheme `hmxarena` can be registered by another app | Strict parser + `/auth/v1/user` verification make injection ineffective |
| S-12 | Clipboard copy uses `ClipData.newPlainText` without `EXTRA_IS_SENSITIVE` | Copied values are UPI/tag/support text, not tokens; low risk |
| S-13 | No `Log.*`/`println` calls in production source | Verified clean |
| S-14 | Publishable Supabase key in `gradle.properties` | It is a public anon key by design; RLS is the control, and no service-role key exists anywhere |
| S-15 | `auth_rls_initplan` on 30 policies | Performance, not confidentiality |

## 6. Verified controls

- `android:allowBackup="false"`; no `android:debuggable` in the main manifest.
- Only `MainActivity` is exported in release; the debug harness is debug-only.
- No `WebView`, no `setJavaScriptEnabled`, no `PendingIntent`, no dynamic code
  loading.
- No `usesCleartextTraffic`, no network-security-config exception, no HTTP URL:
  the only base URL is `https://ahsvxxhaowwhqermruhz.supabase.co`.
- No service-role key, JWT secret, database password or private key is present in
  the repository or the CI configuration.
- Supabase publishable key is injected as a `BuildConfig` value; it is the anon
  key and is expected to be public.
- RLS is enabled on all 33 public tables; the three deny-all tables are
  server-only.
- `profiles.email`/`phone` are not granted to `anon`/`authenticated`.
- `match_credentials` is a zero-policy table read only through a participant
  checked RPC.
- Storage buckets enforce size + MIME limits.
- Release build shrinks and obfuscates with R8.

## 7. Open items requiring non-SQL action

1. **Enable leaked-password protection** in Supabase Auth (dashboard or
   Management API). This is the single highest-value open security item.
2. Consider Keystore-backed encryption for the refresh token (S-09).
3. Optionally add certificate pinning with a rotation escape hatch (S-08).
4. Add SQL impersonation tests for the top 10 RLS policies.

## 8. Verification status

| Control | Evidence |
| --- | --- |
| Deep-link parser | 11 JVM tests in `RecoveryLinkParserTest`; CI |
| Storage policies | Live `pg_policies` re-read after migration |
| Session persistence | Code review + CI compile; no on-device DataStore inspection |
| RPC limits | Live `pg_proc` re-read |
| RLS coverage | Live `pg_class.relrowsecurity` = true for all 33 tables |
| Persistence | Code review + CI compile; no on-device DataStore inspection |
| RPC limits | Live `pg_proc` re-read |
| RLS coverage | Live `pg_class.relrowsecurity` = true for all 33 tables |
| Column grants | Live `information_schema.column_privileges` |
| Secrets scan | Repository grep for service-role/secret strings; none found |
| Checked-in RLS suite | `supabase/tests/rls_test.sql` exists but was **not run** (requires a development DB) |
| Penetration test | **Not performed**; no external test environment authorised |
