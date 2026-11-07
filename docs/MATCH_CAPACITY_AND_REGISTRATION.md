# Match Capacity and Registration

Date: 2026-11-07
Migration: `0014_admin_tournament_match_management` (parts 1-5)
Files: `TournamentRepository.kt`, `Models.kt`, `MatchScreens.kt`, `CommonUi.kt`

## Two capacity models

The app now has two independent registration surfaces. They share the visual
language but not their counters.

| | Tournament | Match |
| --- | --- | --- |
| Table | `tournaments` | `matches` |
| Registration table | `registrations` | `match_registrations` |
| Capacity columns | `max_slots`, `slots_filled` | `max_slots`, `slots_filled` |
| Sign-up RPC | `register_for_tournament` | `register_for_match` |
| Cancel RPC | `cancel_registration` | `cancel_match_registration` |
| Waitlist | yes (existing `waitlisted` status) | **no** |
| Full error | `tournament_full` | `match_full` |
| Eligibility | profile/game account per existing rules | published, window open, game account |

**Rules of thumb**

1. A tournament slot is the player's place in the event.
2. A match slot is a place in one specific lobby. A player may be registered in
   the tournament and in one or more of its matches.
3. Registering for the tournament does **not** automatically fill its matches.
   Registering for a match does **not** require a tournament registration.
4. Cancelling the tournament registration releases every confirmed match slot
   the player holds in that tournament (see
   `TOURNAMENT_REGISTRATION_CANCELLATION_FIX.md`).
5. Match registration is never waitlisted. When full the server returns
   `match_full`; the UI disables the button and shows **MATCH FULL**.

## Atomic match registration

`register_for_match(p_match_id uuid, p_team_id uuid default null)`
is `SECURITY DEFINER` with `search_path = public, pg_temp` and runs as one
transaction:

1. Reject anonymous callers (`not_authenticated`).
2. `select ... from matches where id = p_match_id for update` locks the match
   row, serialising concurrent registrations for that match.
3. `match_not_found` if missing.
4. `match_not_available` when unpublished or archived.
5. `match_registration_closed` unless status is `scheduled` or `check_in`.
6. `registration_window_closed` when now is outside
   [`registration_opens_at`, `registration_closes_at`] (either bound is
   optional).
7. Resolve the game (match, else tournament). Require a matching
   `player_game_accounts` row (`game_account_required`).
8. For a team registration, require the team to exist, the caller to be a
   member, and not exceed `max_team_size` (`team_not_found`,
   `not_team_member`, `team_too_large`).
9. Look up the caller's existing registration `for update`. An active
   (`confirmed`/`waitlisted`) row is `already_registered` — this prevents a
   second row from bypassing the unique constraint.
10. If `max_slots` is set and `slots_filled >= max_slots`, raise `match_full`
    **before** incrementing.
11. Assign `slot_no = slots_filled + 1`, increment `matches.slots_filled`.
12. Insert the registration, or re-confirm a previously cancelled row.
13. Send an in-app notification.

Because the lock, the bound check and the increment are in one transaction, two
simultaneous callers for the last slot cannot both succeed: the second waits
for the lock, re-reads the incremented counter and receives `match_full`.

### Why the client cannot bypass it

- `match_registrations` has only a `select` policy and, after `0016`, only a
  `select` grant for `authenticated`.
- There is no insert/update policy, so a direct PostgREST insert fails with
  `new row violates row-level security policy`.
- The `slots_filled` counter is written only inside the RPCs.
- `matches` grants only `SELECT`; direct updates fail with
  `permission denied for table matches`.

## Cancel

`cancel_match_registration(p_registration_id uuid)`:

1. `not_authenticated` when there is no session.
2. Lock the registration row; `match_registration_not_found` if missing.
3. Reject another user's row unless the caller is an admin (`not_allowed`).
4. Return immediately when already `cancelled` (idempotent).
5. Lock the match row.
6. Mark the registration `cancelled` with `cancelled_at = now()`.
7. If it had been `confirmed`, decrement `slots_filled` with
   `greatest(..., 0)`, so it can never go negative.
8. Notify the user.

History is preserved: the row is not deleted, so the audit trail and the user's
registration list keep the entry marked cancelled.

## Tournament cancellation interaction

`cancel_registration` now also loops over the user's confirmed
`match_registrations` in the tournament's matches, cancels each and decrements
the corresponding match counter, then removes the user's
`match_participants` row. Waitlist promotion for the tournament continues to
work as before.

## Player UI

`MatchesScreen` uses `TournamentRepository.matchFeed()`:

- only published matches;
- joined with the tournament title and the caller's registration state;
- local cancellation/registration is refetched after the RPC.

`MatchDetailScreen`:

- banner (16:9) and logo (1:1);
- reward columns: prize pool, per-kill, entry fee;
- capacity text `filled of max` plus a progress bar;
- `MATCH FULL` pill when `isFull`;
- `Register` / `Cancel registration` with a busy label and confirmation dialog;
- success and error messages come from the backend response;
- after success it reloads the match and the caller's registration, so
  `slots_filled` and `registered` are authoritative.

`MatchFeedCard` marks full and registered states with pills, so a full match is
recognisable without opening it.

## Error mapping

`mapDbMessage` maps `match_full`, `registration_window_closed`,
`already_registered`, `match_registration_closed`, `game_account_required`,
`not_allowed`, `not_authenticated` to readable text. Unknown keys fall back to
the raw server message rather than a generic failure.

## Verification

Executed in rolled-back transactions against the live database:

| Test | Result |
| --- | --- |
| Register for a published match | Passed, `slot_no = 1`, `slots_filled = 1` |
| Register twice | Rejected `already_registered` |
| Register when `max_slots = 1` is full | Rejected `match_full` |
| Cancel own registration | Passed, `slots_filled` 1 → 0 |
| Cancel twice | Passed, idempotent |
| Cancel another user's registration as player | Rejected `not_allowed` |
| Direct insert into `match_registrations` | Blocked by RLS |
| Direct update of `matches` | Blocked (`permission denied`) |
| Tournament cancel releases match slots | Passed |
| Registration row retained as cancelled | Passed |

The concurrency guarantee is by row lock; a true parallel HTTP test was not run
because only the SQL layer is reachable here. The lock is the same pattern
already used by tournament registration.
