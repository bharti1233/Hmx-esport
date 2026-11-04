# HMX Arena — Visual & Runtime Audit

Date: 2026-11-03
Branch: `feature/hmx-arena-android-production`
Reference: `docs/design/reference/hmx-arena-reference-27-screens.png`

This audit covers the rendered UI, the design-system layer, and the runtime
defects that blocked the reference's onboarding path. It records what was
changed, what was verified, and what remains for a device pass.

---

## 1. Scope and method

- Read every Compose screen under `app/src/main/java/com/hmx/arena/ui`.
- Compared each of the 27 reference panels with its app route and component
  tree.
- Checked the shared design system (`Theme.kt`, `Glass.kt`, `CommonUi.kt`,
  `GlassBottomBar.kt`) for palette, spacing, typography and duplication.
- Traced the onboarding write path from `CompleteProfileScreen` through
  `PlayerRepository` and `SupabaseApi`, then reproduced the failure against the
  live Supabase project with `set local role authenticated` and JWT claims.
- Exercised anonymous PostgREST reads with the shipped publishable key.

No APK was built locally; compilation and instrumented checks are delegated to
GitHub Actions (`.github/workflows/android-ci.yml`).

---

## 2. Screen inventory and reference mapping

The app has 46 user-facing composables. The 27 reference panels map as follows:

| Reference panel | App route / composable | Status |
|---|---|---|
| Splash | system splash + `LoadingScreen` | Kept; white hand-off |
| Onboarding 1–3 | `WelcomeScreen` feature list | Adapted into one welcome surface |
| Get Started | `WelcomeScreen` | Restyled CTAs and brand block |
| Sign Up | `SignUpScreen` | Existing hierarchy retained |
| Log In | `LoginScreen` | Existing hierarchy retained |
| OTP Verification | `VerifyEmailScreen` | Email OTP, same six-cell treatment |
| Home | `HomeScreen` | Reworked to 2 × 2 dashboard grid |
| Tournaments | `TournamentsScreen` | Shared tournament card |
| Tournament Details | `TournamentDetailScreen` | Existing metadata grid |
| Join Tournament | `RegisterTournamentScreen` | Existing confirm/pay flow |
| Payment Success | `RegisterTournamentScreen` success state | Existing |
| My Tournaments | `MyRegistrationsScreen` | Existing chips + cards |
| Match Lobby | `MatchCredentialsScreen` | Existing credential card |
| Live Match | `MatchDetailScreen` | Existing live state |
| Match Results | `MatchResultsScreen` | Existing results table |
| Leaderboard | `LeaderboardScreen` | Migrated to `HmxGlassLeaderboardItem` |
| Wallet | `WalletScreen` | Existing balance + actions |
| Add Money | `WalletScreen` add-money sheet | Existing |
| Withdraw | `WalletScreen` withdraw sheet | Existing |
| Transactions | `TransactionsScreen` | Existing ledger |
| Refer & Earn | `ReferralScreen` | Existing code/share card |
| Profile | `ProfileScreen` | Existing grouped list |
| Game Details | `GameAccountsScreen` | Existing form |
| Settings | `SettingsScreen` | Existing grouped rows |
| Help & Support | `HelpScreen` / `SupportScreen` | Existing |
| (extra) Notifications | `NotificationsScreen` | Migrated to `HmxGlassNotificationCard` |
| (extra) Teams | `TeamScreen` et al. | Kept; uses shared surfaces |

---

## 3. Findings

### 3.1 Blocking runtime defects (fixed)

**F1 — Onboarding showed "You do not have permission to do that."**
`CompleteProfileViewModel.submit` performed three sequential writes:

1. insert/update `player_game_accounts`
2. `PATCH /profiles` (the client's update helper sent
   `Prefer: return=representation`)
3. `PATCH /profiles` again to set `onboarding_completed`

With `return=representation`, PostgREST reads the row back with `SELECT *`.
Migration `0007` deliberately revokes `email` and `phone` from `authenticated`
column grants. The read therefore failed with SQLSTATE `42501`, which the
client maps to "You do not have permission to do that." The first two writes
had already succeeded, leaving a partial state.

