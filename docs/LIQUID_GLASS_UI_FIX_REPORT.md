# Liquid Glass UI Fix Report

Date: 2026-11-09
Branch: `feature/hmx-home-category-navigation`

## 1. Scope

This change does not redesign the Liquid Glass system. It reuses the existing
components (`HmxPage`, `GlassSurface`, `GlassSectionHeader`, `GlassButton`,
`GlassIconButton`, `HmxFilterTabs`, `HmxPromoCarousel`,
`HmxAnnouncementTicker`, `HmxMatchShortcutCard`, `MatchFeedCard`) so the Home
simplification and the new category screen stay visually consistent with the
rest of the app.

## 2. Home

- Removed blocks were deleted from the layout; no new visual language was
  introduced.
- The retained sections keep their existing glass surfaces and spacing.
- The "Esports game" heading uses `GlassSectionHeader`; its description is a
  plain body-small line.
- Category cards remain `HmxCategoryArtCard`, now with deterministic SVG-free
  fallbacks instead of a universal trophy.

## 3. Category screen

- Uses `HmxPage` with a real back action, so the top bar and glass chrome are
  identical to other detail screens.
- Hero card: wide artwork or logo or fallback, a vertical fade into the canvas
  colour, name and description, and an optional wallet pill.
- Tabs use the shared `HmxFilterTabs` (equal-width, 44 dp targets, selected
  semantics, optional counts).
- Feed cards use the shared `MatchFeedCard`.
- Loading, empty and error states use `LoadingBlock`, `EmptyBlock` and
  `InlineError`.

## 4. Inset and overlap fixes

- Home and Arena keep a `124.dp` bottom content inset so the floating bottom
  bar never covers the final card.
- The category screen is a drill-in destination with no bottom bar, so it uses
  a `32.dp` bottom inset rather than copying the tab-screen padding.
- `GlassScaffold` continues to cap content width on Medium/Expanded windows and
  to move the bar to a rail on wide layouts.

## 5. Performance

- Category pages are bounded to 20 items.
- Per-tab caches avoid re-fetching when switching tabs.
- Lazy lists use stable keys (`match.id`).
- Coil handles image loading/caching; the existing glass tier continues to
  reduce effects on constrained devices.
- Duplicate refresh taps and duplicate pagination requests are guarded.

## 6. Not changed

No changes were made to theme tokens, colour values, blur tiers, glass
host/backdrop implementation or typography. The previous Liquid Glass redesign
remains intact.
