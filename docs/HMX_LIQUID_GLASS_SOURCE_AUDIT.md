# HMX Arena — Liquid Glass source audit

Audit performed on branch `feature/hmx-arena-android-production` at commit
`7f2ee80` for the visual work started at `d273e4d`.

Library under audit: [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)
(Backdrop). Documentation: <https://kyant.gitbook.io/backdrop>.

This document records what was actually present in the source before and after
the change. A declared dependency alone was **not** accepted as proof.

---

## 1. Build and toolchain baseline

| Item | Value |
| --- | --- |
| Android Gradle Plugin | `8.13.2` |
| Gradle wrapper | `8.14.3` |
| Kotlin | `2.2.21` |
| Compose BOM | `2025.10.01` (Compose UI / Foundation `1.9.4`, Material3 `1.4.0`) |
| compileSdk | `36` |
| minSdk | `26` |
| targetSdk | `36` |
| JVM target | `17` |
| debug application id | `com.hmx.arena.debug` |

No toolchain upgrade and no Compose Multiplatform migration were introduced.
The project remains a native Android Kotlin + Jetpack Compose application.

## 2. Is Backdrop declared and resolved?

**Yes.**

- Version catalog: `gradle/libs.versions.toml`
  - `backdrop = "1.0.0"`
  - `backdrop = { group = "io.github.kyant0", name = "backdrop", version.ref = "backdrop" }`
- Module dependency: `app/build.gradle.kts` → `implementation(libs.backdrop)`
- Maven Central metadata for `io.github.kyant0:backdrop:1.0.0`:

| Variant requirement | Value | Project value | Match |
| --- | --- | --- | --- |
| `org.jetbrains.kotlin:kotlin-stdlib` | `2.2.21` | `2.2.21` | yes |
| `androidx.compose.foundation:foundation` | `1.9.4` | `1.9.4` (BOM) | yes |
| `androidx.compose.ui:ui` | `1.9.4` | `1.9.4` (BOM) | yes |
| `androidx.compose.ui:ui-graphics` | `1.9.4` | `1.9.4` (BOM) | yes |

The newer `1.0.6` artifact requires Kotlin `2.3.10` and Compose `1.10.3`, and
the 2.x line requires Kotlin 2.4 / Compose 1.12. Upgrading would have forced a
whole-toolchain migration, so the project stays on `1.0.0`, which is built
against exactly the versions already in use.

Android CI resolves the dependency successfully and compiles the app
(`:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`).

## 3. Do real screens import and use the Backdrop APIs?

**Yes, through one design-system primitive.** Application code never talks to
the library directly; this keeps the API surface auditable.

| Backdrop API | Where used | Purpose |
| --- | --- | --- |
| `rememberLayerBackdrop` | `HmxBackdrop.kt`, `Glass.kt` | Records the decorative layer and the content layer |
| `Modifier.layerBackdrop` | `HmxBackdrop.kt`, `Glass.kt` | Connects a composable subtree to a recorded layer |
| `Modifier.drawBackdrop` | `LiquidGlass.kt` | Draws the recorded backdrop through a shape with effects |
| `BackdropEffectScope.vibrancy()` | `LiquidGlass.kt` | Saturation lift so the sampled backdrop reads as material |
| `BackdropEffectScope.blur()` | `LiquidGlass.kt` | Real `RenderEffect` blur (`Control` and `Chrome` levels) |
| `BackdropEffectScope.lens()` | `LiquidGlass.kt` | Rounded-rect refraction / edge bending (`Control` and `Chrome`) |
| `Highlight` | `LiquidGlass.kt` | Fine specular rim around the glass |
| `Shadow` | `LiquidGlass.kt` | Soft outer depth |
| `InnerShadow` | `LiquidGlass.kt` | Inner depth on chrome surfaces |
| `onDrawSurface` | `LiquidGlass.kt` | Translucent white surface gradient plus top sheen |

The single primitive is `Modifier.hmxLiquidGlass` in
`app/src/main/java/com/hmx/arena/core/designsystem/LiquidGlass.kt`.
`HmxLiquidGlassSurface` is the shared container built on top of it. Every card,
button, chip, icon button, top bar, bottom bar, sheet and dialog ultimately goes
through that primitive.

## 4. Is a backdrop created and connected to the rendering layers?

**Yes, in two layers.**

### 4.1 Root decorative layer

`HmxGlassHost` in `HmxBackdrop.kt` is mounted once in `HmxRoot.kt`:

```kotlin
HmxGlassHost {
    when { /* auth, onboarding, main app */ }
}
```

It calls `rememberLayerBackdrop { drawRect(canvas); drawContent() }` and attaches
it with `Modifier.layerBackdrop(backdrop)` to a full-screen decorative
background (`HmxLiquidBackdrop`). It then publishes the layer through
`LocalHmxBackdrop`. Cards, buttons, top bars, sheets and dialogs sample it.

The decorative layer is intentionally rich monochrome: white bloom, graphite
cloud, mid-grey mass, two wide arcs, fine rings and a ghost `HMX` wordmark. A
plain white page cannot demonstrate refraction, so this substrate is part of the
implementation, not decoration for its own sake.

