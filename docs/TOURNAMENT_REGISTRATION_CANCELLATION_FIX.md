# Tournament Registration Cancellation Fix

Date: 2026-11-07
Branch: `feature/hmx-admin-tournament-match-management`
Related migrations: `0014_admin_tournament_match_management` (parts 1-5)

## Symptom

Tapping **Cancel registration** on a tournament showed a generic
"Something went wrong. Please try again." error even though the registration
had already been cancelled on the server. The UI then either stayed on the
registered state or refreshed inconsistently, so the user could try again and
see a second error.

## Root cause

The failure was in the transport layer, not in the SQL cancellation logic.

1. `cancel_registration` is declared `returns void`.
2. PostgREST answers a void RPC with `204 No Content`.
3. The Android client called `POST /rest/v1/rpc/cancel_registration` through a
   Retrofit method typed as `JsonElement` (`RestApi.rpc`).
4. Retrofit tried to deserialize the empty body as JSON. That threw before the
   repository could inspect the response, and `guardApi` converted the
   deserialization exception into the generic message.
5. The cancellation had already committed, so the user saw failure and the
   local state still looked registered until a manual refresh.

The same defect affected every other void RPC: `leave_team`,
`mark_notifications_read`, `request_account_deletion`,
`set_match_credentials`, `transfer_team_ownership`, `raise_dispute` and the new
`cancel_match_registration`.

## Fix

### Client transport

`app/src/main/java/com/hmx/arena/data/remote/SupabaseApi.kt`

```kotlin
@POST("rest/v1/rpc/{fn}")
suspend fun rpcVoid(
    @Path("fn") fn: String,
    @Body body: kotlinx.serialization.json.JsonObject,
): retrofit2.Response<Unit>
```

`RemoteDataSource.rpcUnit` now calls `rpcVoid`, checks `isSuccessful`, and maps
the PostgREST error body through `mapDbMessage` / `parseErrorMessage`:

```kotlin
suspend fun rpcUnit(name: String, params: JsonObject = JsonObject(emptyMap())) = guardApi {
    val response = rest.rpcVoid(name, params)
    if (!response.isSuccessful) {
        val message = runCatching { response.errorBody()?.string() }.getOrNull()
        throw ApiException(mapDbMessage(parseErrorMessage(message ?: "")), message)
    }
    Unit
}
```

This is a success-only path: a 204 means the server committed the change, so
the UI may update, and an error body means it did not.

### Repository

`TournamentRepository.cancelRegistration` and
`TournamentRepository.cancelMatchRegistration` both use `rpcUnit`. No local
state is mutated before the call returns.

### Server behaviour

`cancel_registration(p_registration_id uuid)` (migration 0014) is
`SECURITY DEFINER` with `search_path = public, pg_temp` and:

1. Rejects unauthenticated callers (`not_authenticated`).
2. Locks the registration row `for update`.
3. Rejects a missing row (`registration_not_found`).
4. Rejects another user's registration unless `is_admin()` (`not_allowed`).
5. Returns immediately when the row is already `cancelled` (idempotent).
6. Marks the row `cancelled` and sets `cancelled_at`.
7. Decrements `tournaments.slots_filled` only when the cancelled row was
   `confirmed` and the tournament was in `registration_open` or
   `registration_closed`.
8. Promotes the oldest waitlisted registration, assigns a slot, increments
   capacity, adds a single tournament match participant row and notifies the
   promoted player.
9. Cancels the same user's confirmed `match_registrations` rows for that
   tournament and decrements each match's `slots_filled`.
10. Deletes the user's `match_participants` row for the cancelled tournament
    registration.

The counter can never go negative (`greatest(x - 1, 0)`), and duplicate calls
do not decrement twice.

## UI behaviour

`TournamentDetailScreen`:

- **Cancel registration** opens a confirmation dialog.
- The button is disabled and labelled "Cancelling…" while the request is in
  flight (`busy`).
- The success notice is only shown after the repository returns successfully.
- The screen reloads the tournament and the caller's registration after
  success, so capacity and registered state come from the server.
- Backend failures are shown through `hmxMessage()` and the prior state is
  preserved.

Match-level cancellation follows the same pattern in `MatchDetailScreen`.

## Error mapping

`SupabaseApi.kt` now maps the relevant keys explicitly:

| Server key | Message |
| --- | --- |
| `not_authenticated` | Please sign in again to continue. |
| `registration_not_found` / `match_registration_not_found` | The registration could not be found. |
| `not_allowed` | You do not have permission to do that. |
| `registration_window_closed` | Registration is not open right now. |
| `match_full` / `tournament_full` | The event is full. |

## Verification

Executed against the live Supabase project inside rolled-back transactions:

| Test | Result |
| --- | --- |
| Cancel own confirmed tournament registration | Passed, `slots_filled` 1 → 0 |
| Cancel the same registration twice | Passed, no error, no second decrement |
| Cancel another user's registration as a non-admin | Passed, `not_allowed` |
| Cancel own match registration | Passed, match `slots_filled` 1 → 0 |
| Cancel the same match registration twice | Passed, idempotent |
| Cancel another user's match registration as a non-admin | Passed, `not_allowed` |
| Cancelling a tournament registration releases confirmed match slots | Passed |
| Registration history row remains present with `status = cancelled` | Passed |
| Android compile of the void-RPC path | Passed in GitHub Actions (see test report) |

The Android cancellation flow could not be exercised on a device or emulator in
this environment; the API contract and the SQL transaction path were verified,
and the APK/unit-test build is executed by GitHub Actions.

## Known limitations

- There is no push notification; the waitlist promotion uses the in-app
  notification table only.
- Waitlist promotion requires the `waitlist_enabled` app flag. When disabled,
  a full tournament rejects new registrations instead of waitlisting.
- A cancellation after the tournament has moved to `live` or `completed` does
  not reduce `slots_filled`; the status change is preserved for history but
  capacity is frozen at that point.
