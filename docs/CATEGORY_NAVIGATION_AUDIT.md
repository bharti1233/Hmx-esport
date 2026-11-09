# Category Navigation Audit

Date: 2026-11-09
Branch: `feature/hmx-home-category-navigation`

## 1. Symptom

Tapping a category on Home opened the generic tournament list. Users could see
tournaments from other categories, and returning to Home then tapping a
different category could keep the first category because the navigation entry
was reused.

## 2. Root causes found

### 2.1 Wrong destination

`HomeScreen` called:

```kotlin
nav.navigate(Routes.tournaments(category.id)) { launchSingleTop = true }
```

`Routes.tournaments()` is the Arena catalogue route. It accepts a category
filter but is not a category screen: it loads tournaments, not the independent
match feed, and it carries the Arena filter UI.

### 2.2 Constructor-only saved state

`TournamentsViewModel` read its category once:

```kotlin
private val initialCategory = savedStateHandle.get<String>("category")
private val _ui = MutableStateFlow(TournamentsUi(categoryId = initialCategory))
```

A reused destination kept the old value even when the route argument changed.

### 2.3 Weak identity risk

Display names are not stable identifiers. The category must be passed by the
`categories.id` UUID, not by a name or slug, so renamed categories keep working.

## 3. Implemented navigation

| Piece | Value |
| --- | --- |
| Route pattern | `category/{categoryId}` (`Routes.CATEGORY_MATCHES`) |
| Builder | `Routes.categoryMatches(categoryId)` |
| Nav argument | `categoryId`, `NavType.StringType`, required |
| Destination | `CategoryMatchesScreen(nav, categoryId)` |
| Back | `nav.popBackStack()` |
| Home tap | `nav.navigate(Routes.categoryMatches(category.id))` (no `openTab`) |

A plain `navigate` is deliberate. It creates a fresh entry for each category
and avoids the `restoreState` behaviour used by the five bottom-tab
destinations. Back returns to Home, not to a filtered Arena list.

## 4. Category resolution

`CategoryMatchesScreen` never trusts a display name from the route. It calls
`TournamentRepository.category(id)`, which reads the real `categories` row
(description, logo, artwork, active state). If the row is missing, the screen
shows `This category could not be found.` with a retry action.

The Home category list is loaded from `categories` with
`is_active = true`, `is_archived = false`, ordered by `display_order` and then
`name`, so admin ordering and publication state are respected.

## 5. Tab and state isolation

- Category ID is passed explicitly to `CategoryMatchesViewModel.load()` from
  the current nav entry on every composition (`LaunchedEffect(categoryId)`).
- `applyRouteCategory()` was also added to the old tournament ViewModel so it
  follows the current route rather than constructor-only state.
- Stale responses are discarded: a slower request for a previous category
  cannot overwrite the category currently shown.
- Switching tabs changes only `section` inside `CategoryMatchesUi`; the
  category ID and header remain unchanged.
- Back destroys the category destination; reopening any category starts from
  that category's own URL/ID.

## 6. Verification

- Pure route tests in `NavigationRoutesTest` pin the pattern and builder.
- Live REST checks confirmed the category IDs used by the seeded data
  (`Battle Royale`, `Clash Squad`, `Full Map`, `Special Tournament`, `Custom
  Tournament`) return the expected category-filtered counts.
- `MatchFeedRulesTest` pins the status filters used by each tab.
- Compilation and unit-test execution are tracked in
  `docs/TEST_EXECUTION_REPORT.md` (GitHub Actions is the only local compile
  environment available).
