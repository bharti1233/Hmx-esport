# HMX Arena — Global UI & Performance Audit

Date: 2026-11-05
Branch: `feature/hmx-arena-android-production`
Scope: every Compose screen, the shared design system, the Backdrop integration,
theme persistence, every network load that gates a screen, and every submit path.

No APK was built on the developer machine. Compilation, unit tests, lint and the
emulator screenshot matrix run in GitHub Actions (`.github/workflows/android-ci.yml`
and `.github/workflows/android-visual.yml`).

---

## 1. Screen inventory

Navigation is declared in `ui/navigation/MainNavHost.kt`; authentication is a
separate flow in `ui/auth/AuthScreens.kt`.

| Area | Destinations |
|---|---|
| Auth | Welcome, Login, Sign up, Email verification, Forgot password, Recovery reset |
| Onboarding | Complete profile / player setup |
| Tabs | Home, Tournaments (Arena), Matches, Leaderboard, Profile |
| Discovery | Search, Player profile, Notifications, Announcements, Announcement detail |
| Tournament | Detail, Rules, Participants, Schedule, Register, Tournament leaderboard, My registrations |
| Match | Match detail, Room credentials, Results, Submit result, Dispute |
| Team | Team, Create team, Manage team, Invitations, Invite player |
| Account | Edit profile, Game accounts, Wallet, Transactions, Referral, Settings, Account settings, Support, Ticket, Help, About, Legal |

Every destination uses the shared `HmxPage` / `GlassScaffold` chrome. No screen has
its own theme implementation.

## 2. Shared UI components

| Layer | File | Responsibility |
|---|---|---|
| Tokens | `core/designsystem/Theme.kt` | Light/dark palettes, glass tokens, effects budget, shapes, typography, `HmxColor` facade |
| Glass primitive | `core/designsystem/LiquidGlass.kt` | `Modifier.hmxLiquidGlass`, Card/Control/Chrome levels, fallback |
| Backdrop host | `core/designsystem/HmxBackdrop.kt` | Root decorative layer, effects capability, backdrop painter |
| Chrome | `core/designsystem/Glass.kt` | Scaffold, top bar, buttons, icon buttons, chips, states |
| Component library | `ui/common/HmxGlassComponents.kt` | Cards, dialog, sheet, text/search fields, segmented control, theme selector, team/player/stat/notification cards |
| Common UI | `ui/common/CommonUi.kt` | Text fields, cards, empty/error/loading, avatar, status pill |
| Bottom bar | `ui/shell/GlassBottomBar.kt` | Refractive tab bar |

## 3. Dark-mode light leakage — findings

| # | Issue | Location | Root cause | Fix |
|---|---|---|---|---|
| D1 | White wash at the top of every dark card | `LiquidGlass.kt` `onDrawSurface`, `hmxGlassFallback` | The vertical gradient used `tokens.sheenColor` (pure white) as its first colour, not the surface fill | Gradient now starts from `fill`; sheen is a separate 5 % top lip |
| D2 | Grey patch behind dark cards | `Theme.kt` dark `backdropBloom` | 100 % white radial drawn at 10 % alpha over near-black | Bloom `#C1C5CE` at 3.5 %; cloud 3.9 %, mid 2.7 %, arcs 1.6–2.4 % |
| D3 | Bright card edges / “plastic” outline | `Theme.kt` dark `glass.edge` | 36 % white border and 80 % chrome highlight | Edge 20 %, highlight 25 %, chrome highlight 45 % |
| D4 | White base in fallback glass | `LiquidGlass.kt` fallback gradient + border | Fallback used the sheen colour and a hard-coded 50 % border, ignoring the token alpha | Fallback uses `fill` and `tokens.edge.alpha * 0.8` |
| D5 | Watermark and decorative arcs visible as grey shapes | `Theme.kt` dark `watermark`, `backdropArc*` | 8–10 % white at 84 sp and 34 dp stroke | Watermark 4 %, arcs 1.6–2.4 % |
| D6 | White window flash before Compose | `res/values/themes.xml` | Only a light `Theme.HMXArena` / splash existed; no `values-night` variant | Added `values-night/themes.xml` and a dark splash style; `MainActivity` picks it up from the persisted mode |
| D7 | Black splash icon on black background | `res/drawable/ic_hmx_mark.xml` | Icon was near-black in every configuration | Added `drawable-night/ic_hmx_mark.xml` in light ink |
| D8 | Light first frame when app Dark is chosen on a Light system | Splash theme | The platform splash cannot read DataStore | Synchronous write-through cache (`HmxThemeBootstrap`) selects the dark splash before `installSplashScreen()` |
| D9 | Light-mode Material defaults could leak through dialogs/text fields | `Theme.kt` `materialColors()` | Only some M3 slots were mapped | Dark `darkColorScheme` maps background/surface/outline/error; all custom components use tokens |
| D10 | Selected tab / chips / primary buttons invert to white | `GlassBottomBar.kt`, `CommonUi.StatusPill`, `GlassButtonVariant.Ink` | The inverse-ink token is used for contrast | Intentional: it is a solid, opaque selection/CTA, not a translucent white patch; documented in the dark-mode audit |

