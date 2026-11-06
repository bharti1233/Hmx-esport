# HMX Arena — Future Feature Readiness

Date: 2026-11-06
Branch: `feature/hmx-arena-android-production`
Related: `docs/GLOBAL_UI_AND_PERFORMANCE_AUDIT.md`,
`docs/LIQUID_GLASS_IMPLEMENTATION.md`

This document records the architecture that exists today and the extension
points a new feature should use. It deliberately does **not** add speculative
screens or backend calls.

---

## 1. Existing architecture assessment

### 1.1 Package layout

```
com.hmx.arena
├─ MainActivity, HmxRoot            app entry, splash, session gate
├─ core
│  ├─ designsystem                 theme, tokens, Liquid Glass, shared chrome
│  ├─ model                        immutable domain models
│  └─ util                         formatting / error helpers
├─ data
│  ├─ remote                       Supabase API, SessionManager, RemoteDataSource
│  ├─ local                        Room cache
│  └─ repository                   Auth / Player / Team / Tournament
├─ di                              Hilt network + database modules
└─ ui
   ├─ auth, onboarding
   ├─ home, leaderboard, match, profile, referral, settings, team,
   │  tournament, wallet          feature screens + view models
   ├─ common                       shared composables and component aliases
   ├─ shell                        floating bottom bar
   └─ navigation                   Routes + MainNavHost graphs
```

### 1.2 What is already healthy

- **Layering.** Screens never call Supabase directly; they use repository
  classes injected with Hilt. View models own state and business rules.
- **Single design system.** Every screen uses `HmxPage` → `GlassScaffold` →
  the shared top bar, and the components in `ui/common/` /
  `core/designsystem/`. There is no screen-local theme or header.
- **Single source of theme state.** `HmxTheme` is mounted once at the root; the
  Settings selector reads `LocalHmxThemeState`. Adding a new screen
  automatically inherits themes, bars and typography.
- **Navigation is declarative.** `Routes` holds every destination as a constant
  and typed helpers (`Routes.tournament(id)`) build argument URLs.
- **Loading / error / empty states exist** (`LoadingBlock`, `InlineError`,
  `EmptyBlock`, `HmxGlassLoading/EmptyState/ErrorState`) and are used by the
  main data screens.
- **Auth is still authoritative.** The session gate, Supabase session refresh
  and backend validation are untouched by the visual work.

### 1.3 Weak points to keep in mind

| Area | Observation | Implication |
|---|---|---|
| Feature grouping | One package per area, several screens + view models per file | New features should get their own package; do not grow an existing screen file indefinitely |
| Navigation | Flat `NavHost` with helper functions per area | Fine at the current size; split into nested graphs when an area grows past ~8 destinations |
| Backend | Repository methods return domain models; no pagination contract yet | Add paging at the repository boundary, not inside composables |
| Real-time | No Supabase realtime subscription layer | A future live-score feature needs a new data source, not polling inside UI |
| Tests | JVM unit tests + screenshot matrix; no connected Compose test suite | Add `androidTest` when the team is ready to run an emulator job for every PR |
| Strings | Most UI strings are inline English | Introduce `values/strings.xml` before localisation |

---

## 2. Reusable components

Build new screens from these instead of new primitives:

| Need | Component |
|---|---|
| Page chrome | `HmxPage` (title, back, optional leading brand mark, actions, bottom bar) |
| Card / panel | `HmxGlassCard`, `GlassSurface`, `HmxLiquidGlassSurface` |
| Primary / secondary action | `HmxGlassButton`, `HmxGlassOutlinedButton`, `GlassButton` variants |
| Icon action | `HmxGlassIconButton` / `GlassIconButton` |
| Input | `HmxGlassTextField`, `HmxGlassSearchField`, `GlassTextField` |
| Choice | `HmxGlassChip`, segmented control, `HmxThemeSelector` |
| Domain card | `HmxGlassTournamentCard`, `HmxGlassMatchCard`, `HmxGlassPlayerCard`, `HmxGlassStatCard`, `HmxGlassNotificationCard` |
| Feedback | `HmxGlassLoading`, `HmxGlassEmptyState`, `HmxGlassErrorState`, `LoadingBlock`, `InlineError`, `EmptyBlock` |
| Overlays | `HmxGlassBottomSheet`, `HmxGlassDialog`, `HmxGlassFormDialog` |
| Navigation | `HmxGlassBottomBar` |

Rules that keep the identity consistent:

