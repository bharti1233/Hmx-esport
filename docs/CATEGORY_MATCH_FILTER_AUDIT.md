# Category Match Filter Audit

Date: 2026-11-09
Branch: `feature/hmx-home-category-navigation`

## 1. Scope

The category screen must show only the matches that belong to the selected
category, split into Ongoing / Upcoming / Results, with real counts, bounded
pagination, and no dependence on hardcoded dates.

## 2. Canonical tab rules

The database status is the single source of truth. The same rule is used to
build the PostgREST filter and to group any locally held items
(`MatchFeedRules.kt`):

| Tab | Backend status filter | Meaning |
| --- | --- | --- |
| Ongoing | `in.(live,check_in)` | Running or check-in open |
| Upcoming | `eq.scheduled` | Published and not started |
| Results | `eq.completed` | Finished |

There is no date arithmetic deciding the tab. `scheduled_at` is used only for
ordering (`asc` for Ongoing/Upcoming, `desc` for Results) and display.

## 3. Query contract

`categoryMatchQueryParams()` always includes:

```text
category_id = eq.<selected UUID>
is_published = eq.true
archived_at = is.null
status = <tab filter>
limit   = 20 (CATEGORY_MATCH_PAGE_SIZE)
offset  = 0, 20, 40, ...
order   = scheduled_at.asc | scheduled_at.desc
```

The category and tab filters are present on **every** page, so pagination
cannot mix categories or tabs. Cancelled matches are excluded by the status
filters (they are not in any tab list).

`categoryMatchCountQueryParams()` mirrors the list filters and performs a
`HEAD` request with `Prefer: count=exact`. The total is read from
`Content-Range`:

- `0-19/42` -> 42
- `*/0` -> 0
- missing/malformed -> `null`, and the UI withholds the number instead of
  showing a fabricated zero.

## 4. Screen state

`CategoryMatchesUi` keeps:

- the resolved `LookupOption` category,
- optional wallet,
- the selected `MatchFeedSection`,
- nullable per-tab counts,
- one `CategoryTabState` per tab (items, loading, loadingMore, loaded,
  error, hasMore).

Consequences:

- Tabs load independently and are cached per tab.
- Switching tabs does not reset the category.
- Initial tab selection prefers the first tab with a real count; if the user
  has already chosen a tab, their choice wins.
- The empty state is only shown after a tab has actually loaded and returned
  zero rows.
- An initial failure shows `InlineError` with retry; a tab failure shows the
  same inline retry row.
- Pagination is manual (`Load more`), page size 20, stable item keys
  (`match.id`), duplicate IDs removed when merging pages.

## 5. Match card fields

`MatchFeedCard` renders the admin-managed match data:

- 16:9 banner, square logo badge,
- title, admin match number (`matchNumberLabel`),
- status pill and `MATCH FULL` / `Registered` badges,
- category, mode, type, map, format, version,
- prize pool, per-kill reward, entry fee,
- filled / max capacity and progress bar when capacity is configured,
- date/time and countdown,
- tap-through to the existing match detail screen.

## 6. Live verification

Against the live project (`ahsvxxhaowwhqermruhz`) on 2026-11-09:

| Category | Ongoing | Upcoming | Results |
| --- | --- | --- | --- |
| Battle Royale | 1 | 3 | 0 |
| Clash Squad | 0 | 1 | 0 |
| Full Map | 0 | 0 | 0 |
| Special Tournament | 0 | 0 | 0 |
| Custom Tournament | 0 | 0 | 0 |

Each count came from a real `HEAD` + `count=exact` request with the same
filters the app uses. This confirms that an empty category returns a real `0`
after loading, not before.

## 7. Tests

- `MatchFeedRulesTest` — status filters, tab keys, preferred tab, query
  filters, pagination offsets, count filter parity, loading label.
- `ContentRangeCountTest` — exact totals, zero totals, malformed headers.
- `CategoryEmptyStateTest` — exact empty copy.
- `MatchFeedRulesTest` existing tests — public visibility and grouping.
