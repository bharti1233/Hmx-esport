# HMX Arena — Bug and Error Report

Date: 2026-11-07  
Branch: `feature/hmx-arena-android-production`  
Baseline before this audit: `d012ef7`  
Audit commits: `c3c799c`, `46639ed`, `16bf24e`

Severity: **P0** = security/data-loss/crash, **P1** = broken core flow,
**P2** = degraded UX/performance, **P3** = polish.

## 1. Fixed defects

| ID | Sev | Defect | Root cause | Fix | Files | Status |
| --- | --- | --- | --- | --- | --- | --- |
| B-01 | P0 | Any `hmxarena://` URI could inject a session | The old handler read `access_token` from any intent and saved it without validating scheme/host/path/type | Strict parser accepts only scheme `hmxarena`, host `auth`, path `/reset` (or empty), `type=recovery`, and a token hash or a non-identical access/refresh pair | `core/util/RecoveryLinkParser.kt`, `HmxRoot.kt` | Fixed; 11 JVM tests |
| B-02 | P0 | Recovery session had a null/stale user id | `restoreFromRecovery` saved tokens without calling `/auth/v1/user`; the old user id stayed in memory | `restoreFromRecovery` is now `suspend`, verifies the access token with `/auth/v1/user`, and saves the verified id/email | `data/repository/AuthRepository.kt`, `data/remote/SupabaseApi.kt` | Fixed |
| B-03 | P0 | Access token written to disk | `persist()` stored the short-lived token in DataStore | Access token is memory-only; bootstrap removes any legacy value; refresh token remains the only persisted credential | `data/remote/SessionManager.kt` | Fixed |
| B-04 | P1 | Concurrent refresh could return `null` while a refresh was running | `AtomicBoolean` set without holding the lock that waiters used | Whole refresh runs under one `synchronized(refreshLock)`; waiters return the new token or a real failure | `SessionManager.kt` | Fixed |
| B-05 | P1 | Transient backend errors signed the player out | Any non-2xx refresh response called `clear()` | Only 400/401 clear the session; 5xx/429/network failures keep credentials for retry | `SessionManager.kt` | Fixed |
| B-06 | P1 | Requests were sent with an expired/missing access token | Interceptor only attached existing tokens; authenticator ignored 401s without an `Authorization` header | Interceptor refreshes a missing/expired token for non-auth endpoints; authenticator refreshes on any 401 when a refresh token exists | `di/NetworkModule.kt` | Fixed |
| B-07 | P1 | App opened and fired requests with no internet | No startup connectivity gate | `NetworkCapabilities` gate + one health probe, event-driven retry, full-screen offline UI | `data/connectivity/*`, `ui/connectivity/*`, `HmxRoot.kt` | Fixed |
| B-08 | P1 | Team logo uploads always failed | Live policy compared `teams.id` to `storage.foldername(t.name)` (the team name column) instead of the object path; no delete policy | Corrected policies compare `(storage.foldername(objects.name))[1]` to `teams.id` and add owner delete | `supabase/migrations/0012_*`, live Supabase | Fixed and verified live |
| B-09 | P2 | Public leaderboard/participant RPCs accepted unbounded page sizes | `limit greatest(p_limit, 0)` only | Clamp to `least(greatest(p_limit, 0), 500)` | `supabase/migrations/0013_*`, live Supabase | Fixed and verified live |
| B-10 | P2 | Dashboard and my-matches paid two network latencies in sequence | Independent Supabase reads ran sequentially | `coroutineScope` + `async` for independent reads | `TournamentRepository.kt`, `TeamRepository.kt` | Fixed |
| B-11 | P2 | Tablets/foldables/landscape used a stretched phone layout | No window-size handling | Width-based `Compact/Medium/Expanded`, readable max widths, 1/2/3-column grid, 96dp rail | `Adaptive.kt`, `Glass.kt`, `GlassBottomBar.kt`, `TournamentScreens.kt` | Fixed |
| B-12 | P2 | Low-RAM devices paid full glass cost | One effect tier for all devices | `Full/Reduced/Disabled` tier; reduced removes per-card effects and the second backdrop | `HmxBackdrop.kt`, `LiquidGlass.kt`, `Glass.kt` | Fixed |
| B-13 | P2 | Coil caches were unbounded/default-sized | No custom image loader | Memory cache 10%/15% by device, 32MB disk, crossfade off | `HmxArenaApp.kt` | Fixed |
| B-14 | P2 | Onboarding cache/server disagreement kept the old graph | `NavHost` ignores a changed `startDestination` | `key(ui.needsOnboarding)` recreates the graph | `HmxRoot.kt` | Fixed |
| B-15 | P3 | Connection copy claimed "signed in" even for a signed-out player | Copy written for one case | Neutral "Your session is preserved" | `ConnectionRequiredScreen.kt` | Fixed |
| B-16 | P3 | Visual harness reported a theme switch that never happened | The debug showcase derived `isDark` from the luminance of `HmxColor.Ink`; that token is black in Light, so the toggle was inverted. The harness also substring-matched "Dark" and tapped the top-bar icon instead of the Settings segment | Derive state from `hmxColorScheme().isDark`, exact-match the Settings "Dark" segment, and verify the "Dark glass" label before process death | `app/src/debug/.../VisualTestActivity.kt`, `.github/scripts/capture-liquid-glass.sh` | Fixed; visual run `42363668400` green |

