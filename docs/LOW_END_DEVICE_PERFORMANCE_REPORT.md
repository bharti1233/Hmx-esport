# HMX Arena — Low-End Device Performance Report

Date: 2026-11-07  
Branch: `feature/hmx-arena-android-production`  
Commit: `16bf24e`  
Target profile: OPPO A38 class — 4 GB RAM, entry-level 12 nm SoC, Android 13/14,
720×1612 (~269 ppi), Mali-G52 MC2 GPU.

> **Evidence rule.** This report records what was *changed* and *why it should
> reduce work*. It does not claim measured frame times or battery numbers for the
> OPPO A38, because no physical device and no Macrobenchmark module were
> available in this environment. The only timing numbers quoted are the
> previously recorded CI cold/warm starts, and CI renders through SwiftShader
> (software GPU), so they are not representative of the A38.

## 1. What was expensive before

1. **Every glass surface paid for a RenderEffect.** Cards are the most common
   composable on scroll; each one requested blur/lens/vibrancy against the
   recorded backdrop.
2. **A second full-window backdrop layer per screen.** `GlassScaffold` recorded
   the scrolling content again so the bottom bar could refract it, in addition to
   the root backdrop.
3. **Full decorative backdrop on every device.** Extra radial draws (bloom,
   cloud, mid mass, arcs, watermark) ran even on low-RAM hardware.
4. **Default Coil caches.** Coil's default memory cache is a percentage of the
   heap and its default disk cache is 2 % of free space; on a 4 GB device this
   is larger than the app needs for avatars and banners.
5. **Sequential independent network reads** on the two hottest screens
   (home dashboard and team detail), so the player waited for two round trips.

## 2. Changes made

### 2.1 Glass capability tiering

`liquidGlassTier(context)` in `core/designsystem/HmxBackdrop.kt`:

| Tier | Condition | Effect |
| --- | --- | --- |
| `Full` | API ≥ 31, not low-RAM, `memoryClass > 256` | Real Backdrop glass: vibrancy, chrome blur + lens, control blur + lens, specular highlight, shadows |
| `Reduced` | API ≥ 31 and (`isLowRamDevice` or `memoryClass <= 256`) | Chrome keeps a **0.6×** blur; cards and controls use the static gradient fallback; the decorative backdrop drops the cloud, mid mass, second arc and watermark passes; the bottom bar snaps instead of spring-animating |
| `Disabled` | API < 31 | Static gradient fallback for every surface; layout and interaction identical |

No device model is checked anywhere. `isLowRamDevice` is the authoritative
signal; the `memoryClass` check catches budget devices that do not set the flag.

`Modifier.hmxLiquidGlass` returns early to the fallback path for
`Reduced && level != Chrome`, so a scrolling list of 60 tournament cards creates
zero per-card RenderEffects on the target device.

### 2.2 One backdrop instead of two

- Root `HmxGlassHost` records the decorative backdrop once.
- `GlassScaffold` only records a second content layer on the `Full` tier **and**
  only when a bottom bar exists. On `Reduced` the chrome samples the root layer.
- `GlassScreenBackground()` is a no-op when a host backdrop is present, so
  screens no longer repaint the same gradient behind the root.

### 2.3 Cheaper decorative canvas

On `Reduced`, `HmxLiquidBackdrop` keeps the near-black/white canvas, the
single top-right bloom and one wide low-contrast arc, and skips the diffuse
cloud, mid mass, the second arc and the watermark. That removes several large
radial/arc draws per frame while the glass chrome still has enough structure to
read as glass.

### 2.4 Image pipeline

`HmxArenaApp : ImageLoaderFactory`:

- memory cache `10 %` of heap on low-RAM, `15 %` otherwise;
- disk cache hard-capped at **32 MB** in `cacheDir/hmx_images`;
- `crossfade(false)` — no extra animation on every list row;
- Coil still decodes to on-screen size (unchanged behaviour).

### 2.5 Fewer round trips

