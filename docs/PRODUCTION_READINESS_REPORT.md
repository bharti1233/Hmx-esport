# HMX Arena — Production Readiness Report

Date: 2026-11-07  
Branch: `feature/hmx-arena-android-production`  
Head commit: `16bf24e` (audit code), docs commit to follow  
Verdict: **Conditionally ready for a staged rollout**, not for an unrestricted
public launch. See §6.

## 1. Readiness summary

| Dimension | State | Evidence |
| --- | --- | --- |
| Build | Green | `Android CI` run `42363668406` (Unit tests, lint, debug APK) |
| Unit tests | 50 passing, 0 failing | Uploaded `hmx-arena-test-reports` (id `13570866764`) |
| Lint | 0 errors, 47 warnings, 1 hint | `lint-results-debug.xml` |
| Debug APK | Produced (21,424,212 bytes) | Artifact `hmx-arena-debug-apk` |
| Release build | Configured (R8 + shrinkResources), not produced in CI | `app/build.gradle.kts` |
| Visual regression | Green: 20/20 captures, switch + persistence verified | `Liquid Glass visual verification` run `42363668400` |
| Backend | Live project healthy, RLS on all tables | `SUPABASE_COMPLETE_AUDIT.md` |
| Security | 2 critical + 3 high fixed; 1 medium open (leaked-password) | `SECURITY_AUDIT_REPORT.md` |
| Adaptive layout | Implemented and unit-tested | `ADAPTIVE_DEVICE_LAYOUT_REPORT.md` |
| Low-end performance | Structural optimisations, not benchmarked | `LOW_END_DEVICE_PERFORMANCE_REPORT.md` |
| Connectivity gate | Implemented, classification tested | `NETWORK_CONNECTIVITY_AUDIT.md` |

## 2. What is production-ready

### 2.1 Build and tooling

- Single `:app` module compiles against SDK 36 with minSdk 26 and targetSdk 36.
- Kotlin 2.2.21, Compose BOM 1.9.4, Hilt + KSP, Room, Retrofit/OkHttp,
  kotlinx.serialization, Coil.
- `io.github.kyant0:backdrop:1.0.0` is pinned. A newer Backdrop would require
  unrelated Kotlin/Compose upgrades and was deliberately not adopted.
- Release build enables R8 and resource shrinking.
- Two GitHub Actions workflows: build/test/lint and emulator screenshots.

### 2.2 Authentication and session

- Email/password signup, verification, sign-in, password reset, sign-out.
- Recovery links are strictly parsed and verified before a session is accepted.
- Refresh tokens persist; access tokens are memory-only.
- Refresh is serialised and only real auth failures (400/401) clear the session.
- Session/onboarding/navigation state is deterministic and survives process
  death.

### 2.3 Backend

- 33 tables, all RLS-enabled; 40 policies reviewed.
- Sensitive columns (`email`, `phone`, `game_uid`, `room_password`) are gated by
  column grants, RLS or a participant-checked RPC.
- Storage policies corrected for team logos; avatars/evidence already correct.
- Public RPC page sizes bounded.
- Audit/financial tables immutable by trigger.

### 2.4 UX and resilience

- Startup connectivity gate with four distinct failure states, event-driven
  retry, no polling, no forced sign-out.
- Offline tournament catalogue fallback for a previously loaded list.
- Adaptive phone/tablet/foldable layout from available width.
- Light/Dark/Sytem theme persisted before Compose starts.
- No crash-prone global state; ViewModels scope their work.

## 3. Blocking issues for an unrestricted launch

| # | Blocker | Owner action | Severity |
| --- | --- | --- | --- |
| 1 | **Leaked-password protection is disabled** in Supabase Auth | Enable in dashboard / Management API | High |
| 2 | **No release signing / Play configuration** in the repo | Add a signing config via CI secrets and a Play track | High (release process) |
| 3 | **No release build in CI** | Add `assembleRelease` + `bundleRelease` to the workflow | Medium |
| 4 | **No instrumented/UI test suite** | Add `androidTest` for auth, gate, nav | Medium |
| 5 | **No Macrobenchmark / baseline profile** | Benchmark module + `baseline-prof.txt` | Medium |
| 6 | **No physical low-end device run** | Verify on OPPO A38 class hardware | Medium |
| 7 | **Tablet/foldable screenshots not produced** | Boot a 600dp+ AVD in the visual workflow | Low |
| 8 | **22 unindexed foreign keys** | One non-destructive index migration | Low (scale) |
| 9 | **`auth_rls_initplan` on 30 policies** | Reviewed `(select auth.uid())` rewrite | Low (scale) |
| 10 | **No staging Supabase project** | Create staging + seed + policy tests | Medium (process) |

None of items 3–10 is a correctness or security defect in the current build; they
are release-engineering and verification gaps. Items 1 and 2 are the true
launch blockers.

## 4. Data safety

- No destructive SQL was executed. The two migrations only replace policies and
  function bodies.
- No production table or row was deleted, truncated or altered by this audit.
- Room uses `fallbackToDestructiveMigration()` for a tournament-only cache; no
  user, payment or credential data is stored there.
- `allowBackup="false"`; no external storage writes.
- Evidence uploads are private (`result-evidence` bucket, signed URLs only).

## 5. Observability and support

- The app maps backend errors to human copy and never shows SQL/tokens.
- Support ticketing and problem reporting exist end-to-end (client + RLS).
- `audit_logs` and `financial_ledger` are append-only.
- There is **no crash reporting / analytics SDK**. For production this means
  crashes are invisible unless reported. This is a deliberate privacy choice but
  a real operational gap; a crash reporter with a privacy review is recommended.

## 6. Verdict

**Conditionally ready for a limited/staged rollout** (closed test or internal
track) after:

1. enabling leaked-password protection;
2. adding release signing and a release build/bundle job;
3. installing the signed build on at least one low-end and one large-screen
   device.

The visual matrix is already green (`42363668400`). It is **not ready for an
unrestricted public launch** until a crash reporter, a release build in CI, and a
low-end benchmark/device pass exist.

## 7. Rollback

- The audit changes are confined to one feature branch and are not merged to
  `main`.
- Reverting commits `16bf24e`, `46639ed` and `c3c799c` restores the pre-audit
  app.
- Backend migrations `0012`/`0013` are additive policy/function replacements;
  the previous definitions are recoverable from
  `supabase/migrations/0004_*` and `0002_functions.sql` if a rollback is ever
  required.
- No data migration was performed, so there is no data rollback step.

## 8. Sign-off checklist

| Gate | Status |
| --- | --- |
| Unit tests | ✅ CI green |
| Lint | ✅ 0 errors |
| Debug APK | ✅ built |
| Release APK/AAB | ⛔ not built in CI |
| Visual matrix | ✅ green (`42363668400`, 20/20) |
| Security review | ✅ critical/high fixed; 1 medium open |
| Backend review | ✅ live audit complete |
| Accessibility | ⚠️ code-level review; no TalkBack/contrast tooling |
| Performance | ⚠️ structural only; no device benchmark |
| Incident/rollback plan | ✅ documented above |
