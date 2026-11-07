# HMX Arena — API and Business Logic Audit

Date: 2026-11-07  
Branch: `feature/hmx-arena-android-production`  
Commit: `16bf24e`

## 1. API surface

The client talks to Supabase through four layers:

1. **Retrofit `AuthApi` / `RestApi`** — `auth/v1/*`, PostgREST `/rest/v1/*`,
   Storage `/storage/v1/*`.
2. **`RemoteDataSource`** — typed helpers (`select`, `selectOne`, `selectRaw`,
   `insert`, `update`, `delete`, `rpc`, `rpcList`, `rpcUnit`, `upload`,
   `publicUrl`, `signedUrl`).
3. **Four repositories** — `AuthRepository`, `PlayerRepository`,
   `TeamRepository`, `TournamentRepository`.
4. **17 Hilt ViewModels** — hold UI state, call repositories, never build URLs.

| Endpoint family | Used for | Auth |
| --- | --- | --- |
| `POST /auth/v1/signup` | registration with `data.username` | anon key |
| `POST /auth/v1/token?grant_type=password` | sign-in | anon key |
| `POST /auth/v1/token?grant_type=refresh_token` | refresh | anon key |
| `POST /auth/v1/verify` | email OTP / recovery token hash | anon key |
| `POST /auth/v1/recover?redirect_to=hmxarena://auth/reset` | reset email | anon key |
| `POST /auth/v1/resend` | resend verification | anon key |
| `GET /auth/v1/user` | verify recovery session | bearer |
| `PUT /auth/v1/user` | email change | bearer |
| `POST /auth/v1/logout` | sign-out | bearer |
| `GET /auth/v1/health` | connectivity probe | anon key |
| `POST /rest/v1/rpc/<fn>` | all mutations and public reads | bearer / anon |
| `GET/POST/PATCH/DELETE /rest/v1/<table>` | simple own-row CRUD | bearer |
| `POST /storage/v1/object/<bucket>/<path>` | avatar, logo, evidence | bearer |
| `GET /storage/v1/object/public/...` | public image URLs | public |

## 2. Business flows reviewed

### 2.1 Sign-up and verification

- Username, email and password are validated client-side in
  `Validation.kt`; errors are field-scoped.
- `data.username` and `data.display_name` are sent so `handle_new_user` can
  create the profile.
- If the project requires confirmation, `SignUpOutcome.needsVerification` is
  true and the UI moves to the OTP screen; the app does **not** pretend the user
  is signed in.
- `resendVerification` surfaces Supabase rate-limit text as a friendly message.

### 2.2 Sign-in / refresh / sign-out

- Password grant stores access + refresh token in memory via `SessionManager`.
- The access token is never persisted (see security report).
- `bootstrap()` restores the refresh token and marks the session signed-in if a
  user id was stored; the first authenticated request refreshes the access
  token.
- Refresh is serialised under one lock; waiters receive the freshly refreshed
  token rather than `null`.
- Sign-out calls the server and clears DataStore even if the network call fails
  (local state must always clear).

### 2.3 Password recovery

- `requestPasswordReset` sends `redirect_to=hmxarena://auth/reset`.
- The deep link is parsed by `RecoveryLinkParser` (strict scheme/host/path/type).
- Two shapes are supported:
  - `token_hash` → `RecoveryResetViewModel` verifies it with `/auth/v1/verify`,
    then lets the user set a new password;
  - access + refresh pair → `restoreFromRecovery` verifies `/auth/v1/user` and
    saves the verified identity.
- A recovery link is queued while offline and processed once when the gate is
  `Ready`.
- Invalid/expired links show a dialog and never silently replace the session.

### 2.4 Onboarding

- `SignedInGate` loads the profile and decides `needsOnboarding` from
  `onboarding_completed` (server) with a local session fallback.
- `key(ui.needsOnboarding)` recreates the `NavHost` when the flag changes, so a
  cached graph cannot keep the player on the wrong start destination.
- `complete_onboarding` is a server RPC that creates/uses a game account and
  marks the profile; the client does not write `onboarding_completed` directly.

### 2.5 Tournament registration

`RegistrationRules.blocker()` is a pure pre-flight check:

1. already registered → block;
2. no game account → block;
3. `maxTeamSize > 1` and no team → block;
4. status ≠ `registration_open` → block;
5. otherwise allow.

The server is authoritative and returns named errors
(`registration_closed`, `already_registered`, `tournament_full`,
`game_account_required`, `team_required`, `team_too_small`, `eligibility_*`),
which are mapped to player-facing copy. The client never sends a registration it
already knows is invalid, but a race on the last slot is decided by the RPC.

### 2.6 Team lifecycle

- `create_team` (RPC), `updateTeam` (RLS owner UPDATE), `deleteTeam` (RLS owner
  DELETE), `uploadLogo` (storage + `teams.logo_url` update).
- `invite` / `respond` / `leave` / `transferOwnership` are all RPCs, so the
  ownership and roster invariants live in one place.
- `owner_must_transfer` blocks leaving while owning a team.
- `team_game_mismatch`, `team_full`, `player_already_in_team`,
  `cannot_invite_self` are mapped errors.
