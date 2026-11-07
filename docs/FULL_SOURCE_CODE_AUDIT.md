# HMX Arena — Full Source Code Audit

Date: 2026-11-07  
Branch: `feature/hmx-arena-android-production`  
Audit commits: `c3c799c`, `46639ed`, `16bf24e`  
Repository: `https://github.com/bharti1233/Hmx-esport.git`

## 1. Scope and method

This audit is based on the actual repository contents and the live Supabase
project `hmxesport`, not on prior design documents. Every Kotlin module, screen,
ViewModel, repository, remote API interface, Room entity, SQL migration, storage
policy, and CI workflow in the repository was opened and traced.

Two rules were applied throughout:

1. Behaviour, navigation, authentication and the monochrome Liquid Glass design
   must not be removed or rewritten.
2. A result is only called "verified" when a GitHub Actions run produced it.
   Local execution of Android builds is not possible in this environment (no
   Android SDK, Gradle or emulator installed), so build/test evidence comes from
   the workflows described in `TEST_EXECUTION_REPORT.md`.

## 2. Repository inventory

| Item | Count / value |
| --- | --- |
| Tracked files | 124 |
| Kotlin files | 59 |
| SQL migrations | 13 (`0001`–`0013`) plus 1 RLS regression script (`supabase/tests/rls_test.sql`) |
| UI Kotlin files | 24 |
| `@HiltViewModel` classes | 17 |
| Repositories | 4 (`Auth`, `Player`, `Team`, `Tournament`) |
| Android modules | 1 (`:app`) |
| `minSdk` / `targetSdk` / `compileSdk` | 26 / 36 / 36 |
| Kotlin / Compose | 2.2.21 / 1.9.4 (BOM) |
| Backdrop | `io.github.kyant0:backdrop:1.0.0` (pinned) |
| Test source files | 8 JVM unit-test classes |

### Application layers

- `com.hmx.arena` — `MainActivity`, `HmxRoot`, `HmxArenaApp`.
- `core.designsystem` — colour tokens, typography, Liquid Glass primitives,
  adaptive breakpoints, theme persistence and splash bootstrap.
- `core.model` / `core.util` — serialisable domain models, validation, formatting,
  recovery-link parsing.
- `data.remote` — Retrofit/OkHttp API surface, session manager, error mapping.
- `data.local` — Room database used only as a tournament catalogue cache.
- `data.repository` — auth, player, team and tournament business logic.
- `data.connectivity` — `NetworkCapabilities` monitor and backend health probe.
- `ui.*` — 24 composable files and their ViewModels: auth, onboarding, home,
  discover, tournaments, registration, matches, leaderboard, teams, profile,
  wallet, referrals, support, settings, navigation, shell and connectivity.

## 3. Files inspected

### Build and platform configuration

- `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`,
  `gradle.properties`, `settings.gradle.kts`, `gradlew`, wrapper properties.
- `app/src/main/AndroidManifest.xml`, `app/src/debug/AndroidManifest.xml`.
- `app/proguard-rules.pro`, Room schema export configuration.

### Entry points and root state

- `MainActivity.kt`, `HmxRoot.kt`, `HmxArenaApp.kt`.
- `ui/navigation/MainNavHost.kt`, `Routes.kt`, `NavExt.kt`.
- `ui/shell/GlassBottomBar.kt`.

### Design system

- `Theme.kt`, `ThemePreferences.kt`, `Adaptive.kt`.
- `Glass.kt`, `LiquidGlass.kt`, `HmxBackdrop.kt`.
- `ui/common/HmxGlassComponents.kt`, `CommonUi.kt`, `LegalText.kt`.

### Screens and ViewModels

- Auth: `AuthScreens.kt`, `AuthViewModels.kt`.
- Onboarding: `CompleteProfileScreen.kt`.
- Home/discover: `HomeScreen.kt`, `DiscoverScreens.kt`.
- Tournaments: `TournamentScreens.kt`, `TournamentSubScreens.kt`,
  `RegistrationScreens.kt`.
- Matches: `MatchScreens.kt`.
- Leaderboard: `LeaderboardScreen.kt`.
- Teams: `TeamScreens.kt`.
- Profile/settings: `ProfileScreens.kt`, `SettingsScreens.kt`.
- Wallet/referrals/support: `WalletScreens.kt`, `ReferralScreen.kt`.
- Connectivity: `ConnectivityGateViewModel.kt`, `ConnectionRequiredScreen.kt`.
- Visual harness: `app/src/debug/.../VisualTestActivity.kt`.

