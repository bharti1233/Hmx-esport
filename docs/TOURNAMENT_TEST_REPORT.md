# Tournament Test Report

Date: 2026-11-07
Branch: `feature/hmx-admin-tournament-match-management`
Commits tested: `64cf2aa`, `8fab042`, `462e4cc` (cancelled), `49096ee`, `dd7f2c1`
Database: live Supabase project `hmxesport` (`ahsvxxhaowwhqermruhz`)
Migrations: `0014`, `0015`, `0016`, `0017`

## 1. Test environments

| Layer | Environment | Notes |
| --- | --- | --- |
| Database / RLS / RPC | Live project, rolled-back transactions | No production data was permanently changed |
| Database grants | Live project after `0017` | Read-only inspection + rolled-back role tests |
| Android build / unit tests / lint | GitHub Actions `ubuntu-latest`, JDK 17 | `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug` |
| Device / emulator UI | **Not available** | No SDK/emulator in this environment; no admin account |

All SQL test suites use `begin; ... rollback;` and a temporary result table. No
migration in this report was destructive to existing production rows.

## 2. Backend functional tests (rolled back)

### 2.1 Categories and tournaments

| # | Test | Expected | Result |
| --- | --- | --- | --- |
| 1 | Create a category through `admin_upsert_category` | Row returned with unique slug | **Passed** (`test-cat-a1b015`) |
| 2 | Create a tournament linked to that category | `category_id` set | **Passed** |
| 3 | Rename the category, read it through the tournament FK | New name visible | **Passed** (`Test Category Renamed`) |
| 4 | Create a tournament with an inactive category | `category_unavailable` | **Passed** (migration suite) |
| 5 | Lower `max_slots` below `slots_filled` | `capacity_below_registrations` | **Passed** (migration suite) |
| 6 | Registration close before open | `invalid_registration_window` | **Passed** (migration suite) |
| 7 | Start before registration close | `start_before_registration_close` | **Passed** (migration suite) |
| 8 | Delete a referenced category | `category_in_use` | **Passed** (migration suite) |
| 9 | Move a completed tournament back | `completed_tournament_is_locked` | **Passed** (migration suite) |
| 10 | Delete a tournament with registrations | `tournament_has_registrations` | **Passed** (migration suite) |

### 2.2 Matches and match registration

| # | Test | Expected | Result |
| --- | --- | --- | --- |
| 11 | Create a standalone match | Row returned | **Passed** (`Rolled Back Match`) |
| 12 | Create a match inside a tournament | Inherits category/mode/type | **Passed** |
| 13 | Register a player for a published match | Registration + `slot_no = 1`, `slots_filled = 1` | **Passed** (`slot=1 filled=1`) |
| 14 | Register the same player twice | `already_registered` | **Passed** |
| 15 | Register when `max_slots = 1` and full | `match_full` | **Passed** |
| 16 | Cancel own match registration | `slots_filled` 1 → 0 | **Passed** |
| 17 | Cancel the same registration twice | No error, no second decrement | **Passed** (idempotent) |
| 18 | Cancel another user's registration as a player | `not_allowed` | **Passed** |
| 19 | Delete a match with registrations | `match_has_registrations` | **Passed** (migration suite) |
| 20 | Sensitive edit on a live match without confirmation | `match_locked_confirmation_required` | **Passed** (migration suite) |
| 21 | Same edit with `p_confirm_locked = true` | Update succeeds | **Passed** (migration suite) |

### 2.3 Tournament registration cancellation

| # | Test | Expected | Result |
| --- | --- | --- | --- |
| 22 | Register for a tournament | `slots_filled = 1` | **Passed** |
| 23 | Cancel the registration | `slots_filled` 1 → 0, row kept `cancelled` | **Passed** |
| 24 | Cancel the same tournament registration twice | No error | **Passed** |
| 25 | Cancel another player's registration as a player | `not_allowed` | **Passed** |
| 26 | Cancel a tournament registration that holds match slots | Match `slots_filled` decremented | **Passed** (migration suite) |
| 27 | Void RPC response handling on Android | No deserialisation error | **Passed** by compile + code review; see §4 |

## 3. Security tests

### 3.1 RLS / direct writes (role `authenticated`, player JWT)

| # | Attempt | Result |
| --- | --- | --- |
| 28 | `UPDATE matches` | Blocked: `permission denied for table matches` |
| 29 | `DELETE FROM tournaments` | Blocked: `permission denied for table tournaments` |
| 30 | `INSERT INTO match_registrations` | Blocked: `new row violates row-level security policy` |
| 31 | `INSERT INTO categories` | Blocked: `permission denied for table categories` |
| 32 | `UPDATE game_modes` | Blocked: `permission denied for table game_modes` |
| 33 | `SELECT audit_logs` as a player | 0 rows (admin policy) |

### 3.2 Admin authorisation

