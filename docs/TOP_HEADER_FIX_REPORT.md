# HMX Arena — Top Header Fix Report

Date: 2026-11-06
Branch: `feature/hmx-arena-android-production`
Commits: `f0117a4` (divider + brand mark), `12e336b` (light-theme logo ring)
Verification: GitHub Actions runs `42176006279` (f0117a4) and the follow-up run
for `12e336b`; Android CI run `42176006329` (unit tests, lint, debug APK).

---

## 1. Symptom

On every screen that uses the shared header (Home, Arena, Matches,
Leaderboard, Profile, Settings and all detail screens) there was:

- a thin **bright horizontal line** along the bottom of the header in Dark;
- a **grey band / hard shadow** immediately below the header in Light;
- the header looked detached from the page instead of blending into it.

The effect was identical across screens, which pointed at the shared component,
not at individual screens.

## 2. Investigation

### 2.1 What was inspected

- `core/designsystem/Glass.kt` → `GlassScaffold`, `GlassTopBar`
- `core/designsystem/LiquidGlass.kt` → `hmxLiquidGlass`, `hmxGlassFallback`
- `ui/common/CommonUi.kt` → `HmxPage` (the one route every screen uses)
- `ui/common/HmxGlassComponents.kt` → `HmxLiquidGlassTopBar` pass-through
- Material `Scaffold` elevation / tonal elevation: **not used** by the app
  (`GlassScaffold` is a plain `Box`/`Column`), so this was ruled out;
- `HorizontalDivider` / `Divider`: **zero occurrences** in the app;
- window insets: `statusBarsPadding()` is applied once to the header
  container; no separator is painted by insets;
- page gradient / backdrop: the decorative layer is a single recorded
  `Box`; it does not draw a line;
- nested surfaces: the header is the only glass surface above the content.

### 2.2 Measured evidence (before)

Screenshots from visual run `42078886785`, file `04-home-dark.png` and
`03-home-light.png` (1080×2400). Row mean luminance was computed per pixel
row; the header's bottom edge is at y ≈ 295.

| Row | Dark before | Light before |
|---|---|---|
| 285 | 18 | 251 |
| 290 | 19 | 253 |
| **295 (header bottom)** | **46** | 253 |
| 300 | 13 | **237** |
| 305 | 14 | **238** |
| 310 | 14 | **239** |
| 315 | 15 | **240** |
| 320 | 12 | 253 |

- In Dark the row at the header boundary jumps from ~18 to **46** and back to
  ~13: a one-pixel bright specular line.
- In Light the rows below the header sit at **237–240** against a **252–253**
  page: a soft grey shadow band.

### 2.3 Root cause

`GlassTopBar` used `HmxGlassLevel.Chrome`, which in `hmxLiquidGlass` attached:

1. `Highlight.Default` (Backdrop specular shader) with `width = 1.dp`,
   `blurRadius = 0.75.dp` and `chromeHighlightAlpha` (0.45 Dark / 0.92 Light).
   The shader draws a **closed contour** around the pill, including a bright
   line along the bottom edge.
2. `Shadow(radius = 22.dp, offset = (0.dp, 10.dp))` with
   `chromeOuterShadowAlpha` (0.42 Dark / 0.12 Light). Because the bar is
   full-width and only 4 dp above the page, this reads as a grey strip.
3. `InnerShadow(radius = 14.dp, offset = (0.dp, 7.dp))` adding another dark
   band inside the bottom edge.

This is a **glass rendering** problem, not a Material divider and not a
layout/inset problem. Hiding it with an overlay would have hidden the glass
itself, so the fix removes the offending closed contour and shadow and replaces
them with a top-lit outline.

## 3. Fix

### 3.1 New shared edge mode

`LiquidGlass.kt` now exposes:

```kotlin
enum class HmxGlassEdge { Full, Top }
```

- `Full` (default) keeps the previous closed highlight, outer shadow and inner
  shadow for floating cards, sheets and dialogs.
- `Top` (used only by the shared top bar and the small logo disc) disables the
  Backdrop highlight, outer shadow and inner shadow, and paints its own
  vertical-gradient outline:

  ```
  edge (strong) at the top lip
      ↓
  22 % of the edge alpha at 55 % height
      ↓
  transparent at the bottom
  ```

  The fallback path (API < 31 / low-RAM) uses the same rule and also drops the
  drop shadow, so old devices do not get the grey band either.

