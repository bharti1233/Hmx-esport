# HMX Arena — Performance Audit

Date: 2026-11-05
Branch: `feature/hmx-arena-android-production`
Targets: feedback < 100 ms, warm local navigation < 300 ms, no artificial 1 s
delay, no main-thread network, no major glass jank.

---

## 1. Method

The app has no local Android SDK build on the developer machine, so every
measurement runs on the GitHub Actions API 34 x86_64 emulator:

- `am start -W` for cold/warm startup (`TotalTime`, `WaitTime`);
- `dumpsys gfxinfo` while swiping the Home glass list;
- the visual workflow starts each screen cold and captures a screenshot, which
  also exercises the full composition path (`am-start-*.txt` artifacts).

**Why not Macrobenchmark:** adding it would require a separate benchmark module,
a `profileable` release variant and `androidx.benchmark`. On this runner the
emulator renders with SwiftShader (software GPU); Macrobenchmark frame timings
there are not representative of the OPPO A38, and the module would add a second
build variant without producing trustworthy numbers. `am start -W` + `gfxinfo`
give honest, reproducible measurements on the same hardware we already use for
the screenshot matrix. The limitation is documented rather than hidden.

## 2. Bottlenecks found and fixed

### 2.1 Sequential network loads (the ~1 s interaction delay)

| Screen | Before | After |
|---|---|---|
| Home | `my_profile` → `featured` → `my_matches` → `announcements` → `notifications` (sum of five round-trips) | `coroutineScope { async ×5 }` (max of five) |
| Tournament detail | tournament → rules → participants → matches → registration | tournament, then four concurrent reads |
| Registration | registrations → team → game accounts | three concurrent reads after the tournament |
| Wallet | wallet → ledger → flags (`Triple` arguments evaluate in order) | three concurrent reads |
| Referral | profile → claims | concurrent |
| Team | team → invitations | concurrent |
| Manage team | detail → invitations | concurrent |

With a typical 150–350 ms Supabase round-trip, the old Home path spent roughly
0.7–1.5 s before showing any content. The new path is bounded by the slowest
single call.

### 2.2 Network-gated startup

`SignedInGate` waited for `my_profile` before composing the navigation host, so
every cold start showed a full-screen spinner for one round-trip. The onboarding
decision is now cached per signed-in user in the session DataStore, refreshed on
every launch. Returning users see the dashboard on the first composed frame; the
background refresh still corrects a stale cache. A first-ever sign-in keeps the
gate because there is no local decision to trust.

The cached flag is cleared on sign-out, so a different account never inherits
another player's onboarding state.

### 2.3 Glass rendering cost

| Before | After |
|---|---|
| Card level ran `blur(3–4 dp)` for every card, i.e. one RenderEffect per list row | Card level is vibrancy-only; blur/lens only on Control and Chrome |
| `GlassScaffold` recorded a full content backdrop on **every** screen | Recorded only when a bottom bar needs to refract content |
| Control blur 6–7 dp + lens 12 dp; Chrome 18–20 dp + lens 24 dp | Control 5 dp + 9 dp; Chrome 16 dp + 18 dp |
| Dark backdrop drew a 10 % white bloom with 78 % screen radius | 3.5 % neutral bloom |
| Outer shadows up to 36 % | 30 % / 42 % (chrome) |
| Deck of 9 dp / 20 dp shadows kept | Kept; shadows are static draw calls, not RenderEffects |

The page background is a static composition: it does not animate, does not
re-record on scroll, and is shared by every glass surface. No infinite animation
runs in the backdrop or chrome. `GlassShimmer` is used only as a loading
placeholder.

### 2.4 Recomposition and lifecycle

- 45 `collectAsState()` call sites were replaced with
  `collectAsStateWithLifecycle()`, so screens stop collecting when stopped.
- Lazy lists already used stable keys (`it.id`, `match.id`, `rank + entityId`);
  the audit found no unkeyed `items` in the main screens.
- Theme tokens are `@Immutable` data classes; `HmxColor` is a read-only
  `CompositionLocal` facade. Switching mode recomposes colours in place with no
  Activity recreation and no navigation reset.
- No heavy calculation runs inside composition; formatting helpers are cheap and
  pure.

### 2.5 Immediate feedback and duplicate prevention

- Buttons scale on press, icon buttons and cards draw a pressed overlay above
  the glass, and the clickable modifier receives the touch immediately. None of
  these paths wait for a coroutine.
- The theme selector updates its in-memory `StateFlow` first and writes
  DataStore asynchronously; the UI switches in the same frame.
- Submit guards (`loading` / `busy` / `saving`) were added to sign in, sign up,
  verification, forgot password, recovery, onboarding, create team, invite,
  submit result, dispute and edit profile. Double taps now produce one request.
- Buttons are disabled only while their own operation runs. Navigation buttons
  are never disabled by unrelated loading.
- Auth and registration still wait for the backend before declaring success.
  No success state or fabricated data was added.

## 3. Artificial delays

Repository search for `delay(`, `runBlocking`, `Thread.sleep`,
`SystemClock.sleep`, `GlobalScope`, `withContext` found:

- `TickingCountdown` — a real 1 s clock ticker (intended);
- resend-verification cooldown — a real 60 s server-policy cooldown.

There is no navigation debounce or artificial one-second delay. The perceived
delay came from the sequential network work described above.

## 4. Dispatchers and blocking work

- All Supabase calls are `suspend` Retrofit/OkHttp calls; they execute off the
  main thread and resume on the Main dispatcher.
- `SessionManager.refreshBlocking()` is called only from the OkHttp
  `Authenticator` thread, never from the UI.
- Room reads/writes for the tournament cache are `suspend` DAO methods.
- Image loading uses Coil's `AsyncImage`, which is asynchronous.
- No disk I/O or JSON parsing of large payloads happens in a composable.

## 5. Targets

| Target | Status |
|---|---|
| Tap feedback < 100 ms | Press state is local and drawn on the next frame; measured visually, not asserted numerically (software-rendered CI frames are slower than a real device). |
| Warm local navigation < 300 ms | Navigation handlers call `nav.navigate` synchronously; destination composition shows a skeleton until data arrives. Measured values in the benchmark report. |
| No artificial 1 s delay | Pass — no delay exists outside real timers. |
| No main-thread network | Pass — all calls are suspend/off-main. |
| No major glass jank | Pass on the reduced effect budget; gfxinfo percentiles in the benchmark report. |
| Responsive on low-end devices | Effects disabled below API 31 and on `isLowRamDevice`; identical layout without RenderEffect cost. |

## 6. Remaining limitations

- The CI emulator uses SwiftShader, so absolute frame times are pessimistic
  compared with the OPPO A38's GPU.
- The screenshot harness renders production components with static demo data
  because CI has no authenticated backend session. Startup is measured on the
  real `MainActivity`, but dashboard network latency is bounded by the
  concurrency fix rather than reproduced on CI.
- `gfxinfo` total-frame numbers include the deliberate 12 s settle period only
  if a frame is produced; the swipe loop runs after reset, so the percentages
  describe scrolling, not first composition.
