# HMX Arena — Responsive Layout Test Report

Branch: `feature/hmx-admin-tournament-match-management`
Base: `f0ddabc`
Implementation commit: `8ede8fa`
Date: 2026-11-08

---

## 1. Status

**Code-level verification complete. Physical device/emulator verification was
not possible in this environment and is not claimed.**

The adaptive shell and breakpoints already existed and were preserved. This
change adds one derived grid value and reuses the existing adaptive contract on
the new Home/Arena content.

## 2. Window classes

`core/designsystem/Adaptive.kt` continues to use Material width breakpoints:

| Class | Width | Source |
|---|---|---|
| Compact | `< 600 dp` | `hmxWindowClassForWidth` |
| Medium | `600–839 dp` | `hmxWindowClassForWidth` |
| Expanded | `≥ 840 dp` | `hmxWindowClassForWidth` |

Unit test `AdaptiveLayoutTest.widthBreakpoints` pins 0/320/599 -> Compact,
600/720/839 -> Medium and 840/1280 -> Expanded.

## 3. Grid behaviour

| Content | Compact | Medium | Expanded | Test |
|---|---|---|---|---|
| Tournament grid | 1 | 2 | 3 | `gridColumnsFollowWindowClass` |
| Category artwork grid | 2 | 3 | 5 | `categoryArtworkGridUsesItsOwnDensity` |
| Content max width | full | capped | capped | `compactPhonesKeepFullWidthContent` |

The category grid uses `hmxCategoryColumns` because artwork tiles are
square-ish and can be denser than tournament cards. Rows are built with
`chunked(columns)` plus weighted spacers, so the last row never stretches its
tiles or overflows the available width.

## 4. Navigation adaptation

The existing shell places the floating navigation:

- as a bottom capsule on Compact widths;
- in the 96 dp rail container on Medium/Expanded widths.

`HmxLiquidGlassBottomBar` measures its own available width
(`maxWidth < 200.dp`) to decide vertical vs horizontal layout. The five
destinations, route state and semantics are identical in both forms, so there
is no second navigation implementation to keep in sync.

## 5. Phone, tablet, foldable and landscape coverage

| Case | How the layout reacts | Verified how |
|---|---|---|
| Small phone (320–599 dp) | Compact: 2-column categories, 1-column tournaments, full-width content | unit breakpoints + code review |
| Large phone / small tablet (600–839 dp) | Medium: 3-column categories, 2-column tournaments, rail nav | unit breakpoints + code review |
| Tablet (≥ 840 dp) | Expanded: 5-column categories, 3-column tournaments, rail nav | unit breakpoints + code review |
| Foldable inner display | width-class driven; no device-model checks | code review only |
| Foldable cover display | Compact fallback; scrolling lists and 124 dp bottom inset | code review only |
| Landscape phone | width class usually Medium; lists remain vertically scrollable | code review only |
| Split-screen / resizable window | `LocalHmxWindowClass` recomposes from available width | architecture review; not physically tested |

## 6. Font scaling and clipping

- No screen sets a fixed `fontScale` or disables system scaling.
- Values that can grow (wallet balance, tile values, card titles) use
  `maxLines` + ellipsis rather than fixed-height boxes.
- Empty states and inline errors are inside vertically scrolling lists, so a
  large font can increase height without clipping.
- Filter tabs use `heightIn(min = 44.dp)`; they grow with text instead of
  clipping it.
- Category and tournament rows use weights; text never forces a horizontal
  overflow beyond the weight allocation.
- The header wallet capsule ellipsises a long balance instead of pushing the
  add button off-screen.

## 7. Touch targets and insets

- Icon buttons use the existing `HmxDimens.iconButton` size.
- Filter tabs have a 44 dp minimum height.
- Shortcut cards have a 112 dp minimum height.
- All floating-bar screens reserve a 124 dp bottom content inset.
- Edge-to-edge insets remain handled by the existing `GlassScaffold` / page
  components; no new hard-coded status/navigation bar padding was added.

## 8. What was not tested

The following require a device or emulator and are explicitly **not claimed**:

- real font-scale screenshots at 1.3x / 1.5x / 2.0x;
- foldable hinge posture;
- landscape keyboard overlap;
- rendering of real uploaded artwork at every density;
- carousel snap behaviour with a trackpad/gesture;
- performance on a low-end GPU (see the separate performance report).

CI compiles the app, runs the unit tests above and runs Android lint. That is
the only automated verification available in this environment.

## 9. Follow-up for device QA

1. Compact phone portrait and landscape at 1.0x / 1.5x font.
2. Medium tablet split-screen with the rail visible.
3. Foldable inner/outer transition while Home is scrolled.
4. Create a banner and category artwork in admin, then confirm the Home grid
   and Arena grid at all three window classes.
5. Verify the last list item is reachable above the floating bar on each form
   factor.
