# Match Screen Data Integration Audit

Date: 2026-11-08
Branch: `feature/hmx-admin-tournament-match-management`
Baseline code commit: `dd7f2c1`
Integration commit: `183f94b`
Database: live Supabase project `hmxesport` (`ahsvxxhaowwhqermruhz`)
Migration added: `0018_public_match_visibility`

## 1. Objective

Verify that every match-facing screen reads the source it claims to read, that
tournament registration and match registration are never mixed, and that
admin-managed changes appear in the app without hardcoded sample data.

The contract used for the whole audit:

| Screen | Correct source | Must never use |
| --- | --- | --- |
| Home | published/featured tournaments + the caller's active **match** registrations | tournament registrations as a proxy for matches |
| Arena | all eligible published/admin-managed tournaments, with dynamic category and game-mode filters | legacy enum chips or hardcoded modes |
| Matches | the public published match catalogue | the caller's registration set |
| My Matches | only matches the caller actively registered for | the public catalogue |
| Tournament Details | the current tournament row and its reachable matches | match ids from another tournament |
| Match Details | the current match row, its optional tournament, and the caller's **match** registration | tournament registration as match access |

## 2. Screens that were disconnected

These screens compiled and rendered, but their data was wrong or unreachable.

| Screen | Defect found | Impact |
| --- | --- | --- |
| **Arena** (`TournamentScreens.kt`) | Filter chips were hardcoded legacy enum values (`solo`, `duo`, `squad`, `clash_squad`) and queried the legacy `tournaments.mode` column. Admin-managed `category_id` / `game_mode_id` lookup rows were ignored. | New admin categories/modes never appeared, and filtering could return the wrong events. |
| **Home** (`HomeScreen.kt`) | "Your schedule" called `myMatches()`, which was built from **tournament** registrations and then listed every match of those tournaments. | The dashboard showed matches the player had never registered for, and hid independent match registrations. |
| **Matches** (`MatchScreens.kt`) | The "Registered" badge was true when the player had a tournament registration, even without a match registration. The list was otherwise public but not grouped by state. | A tournament entrant looked registered for every match, while a match-only entrant could look unregistered. |
| **My Matches** | There was no destination at all. Home's "My matches" opened the public Matches tab. | Players could not find the matches they actually joined. |
| **Tournament Details** (`TournamentScreens.kt`) | Schedule, participants, rules and leaderboard routes existed in the nav graph but had no entry point. Logo, type, format and version were not shown. A cancelled registration could still be treated as active. | Associated matches were effectively unreachable, and stale registration state could block re-registration. |
| **Match Details** (`MatchScreens.kt`) | Registration/room access came from `matchRegistration ?: tournamentRegistration`. Match number, category and type were missing from the detail card. | A tournament registration granted match-level UI access without a match slot; required match fields were absent. |
| **Match feed cards** (`CommonUi.kt`) | Cards showed only the match name; match number, category and game mode were omitted. | Two "Match 1" rows from different tournaments were indistinguishable. |

## 3. Corrected queries

All reads go through the existing `TournamentRepository`; no screen writes its
own PostgREST query.

### 3.1 Public tournament catalogue (Arena)

`tournamentQueryParams()` in `TournamentRepository.kt`:

```text
status=not.in.(cancelled)
archived_at=is.null
order=is_featured.desc,starts_at.asc
limit=60
select=*,category:categories(...),gameMode:game_modes(...),tournamentType:tournament_types(...)
category_id=eq.<lookup-id>      # only when selected
game_mode_id=eq.<lookup-id>     # only when selected
entry_type=eq.free              # only when free-only is on
```

The legacy `mode=eq.*` filter was removed. Drafts stay hidden through
`tournaments` RLS and are never requested by the public screen.

### 3.2 Dynamic Arena filters

`categories()` and `gameModes()` load active, non-archived rows from the
lookup tables:

```text
select=id,slug,name,description,logo_url,is_active,is_archived,display_order
is_active=eq.true
is_archived=eq.false
order=display_order.asc,name.asc
```

Lookup failures fall back to the last known chip set instead of blanking the
Arena.

### 3.3 Public match feed (Matches, Home featured data)

`publishedMatchQueryParams()`:

