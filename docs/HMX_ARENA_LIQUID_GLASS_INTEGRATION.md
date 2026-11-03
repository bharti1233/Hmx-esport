# HMX Arena — Liquid Glass Integration

This document describes how Kyant0's Backdrop library is integrated, why a
specific version was chosen, how the effect is kept safe on low-RAM devices,
and how the required `HmxGlass*` component set is structured.

---

## 1. Dependency decision

### 1.1 Current published versions

`io.github.kyant0:backdrop` latest is `2.0.1`. Its Gradle metadata requires:

- Kotlin `2.4.10`
- Compose Multiplatform `1.12.0`
- Gradle metadata produced by Gradle `9.7.1`

The project was on Kotlin `2.1.21`, Compose BOM `2025.05.01` (Compose `1.8.2`),
AGP `8.13.2`, Gradle `8.14.3`. Upgrading to `2.0.1` would also force a Kotlin
2.4 / Compose 1.12 migration across Hilt, KSP and Room, which is a much larger
change than the visual task justifies.

### 1.2 Selected version

`io.github.kyant0:backdrop:1.0.0`:

- Kotlin stdlib `2.2.21`
- `androidx.compose.foundation` / `ui` / `ui-graphics` `1.9.4`
- Apache 2.0

The project was upgraded to the matching baseline:

| Component | Before | After |
|---|---|---|
| Kotlin | 2.1.21 | **2.2.21** |
| KSP | 2.1.21-2.0.2 | **2.2.21-2.0.5** |
| Compose BOM | 2025.05.01 (1.8.2) | **2025.10.01 (1.9.4)** |
| AGP | 8.13.2 | unchanged |
| Gradle | 8.14.3 | unchanged |
| Hilt | 2.56.2 | unchanged |

Compose BOM `2025.10.01` resolves `ui`, `foundation` and `runtime` to `1.9.4`
and `material3` to `1.4.0`; the Compose `1.9.4` artifacts match Backdrop's
runtime dependencies exactly.
`libs.versions.toml` declares `backdrop = "1.0.0"` and
`app/build.gradle.kts` adds `implementation(libs.backdrop)`.

If a future upgrade to Backdrop 2.x is attempted, do it as a dedicated
toolchain migration: Kotlin 2.4, Compose 1.12, AGP/Gradle validation, and a
device pass. Do not mix it into a UI change.

---

## 2. Architecture

```
MainActivity
└── HMXArenaTheme
    └── HmxRoot
        └── HmxGlassHost                      ← records one decorative layer
            ├── Box(layerBackdrop(backdrop))  ← background only
            │   └── GlassScreenBackground()
            └── CompositionLocalProvider(
                  LocalHmxBackdrop,
                  LocalHmxGlassEffects
                )
                └── AuthFlow / MainNavHost / screens
                    ├── HmxGlassTopBar      ─┐
                    ├── HmxGlassBottomBar    ├─ refractive = true
                    ├── HmxGlassCard(hero)   │
                    ├── HmxGlassBottomSheet  │
                    └── HmxGlassDialog      ─┘
                    └── list cards / rows  ─── cheap translucent fill
```

Key point: the recorded layer contains **only** the decorative background, not
the app UI. Recording the UI itself would create a feedback loop (glass sampling
itself) and a full-screen offscreen pass on every frame.

`HmxGlassHost` is defined in
`app/src/main/java/com/hmx/arena/core/designsystem/HmxBackdrop.kt`.
`GlassSurface` in `Glass.kt` consumes the locals and chooses one of two paths:

- **Refractive**: `Modifier.drawBackdrop(backdrop, shape, effects, onDrawSurface)`
  with `blur(18.dp)` and `lens(refractionHeight = 14.dp, amount = 9.dp)`.
- **Fallback**: the original vertical white gradient + hairline border.

Both paths use identical padding, shape, border and elevation, so there is no
layout shift when effects turn off.

---

## 3. Effect policy (performance and low-RAM safety)

The brief requires Liquid Glass to be selective. The rules are enforced in
code, not just by convention:

1. **One background layer.** `HmxGlassHost` records the background once.
2. **Chrome only.** Refraction is opt-in via `GlassSurface(refractive = true)`.
   It is used by the top bar, bottom bar, hero profile card, bottom sheet and
   dialog. Cards, chips, fields, leaderboard rows, notification rows and
   tournament lists never request it.
3. **No per-item blur.** `HmxGlassTournamentCard`, `HmxGlassTeamCard`,
   `HmxGlassPlayerCard`, `HmxGlassLeaderboardItem`, `HmxGlassMatchCard` and
   `HmxGlassNotificationCard` all use the cheap fill.
4. **API gating.** Backdrop's `blur()` returns immediately below API 31 and
   `lens()` below API 33. The app still calls them so the same code path works
   on modern devices; older devices simply get the fill.
5. **Low-RAM gating.** `liquidGlassSupported(context)` returns false when
   `ActivityManager.isLowRamDevice` is true. `LocalHmxGlassEffects` then forces
   the fallback everywhere.
6. **No functional dependency.** Buttons, navigation, forms, dialogs and error
   states all work with effects disabled. The glass is decoration only.
7. **Bounded surfaces.** A screen has at most three refractive surfaces (top
   bar, bottom bar, one hero/sheet). Each creates a small graphics layer, not a
   full-screen one.

---

## 4. Component library

