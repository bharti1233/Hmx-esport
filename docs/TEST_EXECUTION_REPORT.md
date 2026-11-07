# HMX Arena — Test Execution Report

Date: 2026-11-07  
Branch: `feature/hmx-arena-android-production`  
Head commit: `16bf24e`  
Environment: GitHub Actions (`ubuntu-latest`), no local Android SDK/emulator.

## 1. Summary

| Suite | Run | Result |
| --- | --- | --- |
| JVM unit tests | `Android CI` `42363668406` | ✅ 50 tests, 0 failures, 0 ignored |
| Android lint | `Android CI` `42363668406` | ✅ 0 errors (47 warnings, 1 hint) |
| Debug APK | `Android CI` `42363668406` | ✅ built (21,424,212 bytes) |
| Emulator screenshot matrix | `Liquid Glass visual verification` `42363668400` | ✅ 20/20 captures, theme switch + persistence green (§4) |
| Instrumented / UI tests | — | ❌ none exist |
| Macrobenchmark | — | ❌ none exists |
| Backend SQL tests | `supabase/tests/rls_test.sql` | ⚠️ suite exists; not executed here (needs a development database) |

Prior reference runs:

- `Android CI` `42194947871` for `d012ef7` — success (pre-audit baseline).
- `Android CI` `42237574844` for `c3c799c` — **failure**, lint
  `UnusedBoxWithConstraintsScope` at `Glass.kt:160`; fixed in `46639ed`.
- `Android CI` `42238114538` for `46639ed` — success (50 tests, lint, APK).
- `Liquid Glass visual verification` `42238114110` for `46639ed` — **failure**
  in the visual harness: its debug-only `isDark` check was inverted and it
  substring-matched "Dark" to the top-bar icon. Fixed in `16bf24e`.

## 2. JVM unit tests

Artifact `hmx-arena-test-reports` (id `13570866764`) for run `42363668406`:

```
Test Summary
  50 tests
  0 failures
  0 ignored
  0.172 s duration
  100% successful

Packages
  com.hmx.arena.core.designsystem            12 tests  0 failures
  com.hmx.arena.core.util                    31 tests  0 failures
  com.hmx.arena.data.remote                   3 tests  0 failures
  com.hmx.arena.ui.connectivity               4 tests  0 failures

Classes
  AdaptiveLayoutTest                          5
  ThemeTokensTest                             7
  FormatTest                                  6
  RecoveryLinkParserTest                     11
  RegistrationRulesTest                       7
  ValidationTest                              7
  SupabaseErrorTest                           3
  ConnectivityClassificationTest              4
```

### 2.1 What is covered

- **`RecoveryLinkParserTest` (11)** — accepted fragment session links,
  `token_hash` query links, percent-encoding; rejected null/blank, wrong scheme,
  wrong host, wrong path, missing/wrong `type`, forged identical access+refresh
  pair, incomplete pair, no token, and fragment-over-query precedence.
- **`AdaptiveLayoutTest` (5)** — 599/600/839/840 dp boundaries, compact
  full-width, 760/980 dp caps, 1/2/3 grid columns, 96 dp rail.
- **`ConnectivityClassificationTest` (4)** — only `Reachable` → `Ready`;
  `Unavailable` and `Timeout` stay distinct; default is `Checking`.
- **`ThemeTokensTest` (7)** — theme-mode parsing and dark-palette invariants
  (near-black canvas, no bright sheen/edge).
- **`RegistrationRulesTest` (7)** — already-registered, game account, team,
  registration-closed precedence.
- **`ValidationTest` (7)**, **`FormatTest` (6)**, **`SupabaseErrorTest` (3)** —
  input validation, display formatting, and SQL/auth error mapping.

### 2.2 What is *not* covered

- `SessionManager` refresh races and transient-error behaviour (code-reviewed,
  not unit-tested; it needs a coroutine + DataStore harness).
- `ConnectivityMonitor` and `BackendHealthChecker` (Android/OkHttp integration).
- Any repository against a live or fake Supabase.
- Any Compose UI or navigation flow.

## 3. Android lint

Run `42363668406`, step `Android lint` — success.

```
Lint found 0 errors, 47 warnings, 1 hint.
```

Warning breakdown (from `lint-results-debug.xml`, 48 `<issue>` entries):

| Check | Count | Nature |
| --- | --- | --- |
| `GradleDependency` | 17 | Newer dependency versions available (deliberately pinned) |
| `NewerVersionAvailable` | 15 | Same, for libraries |
| `ModifierParameter` | 7 | Compose modifier naming/style hints |
| `AndroidGradlePluginVersion` | 2 | Newer AGP available |
| `OldTargetApi` | 1 | targetSdk 36 vs newer preview |
| `UnusedAttribute` | 1 | manifest attribute |
| `ComposableNaming` | 1 | function naming |
| `DataExtractionRules` | 1 | backup rules hint |
| `ObsoleteSdkInt` | 1 | redundant version check |
| `AutoboxingStateCreation` | 1 | `mutableStateOf<Int>` suggestion |
| `UseOfNonLambdaOffsetOverload` | 1 | Compose perf hint |