- `teamDetail` now fetches team + members in parallel, then profiles.

### 2.7 Matches, results and disputes

- `matches` are public read; credentials are only available through
  `get_match_credentials`, which enforces `not_match_participant` and
  `match_not_started` server-side.
- `submitResult` uploads evidence to the private bucket under
  `<user_id>/<match_id>-<ts>.jpg`, then calls `submit_match_result`.
  The server validates the path ownership (`invalid_evidence_path`).
- `verify_match_result` and `resolve_dispute` are admin-only
  (`not_admin`), not executable by `authenticated`.
- `raise_dispute` requires a 10-character reason and a non-final result.

### 2.8 Wallet, ledger and referrals

- `wallet_accounts` and `financial_ledger` are own-row read only. The ledger is
  immutable by trigger, so the client cannot edit a balance.
- No client code writes a balance or marks a payment successful; payment orders
  are server-driven. This is correct because a client-side write would be a
  critical tampering hole.
- `claim_referral` enforces self-referral, already-referred, first-7-days and
  the feature flag server-side; the client only trims/upper-cases the code.
- Referral rows are readable only by referrer, referred user or admin.

### 2.9 Reports and support

- `reports` insert is restricted to `reporter_id = auth.uid()`, and reads are
  own/admin, so a player cannot enumerate other reports.
- `submit_support_ticket` is an RPC; `sendSupportMessage` inserts directly but
  the RLS policy requires the ticket to belong to the caller.
- Support messages are immutable from the client's perspective (no UPDATE
  policy).

## 3. Error handling and resilience

- `mapDbMessage` converts stable SQL error codes into human copy; unknown errors
  become a generic retry message. No SQL text, token or stack trace reaches the
  UI.
- `parseErrorMessage` tolerates `message`, `error_description`, `msg` and
  `error` shapes from Auth, PostgREST and Storage.
- `guardApi` distinguishes:
  - `ApiException` (already mapped),
  - `HttpException` (mapped body),
  - `IOException` (offline copy),
  - everything else (generic).
- The tournament catalogue falls back to the Room cache only for `IOException`
  and only for the cacheable full list. A permission or validation error is
  never hidden by stale cache.
- `SessionManager.snapshotUserId()` throws "Please sign in again" instead of
  writing rows with a null owner.

## 4. Findings

### Fixed in this audit

| ID | Finding | Fix |
| --- | --- | --- |
| A-01 | Recovery link accepted any `hmxarena://` URI | Strict parser |
| A-02 | Recovery session not verified against `/auth/v1/user` | Verified restore |
| A-03 | Sequential independent reads on dashboard/team detail | `async` in `coroutineScope` |
| A-04 | Onboarding graph could stay on the old start destination | `key(needsOnboarding)` |
| A-05 | Public RPC page size unbounded | Server clamp to 500 |
| A-06 | Team logo upload/delete policy wrong | Qualified storage policy + delete |

### Investigated and left unchanged

| ID | Observation | Reason |
| --- | --- | --- |
| A-07 | `myMatches()` picks the first registration for a tournament | The schema/registration RPC prevents duplicate active registrations per tournament; a duplicate would be a server bug, and changing the client selection would mask it |
| A-08 | `updateTeam` writes `teams` directly instead of an RPC | RLS restricts it to the owner; moving it to an RPC would duplicate the same check |
| A-09 | `deleteTeam` is a direct DELETE | Cascades are defined with `ON DELETE CASCADE`/`SET NULL`; an RPC would add no invariant |
| A-10 | `room_password` released through an RPC with a time window | Correct design; the client only displays what the server releases |
| A-11 | `leaderboard` omits `p_game` | The current UI is all-game; the RPC default is `null` (no filter), which matches the screen |
| A-12 | No client retry for a 429 | Retrying automatically can worsen rate limits; the UI asks the user to wait |

## 5. API contract risks

- **PostgREST string filters** are built with `eq.`, `in.(...)`, `ilike.*…*`.
  User input is stripped of `(`, `)` and `,` in search paths
  (`TournamentRepository`, `PlayerRepository.searchPlayers`), preventing filter
  injection. No raw SQL is built on the client.
- **`in.(...)` lists** come from ids already returned by the server, not free
  text.
- **No pagination cursor** on notifications/support; each is bounded by a
  `limit` (60/50) or by a user's own row count, which is acceptable at current
  scale.
- **No idempotency key** is sent from the client even though `idempotency_keys`
  exists server-side. A repeated tap on a mutating button is guarded by ViewModel
  `loading` flags; a second device or a retried request relies on server
  uniqueness constraints. Adding an idempotency header is a next-phase
  improvement.

## 6. Verification status

| Item | Status |
| --- | --- |
| Repository/API review | Complete, source-level |
| Error mapping tests | `SupabaseErrorTest` in CI |
| Eligibility rules | `RegistrationRulesTest` in CI |
| RPC contracts | Cross-checked against live `pg_proc` |
| End-to-end registration/team flow | **Not executed** against a seeded test account |
| Payment/referral flow | **Not executed**; reviewed statically |

No business flow was removed or rewritten; only defect fixes and parallelisation
were applied.
