# Final Audit

This audit maps the delivery requirements to evidence and records every
known gap. It is deliberately conservative: if something is not implemented,
it is listed as a gap rather than described as "coming soon".

## 1. Repository and process

| Requirement | Status | Evidence |
| --- | --- | --- |
| Public repo used | Yes | `github.com/bharti1233/Hmx-esport` |
| Feature branch | Yes | `feature/hmx-arena-android-production` |
| `main` preserved | Yes | Branch was never deleted or force-pushed. |
| No secrets committed | Verified | Only the publishable Supabase key is in `gradle.properties`; `.gitignore` excludes env/keystore files. |
| CI-only builds | Yes | All results come from GitHub Actions; no local APK built. |
| CI artifact | Yes | `hmx-arena-debug-apk` uploaded on success. |
| Commit history | Yes | Scaffold, CI fixes, full app, compile fixes. |

## 2. Stack

| Requirement | Status |
| --- | --- |
| Kotlin | Yes |
| Jetpack Compose + Material 3 | Yes |
| MVVM + StateFlow | Yes (one UI state per screen) |
| Clean-ish layering | Yes at package level (single module, documented reason) |
| Coroutines | Yes |
| Hilt | Yes |
| Room | Yes (tournament catalogue cache) |
| DataStore | Yes (session metadata + onboarding) |
| Retrofit + OkHttp | Yes |
| Kotlin Serialization | Yes |

## 3. Design

| Requirement | Status |
| --- | --- |
| Pure white background | Yes (`HmxColor.Canvas = #FFFFFF`) |
| Frosted translucent white cards | Yes (`GlassSurface`) |
| Hairline borders | Yes (`HmxDimens.hairline`) |
| Monochrome | Yes; only ink, grey, and semantic red for destructive actions |
| Soft shadows | Yes |
| No purple/blue/neon | Yes; no coloured accent tokens exist |
| No colourful gradients | Yes; only white/grey radial washes |
| No dark theme | Yes; single light scheme |
| Glass blur | Not used; faked with alpha + hairline + elevation for API 26 support |

## 4. Screens and flows

Signed-in destinations: 40 (5 tabs + 19 account/support + 6 tournament +
5 match + 5 team). Auth/recovery: 6. Total user-facing screens: 46, plus four
legal documents rendered by one screen.

| Area | Status | Notes |
| --- | --- | --- |
| Welcome / sign up / sign in / verify / forgot | Done | Real Supabase Auth. |
| Password recovery deep link | Done | Fragment + token-hash parsing, invalid-link dialog. |
| Onboarding gate | Done | `my_profile().onboarding_completed`. |
| Home | Done | Real featured, matches, news, unread count. |
| Catalogue + search/filter | Done | Server + Room cache. |
| Tournament detail + subscreens | Done | Rules, participants, schedule, leaderboard. |
| Registration / cancellation | Done | RPC + eligibility rules + slot counts. |
| My registrations | Done | Real rows. |
| Matches / detail / credentials | Done | Credentials only through RPC. |
| Result submission | Done | Screenshot evidence, private bucket. |
| Disputes | Done | Reason validation, server RPC. |
| Leaderboards | Done | Global + per tournament. |
| Teams (create/invite/accept/manage) | Done | Server RPCs enforce ownership. |
| Wallet / transactions | Done (read-only) | Funding disabled. |
| Referrals | Done | Claim RPC with 7-day window. |
| Profile / edit / game accounts | Done | Avatar upload, validation. |
| Settings / account / support / help / about / legal | Done | Support replies implemented. |
| Notifications | In-app only | No push/FCM. Badge + mark read. |
| Dead buttons / placeholders | None known | Every handler calls a repository or navigates. |

## 5. Backend

| Requirement | Status |
| --- | --- |
| Real Supabase project | Yes |
| Migrations 0001-0008 applied | Yes |
| 33 tables with constraints/indexes | Yes |
| RLS on every table | Yes |
| Private evidence bucket | Yes |
| Room credentials only via RPC | Yes |
| Payments/refunds/payouts disabled | Yes — server flags false |
| RLS test suite | 18/18 checks pass |

## 6. Security

| Requirement | Status |
| --- | --- |
| No service-role key in app | Yes |
| Contact details not exposed to other users | Yes (column revoke + `my_profile()`) |
| Owner/team/admin policies | Yes |
| Audit log immutable | Yes |
| Input validation | Yes (client + DB) |
| Token storage | Access in memory, refresh in private DataStore |
| Backup disabled | Yes (`allowBackup=false`) |

## 7. Tests

| Requirement | Status |
| --- | --- |
| JVM unit tests | 3 files, validation/formatting/rules |
| RLS SQL tests | 18/18 passing |
| CI compile + lint + APK | Green after fixes (see FINAL_TEST_REPORT for run id) |
| Instrumented UI tests | **Not present** |
| Payment tests | **Not applicable / disabled** |

## 8. Documentation

All 12 files exist in `docs/`: INITIAL_AUDIT, PRD, ARCHITECTURE,
DATABASE_SCHEMA, API_SPECIFICATION, SECURITY_PLAN, IMPLEMENTATION_PLAN,
TEST_PLAN, FINAL_AUDIT, FINAL_TEST_REPORT, BUILD_GUIDE, CHANGELOG.

## 9. Known gaps (must be stated to stakeholders)

1. **No push notifications / FCM / email digests.** The app says so in Settings.
2. **No realtime.** Refresh/open reloads only.
3. **No SMS OTP.** Email auth only.
4. **Paid flows are schema-only.** No gateway, no funding, no refunds/payouts.
5. **Email templates and redirect allowlist need dashboard configuration**
   before verification/recovery links work end to end.
6. **No instrumented UI test suite.** Manual smoke checklist provided.
7. **Single Gradle module.** Chosen for CI memory; not a multi-module design.
8. **No admin console.** Staff operate through the Supabase dashboard/service role.
9. **No certificate pinning / Play Integrity.**
10. **Demo seed data exists** for free tournaments and announcements; it is
    clearly labelled demo content, not fabricated live results.
11. **`slots_filled` is not updated by triggers on every path**; the UI derives
    remaining slots from `max_slots - slots_filled`, and registration RPC is the
    authority for capacity. Treat displayed counts as informational.
12. **Support replies from staff are not automated**; they are inserted
    server-side and appear to the player on reload.

## 10. Merge readiness

The feature branch is merge-ready when the latest CI run is green. No secrets,
no local-only artifacts and no generated build output are committed.
