# Responsive Layout Audit

Date: 2026-11-09
Branch: `feature/hmx-home-category-navigation`

## 1. Breakpoints used

The app derives layout from the available window width, not the device model
(`core/designsystem/Adaptive.kt`):

| Window class | Width | Category grid | Tournament grid | Content cap |
| --- | --- | --- | --- | --- |
| Compact | `< 600.dp` | 2 columns | 1 column | unbounded |
| Medium | `600–839.dp` | 3 columns | 2 columns | 760.dp |
| Expanded | `>= 840.dp` | 5 columns | 3 columns | 980.dp |

This covers phones, split-screen, mid-fold foldables, tablets and resized
windows. On Medium/Expanded with a bottom bar, `GlassScaffold` reserves the
96.dp navigation rail so content is not covered.

## 2. Home

- Category rows are chunked by `hmxCategoryColumns`, with `weight(1f)` per
  card and spacer weights for the final short row, so no card stretches or
  clips.
- Category names are single-line with ellipsis; the artwork uses a fixed
  aspect ratio and crops rather than distorting.
- My Matches shortcuts are three weighted cards; long labels are bounded.
- The Home content column is capped by `GlassScaffold` on larger windows.

## 3. Category screen

- Single-column feed inside the scaffold's content cap; the hero keeps a
  `2.1:1` aspect ratio on every width.
- Name/description are limited to two lines with ellipsis.
- Tabs are equal-width with 44.dp minimum height and single-line labels.
- Match cards use `MatchFeedCard`, which already wraps reward cells and
  metadata and truncates titles.

## 4. Insets

- Tab destinations (Home, Arena, Matches, Leaderboard, Wallet, Profile,
  Referral) reserve the floating bar inset. Home, Arena, Matches, Leaderboard,
  Wallet and Profile use `124.dp`; the Earn/Referral list was corrected to the
  same `124.dp` bottom inset in this change. The shared bottom bar is drawn
  with navigation-bar padding.
- Drill-in screens such as the category screen do not add tab-bar padding.
- Status bars are handled by `GlassScaffold` (`statusBarsPadding`).

## 5. Accessibility

- Category cards expose a semantic role and description.
- Tabs expose `Role.Tab` and selected state.
- Icon buttons have content descriptions (`Refresh category matches`, etc.).
- Touch targets are at least 44 dp for tabs and icon buttons.
- Text uses theme typography and supports system font scaling; long text is
  ellipsized rather than clipped.

## 6. Verification limits

No emulator or physical device was available, so this audit is a source-level
review plus the existing adaptive unit tests (`AdaptiveLayoutTest`) and
compilation in CI. Landscape, foldable posture, large-font and RTL rendering
still need a manual pass on hardware before release.
