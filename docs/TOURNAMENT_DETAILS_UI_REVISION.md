# Tournament Details UI Revision

Date: 2026-11-07
File: `app/src/main/java/com/hmx/arena/ui/tournament/TournamentScreens.kt`

## Goal

The previous details screen was a long page of secondary navigation,
duplicated information and empty spacing. The revision keeps only the
information a player needs to decide whether to register, plus the
register/cancel action and the caller's registration state.

## Removed

- The navigation box linking to Rules, Participants, Match schedule and
  Tournament leaderboard.
- The **About** block (long free-text description).
- The **Schedule** block beyond the single match date/time.
- The data reads that only existed for those blocks (`rules`,
  `participants`, `tournamentMatches`) from the details view model.
- The resulting empty spacers.

The underlying sub-screens and routes still exist for other entry points; they
are simply no longer promoted from the details page.

## Kept and presented

Top to bottom:

1. **Banner** (16:9, only when a banner URL exists).
2. **Status, game mode and category pills.**
3. **Title** and a one-line summary of game · category · mode · map.
4. **Prize pool, entry fee and per-kill reward** columns. Per-kill is hidden
   when zero.
5. **Capacity**: `filled of max slots`, progress bar, "N left" or a `FULL`
   pill.
6. **Match date and time** with the live countdown, plus the registration
   close time when set.
7. **Registration state**:
   - confirmed: "You are registered", slot number or status, and
     **Cancel registration**;
   - waitlisted: "You are on the waitlist";
   - not registered and registration open: **Register now** (or
     **Join the waitlist** when full);
   - otherwise: a status-specific notice.
8. Inline success and error messages after an action.

## Data source

`TournamentDetailViewModel` now loads exactly two things:

```kotlin
val tournament = repository.tournament(id)
val registration = repository.registrations()
    .firstOrNull { it.tournamentId == id }
```

Both come from the backend through PostgREST and the caller's RLS scope. There
are no sample values, hardcoded tournaments or local fallbacks.

## Behaviour

- The toolbar share action remains.
- Cancel uses the shared `ConfirmDialog` and `repository.cancelRegistration`,
  which now uses the void-RPC path described in
  `TOURNAMENT_REGISTRATION_CANCELLATION_FIX.md`.
- The busy state disables the button and changes its label; no optimistic
  local mutation is performed.
- After success the screen refetches, so capacity and registration state are
  authoritative.
- Empty banner, missing map, zero prize and zero per-kill are handled without
  leaving blank rows.

## Visual identity

The screen reuses the existing monochrome Liquid Glass components
(`GlassSurface`, `GlassButton`, `StatusPill`, `DetailStat`,
`LinearProgressIndicator`) and the theme tokens `HmxColor`, `HmxDimens` and
`HmxShapes`. Nothing in the revision introduces a new palette or a light-only
or dark-only style, so Light, Dark and System all keep the same hierarchy.

## Verification

- Static review of the new layout and state branches.
- GitHub Actions compiles the module and runs lint/unit tests (see
  `TOURNAMENT_TEST_REPORT.md` for the exact run).
- Device/emulator visual testing was not available in this environment.
