# HMX Arena — Arena and Matches UI Report

Branch: `feature/hmx-admin-tournament-match-management`
Base: `f0ddabc`
Implementation commit: `8ede8fa`
Date: 2026-11-08

---

## 1. Result

Arena and the match surfaces now share one visual and data contract:

- Arena = tournament discovery (`tournaments` route, Play tab context).
- Matches = public match feed (`matches` route, Play tab context).
- My Matches = registration-only list (`my-matches`, drilled in with a back
  action).

No route was removed. Arena and Matches continue to use the existing
repositories, RLS reads and registration RPCs.

## 2. Arena

### Search

A glass search field filters by title/keyword. It uses the existing
`TournamentFilter.query` and the same repository call as before.

### Categories

The dynamic category list is rendered as image-first artwork cards:

- wide admin `image_url`, then square `logo_url`, then neutral icon fallback;
- a real selected ring when the current filter matches;
- tapping the selected category clears it; tapping another applies it.

The selected category is written to the route
`tournaments?category=<id>`. `TournamentsViewModel` reads the `category`
argument from `SavedStateHandle` on first creation, so Home shortcuts and the
Arena screen agree.

### Game modes and entry filter

- "All modes" chip + one chip per active, non-archived game mode.
- "Free entry" chip maps to the existing `entry_type=eq.free` filter.
- An empty lookup list shows a factual message instead of placeholder chips.

### Tournament cards

Published tournament cards remain `TournamentCard` (banner, logo, title,
dates, prize, entry, capacity/status as provided by the model). Cards navigate
to the existing tournament detail.

### States

- loading: `LoadingBlock("Loading tournaments")`;
- error with no data: `InlineError` + retry;
- empty result: `EmptyBlock` with "Clear filters" that resets query, category,
  mode and the free flag;
- bottom inset reserves 124 dp for the floating bar.

## 3. Public match feed

### Filters

`MatchesScreen` adds `HmxFilterTabs` with real counts:

| Tab | Rule |
|---|---|
| All | every public match in the feed |
| Ongoing | `live`, `check_in` |
| Upcoming | `scheduled` |
| Results | `completed` |

Counts come from `matchSectionCounts`, which counts only public matches
(published, not archived, not cancelled) and uses the same `matchSectionOf`
rule as the grouped list. Filtering only changes presentation; it never
changes the server query or registration eligibility.

If a filter has no rows, an in-place empty state offers "Show all".

### Match card

`MatchFeedCard` keeps the required media and data contract:

- true 16:9 banner area;
- separate 1:1 logo badge (not cropped into the banner ratio);
- title, status, tournament title;
- category, mode, type, map, format/version;
- prize pool, per-kill and entry reward cells (`—` or "Free" when the real
  value is zero);
- capacity line ("X of Y slots", "Full" / "N left") and a progress bar only
  when the server reports capacity;
- `MATCH FULL` and `Registered` badges;
- scheduled date/time and a ticking countdown;
- decorative images use `contentDescription = null`; the card is a
  `Role.Button` with the match name available as text.

Capacity enforcement stays server-side in the existing registration RPC and
database constraints. The UI never grants a slot and never fabricates
"slots remaining".

## 4. My Matches

`MyMatchesScreen` starts from `match_registrations` (not the public
catalogue), so it can only show matches the signed-in player joined.

- optional `section` route argument (`ongoing` / `upcoming` / `completed`);
- All / Ongoing / Upcoming / Results tabs with counts;
- "N registered" summary and a shortcut back to the public feed;
- two empty states: no registrations at all, or nothing in the selected
  section;
- back action returns to the previous destination;
- no bottom bar, because it is a drill-in destination rather than a tab.

## 5. Navigation

| Destination | Route | Bottom bar selection |
|---|---|---|
| Arena | `tournaments?category={category}` | Play (`home`) |
| Matches | `matches` | Play (`home`) |
| My Matches | `my-matches?section={section}` | none (drill-in) |

`Routes.myMatches("all")` normalises to `my-matches`, and
`Routes.tournaments(null)` normalises to `tournaments`. Both optional-argument
routes are registered with nullable string arguments and a null default.

## 6. Tests

- `MatchFeedRulesTest`
  - section counts count only public matches;
  - `matchSectionOf` agrees with the grouped feed;
  - drafts, archived and cancelled matches never leak;
  - registration badge depends only on `match_registrations`.
- `NavigationRoutesTest`
  - optional category/section arguments are omitted when empty;
  - `all` normalises to the bare My Matches route;
  - the Arena/Matches destinations are not bottom-bar tabs.

## 7. Limitations

- No device/emulator was available, so image cropping, carousel paging and
  landscape/foldable rendering were not physically tested.
- The live production data has 5 categories, 4 game modes and 5 tournament
  types; artwork depends on an admin uploading `image_url`. Without artwork
  the documented logo/neutral fallback is used.
- `TournamentCard` and `MatchFeedCard` are shared with Home and other screens;
  the reports document the components rather than duplicating them.