Blur, lens, vibrancy, the glass fill gradient and the top sheen are unchanged:
the surface is still real Liquid Glass, it just does not draw a hard bottom
contour.

### 3.2 Shared header (`GlassTopBar`)

- `edge = HmxGlassEdge.Top`, `elevation = 0.dp`.
- Same 56 dp content height, same outer 4 dp vertical / 16 dp horizontal
  padding, same title typography.
- Title is now `maxLines = 1` with `TextOverflow.Ellipsis` for narrow screens.
- New optional `leading` slot, rendered before the title. It is part of the
  shared component, so no screen has a private header implementation.

`HmxPage` (used by every screen) and `HmxLiquidGlassTopBar` /
`HmxGlassTopBar` forward the new `leading` slot.

### 3.3 Circular HMX logo

New `HmxBrandMark` in `core/designsystem/Glass.kt`:

- reuses the existing vector **`res/drawable/ic_hmx_mark.xml`** (with the
  `drawable-night` variant) — the same asset used by the adaptive launcher
  icon and the splash; no new/random logo was generated;
- draws it at 60 % of a 36 dp circular glass disc, tinted with `HmxColor.Ink`
  so it is near-black in Light and off-white in Dark;
- the disc uses `HmxGlassEdge.Top` (no shadow) plus a 1 dp
  `HmxColor.HairlineStrong` ring, so the circle stays visible on the very
  light header (a white-on-white disc would disappear);
- 36 dp fits inside the existing 56 dp header, so **header height is
  unchanged**.

Home passes `leading = { HmxBrandMark() }`; the notification button, its
unread badge (`9+` cap), and its `Routes.NOTIFICATIONS` click are untouched.

Final header layout:

```
[ circular HMX mark ] [ HMX Arena ]                    [ notifications ]
```

with `Row(verticalAlignment = CenterVertically)`, the title on `weight(1f)`
and an ellipsis fallback, so the layout adapts from small phones to tablets.

## 4. Result (after)

Screenshots from visual run `42176006279` (commit `f0117a4`), same files and
same pixel-row method:

| Row | Dark after | Light after |
|---|---|---|
| 285 | 17 | 252 |
| 290 | 17 | 252 |
| 295 | 17 | 253 |
| 300 | 16 | 252 |
| 305 | 17 | 252 |
| 310 | 17 | 251 |
| 315 | 18 | 252 |
| 320 | 12 | 253 |

- Dark: no bright row at the header boundary (46 → 17; the line is gone).
- Light: no grey band below the header (237–240 → 251–252; the shadow is gone).
- The page now starts cleanly after the header; spacing is unchanged
  (header 4 dp outer, content 12 dp top padding).
- The same run captured Home, Arena, Tournament, Matches, Leaderboard,
  Profile and Settings in both themes, so the shared fix is verified across
  the screens that use `HmxPage`.

Commit `12e336b` only adds the ring around the brand mark; the edge/shadow
behaviour is unchanged and is re-verified by its visual run.

## 5. Files changed

| File | Change |
|---|---|
| `core/designsystem/LiquidGlass.kt` | `HmxGlassEdge`, conditional highlight/shadow/inner shadow, top-only gradient outline, fallback parity |
| `core/designsystem/Glass.kt` | `GlassTopBar` uses `Top` edge + `leading` slot; new `HmxBrandMark` |
| `ui/common/HmxGlassComponents.kt` | `leading` forwarding on both top-bar entry points |
| `ui/common/CommonUi.kt` | `HmxPage(leading = …)` |
| `ui/home/HomeScreen.kt` | Home header passes the circular mark |
| `debug/.../VisualTestActivity.kt` | Debug screenshot harness shows the mark on its Home showcase |
| `core/designsystem/Theme.kt` | Lower Dark decorative arc/cloud alphas (visible grey streak behind cards) |
| `test/.../ThemeTokensTest.kt` | Guards the new Dark decoration limits |

## 6. Limitations

- Verification is screenshot-based on an emulator, not a physical device; the
  pixel-row analysis is deterministic and reproducible from the CI artifact.
- The notification badge is present in the production Home header but the CI
  screenshot harness has no backend session, so the badge count shown in CI is
  the harness value (0). The click path was not modified.
- No connected Compose UI test runner is configured; the header is covered by
  the screenshot matrix and the shared unit-test suite. See
  `docs/UI_REGRESSION_TEST_REPORT.md`.