`app/src/main/java/com/hmx/arena/ui/common/HmxGlassComponents.kt` exposes the
required public API. All names are stable and monochrome by construction.

| Component | Behaviour |
|---|---|
| `HmxGlassCard` | Base card; optional click, padding, `refractive` flag. |
| `HmxGlassButton` | Ink CTA by default; loading + disabled states. |
| `HmxGlassOutlinedButton` | Hairline secondary action. |
| `HmxGlassIconButton` | 44 dp circular icon control with content description. |
| `HmxGlassBottomBar` | Floating five-tab pill, ink active capsule. |
| `HmxGlassTopBar` | Title + optional back/actions in a low-alpha glass pill. |
| `HmxGlassBottomSheet` | Modal sheet with drag handle, glass surface, nav-bar inset. |
| `HmxGlassDialog` | Rounded glass confirmation dialog. |
| `HmxGlassTextField` | Labelled field with inline error and IME action. |
| `HmxGlassSearchField` | Search field with leading glyph and clear action. |
| `HmxGlassChip` | Selectable filter chip; ink when selected. |
| `HmxGlassTournamentCard` | Tournament summary, prize pool, slots, countdown. |
| `HmxGlassTeamCard` | Team tag tile, name, members, region. |
| `HmxGlassPlayerCard` | Avatar, display name, handle, points. |
| `HmxGlassLeaderboardItem` | Rank capsule, avatar, points; top three inverted. |
| `HmxGlassMatchCard` | Tournament, match name, schedule, countdown. |
| `HmxGlassStatCard` | Value + label + optional icon/caption. |
| `HmxGlassNotificationCard` | Unread dot, title, body, relative time. |
| `HmxGlassLoading` | Thin ink spinner + label. |
| `HmxGlassEmptyState` | Icon, title, message, optional action. |
| `HmxGlassErrorState` | Error message + retry. |

The components are thin, composable wrappers over the design-system primitives
so existing screens can migrate incrementally without changing data flow.

### 4.1 Migration applied in this pass

- `HmxPage` uses `HmxGlassTopBar`.
- All five tab screens use `HmxGlassBottomBar`.
- `LeaderboardScreen` uses `HmxGlassLeaderboardItem`.
- `NotificationsScreen` uses `HmxGlassNotificationCard`.
- `CompleteProfileScreen` uses `HmxGlassTextField` and `HmxGlassButton`.
- `HomeScreen` uses `HmxGlassButton`, `HmxGlassCard` quick tiles and a
  refractive hero card.
- Shared `TournamentCard` / `MatchCard` remain the implementation behind the
  named wrappers, so every existing call site inherits the monochrome card
  styling.

---

## 5. Backdrop API usage

The exact calls used:

```kotlin
val backdrop = rememberLayerBackdrop()

Box(Modifier.layerBackdrop(backdrop)) {
    GlassScreenBackground()
}

Box(
    Modifier.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            blur(radius = 18.dp.toPx())
            lens(
                refractionHeight = 14.dp.toPx(),
                refractionAmount = 9.dp.toPx(),
                depthEffect = true,
            )
        },
        onDrawSurface = {
            drawRect(fill.copy(alpha = minOf(fill.alpha, 0.62f)))
        },
    ),
)
```

`drawBackdrop` clips its graphics layer to `shape`, so the surface fill and
content stay inside the card/bar outline. The `onDrawSurface` fill is capped at
62 % alpha so the refracted background remains visible without making text
unreadable.

---

## 6. RLS / onboarding interaction

The visual pass surfaced and fixed three database defects that blocked the
reference onboarding journey. They are documented in detail in
`docs/HMX_ARENA_VISUAL_AUDIT.md`; the relevant contract is:

- Android never holds a service-role key. The client key is the publishable
  key in `gradle.properties`.
- `complete_onboarding` is SECURITY DEFINER, granted only to `authenticated`,
  and scopes every write with `auth.uid()`.
- The RPC writes profile, game account and notification atomically.
- PostgREST writes use `return=minimal`, because `return=representation` reads
  columns revoked from `authenticated` by migration `0007`.
- Anonymous catalogue reads are allowed by granting EXECUTE on the policy
  helpers (`is_admin`, `is_match_participant`) to `anon`; the helpers return
  false without a JWT, so no rows are exposed.

---

## 7. Build and verification

- Local APK builds are intentionally not run. The single source of truth is
  `.github/workflows/android-ci.yml`, which runs `testDebugUnitTest`,
  `lintDebug` and `assembleDebug`, then uploads the debug APK.
- The dependency bump (Kotlin 2.2.21 / KSP 2.2.21-2.0.5 / Compose BOM
  2025.10.01 / Backdrop 1.0.0) must pass CI before merge.
- Unit coverage added: `SupabaseErrorTest` verifies the onboarding error
  mapping and PostgREST error parsing.
- Database verification was executed against the live project in rolled-back
  transactions; no production rows were modified.

### 7.1 Rollback

If CI reveals a toolchain incompatibility, revert in this order:

1. Remove `implementation(libs.backdrop)` and the `HmxGlassHost` wrapper.
2. Revert Compose BOM to `2025.05.01`, Kotlin to `2.1.21`, KSP to
   `2.1.21-2.0.2`.
3. Keep the onboarding RPC, `return=minimal` change and database migrations;
   they are independent of the visual layer.

The fallback glass path does not depend on Backdrop, so the app remains
visually coherent even if the dependency is removed.
