# HMX Arena — Liquid Glass implementation

Implementation branch: `feature/hmx-arena-android-production`.

The visual layer is built on the Kyant0 **Backdrop** library
(`io.github.kyant0:backdrop:1.0.0`). The library provides low-level drawing and
effects only; all HMX components below are our own native Compose wrappers.

---

## 1. Architecture

```
HmxRoot
└─ HmxGlassHost                         (root decorative backdrop → LocalHmxBackdrop)
   └─ HmxRoot navigation / auth / main
      └─ HmxPage
         └─ GlassScaffold               (records page content → content backdrop)
            ├─ HmxLiquidGlassTopBar     (Chrome glass, samples root backdrop)
            ├─ page content
            │  └─ HmxLiquidGlass*       (Card / Control glass, samples root backdrop)
            └─ HmxLiquidGlassBottomBar  (Chrome glass, samples recorded page content)
```

Two layers, never nested:

1. **Root decorative layer** — recorded once by `HmxGlassHost`. It contains only
   the monochrome decorative background, never UI. Cards, buttons, top bars and
   dialogs sample it.
2. **Content layer** — recorded per screen by `GlassScaffold`. It contains the
   opaque background plus the scrolling page. The bottom bar is drawn outside
   it and samples it, so the bar refracts real content and does not refract
   itself.

`GlassScreenBackground()` is a no-op when the root host is present, so the
visible background and the pixels sampled by glass are the same composition. The
content layer forces its own copy because it must be opaque before recording.

## 2. The single glass primitive

`app/src/main/java/com/hmx/arena/core/designsystem/LiquidGlass.kt`

```kotlin
@Composable
fun Modifier.hmxLiquidGlass(
    shape: Shape,
    level: HmxGlassLevel = HmxGlassLevel.Card,
    fill: Color = HmxColor.Glass,
    elevation: Dp = 10.dp,
): Modifier
```

On capable devices it chains:

```kotlin
drawBackdrop(
    backdrop = LocalHmxBackdrop.current,
    shape = { shape },
    effects = {
        vibrancy()
        blur(...)      // Control / Chrome only
        lens(...)      // Control / Chrome only
    },
    highlight = { Highlight.Default.copy(...) },
    shadow = { Shadow(...) },
    innerShadow = if (chrome) { { InnerShadow(...) } } else null,
    onDrawSurface = {
        // translucent white vertical gradient + top specular sheen
    },
)
```

Fallback: below API 31, on `ActivityManager.isLowRamDevice`, or when no host
backdrop is mounted, the same function returns a clipped translucent gradient
with a white edge and a soft shadow. Layout, hit targets and typography are
identical.

### Effect levels

| Level | Blur | Lens | Inner shadow | Intended use |
| --- | --- | --- | --- | --- |
| `Card` | none | none | no | list rows and content cards |
| `Control` | 6 dp | 8 / 12 dp | no | buttons, chips, icon buttons, fields |
| `Chrome` | 18 dp | 18 / 24 dp | yes | bars, sheets, dialogs, hero surfaces |

Limiting blur/lens to `Control`/`Chrome` and keeping list rows at `Card` avoids
a shader per row.

## 3. Component library

`app/src/main/java/com/hmx/arena/ui/common/HmxGlassComponents.kt`

| Required component | Implementation | Glass level |
| --- | --- | --- |
| `HmxLiquidGlassCard` | `HmxLiquidGlassSurface` + `Column` | `Card` (or `Chrome` when `refractive`) |
| `HmxLiquidGlassButton` | `GlassButton` (pill, pressed scale) | `Control` / `Card` for ink |
| `HmxLiquidGlassOutlinedButton` | `GlassButton(Secondary)` | `Control` |
| `HmxLiquidGlassIconButton` | `GlassIconButton` (circle) | `Control` |
| `HmxLiquidGlassBottomBar` | shell `HmxLiquidGlassBottomBar` | `Chrome` |
| `HmxLiquidGlassTopBar` | `GlassTopBar` (floating pill) | `Chrome` |
| `HmxLiquidGlassBottomSheet` | in-window `AnimatedVisibility` + glass surface | `Chrome` |
| `HmxLiquidGlassDialog` | in-window scrim + glass surface | `Chrome` |
| `HmxLiquidGlassTextField` | `GlassTextField` wrapped in glass | `Control` |
| `HmxLiquidGlassSearchBar` | search field with clear action | `Control` |
| `HmxLiquidGlassChip` | `GlassChip` | `Control` / `Card` selected |
| `HmxLiquidGlassTournamentCard` | `TournamentCard` | `Card` |
| `HmxLiquidGlassMatchCard` | `MatchCard` | `Card` |
| `HmxLiquidGlassPlayerCard` | avatar + profile row | `Card` |
| `HmxLiquidGlassStatCard` | value/label/caption card | `Card` |

Additional product components: `HmxLiquidGlassTeamCard`,
`HmxLiquidGlassLeaderboardItem`, `HmxLiquidGlassNotificationCard`,
`HmxLiquidGlassLoading`, `HmxLiquidGlassEmptyState`,
`HmxLiquidGlassErrorState`.

Compatibility aliases (`HmxGlassCard`, `HmxGlassButton`, `HmxGlassBottomBar`,
…) delegate to the new implementations. Existing screens migrate without a
flag day, and there is only one rendering path.

## 4. Bottom navigation (reference component)

`app/src/main/java/com/hmx/arena/ui/shell/GlassBottomBar.kt`