No hard-coded `Color.White`, `Color.Black` or `Color(0xFF…)` remains outside
`Theme.kt` (verified with a repository-wide search), and no Material `AlertDialog`
remains in the main UI.

## 4. Liquid Glass consistency — findings

| # | Issue | Location | Root cause | Fix |
|---|---|---|---|---|
| G1 | Some cards were transparent rectangles with a border | Old `GlassSurface` implementation | Only fill + border, no recorded backdrop | All surfaces route through `HmxLiquidGlassSurface` → `Modifier.hmxLiquidGlass` |
| G2 | Glass sampled the UI, causing a feedback loop under sheets | `GlassScaffold` | Content layer was recorded for every screen, including non-tab screens | Content layer is recorded only when a bottom bar exists; root layer records decorations only |
| G3 | Effects existed but were not tuned per surface | `Theme.kt` effects | One blur/lens budget for cards, controls and chrome | Card = vibrancy only; Control = 5 dp blur + small lens; Chrome = 16 dp blur + lens |
| G4 | Home hero “refraction” was random | Tuning | Blur radius larger than the visual detail behind it | Static monochrome backdrop supplies controlled detail; chrome budget reduced |
| G5 | Frames dropped while scrolling | Cards | A RenderEffect blur per list row | Cards no longer blur; they keep vibrancy, edge, highlight and shadow |
| G6 | Low-RAM / API < 31 devices | `liquidGlassSupported` | No graceful plan | Effects disabled; identical layout with translucent gradient, border and shadow |
| G7 | Inconsistent pressed feedback | Buttons, icon buttons, cards | Some used default ripple only, some nothing | All buttons scale; icon buttons add a pressed overlay; clickable cards add a pressed overlay; every clickable still has an immediate indication |
| G8 | Bright line / grey band under the shared header | `Glass.kt` `GlassTopBar`, `LiquidGlass.kt` | Chrome glass drew a closed specular contour plus a 22 dp drop shadow and inner shadow | New `HmxGlassEdge.Top` disables the closed contour/shadow and draws a top-only outline; see `TOP_HEADER_FIX_REPORT.md` |
| G9 | Settings “appearance” preview squeezed to one character per line | `Glass.kt` `GlassChip` | Inner `Box(fillMaxSize)` made a chip in a `Row` consume all remaining width | Chip content sizes to its label (`maxLines = 1`, no wrap) |

Backdrop compatibility decision: stay on `io.github.kyant0:backdrop:1.0.0`.
Its metadata targets Kotlin `2.2.21` / Compose `1.9.4`, exactly the versions this
project already uses. `1.0.6` requires Kotlin 2.3.10 / Compose 1.10.3 and 2.x
requires Kotlin 2.4 / Compose 1.12; upgrading would force an unrelated toolchain
migration. The library is used as a drawing primitive only; every public
component is our own wrapper, so a later upgrade is isolated to
`LiquidGlass.kt`.

