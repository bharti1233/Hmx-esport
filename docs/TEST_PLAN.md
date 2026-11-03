# Test Plan

## 1. Strategy

Because the development device cannot run local Gradle builds, all automated
verification runs on GitHub Actions. The test pyramid is therefore:

1. **JVM unit tests** — pure logic, fast, run on every push.
2. **Android lint** — correctness, accessibility, API-level and manifest checks.
3. **Debug APK assembly** — proves the whole app compiles and packages.
4. **SQL RLS regression suite** — run against the Supabase project to prove
   the security contract independently of the client.
5. **Manual device smoke test** — install the CI artifact and exercise flows.

Instrumented Compose UI tests are not part of this delivery; the CI runner
would need an emulator and the scope did not allow that. This is stated in the
final report rather than implied.

## 2. JVM unit tests

`app/src/test/java/com/hmx/arena/core/util/`

| File | Coverage |
| --- | --- |
| `ValidationTest.kt` | email, username, password strength, OTP, game UID/IGN/level, team name/tag, dispute reason, code normalisation. |
| `FormatTest.kt` | rupee formatting, initials, status/mode labels, invalid timestamps, countdown (past/future/far), relative time. |
| `RegistrationRulesTest.kt` | duplicate registration, missing game account, team requirement, closed registration, message text, slot/price derivation. |

Run: `./gradlew testDebugUnitTest` (CI only).

## 3. RLS / privilege suite

`supabase/tests/rls_test.sql` creates two throwaway auth users and one
throwaway tournament, switches to `authenticated`/`anon`, and asserts:

- own profile readable; another user's public username readable;
- another user's email is denied at the column level;
- updating another profile affects zero rows;
- team creation, membership and invitation visibility;
- tournament registration works for the owner and is blocked for `anon`;
- room credentials are visible to a participant and denied to a non-participant;
- wallet, ledger, notifications, support and referral rows are owner-scoped;
- anonymous leaderboard read works;
- cleanup removes test users, registrations, credentials and the tournament.

The suite ends with `select check_name, outcome, detail from rls_results` and
all rows must read `pass`. It was executed on the live project after every
migration and finished 18/18 passing (29 assertion branches, of which the
happy path executes 18).

## 4. CI pipeline

`.github/workflows/android-ci.yml`:

1. checkout, JDK 17, Android SDK packages,
2. `./gradlew testDebugUnitTest`,
3. `./gradlew lintDebug`,
4. `./gradlew assembleDebug`,
5. upload `app-debug.apk` artifact,
6. upload test reports and build logs on failure.

`lintDebug` has `abortOnError = true`, so lint findings fail the build.

## 5. Manual smoke checklist (device)

Use the latest CI APK.

### Auth
- [ ] Sign up with a new email, receive the code, verify, land on onboarding.
- [ ] Complete profile, add game account, reach Home.
- [ ] Sign out, sign in again, session survives process death.
- [ ] Forgot password sends a link; the deep link opens the reset screen.
- [ ] Invalid/expired link shows the dialog and stays signed out.

### Discovery
- [ ] Home shows real featured/live tournaments from Supabase.
- [ ] Announcements open and render body text.
- [ ] Notifications mark read and update the badge.
- [ ] Search returns players; profile opens; report entry opens a ticket.
- [ ] Airplane mode → catalogue falls back to Room cache or shows retry.

### Tournaments
- [ ] Register for a free solo tournament; slot count increments.
- [ ] Duplicate registration is rejected with a readable message.
- [ ] Cancel registration frees the slot.
- [ ] Rules, participants, schedule and leaderboard tabs load.
- [ ] Paid tournament buttons remain disabled/explained.

### Matches
- [ ] Registered user opens credentials for the live match.
- [ ] Non-participant gets the permission error, not the room code.
- [ ] Submit result with a screenshot; evidence uploads privately.
- [ ] Raise a dispute; the reason length is enforced.
- [ ] Results show verification state.

### Teams
- [ ] Create a team, invite a second account, accept, see shared matches.
- [ ] Transfer ownership, leave team, delete team as owner.
- [ ] Team logo upload works and displays.

### Account
- [ ] Edit profile and avatar; changes persist after reload.
- [ ] Add/edit/remove game account.
- [ ] Support ticket creation and reply appear in the thread.
- [ ] Change email sends confirmation; password reset link works.
- [ ] Account deletion request signs out and is recorded.
- [ ] Legal documents scroll and are readable.

### Visual
- [ ] Pure white background, glass cards, hairline borders, no colour accents.
- [ ] Bottom bar does not overlap content; insets respected.
- [ ] Text scales without clipping at large font sizes.
- [ ] No dead buttons or placeholder screens.

## 6. Not covered

- Emulator/instrumented UI automation.
- Load/performance testing.
- Payment, refund and payout flows (disabled by policy).
- Push notification delivery (not implemented).
- Realtime updates (not implemented).
- Multi-device/tablet layout tuning beyond Compose adaptive defaults.