1. Never hard-code a colour; use `HmxColor.*`.
2. Never create a private `TopAppBar` inside a feature; extend `HmxPage`.
3. Use `HmxGlassLevel.Card` for repeated list rows (no blur), `Control` for
   interactive controls, `Chrome` for bars/sheets/dialogs.
4. Give lazy lists stable keys.
5. Collect state with `collectAsStateWithLifecycle()`.

---

## 3. Navigation extension points

- Add a constant + optional typed helper to
  `ui/navigation/Routes.kt`.
- Add one `composable(...)` entry to the matching `*Destinations` function in
  `ui/navigation/MainNavHost.kt` (`tab`, `account`, `tournament`, `match`,
  `team`). New areas should get their own function rather than being appended
  to an unrelated one.
- Pass navigation callbacks into the screen instead of passing the
  `NavHostController` when the screen is reusable or previewable.
- Tab destinations must keep a single instance and reset to the tab root:
  `nav.openTab(...)` already implements `popUpTo` + `launchSingleTop`.
- Deep links are not declared yet. If they are added, register them on the
  destination and keep the argument names identical to `Routes` helpers.

Suggested future destinations:

```
community, community/post/{id}
streams, stream/{matchId}
store, order/{orderId}
team/{teamId}/stats
match/{id}/scoreboard
```

---

## 4. Backend integration boundaries

```
Compose screen
    ↓ events
ViewModel (state, validation, duplicate-submit guard)
    ↓ suspend functions
Repository (domain models, error mapping)
    ↓
SupabaseApi (PostgREST/RPC)        SessionManager (token refresh, auth)
```

- **Do not** add network calls to composables or to `MainActivity`.
- **Do not** expose the service-role key or weaken RLS. All writes remain
  user-scoped and validated by the backend.
- New repositories belong in `data/repository` and get a Hilt constructor
  (`@Inject constructor(...)`); no manual singletons.
- Add DTO → domain mapping in the repository. UI models must not depend on
  PostgREST row shapes.
- Put independent reads in `coroutineScope { async }` (the pattern used by Home
  and tournament details) so a new screen does not reintroduce sequential
  round-trips.
- For writes, add a `busy` flag at the top of the submit function and only
  report success after the repository returns. Never optimistically fake a
  tournament registration, result or payment.
- Optional local cache belongs in `data/local` (Room), driven by the
  repository. Do not read the database from composition.
- Realtime/live features should be a `Flow` from a data source, collected with
  `collectAsStateWithLifecycle`, not a timer inside the UI.

---

## 5. Technical limitations

- **CI has no authenticated backend session.** The screenshot harness renders
  production components with representative static data. Startup is measured
  on the real activity, but end-to-end Supabase latency is not reproduced.
- **The emulator uses SwiftShader.** Blur/frame timings are pessimistic and not
  representative of a real GPU. Macrobenchmark is not configured; `am start -W`
  and `gfxinfo` are the current instruments.
- **Backdrop 1.0.0 is pinned** to Kotlin 2.2.21 / Compose 1.9.4. Upgrading the
  library requires a coordinated Kotlin/Compose/AGP upgrade; keep the wrapper
  surface (`LiquidGlass.kt`) small so that migration stays isolated.
- **No connected Compose test job.** UI verification is screenshot-based today.
- **No string resources / localisation layer** yet.
- **No offline mutation queue.** Writes require connectivity; the UI surfaces
  the error but does not retry automatically.

---

## 6. Recommended approach for a new feature

1. **Model** — add the domain model in `core/model` (immutable data class).
2. **Data** — add repository functions with suspend signatures and domain
   results; reuse `ApiException`/`hmxMessage()` for errors.
3. **State** — add a `ViewModel` in the feature package with a
   `MutableStateFlow<UiState<T>>`, parallel independent loads, a `busy` guard
   for writes and no Android imports.
4. **Screen** — create `XxxScreen(nav: NavHostController)` using `HmxPage`,
   the shared cards/fields and `collectAsStateWithLifecycle()`. Include
   loading, empty and error paths from the start.
5. **Navigation** — add the route and destination; keep the back stack
   explicit for success flows (`popUpTo` + `launchSingleTop`).
6. **Tests** — add JVM tests for parsing/validation/business rules; add the
   screen to the debug `VisualTestActivity` showcase so the screenshot workflow
   checks both themes automatically.
7. **Docs** — update this file if a new boundary or component is introduced.

The existing screen set already follows this structure, so a new feature can be
added without touching the header, theme, glass primitive or navigation
architecture.