## 5. Theme selector — findings

| # | Issue | Location | Root cause | Fix |
|---|---|---|---|---|
| T1 | Choosing Dark in Settings did not change the app | `SettingsScreens.kt` | `hiltViewModel<ThemeViewModel>()` inside a NavHost destination created a **navigation-entry-scoped** instance; the root activity held a different instance | Settings now reads `LocalHmxThemeState` supplied by the single root `HmxTheme` and calls its `setMode` |
| T2 | Stale colours after switching | All screens | Theme colours were read once or from a local copy | All colours come from `LocalHmxColorScheme`; `HmxTheme` recomposes the whole tree in place |
| T3 | Persistence | `ThemePreferences.kt` | Only in-memory mode | DataStore-backed `ThemeViewModel`, splash held until the stored value loads |
| T4 | “System” did not follow live system changes | `MainActivity.kt` | Mode resolved once | `HmxTheme` resolves `isSystemInDarkTheme()` on every composition |
| T5 | Status/nav bar icons wrong after switch | `MainActivity.kt` | Not updated | `SideEffect` in `HmxTheme` sets `isAppearanceLightStatusBars` / `isAppearanceLightNavigationBars` |

## 6. Interaction latency — findings

| # | Issue | Location | Root cause | Fix |
|---|---|---|---|---|
| P1 | Home took around a second | `HomeViewModel.load` | Five independent Supabase calls ran sequentially (`myProfile`, `featured`, `myMatches`, `announcements`, `notifications`) | `coroutineScope { async { … } }`, all five concurrent |
| P2 | Tournament details loaded slowly | `TournamentDetailViewModel.load` | Tournament → rules → participants → matches → registrations, sequentially | Independent reads fetched concurrently after the tournament itself |
| P3 | Registration screen loaded slowly | `RegisterViewModel.load` | Registrations → team → game accounts, sequentially | Concurrent |
| P4 | Wallet loaded slowly | `WalletViewModel.load` | `Triple(wallet(), ledger(), flags())` evaluates its arguments sequentially | Concurrent |
| P5 | Referral / Team / Manage team | `ReferralViewModel`, `TeamViewModel`, `ManageTeamViewModel` | Profile/claims, team/invitations, detail/invitations fetched sequentially | Concurrent |
| P6 | App showed a full-screen spinner before the dashboard | `SignedInGate` | Waited for `my_profile` before composing the nav host | Last onboarding decision cached per session; dashboard renders immediately and the refresh corrects a stale cache |
| P7 | Theme switching felt slow / failed | Settings | Separate view model instance + DataStore round-trip before any UI change | In-memory update first (same frame), DataStore write in the background |
| P8 | Duplicate submissions possible | Auth, onboarding, create team, invite, submit result, dispute, edit profile | `submit()` launched a new coroutine on every tap before the busy flag recomposed | Guard at the top of each submit (`loading` / `busy` / `saving`) |
| P9 | Collectors kept running behind other screens | 45 `collectAsState()` call sites | Not lifecycle-aware | All replaced with `collectAsStateWithLifecycle()` |
| P10 | No artificial delay in navigation | Audit | The only `delay(1000)` occurrences are the countdown ticker and the resend cooldown, which are real timers, not navigation delays | Kept |

No `runBlocking`, `Thread.sleep`, `SystemClock.sleep`, `GlobalScope`, or
main-thread network call exists in `app/src/main`.

## 7. Measurement plan

The visual workflow records, on a real API 34 emulator:

- cold start of the production `MainActivity` ×3 (`am start -W` `TotalTime`);
- warm start ×3;
- `dumpsys gfxinfo` while scrolling the densest glass screen (Home, dark);
- the full 20-screenshot light/dark matrix and the theme-switch sequence.

Results and any missed targets are recorded in
`docs/PERFORMANCE_BENCHMARK_REPORT.md`.