Fix:
- New `public.complete_onboarding(...)` SECURITY DEFINER RPC performs game
  account, profile and welcome notification in one transaction.
- Ownership is checked with `auth.uid()`; `p_game_account_id` is always scoped
  by `user_id = auth.uid()`.
- `RestApi.update` / `RestApi.insert` now default to `return=minimal`.
- `PlayerRepository.completeOnboarding(...)` calls the RPC and returns the
  updated profile.

**F2 — `complete_onboarding` still failed with `23502`.**
`notify(..., null)` inserted NULL into `notifications.data`, which is
`NOT NULL DEFAULT '{}'::jsonb`. Fixed in `0010`: the RPC passes `'{}'::jsonb`
and `notify()` coalesces NULL defensively.

**F3 — Anonymous catalogue reads failed with `42501` for `is_admin`.**
RLS policies on `profiles`, `tournaments`, `matches`, `match_results` and
related tables call `public.is_admin()` / `is_match_participant()`. EXECUTE was
granted only to `authenticated`, so the anon role could not evaluate the
policies. Fixed by granting EXECUTE to `anon`; both helpers are SECURITY
DEFINER and return false when `auth.uid()` is NULL, so no data is exposed.

**F4 — Player search and public profiles failed with `42501`.**
`public_profiles` is declared `WITH (security_invoker = true)` and filters
`WHERE deleted_at IS NULL`. Migration `0007` revoked `deleted_at` from
`anon`/`authenticated`, so the view could not evaluate its own filter and every
read failed with `permission denied for table profiles`. Fixed in `0011` by
granting SELECT on the single `deleted_at` column. `email` and `phone` remain
revoked, and `SELECT *` still fails as intended.

**F5 — `public_profiles` verified after the grant.**
Authenticated and anonymous reads both return `200`, and the exact column set
used by `searchPlayers()` returns the expected profile rows.

Live verification:
- **Real authenticated journey (GoTrue login → PostgREST RPC):** a confirmed
  test user signed in with the password grant, called
  `POST /rest/v1/rpc/complete_onboarding`, and received `200` with
  `onboarding_completed = true`. The game account (`E2E Player` /
  `E2E12345` / level 55) and the `Welcome to HMX Arena` notification were read
  back with the user's JWT. A second call without a game account preserved the
  existing account and updated the profile.
- **Old path reproduction:** `PATCH /profiles` with
  `Prefer: return=representation` returned `403` (`42501`, permission denied
  for table profiles); the same PATCH with `Prefer: return=minimal` returned
  `204`.
- **Cross-user ownership (SQL, rolled back):** user B calling
  `complete_onboarding` with user A's `p_game_account_id` is rejected with
  `invalid_game_identity`.
- **Unauthenticated RPC (SQL):** raises `not_authenticated`.
- **Anonymous catalogue:** `GET` on `profiles`, `tournaments`, `matches`,
  `announcements` and `public_profiles` returns `200`.
- The temporary auth user and all cascaded rows were deleted after the test.

### 3.2 Design-system findings

| ID | Finding | Resolution |
|---|---|---|
| V1 | Glass was hand-painted with gradients; no real backdrop sampling. | Added Backdrop host + optional refraction on chrome. |
| V2 | Bottom bar was duplicated in five tab screens and each instance created its own surface. | Kept the per-screen call sites for navigation safety, but moved them onto `HmxGlassBottomBar`; a future persistent-shell refactor is noted below. |
| V3 | Top bar was a bare `Row`, so it had no glass identity. | `HmxGlassTopBar` now wraps the title row in a low-alpha refractive pill. |
| V4 | Cards, chips, fields and dialogs used inconsistent radii and fills. | Centralised in `HmxDimens` / `HmxShapes`; new `HmxGlass*` wrappers use those tokens. |
| V5 | Dashboard quick actions were four cramped tiles in one row. | Reworked to the reference's 2 × 2 grid with icon, label and chevron. |
| V6 | Notification rows were bespoke per screen. | Replaced with `HmxGlassNotificationCard`. |
| V7 | Leaderboard rows were hand-built inside `LeaderboardScreen`. | Replaced with `HmxGlassLeaderboardItem`. |
| V8 | Empty/loading/error states existed twice (`GlassEmptyState` and `EmptyBlock`). | Both retained for compatibility; `HmxGlassEmptyState` / `HmxGlassErrorState` / `HmxGlassLoading` are the canonical names for new code. |