```text
is_published=eq.true
archived_at=is.null
status=neq.cancelled
order=scheduled_at.asc
limit=60
select=*,category:categories(...),gameMode:game_modes(...),tournamentType:tournament_types(...)
```

`matchFeed()` attaches `MatchFeedItem.registered` from `match_registrations`
only. It no longer reads tournament registrations.

### 3.4 My Matches

`myMatchFeed()` starts from the caller's active match registrations, then
resolves only those match ids:

```text
match_registrations?user_id=eq.<uid>&status=in.(confirmed,waitlisted)
matches?id=in.(<registered-match-ids>)
        &is_published=eq.true
        &archived_at=is.null
        &status=neq.cancelled
```

If the registration set is empty the result is empty. There is no fallback to
the public catalogue, so My Matches can never become "all matches".

### 3.5 Tournament Details and tournament schedule

`tournament(id)` reads the current row (including non-draft status updates).
`tournamentMatches(tournamentId)` now requires:

```text
tournament_id=eq.<tournament-id>
is_published=eq.true
archived_at=is.null
status=neq.cancelled
order=scheduled_at.asc
```

Tournament Details now links to Schedule, Participants, Rules and Leaderboard,
and refresh reloads the row so admin edits propagate. The active-registration
check accepts only `confirmed` or `waitlisted`.

### 3.6 Match Details

`match(id)` now returns only a published, non-archived match. The detail screen
uses:

- `matches` row for title/name, `match_number`, category, game mode, type,
  prize pool, per-kill, entry fee, version, map, capacity, date/time and
  registration window;
- optional `tournaments` row for the tournament title (otherwise
  "Independent match");
- `match_registrations` for the caller's slot/waitlist state, cancel action and
  room access;
- `get_match_credentials()` for credentials, which independently verifies
  participation server-side.

### 3.7 Home

- Featured tournaments: published, non-cancelled, non-archived, ordered by
  `starts_at`.
- "Your schedule": `myMatchFeed()` (match registrations only).
- Added a refresh action so admin changes are visible without restarting.
- Cached catalogue rows are filtered to remove cancelled/archived entries.

## 4. Security changes

### 4.1 `matches_select`

Before migration `0018`, the policy was:

```sql
using (true)
```

Any caller with the anon key could read unpublished or archived matches by
guessing an id. The client filtered `is_published`, but security must not depend
on client discipline. `0018` replaces it with:

```sql
using (
    public.is_admin()
    or (is_published = true and archived_at is null)
)
```

Verified live after applying the migration.

### 4.2 `is_match_participant`

The function previously accepted only `match_participants` /
`registrations`. A player who registered directly for a match (the independent
match flow) was not a participant for private resources. It now also accepts:

```sql
match_registrations.match_id = p_match_id
and match_registrations.user_id = p_user_id
and match_registrations.status = 'confirmed'
```

Tournament participation behaves exactly as before.

### 4.3 What public feeds still do not expose

- Draft matches (`is_published = false`) and archived matches.
- Cancelled matches in public lists.
- `match_credentials`: the table has no SELECT policy; credentials are only
  returned by `get_match_credentials()` after a participation check.
- Other users' match registrations: `match_registrations` remains
  select-restricted by RLS.
- No RLS policy was weakened. No write grant was added.

## 5. Verification against the 12 requested scenarios

Run as one rolled-back transaction against the live database, with an
authenticated admin role for admin RPCs and an authenticated player role for
registration checks. No test row survived the rollback.

| # | Scenario | Assertion | Result |
| --- | --- | --- | --- |
| 1 | Admin creates a match | Draft row is `is_published = false` | **Passed** (`01_admin_create_draft`) |
| 2 | Admin publishes it | `is_published = true` | **Passed** (`02_publish`) |
| 3 | Public Matches feed | Anon sees the published match once | **Passed** (`03_public_feed_visible`) |
| 4 | Tournament association | `tournament_id` matches the selected tournament | **Passed** (`04_tournament_relationship`) |
| 5 | Match artwork | Banner and logo both present | **Passed** (`05_banner_and_logo`) |
| 6 | Non-registered player, public feed | Still sees the published match | **Passed** (`06_non_registered_sees_public`) |
| 7 | Registered player, My Matches | Confirmed match registration resolves the match | **Passed** (`07_registered_user_registers`, `07_registered_user_my_matches`) |
| 8 | Non-registered player, My Matches | Match is absent | **Passed** (`08_non_registered_not_in_my_matches`) |
| 9 | Draft visibility | Anon cannot read the draft | **Passed** (`09_draft_hidden_from_anon`) |
| 10 | Admin edit propagation | Public read returns the updated name | **Passed** (`10_admin_edit`, `10_public_reads_update`) |
| 11 | Capacity enforcement | Second registration on `max_slots = 1` raises `match_full` | **Passed** (`11_full_blocks_registration`) |
| 12 | Cancellation | `slots_filled` returns to 0 and My Matches empties | **Passed** (`12_capacity_restored`, `12_my_matches_empty_after_cancel`) |