## 2. Defects found and deliberately not changed

| ID | Sev | Finding | Why it is not a code change |
| --- | --- | --- | --- |
| O-01 | P2 | `fallbackToDestructiveMigration()` in `DatabaseModule` | The Room database contains only cached tournament JSON, never user, payment or credential data. A schema bump may discard a cache but cannot lose account data. Replacing it with a strict migration would require a versioned Room schema on every future cache change for no security benefit. Documented as an accepted risk. |
| O-02 | P2 | Leaked-password protection disabled in Supabase Auth | This is a hosted Auth setting, not a database policy. Enabling it requires dashboard/Management API access; doing it blindly from SQL is not supported. Recommended for the next backend phase. |
| O-03 | P3 | `anon` can execute `is_admin()` and `is_match_participant()` | These are boolean oracles, not data accessors. `is_admin()` is referenced by SELECT policies on public content (`announcements`, `profiles`, `tournaments`), so revoking `anon` EXECUTE would break anonymous reads with a permission error. Risk is low and intentionally retained. |
| O-04 | P3 | 22 unindexed foreign keys | Advisor findings mostly cover admin/payment/audit paths with tiny row counts. Adding indexes is safe but not a release blocker; proposed as a next-phase performance migration. |
| O-05 | P3 | RLS policies call `auth.uid()` per row | Supabase advisor `auth_rls_initplan` (30 policies). Wrapping with `(select auth.uid())` is a scale optimisation; rewriting 30 production policies is a larger change than this audit's safe-fix scope. Listed for the next backend hardening pass. |
| O-06 | P3 | `statusBarColor`/`navigationBarColor` deprecation | Required for edge-to-edge on the supported API range; the call is suppressed and has no functional alternative that works on API 26–36. |

## 3. Error-message mapping

`SupabaseApi.kt` maps Postgres/PostgREST error codes to player-facing text
(`registration_closed`, `tournament_full`, `already_registered`,
`game_account_required`, `not_match_participant`, etc.). Unknown errors fall back
to a generic retry message; no SQL text, token or stack trace is shown to the
player.

## 4. Regression guards added

- `RecoveryLinkParserTest` — scheme/host/path/type/token rules, encoded values,
  forged identical token pair, fragment precedence.
- `AdaptiveLayoutTest` — 600/840dp breakpoints, max content widths, grid
  columns, rail width.
- `ConnectivityClassificationTest` — only `Reachable` opens the gate; timeout
  and backend error stay distinct.
- Existing `ThemeTokensTest`, `ValidationTest`, `FormatTest`,
  `RegistrationRulesTest`, `SupabaseErrorTest` continue to run in CI.

## 5. Verification

All fixed items are compiled and unit-tested by the `Android CI` workflow
(`42363668406`). The visual-harness fix (B-16) is exercised by
`Liquid Glass visual verification` `42363668400`, which captures 20/20 screens and
passes both the in-place switch and process-death persistence assertions. Backend
policy/RPC changes were applied to the live `hmxesport` project and re-read from
`pg_policies` / `pg_proc` afterwards. See `TEST_EXECUTION_REPORT.md` for run ids
and `SUPABASE_COMPLETE_AUDIT.md` for the live evidence.