`TournamentRepository.myMatches()` and `TeamRepository.teamDetail()` now run
independent Supabase reads with `coroutineScope { async { … } }`. The dependent
lookups (profiles after members, tournament join after both lists) stay
sequential because they need the previous result. On a high-latency mobile
connection this removes one full round-trip from each screen.

### 2.6 List virtualisation

The tournament catalogue is a `LazyVerticalGrid` (1/2/3 columns by window
class). Only visible rows compose; the previous `LazyColumn` was already lazy,
so the win here is fewer scroll passes on tablets rather than a change on phones.

## 3. What a low-end device now avoids

For a typical home → tournament list → detail session on the `Reduced` tier:

- no per-card `RenderEffect` (blur/lens) during scroll;
- no second full-window content backdrop;
- no cloud, mid-mass, second-arc or watermark backdrop passes;
- no crossfade animation per image;
- one fewer network round trip on the dashboard and on team detail;
- no spring animation on the navigation rail capsule.

## 4. Memory behaviour

| Component | Bound |
| --- | --- |
| Coil memory cache | 10 % of heap on low-RAM (≈ 4 GB device: tens of MB, not a fixed claim) |
| Coil disk cache | 32 MB hard cap |
| Room | Tournament catalogue only (`CachedTournament`), one table |
| Session | Access token in memory; refresh token + ids in DataStore |
| Bitmaps | Coil decodes to on-screen size; no manual full-size bitmap retention found in the audit |

No `BitmapFactory` call, no unbounded in-memory list of images, and no
application-level static cache of models was found.

## 5. Measurement plan (not yet executed)

To turn the mechanism above into verified numbers, the next phase needs:

1. A `benchmark` module with Macrobenchmark `StartupTimingMetric`, `FrameTimingMetric`
   and `TraceSectionMetric`, run on a physical OPPO A38 (or an equivalent
   `isLowRamDevice` emulator with a real GPU).
2. A baseline profile (`baseline-prof.txt`) generated with
   `BaselineProfileRule` and shipped in `app/src/main`, which the current
   project does not yet have.
3. Release-build (R8) measurement; debug builds are not representative.
4. A cold/warm start matrix before and after the tiering change on the same
   device and build type.

Until then, the latest CI evidence is the visual run `42363668400`: cold start
`TotalTime` **1946 / 1789 / 1978 ms** (median **1946 ms**) and warm start
**293 / 298 / 325 ms** (median **298 ms**) on the GitHub Actions Pixel 6 AVD
with SwiftShader. The same run reported 37 rendered frames, all classified
janky, because the software renderer cannot hit frame deadlines. None of these
figures is an A38 measurement, and none is presented as an improvement over the
earlier `2161 ms` / `361 ms` run: the two runs used a software-rendered emulator,
not the same hardware or build type.

## 6. Risks and trade-offs

- **Reduced tier looks flatter.** Cards lose the live blur but keep the same
  gradient, hairline and shadow tokens, so hierarchy is preserved. This is a
  deliberate battery/jank trade-off, not a visual redesign.
- **API < 31 has no live glass.** Backdrop requires Android 12+; the fallback is
  intentional and was already the case.
- **`crossfade(false)`** makes image loads appear instantly rather than fading.
  That is a small perceived-polish change accepted for scroll cost.
- **Parallel reads** increase peak concurrent connections by one per screen. Only
  two requests run at once and OkHttp pools them, so this is safe.

## 7. Verification status

| Item | Status |
| --- | --- |
| Tier selection logic | Implemented; no device-model checks |
| Compilation | GitHub Actions `Android CI` `42363668406` for `16bf24e` |
| Unit tests | JVM suite passes in CI |
| On-device frame timing | **Not measured** — no physical device / Macrobenchmark |
| A38 cold/warm start | **Not measured** |
| Baseline profile | Not present |

This report therefore claims a **structural** reduction in GPU/CPU/network work,
not a measured percentage improvement.