### Data and networking

- `data/remote/SupabaseApi.kt`, `SessionManager.kt`.
- `di/NetworkModule.kt`, `di/DatabaseModule.kt`, `di/AppModule.kt`.
- `data/repository/*.kt` (four repositories).
- `data/local/HmxDatabase.kt`, `CachedTournament.kt`.
- `data/connectivity/ConnectivityMonitor.kt`, `BackendHealthChecker.kt`.
- `core/util/RecoveryLinkParser.kt`, `Validation.kt`, `Format.kt`,
  `RegistrationRules.kt`.

### Backend and CI

- `supabase/migrations/0001_core_schema.sql` … `0013_clamp_public_rpc_limits.sql`.
- Live catalog: 33 public tables, 40 public RLS policies, 29 public functions,
  16 triggers, 76 indexes, 1 view, 4 storage buckets.
- `.github/workflows/android-ci.yml`, `.github/workflows/android-visual.yml`,
  `.github/scripts/capture-liquid-glass.sh`.

## 4. Architecture findings

1. **Single-module app.** A single `:app` module is appropriate for the current
   size. There is no cyclic dependency between UI and data layers.
2. **Hilt wiring is consistent.** Every ViewModel is `@HiltViewModel` and every
   repository is `@Singleton`; no service locator or global mutable state was
   found.
3. **One network client.** All business calls share the authenticated OkHttp
   client in `NetworkModule`; the health probe deliberately uses a separate bare
   client so it never carries a user token.
4. **Room is a cache, not a source of truth.** `CachedTournament` is used only
   for the offline tournament catalogue fallback. No user, payment or credential
   data is stored in Room.
5. **Session storage is split correctly after the fix.** Refresh token, user id,
   email and onboarding flag live in app-private DataStore; the short-lived
   access token is memory-only.
6. **Navigation is state-driven.** `HmxRoot` renders exactly one of connectivity,
   recovery, auth, onboarding or the main graph. The onboarding redirect is
   keyed on `needsOnboarding` so a changed start destination is not ignored by an
   existing `NavHostController`.

## 5. Defect summary (full detail in `BUG_AND_ERROR_REPORT.md`)

Fixed in this branch:

- Recovery deep links accepted any `hmxarena://` URI and could replace the
  session.
- `restoreFromRecovery` did not verify the token with `/auth/v1/user` and left
  user id/email null.
- Access token was persisted to DataStore.
- Concurrent refresh could lose a wake-up and return a null token.
- A transient 5xx/429 refresh response signed the player out.
- No startup network gate; screens fired requests with no connectivity.
- No adaptive layout for tablets/foldables/landscape.
- Full glass effects ran on low-RAM devices.
- Team logo storage policies could never match the upload path; no delete
  policy.
- Leaderboard/participant RPC limits were not upper-bounded.
- Home/my-matches/team-detail reads were sequential where they could be parallel.
- Onboarding cache/server disagreement could keep the old graph.

Open or environment-limited items:

- No staging Supabase project; production fixes are policy/RPC only.
- No Macrobenchmark module and no connected instrumentation suite.
- Visual verification runs on SwiftShader, so frame timings are not
  representative of a real GPU (OPPO A38 class) device.
- Leaked-password protection is disabled in Supabase Auth (dashboard setting,
  cannot be changed through SQL safely from here).

## 6. Verification status

| Area | Status |
| --- | --- |
| Source inventory | Verified from `git ls-files` and live catalog |
| Kotlin compile | GitHub Actions `Android CI` `42363668406`, commit `16bf24e` |
| JVM unit tests | GitHub Actions `Android CI` (8 test classes, 50 tests) |
| Android lint | GitHub Actions `Android CI` (0 errors) |
| Visual matrix | GitHub Actions `Liquid Glass visual verification` `42363668400` (20/20) |
| Supabase policies | Verified live through MCP `hmxesport` |
| Device matrix (phone/fold/tablet) | Layout logic tested on JVM; screenshots only from a Pixel 6 emulator |

Where this report says "verified", the run id is recorded in
`TEST_EXECUTION_REPORT.md`. Nothing in this report is inferred from a screenshot
that was not actually produced.
