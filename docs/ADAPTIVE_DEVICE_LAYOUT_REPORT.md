# HMX Arena — Adaptive Device Layout Report

Date: 2026-11-07  
Branch: `feature/hmx-arena-android-production`  
Commit: `16bf24e`  
Implementation: `core/designsystem/Adaptive.kt`, `Glass.kt`,
`ui/shell/GlassBottomBar.kt`, `ui/tournament/TournamentScreens.kt`,
`HmxBackdrop.kt`

## 1. Requirement

The app must reflow automatically on small/normal/large phones, foldables,
tablets, landscape and multi-window, without any device-model checks. Phones keep
the bottom navigation bar; larger windows use a rail, multi-column content, and
a readable maximum content width.

## 2. Implementation

### Window size classes

`Adaptive.kt` derives the class from the available width, not from
`smallestScreenWidthDp` or a model name:

| Available width | Class | Layout |
| --- | --- | --- |
| `< 600dp` | `Compact` | Phone bottom bar, full-width lists, 1 grid column |
| `600–839dp` | `Medium` | Left navigation rail, 2 grid columns, content capped at 760dp |
| `>= 840dp` | `Expanded` | Left navigation rail, 3 grid columns, content capped at 980dp |

Because the class is recomputed from `BoxWithConstraints.maxWidth` on every
measure pass, these cases all reflow without extra code:

- phone in portrait / landscape;
- foldable half-open (width crosses 600dp);
- tablet portrait (usually `Medium`) and landscape (`Expanded`);
- Android split-screen / freeform / desktop window resize;
- different densities, because the breakpoints are in dp.

### Navigation: one component, two orientations

`GlassScaffold` places the existing `HmxLiquidGlassBottomBar` in a 96dp-wide
container at `CenterStart` when the class is `Medium` or `Expanded`, and at
`BottomCenter` otherwise. The bottom-bar composable measures its own available
width and renders vertically when `maxWidth < 200.dp`. This keeps one source of
truth for tabs, selected state, semantics and click handling; there is no second
navigation implementation to drift.

The vertical rail:

- shows the same five destinations and the same animated ink capsule;
- uses the same `Role.Tab` semantics and `selected` state;
- is inset from the navigation-bar area;
- gives each tab an equal height slice so it scales with the window height.

The content column is inset by `HmxRailWidth` when the rail is shown, so no card
or top bar sits underneath it.

### Content width

`hmxContentMaxWidth()` caps the top bar and screen content at 760dp / 980dp on
medium/expanded windows. Compact phones stay full width. Long text lines and
cards therefore keep a readable measure instead of stretching across a tablet.

### Tournament grid

The tournament list switched from `LazyColumn` to `LazyVerticalGrid`:

- 1 column on compact;
- 2 on medium;
- 3 on expanded;
- search, filter chips, result count, error/empty/loading states use
  `GridItemSpan(maxLineSpan)` so they stay full-width;
- `horizontalArrangement` adds the same 12dp gap as the vertical rhythm.

The remaining list screens keep `LazyColumn` with the content-width cap. They are
single-column task flows (forms, detail pages, settings) where a grid would
reduce readability.

### Top bar

The shared top bar is capped by the same max width and inset by the rail width,
so on a tablet the title, brand mark and actions stay aligned with the content
column.

## 3. Font scaling and touch targets

- All text uses Material 3 `sp` typography; no fixed-pixel text sizes were added.
- At 200% font scale the horizontal bar's `labelSmall` line (26dp at 2×), 20dp
  icon and 4dp gap total 50dp inside the 58dp inner bar, so they still fit.
- Icon-only controls use `HmxDimens.iconButton = 48dp`, matching the minimum
  touch target.
- The vertical rail gives each tab a full-height slice, so a larger label grows
  into the slice rather than overlapping the next tab.
- Screens that can grow vertically (forms, detail pages, offline screen) use
  `verticalScroll`; lists use lazy containers.

## 4. Low-end interaction

On the `Reduced` glass tier the rail's selected-capsule animation snaps instead
of running a spring, and the content backdrop is not recorded a second time.
Layout, hit targets and semantics are identical.

## 5. Verification

- `AdaptiveLayoutTest` (JVM) pins the 599/600/839/840dp boundaries, the 760/980dp
  caps, the 1/2/3 column mapping and the 96dp rail width.
- `Android CI` compiles and tests the adaptive code.
- The visual workflow runs a Pixel 6 profile. It does **not** currently boot a
  tablet or foldable AVD, so tablet screenshots are not claimed. The layout
  classes themselves are covered by JVM tests and by measuring the emulator at
  its real width.

## 6. Residual risk / next phase

- Add `screenshots` on a 600dp+ AVD (tablet or resizable emulator) to prove the
  rail visually. The code path is width-driven and unit-tested, but a screenshot
  from a large window is still desirable evidence.
- A foldable hinge/fold-aware layout (`WindowLayoutInfo`) would let a two-pane
  layout avoid the hinge. The current single-column content cap is safe on a
  foldable but does not use both panes for a master-detail flow.
- Extremely large font scales (>200%) can make the floating bar taller than the
  fixed 58dp inner height on very small screens; the labels remain readable in
  the rail, and a future pass can switch the bar to intrinsic height.