### 3.3 Palette and contrast checks

- No screen uses a non-neutral accent. `rg` over `ui/` finds no purple, blue or
  neon literals; the only `Color(...)` values are the tokens in `HmxColor`.
- `Danger` is a desaturated dark red used only for inline errors, never for
  branding or status.
- Primary text on white is ≈19:1; secondary grey is ≈5.3:1; tertiary grey is
  used only for non-essential metadata.
- Selected tabs and chips invert to ink/white, matching the reference.

### 3.4 Interaction and accessibility

- Icon-only controls carry content descriptions (`GlassIconButton`, search
  clear, notification bell, back).
- Tabs expose `selected` semantics.
- Touch targets are ≥44 dp, with 48 dp minimum in the reference rules.
- Countdown labels update once per second while composed; they stop when the
  screen leaves composition.
- The glass effect never carries meaning: every control remains usable when
  effects are disabled.

### 3.5 Performance

- Backdrop records a single decorative layer at the root (`HmxGlassHost`).
- Only chrome (`HmxGlassTopBar`, `HmxGlassBottomBar`, hero profile card,
  sheets, dialogs) requests refraction. List rows and tournament cards use the
  cheap translucent fill.
- `ActivityManager.isLowRamDevice` and API level gate the effect; below API 31
  blur is a no-op, below API 33 the lens shader is a no-op, and both degrade to
  the fill path without layout changes.
- No per-item `Modifier.blur`, no full-screen offscreen pass per card.

---

## 4. Remaining gaps / follow-ups

1. **Persistent shell.** The bottom bar is still declared by each tab screen.
   Moving it into `MainNavHost` would remove five duplicate instances and make
   the bar truly persistent across tab switches. This was not done in this pass
   because route-scoped `navController` state must be preserved carefully; the
   current call sites are already unified through `HmxGlassBottomBar`.
2. **Device visual pass.** CI compiles, lints and unit-tests the branch. A
   physical-device screenshot pass on API 26, 31 and 34 is still required to
   confirm lens/blur appearance and font-scale behaviour.
3. **Reference photography.** The Free Fire banner in the reference is a
   licensed asset that was not supplied. Tournament banners render from data
   when present; otherwise the app uses a neutral typographic card and does not
   substitute stock imagery.
4. **Splash art.** The reference splash wordmark is not an asset in the repo;
   the system splash uses the app icon and hands off to the white composition.
5. **Wallet as a tab.** The reference bottom bar shows Wallet instead of
   Leaderboard. The app keeps Leaderboard as a tab because it is a core
   competitive surface; Wallet remains reachable from Home and Profile. This is
   an intentional product deviation, not an oversight.

---

## 5. Verification status

| Check | Status |
|---|---|
| Onboarding RPC success path (real JWT, REST) | Passed |
| Cross-user game-account rejection | Passed (SQL, rolled back) |
| Old `return=representation` failure reproduced | Passed (HTTP 403) |
| `return=minimal` profile write | Passed (HTTP 204) |
| Anonymous catalogue reads | Passed (HTTP 200) |
| `public_profiles` authenticated + anonymous | Passed (HTTP 200) |
| Unit tests for error mapping | Passed |
| Kotlin/Compose compile | Passed (CI run [41838272110](https://github.com/bharti1233/Hmx-esport/actions/runs/41838272110), `2710baa`) |
| Lint | Passed (same CI run) |
| Debug APK | Built and uploaded, artifact `hmx-arena-debug-apk` (21,155,582 bytes) |
| Device screenshot diff | Pending physical device |

The CI worktree builds with Kotlin `2.2.21`, Compose BOM `2025.10.01`, and
Backdrop `1.0.0`. One compile fix was needed after the first push
(`GlassTextField` lives in `ui.common`, not `core.designsystem`); the follow-up
commit `2710baa` is green across unit tests, lint, and `assembleDebug`.