`abortOnError = true`, so any lint **error** fails the build. The single prior
error (`UnusedBoxWithConstraintsScope`) was fixed by replacing the unused
`BoxWithConstraints` with `Box` in `GlassScaffold`.

The pinned-dependency warnings are intentional: `io.github.kyant0:backdrop:1.0.0`,
Kotlin 2.2.21 and Compose 1.9.4 are a tested combination, and upgrading them
would require an unrelated migration.

## 4. Visual regression workflow

Workflow: `.github/workflows/android-visual.yml`  
Run: `42363668400` for `16bf24e` — **success**  
Artifact: `hmx-liquid-glass-screenshots` (id `13571132456`)  
Runner: Pixel 6 AVD, SwiftShader software GPU.

The harness:

1. installs the debug APK on a fresh emulator;
2. warms the process, then captures the Light/Dark matrix (home, tournaments,
   matches, leaderboard, profile, settings, components, states, dialogs, offline);
3. checks an in-place theme switch on the Arena screen and that typed search text
   survives;
4. selects **Dark** explicitly in Settings, verifies the "Dark glass" label,
   force-stops the process, relaunches without a `theme` extra, and asserts the
   Dark label is restored from DataStore;
5. uploads every screenshot and UI dump as the
   `hmx-liquid-glass-screenshots` artifact.

Observed assertions in run `42363668400`:

```
=== captured 20 screenshots ===
Theme switch check: dark appearance active on the same Arena screen
Theme switch check: light appearance active on the same Arena screen
Theme switch check: typed search text survived the switch
Persistence check: Dark selected in Settings (attempt 1)
Persistence check: dark appearance restored after process death
```

The screenshots confirm the same Arena screen in Dark with `weekend` still in the
search field, then in Light with the text intact, and Settings reopened in Dark
with the **Dark** segment selected and the "Dark glass" label after process death.

Two earlier failures were in the visual harness, not in product persistence:

- `42194947855` (`d012ef7`) toggled by tap count and assumed every tap landed.
- `42238114110` (`46639ed`) revealed that the debug showcase derived `isDark`
  from the luminance of `HmxColor.Ink`. That token is black in Light, so the
  toggle was inverted: the workflow's first tap was a no-op, and its
  substring search for "Dark" tapped the top-bar icon instead of the Settings
  segment. `16bf24e` derives the state from `hmxColorScheme().isDark`, exact-matches
  the segment and retries the selection.

The workflow therefore proves rendering, in-place switching, form-state
preservation and DataStore persistence on a software-rendered phone AVD. It does
**not** prove frame timing, large-screen layout or a physical low-end device.

## 5. Backend verification

Backend changes were verified by re-reading the live catalog, not by an
automated test suite:

| Check | Method | Result |
| --- | --- | --- |
| All 33 tables have RLS | `pg_class.relrowsecurity` | ✅ |
| Team-logo policies use `objects.name` | `pg_policies` | ✅ |
| Owner delete policy exists | `pg_policies` | ✅ |
| RPC clamp present | `pg_proc` | ✅ |
| Sensitive column grants | `information_schema.column_privileges` | ✅ email/phone not granted |
| Advisor review | MCP `get_advisors` | ✅ no missing client-table policy |

No impersonation run (`SET ROLE authenticated; SELECT …`) was executed against
this production project, and no staging project exists. A regression script does
exist at `supabase/tests/rls_test.sql` (it creates throwaway users/tournament,
asserts the contract, then cleans up); it is intended for a development
database and was deliberately **not** run against production.

## 6. Reproducing

Local (requires Android SDK + JDK 17):

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

CI: push to `feature/hmx-arena-android-production` or run the two workflows from
the Actions tab (`workflow_dispatch` is enabled for the visual workflow).

## 7. Honest limitations

- All results come from a Linux CI runner, not a physical phone.
- The emulator uses SwiftShader, so frame timing and jank from the visual run are
  **not** representative of a real GPU or of the OPPO A38 target.
- No test executes against the production database with a real session; backend
  confidence comes from catalog inspection and source review.
- The 20 visual captures are phone-size (1080×2400, 411 dp wide). Tablet,
  foldable and landscape layouts are covered only by JVM breakpoint tests and
  source review; no large-window AVD exists in the workflow.
- The 50 unit tests are fast JVM tests; they do not replace an instrumented UI
  and auth suite.
