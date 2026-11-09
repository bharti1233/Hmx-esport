# Home Page Redesign Report

Date: 2026-11-09
Branch: `feature/hmx-home-category-navigation`
Base: `feature/hmx-admin-tournament-match-management` at `791f618`

## 1. Objective

Simplify the Home screen so it focuses on the player and the path into a match
category, while preserving real Supabase data, the existing Liquid Glass design
system, admin-managed content, and every dedicated screen that used to be
linked from Home.

## 2. Final Home structure (top to bottom)

| Order | Section | Source |
| --- | --- | --- |
| 1 | HMX Arena header (brand mark, refresh, notifications) | `HomeScreen.kt` |
| 2 | Profile + wallet card | `HomeHeader` |
| 3 | Announcements ticker / notifications entry | `HmxAnnouncementTicker` |
| 4 | Promotional banners | `HmxPromoCarousel` |
| 5 | My Matches shortcuts (Ongoing / Upcoming / Completed) | `MyMatchesShortcuts` |
| 6 | `Esports game` heading + `Pick a category to find its matches.` | `HomeLayout.kt` |
| 7 | Admin-created categories | `CategorySection` / `HmxCategoryArtCard` |

The order is a compile-time contract in
`app/src/main/java/com/hmx/arena/ui/home/HomeLayout.kt` and is pinned by
`HomeLayoutTest`.

## 3. Removed from Home only

| Removed block | Still available elsewhere |
| --- | --- |
| Refer and Earn card | Referral tab (`Routes.REFERRAL`) |
| Featured arenas | Arena (`Routes.TOURNAMENTS`) |
| Your schedule list | My Matches (`Routes.MY_MATCHES`) |
| Arena news list | Announcements (`Routes.ANNOUNCEMENTS`) |

No backend table, RPC, repository method, admin screen, or dedicated screen was
deleted. The `featured()` repository query is no longer called by Home; the
remaining data still comes from the live Supabase project.

## 4. Category entry point

Home no longer calls `Routes.tournaments(category.id)`. Each category card now
calls `Routes.categoryMatches(category.id)` using the row's stable UUID. The
new destination loads the category row by ID and then loads only that
category's published matches server-side.

This removes two confirmed root causes of the old behaviour:

1. The generic tournament list was reused for a category tap.
2. The old `TournamentsViewModel` only read `SavedStateHandle["category"]` in
   its constructor, so a reused navigation entry could keep the previous
   category.

`TournamentsViewModel` now also has `applyRouteCategory(categoryId)`, driven by
the current nav entry, so any remaining caller of the tournament route cannot
retain a stale category.

## 5. Loading and empty behaviour

- Categories are loaded with the existing repository query that filters
  `is_active = true`, `is_archived = false`, ordered by `display_order` then
  `name`.
- While Home has no data, it shows a loading block, never an empty dashboard.
- The Arena count row now reads `Loading tournaments…` or
  `Tournaments unavailable` instead of flashing `0 tournaments` before the
  first query completes (`tournamentCountLabel`, tested).
- The category screen shows its own exact empty message
  `No matches available in this category yet.`

## 6. Files changed

| File | Change |
| --- | --- |
| `ui/home/HomeScreen.kt` | Removed four blocks, category tap now uses stable ID |
| `ui/home/HomeLayout.kt` | New canonical section order and copy contract |
| `ui/common/HmxContentComponents.kt` | Artwork/logo/fallback precedence |
| `ui/common/CommonUi.kt` | Match cards show the admin match number |
| `ui/navigation/Routes.kt` | New `category/{categoryId}` route |
| `ui/navigation/MainNavHost.kt` | Registers the category destination and route arg |
| `ui/tournament/TournamentScreens.kt` | Route-aware category reload, loading-aware count |
| `ui/category/CategoryMatchesScreen.kt` | New category match screen |

## 7. Limitations

- No Android SDK or emulator was available in this environment, so visual
  verification is limited to source review and CI compilation.
- The nine required reports are part of this branch; CI status is recorded in
  `docs/TEST_EXECUTION_REPORT.md`.
