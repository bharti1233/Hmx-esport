# HMX Arena — Dark Mode Audit

Date: 2026-11-05
Branch: `feature/hmx-arena-android-production`
Related: `docs/GLOBAL_UI_AND_PERFORMANCE_AUDIT.md`,
`docs/LIQUID_GLASS_IMPLEMENTATION.md`

---

## 1. Root cause of the light leakage

The dark theme was not a dark palette with a brightness bug: it was a **light
glass drawing pipeline with dark fill colours swapped in**, and several drawing
steps still used the light sheen/glow colours.

The actual sources, in order of visual impact:

1. **The card gradient started with white.**
   `LiquidGlass.kt` painted
   `Brush.verticalGradient(0f to tokens.sheenColor.copy(alpha = topAlpha), …)`.
   In dark mode `sheenColor` was `Color.White`, so every card had a white-to-
   charcoal wash across its top third. The `fill` colour only appeared at 28 %
   of the gradient. This is the “white patch” on the cards.

2. **The decorative backdrop bloom was white.**
   `Theme.kt` set `backdropBloom = Color.White` for dark and
   `HmxBackdrop.kt` drew it as a radial at 10 % alpha with a radius of 78 % of
   the screen. Over `#09090A`, that is a large grey glow behind the top-right
   of every screen.

3. **Reflections and edges were tuned for light glass.**
   Dark had a 10 % white top sheen, 18 % white chrome sheen, 45 % white
   highlight, 80 % chrome highlight and a 36 % white border. Individually each
   is subtle; together they made dark glass look like bright plastic.

4. **The fallback path ignored the token alpha.**
   On API < 31 / low-RAM devices the fallback gradient used
   `sheenColor` as its first stop and a hard-coded 50 % white border, so those
   devices were brighter than the primary path.

5. **The window and splash were always white.**
   `res/values/themes.xml` was the only theme file; there was no
   `values-night`. The platform painted `@color/hmx_white` before Compose ran,
   producing a white flash on every cold start in dark mode. The splash icon
   was near-black in every configuration, so a dark splash would have hidden it.

6. **The Settings selector did not drive the root theme.**
   The selector obtained a `ThemeViewModel` scoped to the Settings navigation
   entry, while `MainActivity` held a different instance. Selecting Dark wrote
   DataStore but the root composition never observed the change. This is not a
   colour leak, but it made the whole mode appear broken and left users on the
   light theme.

## 2. Dark palette (final)

| Token | Value | Purpose |
|---|---|---|
| `canvas` | `#09090A` | Near-black page |
| `canvasTint` | `#131316` | Ripple / progress track |
| `ink` | `#F5F5F7` | Primary text |
| `inkSecondary` | `#B6B6BF` | Secondary text |
| `inkTertiary` | `#8C8C96` | Hints, labels |
| `onInk` | `#09090A` | Text on inverse surfaces |
| `hairline` | 14 % white | Fine border |
| `hairlineStrong` | 24 % white | Section separators |
| `glass.surface` | `#3D17171A` | Card fill |
| `glass.surfaceStrong` | `#701D1D21` | Prominent card |
| `glass.surfaceMuted` | `#241F1F24` | Quiet fill |
| `glass.chrome` | `#4D141417` | Bars, sheets, dialogs |
| `glass.control` | `#5C1D1D21` | Buttons, inputs |
| `glass.edge` | 20 % white | Specular edge |
| `glass.sheenColor` | `#E2E4EA` (not white) | Top reflection |
| `glass.sheenAlpha` / chrome | 5 % / 9 % | Restrained reflection |
| `glass.highlightAlpha` / chrome | 25 % / 45 % | Edge highlight |
| `backdropBloom` | `#C1C5CE` at 3.5 % | Faint neutral light source |
| `backdropCloud` | 3.9 % `#C9C9D2` | Diffuse mass |
| `backdropMid` | 2.7 % | Mid glow |
| `backdropArc` / soft / ring | 2.4 % / 1.6 % / 2 % | Detail for the lens |
| `watermark` | 4 % white | “HMX” texture |
| `pressedOverlay` | 11 % white | Press feedback |
| `shadow` | 40 % black | Depth |

The base glass gradient is now `fill → fill → fill × 0.95`, so the top of a card
is at most 3 % brighter than its body. The separate 5 % neutral lip is the only
specular reflection.

## 3. Light palette (unchanged identity)

| Token | Value |
|---|---|
| `canvas` | `#FDFDFE` |
| `canvasTint` | `#F3F3F5` |
| `ink` | `#0B0B0C` |
| `inkSecondary` | `#64646B` |
| `glass.surface` | 32 % white |
| `glass.surfaceStrong` | 54 % white |
| `glass.chrome` | 28 % white |
| `glass.control` | 40 % white |
| `glass.edge` | 60 % white |
| `glass.sheenColor` | white |
| `glass.sheenAlpha` / chrome | 7 % / 13 % |
| `backdropBloom` | white at 45 % |

Both themes use the same component tree and the same tokens; only the values
differ. No screen has a `if (dark)` colour branch outside `Theme.kt`, and no
hard-coded colour exists outside the palette file.

## 4. Window, splash and system bars

- `values-night/themes.xml` defines dark `Theme.HMXArena` and
  `Theme.HMXArena.Splash` with `@color/hmx_black` (`#0A0A0B`) and light system-
  bar icons.
- `Theme.HMXArena.Splash.Dark` is selected in `MainActivity.onCreate` when the
  persisted in-app mode is Dark but the system is Light. The decision comes from
  `HmxThemeBootstrap`, a synchronous write-through mirror of DataStore. The
  DataStore flow remains the source of truth.
- `drawable-night/ic_hmx_mark.xml` provides a light-ink logo for dark splash.
- `HmxTheme` updates `isAppearanceLightStatusBars` and
  `isAppearanceLightNavigationBars` in a `SideEffect` on every mode change, and
  keeps both bars transparent.

## 5. What was verified

- JVM token tests (`ThemeTokensTest`) assert dark canvas luminance < 0.05, dark
  sheen not pure white and ≤ 8 % alpha, dark edge ≤ 25 % alpha, dark bloom not
  white, subtle watermark/arcs, and zero card blur.
- Repository search: zero `Color.White` / `Color.Black` / literal `Color(0xFF…)`
  outside `Theme.kt`; zero `AlertDialog` in the main UI.
- Emulator screenshot matrix (20 captures, light + dark, via GitHub Actions)
  covering sign in, home, arena, tournament detail, matches, leaderboard,
  profile and settings, plus immediate theme switching and persistence. See
  `docs/UI_REGRESSION_TEST_REPORT.md` for the run and artifact IDs.

## 6. Known intentional inversions

In dark mode the **selected bottom-bar pill**, filled status chips and the
`Ink` button variant use the inverse-ink token: a solid white surface with
near-black text. These are deliberate high-contrast selection/CTA surfaces, not
translucent white patches, gradients, glows or leaking light-mode decorations.
They mirror the black pill/button used in light mode, keeping the component
language identical across themes.