### 4.2 Recorded content layer for the bottom bar

`GlassScaffold` in `Glass.kt` creates a second `rememberLayerBackdrop` around the
scrolling page content:

```kotlin
Box(Modifier.fillMaxSize().layerBackdrop(contentBackdrop)) {
    GlassScreenBackground(force = true)
    content(PaddingValues(0.dp))
}
```

The floating bottom bar is drawn **outside** that layer and receives
`contentBackdrop` through a local override. It therefore samples the real list
behind it, while the bar itself is not recorded, which prevents a feedback
loop. This is the pattern from the official “Glass Bottom Bar” tutorial.

## 5. Are blur, lens, highlights and surface rendering actually applied?

**Yes, with capability gating.**

`Modifier.hmxLiquidGlass` uses three levels:

| Level | Effects | Used by |
| --- | --- | --- |
| `Card` | backdrop sampling + surface + highlight + shadow, no blur/lens | list cards, rows |
| `Control` | `vibrancy` + small `blur` + small `lens` | buttons, icon buttons, chips, text fields |
| `Chrome` | `vibrancy` + stronger `blur` + stronger `lens` + inner shadow | top bar, bottom bar, sheets, dialogs, hero surfaces |

Effects are enabled only when:

```kotlin
Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    && !activityManager.isLowRamDevice
```

Otherwise `Modifier.hmxLiquidGlass` returns a translucent fallback with the same
layout, clipping, border and shadow. Below API 31 the library itself makes
`blur` a no-op, and below API 33 it makes `lens` a no-op; the app does not rely
on those calls having an effect.

The bottom bar, top bar and hero cards use `Chrome`. This keeps simultaneous
blur/lens layers to a small number per screen instead of one per list row.

## 6. Is the UI still using ordinary Material cards/surfaces?

No conventional Material `Card`, `ElevatedCard`, `OutlinedCard` or `Surface` is
used for app chrome. The design-system wrappers (`GlassSurface`,
`HmxLiquidGlassSurface`) now route to the real Backdrop primitive.

Remaining Material building blocks, all deliberately inside a glass surface or
used for text/progress only:

- `OutlinedTextField` inside `HmxLiquidGlassTextField` — the field’s container,
  border and background are transparent; the glass is the surrounding
  `Control` surface.
- `TextButton` for dialog actions and inline retry actions.
- `CircularProgressIndicator` for loading.
- `MaterialTheme` typography/shapes only.
- A few custom `AlertDialog`s that embed forms and long legal copy
  (report player, add/edit game account, change email, policy text). These are
  tracked as a remaining limitation; the confirmation dialogs and reset-link
  dialog have already moved to the in-window glass dialog.

No screen renders a white rounded card with a grey border as its primary
surface.

## 7. Is the bottom navigation genuinely refractive?

**Yes.** `app/src/main/java/com/hmx/arena/ui/shell/GlassBottomBar.kt`:

- The container is an `HmxLiquidGlassSurface` with `HmxGlassLevel.Chrome`.
- Its backdrop is the recorded content layer supplied by `GlassScaffold`, so
  blur/lens sample the actual list scrolling under the bar.
- It has a real highlight rim, outer shadow and inner shadow.
- There is no opaque white plate behind it; only the selected indicator is
  opaque black.
- The indicator is an animated black rounded capsule driven by
  `animateFloatAsState` with a non-bouncy spring.
- Inactive icons/labels are neutral grey; selected are white.
- The bar respects `navigationBarsPadding()` and is placed outside the content
  layer.

The debug harness places a black/grey striped block under the bar on purpose:
if blur/lens were absent, the stripes would remain razor-sharp through the
capsule. The captured screenshots are the acceptance evidence.

## 8. What was actually wrong before this change

1. `drawBackdrop` existed, but it was used by a single optional `refractive`
   path. Most screens rendered `GlassSurface` as an opaque/near-opaque white
   container with a grey hairline border and a Material drop shadow.
2. The backdrop recorded by the host was almost plain white (two very faint
   radial washes), so even where `drawBackdrop` ran there was almost nothing to
   blur or refract.
3. The bottom bar was a rounded white container, not a floating capsule sampling
   scrolling content.
4. Glass fills were too opaque (`0xCCFFFFFF` / `0xF2FFFFFF`), which hid the
   backdrop and reproduced the Material-card look.
5. There was no lens/highlight/shadow composition and no debug harness or
   emulator screenshot check, so visual regressions could pass CI unnoticed.

## 9. Audit conclusion

The dependency is declared, resolves against the exact project toolchain, and is
now used by real Compose surfaces on every major screen. A real backdrop is
recorded, connected to the rendering layers, sampled with blur/lens/highlight/
shadow on capable devices, and replaced by an identical-layout fallback
elsewhere. The bottom bar specifically refracts recorded scrolling content.

The remaining work is limited to a few transient form/legal `AlertDialog`s,
documented in `HMX_LIQUID_GLASS_IMPLEMENTATION.md`, plus device-specific tuning
of effect strength on the OPPO A38 class of hardware.