- Floating translucent pill, `Chrome` level, real backdrop sampling.
- No opaque plate behind the capsule.
- Black selected capsule animated with
  `animateFloatAsState(spring(dampingRatio = NoBouncy, stiffness = MediumLow))`.
- Five tabs: Home, Arena, Matches, Ranks, Profile.
- White icons/labels on the black indicator, neutral grey otherwise.
- `navigationBarsPadding()` and horizontal/vertical inset padding.
- Drawn outside the recorded content layer, so it refracts the list.

Screens reserve `124.dp` bottom content padding on the five tab destinations so
the last row can scroll clear of the floating capsule.

## 5. Visual language

Strictly monochrome tokens in
`app/src/main/java/com/hmx/arena/core/designsystem/Theme.kt`:

```kotlin
Glass       = 0x52FFFFFF   // content card fill
GlassStrong = 0x8AFFFFFF
GlassMuted  = 0x24FFFFFF
GlassChrome = 0x3DFFFFFF   // bars / sheets / dialogs
GlassControl= 0x66FFFFFF   // buttons / chips / fields
GlassEdge   = 0x99FFFFFF
Ink         = 0xFF0B0B0C
Canvas      = 0xFFFDFDFE
```

Only white, near-white, light grey, neutral grey, black and transparent white
are used. No purple, blue, neon or multi-colour gaming gradients were added.
Existing semantic `Danger` stays a desaturated dark red for destructive text
only.

The decorative backdrop uses a white bloom, graphite cloud, grey mass, two arcs,
two rings and a ghost `HMX` wordmark. It is static: a perpetual animation would
keep offscreen layers invalidating every frame and would put the sampled and
visible layers out of phase. Interaction motion lives in the bottom-bar
indicator and button press scale instead.

## 6. Screens and surfaces updated

All screens inherit the new primitives, because `HmxPage`, `GlassSurface`,
`GlassButton`, `GlassIconButton`, `GlassChip`, `GlassTextField`, `TournamentCard`
and `MatchCard` are shared. The following were reviewed and migrated:

| Area | Files |
| --- | --- |
| Home / dashboard | `home/HomeScreen.kt` |
| Discovery, announcements, search, player detail | `home/DiscoverScreens.kt` |
| Arena list, tournament detail | `tournament/TournamentScreens.kt` |
| Rules, bracket and tournament sub-pages | `tournament/TournamentSubScreens.kt` |
| Registration and my registrations | `tournament/RegistrationScreens.kt` |
| Matches list and match detail | `match/MatchScreens.kt` |
| Leaderboard | `leaderboard/LeaderboardScreen.kt` |
| Profile, game accounts, teams entry | `profile/ProfileScreens.kt` |
| Teams and team detail | `team/TeamScreens.kt` |
| Wallet and transactions | `wallet/WalletScreens.kt` |
| Settings | `settings/SettingsScreens.kt` |
| Notifications | `home/DiscoverScreens.kt` + common notification card |
| Support | `settings/SettingsScreens.kt` |
| Referral | `referral/ReferralScreen.kt` |
| Auth, recovery, policy dialogs | `auth/AuthScreens.kt` |
| Onboarding / complete profile | `onboarding/CompleteProfileScreen.kt` |
| Shared chrome and cards | `common/CommonUi.kt`, `common/HmxGlassComponents.kt` |

Business logic, navigation, Supabase calls, permissions and wallet payment
disablement were not changed. The work is presentational.

## 7. Performance and low-RAM behaviour

- `Card` surfaces perform **no** blur and no lens shader, only backdrop sampling.
- `Control` and `Chrome` are bounded: a screen has one bottom bar, one top bar
  and a small number of buttons/fields.
- One content backdrop per screen, and only while effects are enabled. Low-RAM
  devices create no content layer at all.
- The decorative background is static, so no full-screen layer invalidates
  continuously.
- Duplicate screen backgrounds are suppressed while the host is mounted.
- `graphicsLayer` offscreen work is owned by the library and limited to the
  surfaces that request it.
- The fallback removes all `RenderEffect` work and keeps the same tree, so
  behaviour and hit targets do not change.

## 8. Verification

- Android CI: unit tests, `lintDebug`, debug APK.
- Liquid Glass visual workflow: API 34 x86_64 emulator, launches the debug-only
  `VisualTestActivity`, captures four PNGs (home, scrolled, sheet, dialog) and
  uploads them as an artifact.
- The harness places black/grey stripes behind the floating bar. Sharp stripes
  through the capsule would mean blur/lens are not running; softened, bent
  stripes are the visual proof.

See `HMX_LIQUID_GLASS_VISUAL_TEST_REPORT.md` for the captured results.

## 9. Remaining limitations

- Custom `AlertDialog`s with embedded forms or long legal text
  (report player, add/edit game account, change email, policy) still use the
  Material dialog window. They sit on a dim scrim rather than a glass window
  because a separate dialog window cannot share the in-window backdrop
  coordinates. Confirmation dialogs and the reset-link dialog already use
  `HmxLiquidGlassDialog`.
- `OutlinedTextField` remains the text input implementation inside the glass
  field; its own container, border and background are transparent.
- Blur is a no-op below API 31 and lens below API 33 by library design; those
  devices get the translucent fallback.
- Effect strength is tuned for the emulator and mid-range phones; an OPPO A38
  should be checked on-device. If blur cost is visible there, the
  `isLowRamDevice` gate or the `Control` radii can be lowered without changing
  layout.
