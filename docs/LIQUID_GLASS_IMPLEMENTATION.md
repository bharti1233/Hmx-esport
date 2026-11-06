# HMX Arena — Liquid Glass Implementation

Date: 2026-11-05
Branch: `feature/hmx-arena-android-production`
Library: [`Kyant0/AndroidLiquidGlass`](https://github.com/Kyant0/AndroidLiquidGlass) / Backdrop `io.github.kyant0:backdrop:1.0.0`

---

## 1. Why Backdrop 1.0.0

The project is pinned to Kotlin `2.2.21`, AGP `8.13.2` and Compose BOM
`2025.10.01`. Backdrop `1.0.0` publishes metadata for exactly Kotlin `2.2.21`
and Compose `1.9.4`, so it resolves without touching the toolchain.

| Backdrop | Required Kotlin | Required Compose | Decision |
|---|---|---|---|
| `1.0.0` | 2.2.21 | 1.9.4 | **Used** |
| `1.0.6` | 2.3.10 | 1.10.3 | Rejected — forces Kotlin/Compose upgrade |
| `2.x` | 2.4 | 1.12 | Rejected — forces a much larger migration |

The library is treated as a low-level drawing primitive. Every public HMX
component is our own wrapper, so a future Backdrop upgrade is isolated to
`core/designsystem/LiquidGlass.kt` and `HmxBackdrop.kt`.

## 2. Layer architecture

```
HmxRoot
└─ HmxGlassHost                     records the decorative background only
   └─ auth / SignedInGate / MainNavHost
      └─ HmxPage → GlassScaffold
         ├─ top bar (Chrome glass, samples root layer)
         ├─ content
         │  └─ cards / buttons / fields (Card or Control, samples root layer)
         └─ bottom bar (Chrome glass, samples the recorded content layer)
```

- **Root layer** — recorded once by `HmxGlassHost`. It contains the monochrome
  background, bloom, diffuse masses and two low-contrast arcs. It never contains
  UI, so glass can sample it without a feedback loop.
- **Content layer** — recorded by `GlassScaffold` **only when a bottom bar
  exists**, so the floating bar refracts the real list scrolling underneath it.
  Adding it to every screen doubled graphics work with no visible benefit, so it
  is conditional.
- Both layers are single-instance `rememberLayerBackdrop { … }` records. There is
  no per-frame recomposition of the backdrop.

## 3. The primitive

`LiquidGlass.kt`:

```kotlin
@Composable
fun Modifier.hmxLiquidGlass(
    shape: Shape,
    level: HmxGlassLevel = HmxGlassLevel.Card,
    fill: Color = HmxColor.Glass,
    elevation: Dp = 10.dp,
    edge: HmxGlassEdge = HmxGlassEdge.Full,
): Modifier
```

On capable devices (`Build.VERSION.SDK_INT >= 31` and not
`ActivityManager.isLowRamDevice`) it chains `drawBackdrop`:

- `vibrancy()` on every level;
- Control: `blur(5.dp) + lens(6.dp, 9.dp)`;
- Chrome: `blur(16.dp) + lens(14.dp, 18.dp)`;
- Card: **no blur/lens** — these are the most numerous surfaces (one per list
  row), and a RenderEffect per row was the main scroll-jank source. Cards keep
  vibrancy, the surface gradient, the specular lip, the edge highlight, the outer
  shadow and (chrome-level only) the inner shadow.

Highlight, shadow and surface are drawn from the active `HmxGlassTokens`, so
Light and Dark produce different physics from the same component tree.

### 3.1 Edge mode

`HmxGlassEdge` controls how the specular outline is drawn:

| Mode | Behaviour | Used by |
|---|---|---|
| `Full` (default) | Closed Backdrop `Highlight.Default`, outer `Shadow`, and (Chrome) `InnerShadow` | Floating cards, sheets, dialogs, buttons |
| `Top` | No Backdrop highlight/shadow/inner shadow; a vertical-gradient outline that is strong at the top lip and transparent at the bottom | Shared top bar, circular brand mark |

`Top` exists because the closed contour plus the 22 dp chrome drop shadow drew a
bright 1 px line and a grey band along the bottom of the full-width header. The
fallback path mirrors the same rule and also drops the drop shadow. Blur, lens,
vibrancy, fill gradient and the top sheen are unchanged — it is still real
glass, just without a hard bottom contour. See `TOP_HEADER_FIX_REPORT.md`.

## 4. Effect budget

| Level | Blur | Lens | Used by |
|---|---|---|---|
| Card | none | none | list cards, stat cards, inputs’ container, quiet rows |
| Control | 5 dp | 6 dp / 9 dp | buttons, icon buttons, chips, search, segmented control |
| Chrome | 16 dp | 14 dp / 18 dp | top bar, bottom bar, sheets, dialogs, hero cards |

Rules:

- No nested Backdrop layers. The bottom bar is drawn outside the content layer.
- No blur on repeated list rows.
- No infinite animations in chrome or backgrounds. The only infinite animation
  is the small `GlassShimmer` placeholder used while data loads.
- Background is static per theme; it is not animated or re-recorded on scroll.

## 5. Fallback strategy

If any of these is true, `hmxLiquidGlass` returns a clipped translucent gradient
with a token-driven border and soft shadow:

- API < 31 (no `RenderEffect` blur);
- the device reports `isLowRamDevice`;
- no root backdrop is mounted (previews, isolated component tests).

Layout, hit targets, typography, colours and state are identical. Only the
refraction/blur is dropped. There is no “glass off” mode that removes the design.

## 6. Component library

Real-glass implementations in `ui/common/HmxGlassComponents.kt`, with short
aliases matching the brief:

| Brief name | Implementation | Level |
|---|---|---|
| `HmxGlassCard` | `HmxLiquidGlassCard` | Card |
| `HmxGlassButton` | `HmxLiquidGlassButton` | Control / Card (Ink) |
| `HmxGlassOutlinedButton` | `HmxLiquidGlassOutlinedButton` | Control |
| `HmxGlassIconButton` | `HmxLiquidGlassIconButton` | Control |
| `HmxGlassTextField` | `HmxLiquidGlassTextField` | Control |
| `HmxGlassSearchField` | `HmxLiquidGlassSearchBar` | Control |
| `HmxGlassChip` | `HmxLiquidGlassChip` | Control |
| `HmxGlassTopBar` | `HmxLiquidGlassTopBar` | Chrome |
| `HmxGlassBottomBar` | `HmxLiquidGlassBottomBar` | Chrome |
| `HmxGlassBottomSheet` | `HmxLiquidGlassBottomSheet` | Chrome |
| `HmxGlassDialog` | `HmxLiquidGlassDialog` + `HmxLiquidGlassFormDialog` | Chrome |
| `HmxGlassTournamentCard` | `HmxLiquidGlassTournamentCard` | Card |
| `HmxGlassMatchCard` | `HmxLiquidGlassMatchCard` | Card |
| `HmxGlassPlayerCard` | `HmxLiquidGlassPlayerCard` | Card |
| `HmxGlassStatCard` | `HmxLiquidGlassStatCard` | Card |
| `HmxGlassNotificationCard` | `HmxLiquidGlassNotificationCard` | Card |
| `HmxGlassLoading` | `HmxLiquidGlassLoading` | Card |
| `HmxGlassEmptyState` | `HmxLiquidGlassEmptyState` | Card |
| `HmxGlassErrorState` | `HmxLiquidGlassErrorState` | Card |

All are theme-aware by construction; none accepts a hard-coded colour.

### 6.1 Shared header brand mark

`GlassTopBar` (and therefore `HmxPage`) accepts an optional `leading` slot.
Home passes `HmxBrandMark()`: the existing `res/drawable/ic_hmx_mark.xml`
(plus `drawable-night` variant) tinted with `HmxColor.Ink`, inside a 36 dp
circular `Control` glass disc with a `HmxGlassEdge.Top` edge and a 1 dp
`HmxColor.HairlineStrong` ring for light-theme legibility. It replaces the
title-only start of the row; the notification button, unread badge and click
target on the right are unchanged, and the 56 dp header height is unchanged.

### 6.2 Chip sizing

`GlassChip` sizes to its label (`maxLines = 1`, `softWrap = false`). It must not
use `fillMaxSize()` internally: a chip inside a `Row` would then consume the
remaining width and squeeze neighbouring text (this broke the Settings
appearance preview).

## 7. Application coverage

The same components are used across:

- Welcome, sign up, sign in, forgot password, verification, recovery.
- Player setup / complete profile.
- Home, tournament discovery, tournament details, registration and success.
- My matches, match details, room credentials, results, evidence, disputes.
- Leaderboard and tournament rankings.
- Profile, edit profile, game accounts.
- Teams, create/manage team, invitations, invite player.
- Wallet, transactions, referral.
- Notifications, announcements, search, player profile.
- Support, ticket, help, about, legal, settings and account settings.

## 8. Interaction feedback

- `GlassButton` / outlined / icon buttons scale to 0.94–0.98 on press.
- Icon buttons and clickable cards additionally draw
  `HmxColor.PressedOverlay` **above** the glass content so the effect cannot be
  hidden by the backdrop.
- All buttons preserve a `Role.Button` and a semantics `contentDescription`.
- Press feedback is local state (`MutableInteractionSource`); it never waits for a
  network call.

## 9. Accessibility and semantics

- Segmented control / theme selector expose `selected` and a
  `contentDescription` (`“Appearance theme”`).
- Bottom tabs expose `Role.Tab`, `selected` and their label.
- Icon buttons expose their action.
- Selection is conveyed by shape/position and colour, not colour alone (the
  moving pill plus the label/icon colour change).
- Dark and light text/background pairs meet the contrast targets used by the
  reference design; tertiary text is only used for captions and hints.

## 10. Files

- `core/designsystem/Theme.kt` — palettes, tokens, effects budget, shapes.
- `core/designsystem/LiquidGlass.kt` — real glass + fallback.
- `core/designsystem/HmxBackdrop.kt` — host, capability check, decorative layer.
- `core/designsystem/Glass.kt` — scaffold, top bar, buttons, chip, states.
- `core/designsystem/ThemePreferences.kt` — DataStore mode + root view model.
- `ui/common/HmxGlassComponents.kt` — component library and aliases.
- `ui/common/CommonUi.kt` — fields, cards, avatar, pills, loading/error/empty.
- `ui/shell/GlassBottomBar.kt` — refractive tab bar.