Supplementary live REST checks with the publishable (anon) key:

- public matches returned published, non-archived rows with embedded category,
  game mode and tournament type;
- categories returned 5 active/non-archived rows;
- game modes returned 4 active/non-archived rows;
- Arena filter requests using `category_id` and `game_mode_id` returned only
  matching tournaments.

## 6. Regression tests added

`app/src/test/java/com/hmx/arena/data/repository/MatchFeedRulesTest.kt`:

| Test | Coverage |
| --- | --- |
| `publicMatchRequiresPublishedAndNotArchivedAndNotCancelled` | Draft, archived and cancelled matches are never public |
| `feedGroupsUpcomingOngoingAndCompletedWithoutLeakingHiddenRows` | Ongoing/upcoming/completed grouping, ordering, and hidden-row exclusion |
| `registeredBadgeOnlyDependsOnMatchRegistration` | Badge is false without a match registration and true with one |
| `arenaQueryUsesDynamicLookupForeignKeysAndHidesIneligibleRows` | No legacy `mode` param; `category_id` / `game_mode_id` used; cancelled/archived excluded |
| `publicMatchQueryNeverRequestsDraftsOrArchives` | Public match query filters and embedded lookups |

`MatchFeedRules.kt` holds the pure visibility/grouping rules so the UI cannot
silently reintroduce a draft or cancelled row.

## 7. Build and CI verification

| Check | Environment | Status |
| --- | --- | --- |
| `:app:testDebugUnitTest` | GitHub Actions, JDK 17 | Recorded in the CI run for this branch |
| `:app:lintDebug` | GitHub Actions, JDK 17 | Recorded in the CI run for this branch |
| `:app:assembleDebug` | GitHub Actions, JDK 17 | Recorded in the CI run for this branch |
| Device/emulator UI | Not available in this environment | Static review + CI only |

Local compilation was not possible because this environment has no Android SDK
(`ANDROID_HOME` is empty and there is no `local.properties`); the Gradle wrapper
itself runs, so compile/test/lint/APK verification is delegated to the existing
`Android CI` workflow.

## 8. Files changed

| File | Change |
| --- | --- |
| `data/repository/TournamentRepository.kt` | Dynamic filters, public/My Matches feeds, published-only reads |
| `data/repository/MatchFeedRules.kt` | New pure visibility and grouping rules |
| `ui/home/HomeScreen.kt` | Match-registration schedule, My Matches link, refresh |
| `ui/match/MatchScreens.kt` | Public feed grouping, match-only badge/access, detail fields |
| `ui/match/MyMatchesScreen.kt` | New user-specific destination |
| `ui/tournament/TournamentScreens.kt` | Dynamic Arena filters, logo/type/version, hub links, refresh |
| `ui/tournament/TournamentSubScreens.kt` | Tournament schedule refresh and published match query |
| `ui/common/CommonUi.kt` | Match number/category/mode on cards |
| `ui/navigation/Routes.kt` | `MY_MATCHES` route |
| `ui/navigation/MainNavHost.kt` | Wired `MyMatchesScreen` |
| `supabase/migrations/0018_public_match_visibility.sql` | RLS hardening + match-registration participation |
| `app/src/test/.../MatchFeedRulesTest.kt` | Regression tests |

## 9. Known limitations

- The live project has no admin account. Admin RPCs were exercised by
  temporarily inserting an admin role inside a rolled-back transaction.
- No device or emulator was available, so visual behaviour is covered by static
  review, unit rules and CI, not instrumentation tests.
- There is no staging project; database checks used rolled-back transactions on
  the production database and left no test rows.
