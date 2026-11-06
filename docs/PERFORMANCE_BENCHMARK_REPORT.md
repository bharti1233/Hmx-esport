# HMX Arena — Performance Benchmark Report

Date: 2026-11-06
Branch: `feature/hmx-arena-android-production`
Measured commit: `f0117a4` (visual run `42176006279`, artifact `13482694340`)
Comparison build: `106bcfb` (visual run `42078886785`, artifact `13440276603`)
Final verification build: `06f74ad`

All numbers below are copied verbatim from the CI artifact
`artifacts/performance/performance-summary.txt` and the raw
`cold-start-*.txt` / `warm-start-*.txt` / `gfxinfo-home-dark.txt` files. No
number in this report is estimated.

---

## 1. Environment and method

| Item | Value |
|---|---|
| Runner | GitHub Actions `ubuntu-latest` |
| Device | Android emulator, API 34, x86_64 |
| GPU | `-gpu swiftshader_indirect` (software rendering) |
| Display | 1080×2400, 420 dpi, 411×914 dp |
| Build | debug APK (`applicationId com.hmx.arena.debug`) |
| Animations | disabled by the workflow |
| Cold start | `am force-stop` + `am start -W` ×3 |
| Warm start | `KEYCODE_HOME` + `am start -W` ×3 |
| Scroll jank | reset `gfxinfo`, 8 × `input swipe` on dark Home, read `gfxinfo` |

The cold-start measurement launches the **production `MainActivity`** with no
session, i.e. the real splash → welcome/auth path, including the splash hold
until the persisted theme mode loads.

### Why not Macrobenchmark

`androidx.benchmark` requires a separate benchmark module and a `profileable`
release variant. On this runner the emulator has no hardware GPU, so its frame
timings are dominated by SwiftShader and are not representative of a real
phone. Adding a second build variant would not produce trustworthy numbers
here. `am start -W` and `gfxinfo` are reproducible on the same emulator that
produces the screenshot matrix; the limitation is stated rather than hidden.

## 2. Results — measured (f0117a4)

### 2.1 Startup

| Run | Cold `TotalTime` | Warm `TotalTime` |
|---|---|---|
| 1 | 2161 ms | 361 ms |
| 2 | 2292 ms | 321 ms |
| 3 | 2135 ms | 450 ms |
| **Median** | **2161 ms** | **361 ms** |
| Mean | 2196 ms | 377 ms |

Cold `WaitTime` follows `TotalTime` within 1–6 ms in the raw files. All three
cold launches reported `LaunchState: COLD`; all three warm launches reported
`HOT` and "current task has been brought to the front".

### 2.2 Scroll jank (dark Home, software GPU)

| Metric | Value |
|---|---|
| Total frames rendered | 30 |
| Janky frames | 30 (100.00 %) |
| 50th percentile | 200 ms |
| 90th percentile | 350 ms |
| 95th percentile | 500 ms |
| 99th percentile | 500 ms |
| Missed vsync | 14 |
| Slow UI thread | 18 |
| Slow issue draw commands | 30 |
| 50th GPU percentile | 4950 ms |

The GPU histogram reports every one of the 30 frames at the 4950 ms bucket.
That is the SwiftShader fallback path (no hardware rasteriser), not a
measurement of HMX glass on a real GPU. The 30 frames across 8 swipes also show
that the software renderer is dropping most input frames, so percentages and
percentiles describe "software rendering is the bottleneck", not the app's
steady-state performance on a phone.

## 3. Run-to-run comparison

The immediately preceding build (`106bcfb`, run `42078886785`) used the same
harness:

| Metric | `106bcfb` | `f0117a4` |
|---|---|---|
| Cold median | 2651 ms | 2161 ms |
| Cold range | 2576–2706 ms | 2135–2292 ms |
| Warm median | 494 ms | 361 ms |
| Warm range | 273–964 ms | 321–450 ms |
| gfx frames | 36 | 30 |
| gfx p50 / p90 | 300 / 650 ms | 200 / 350 ms |

The header change between these commits removes one 1 dp highlight and one
shadow from a single surface; it is not expected to change startup or scroll
cost. The improvement is most likely shader-cache / emulator-state variance,
so it is **not** claimed as a causal optimization. It is included to show the
noise floor of the instrument.

## 4. Targets

| Target | Result | Verdict |
|---|---|---|
| Tap feedback < 100 ms | Not instrumented in CI. Pressed state is local (`MutableInteractionSource`, 90 ms scale, immediate overlay) and does not wait for a coroutine. | **Not measured** — source-verified only |
| Warm local navigation < 300 ms | Navigation handlers call `nav.navigate` synchronously; destination composition happens immediately. No end-to-end nav-first-frame metric exists in the harness. | **Not measured** |
| No artificial 1 s delay | Repository search: only the real countdown ticker and the 60 s resend cooldown use `delay`. | **Pass** |
| No main-thread network | All Supabase calls are suspend/off-main; no `runBlocking`/`Thread.sleep` in `app/src/main`. | **Pass** |
| No major glass jank | 100 % janky on SwiftShader (software GPU). Card-level blur is disabled and content backdrops are conditional, but this runner cannot prove real-device smoothness. | **Not verified on this runner** |
| Warm start < 300 ms | Median 361 ms on SwiftShader. | **Missed on CI** |

The warm-start medal is 361 ms on the software renderer. On a hardware-GPU
phone the same code path is expected to be faster, but no physical-device
number exists in this project, so this report does not claim one.

## 5. Performance work this phase

These changes are in the code under measurement (all committed in `106bcfb`
and verified by later builds):

| Change | Effect |
|---|---|
| Home: five sequential Supabase reads → `coroutineScope { async ×5 }` | First useful frame is bounded by the slowest call, not the sum |
| Tournament detail, registration, wallet, referral, team, manage team | Same parallelization |
| Onboarding decision cached per session; dashboard composes before the profile refresh | Removes a full round-trip from the signed-in cold path |
| 45 `collectAsState()` → `collectAsStateWithLifecycle()` | No collection while a screen is stopped |
| Card glass = vibrancy only (no per-row RenderEffect) | Removes the main scroll cost from long lists |
| Content backdrop recorded only when a bottom bar needs it | Avoids a duplicate recorded layer on non-tab screens |
| Chrome blur 16 dp / lens 18 dp; Control 5 dp / 9 dp | Reduced from 18–20 dp / 24 dp |
| Dark decorative bloom/arc/cloud reduced | Less overdraw and no grey streak |
| Submit guards on all write paths | Duplicate taps do not enqueue duplicate requests |

## 6. Where the numbers can be improved

1. Add a `benchmark` module with a `profileable` release variant and run
   Macrobenchmark on a hardware-GPU device or a KVM emulator with host GPU.
2. Record `tap → first frame` with `FrameMetrics`/`JankStats` in a debug-only
   screen; the current harness does not expose that transition.
3. Add a Baseline Profile for the cold start path.
4. Keep using the software-emulator run only for the screenshot matrix, where
   it is deterministic; do not treat its frame times as a product SLO.

The honest summary: **startup, memory, and network gating were improved and
the glass effect budget was reduced; the CI emulator cannot certify a 300 ms
warm navigation or a 100 ms tap-to-frame target.**