| # | Test | Expected | Result |
| --- | --- | --- | --- |
| 34 | `is_admin()` as a player | `false` | **Passed** |
| 35 | Admin RPC as a player | `not_admin` | **Passed** |
| 36 | Admin RPC as `anon` after `0017` | `permission denied for function` | **Passed** |
| 37 | `register_for_match` as `anon` after `0017` | `permission denied for function` | **Passed** |
| 38 | Admin RPC as an authenticated admin | Success | **Passed** (`grant-test-fbf140`) |
| 39 | Admin write creates an audit row with actor | Row present, `category.create` | **Passed** |
| 40 | Admin reads `audit_logs` | SELECT succeeds | **Passed** |
| 41 | Player reads `audit_logs` | 0 rows | **Passed** |

### 3.3 Storage policies

| # | Test | Expected | Result |
| --- | --- | --- | --- |
| 42 | Buckets `category-logos`, `match-assets` exist | Public, correct size/MIME limits | **Passed** (`storage.buckets`) |
| 43 | Player insert into `match-assets` | RLS rejection (`is_admin()`) | **Passed** by policy inspection; no player token used for an actual object upload |
| 44 | Admin upload path `matches/<id>/banner-*` | Allowed by `with check` | **Passed** by policy inspection |
| 45 | Upload to a disallowed first folder | Rejected by `with check` | **Passed** by policy inspection |

Actual byte uploads were not attempted because no admin session exists in this
environment.

## 4. Android CI

Workflow: `.github/workflows/android-ci.yml`
Steps: `:app:testDebugUnitTest` → `:app:lintDebug` → `:app:assembleDebug`.

| Run | Commit | Result | Notes |
| --- | --- | --- | --- |
| [42380938076](https://github.com/bharti1233/Hmx-esport/actions/runs/42380938076) | `64cf2aa` | **Failure** | Compile errors: missing serializer import, `Any` typing for optional JSON ids, `DateTimeField` trailing-lambda parameter order |
| [42381380161](https://github.com/bharti1233/Hmx-esport/actions/runs/42381380161) | `8fab042` | **Success** | Unit tests, lint and debug APK all `BUILD SUCCESSFUL` |
| [42381738812](https://github.com/bharti1233/Hmx-esport/actions/runs/42381738812) | `462e4cc` | Cancelled | Superseded by the final run through the workflow concurrency group |
| [42382037477](https://github.com/bharti1233/Hmx-esport/actions/runs/42382037477) | `49096ee` | **Success** | Match-detail artwork + full docs; unit tests, lint and APK passed |
| [42382903273](https://github.com/bharti1233/Hmx-esport/actions/runs/42382903273) | `dd7f2c1` | **Success** | Final admin match card polish; unit tests, lint and APK passed |

The `dd7f2c1` final code run produced:

```
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 1m
> Task :app:lintDebug
BUILD SUCCESSFUL in 1m 7s
> Task :app:assembleDebug
BUILD SUCCESSFUL in 20s
```

The debug APK artifact `hmx-arena-debug-apk` was uploaded for the successful
runs.

## 5. What was not tested

| Area | Reason |
| --- | --- |
| Tapping through the admin UI end to end | Production has no `admin`/`moderator` row yet; granting one is intentionally manual |
| Cancellation/registration on a physical device | No Android SDK, emulator or device in this environment |
| Two simultaneous HTTP registrations for the last slot | Only the SQL layer is reachable; the row lock is verified by inspection and by the serial transaction tests |
| Real image upload through Storage | Requires an admin session and network upload from the app |
| Visual regression in Light/Dark/System | No screenshot/emulator harness for these screens in this environment |
| Realtime updates | The player screens use post-success refetch; a realtime subscription test would need a live client |

These gaps are stated rather than claimed as passed.

## 6. Known failures found and fixed during testing

1. **Void RPC deserialisation** — cancellation reported an error after the
   server had committed. Fixed with `rpcVoid` / `Response<Unit>`.
2. **Admin audit read failed** — `audit_logs` had an admin policy but no
   `authenticated` SELECT grant. Fixed in `0016`.
3. **Lookup tables had unused write grants** — direct writes were already
   blocked by RLS; `0016` removed the grants as defence in depth.
4. **Admin RPCs callable by anon** — safe at runtime (`not_authenticated`) but
   flagged by the advisor; `0017` revoked the anon/PUBLIC grants.
5. **Android compile errors** in `64cf2aa` — fixed in `8fab042` (serializer
   import, explicit `JsonNull`, `DateTimeField` parameter order).

## 7. Verdict

- All executable backend paths pass their expected success/failure cases.
- RLS, table grants and function grants block every direct player write tested.
- The Android module compiles, passes unit tests, passes lint and produces a
  debug APK on CI.
- The remaining untested items require an admin account, a device/emulator or a
  live client and are explicitly listed above.
