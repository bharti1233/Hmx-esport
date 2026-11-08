# HMX Arena — Liquid Glass Design System (Redesign Additions)

Branch: `feature/hmx-admin-tournament-match-management`
Base: `f0ddabc`
Implementation commit: `8ede8fa`
Date: 2026-11-08

---

## 1. Scope

This document records the design-system surface used by the Liquid Glass
redesign so future screens stay consistent. It does not replace the existing
glass architecture; it documents the additive tokens, components and rules.

The redesign keeps the original stack:

- Kotlin + Jetpack Compose, Material 3.
- `io.github.kyant0:backdrop:1.0.0` for real backdrop blur/refraction.
- The existing `GlassSurface` / `HmxLiquidGlassSurface` layering.
- Light, Dark and System theme persistence through DataStore.

## 2. Colour tokens added

Three product tokens were added to `HmxColorScheme` and exposed through
`HmxColor`:

| Token | Light | Dark | Use |
|---|---|---|---|
| `accent` | `#C62626` | `#E5484D` | single warm red accent for live/action emphasis |
| `onAccent` | `#FFFFFF` | `#140607` | text/icon on an accent fill |
| `accentSoft` | `#14C62626` | `#26E5484D` | low-alpha accent wash behind icons and artwork fallbacks |

Rules:

- No purple, blue or neon accent is introduced.
- `accent` is an emphasis colour, not a background wash. Large surfaces stay
  neutral glass.
- `accentSoft` must remain low-alpha so it never becomes an opaque plate or a
  white leak in Dark theme.
- Destructive actions keep `danger`; accent does not replace it.

`GlassButtonVariant` gains `Accent`, which uses `accent` as fill and `onAccent`
as content colour, with the same pressed-scale interaction as `Ink`.

## 3. Components

### Shared content components

`app/src/main/java/com/hmx/arena/ui/common/HmxContentComponents.kt`

| Component | Responsibility | Empty state |
|---|---|---|
| `HmxAnnouncementTicker` | newest published announcement, opens the list | "No announcements right now" |
| `HmxPromoCarousel` | horizontally paged admin banners, 2.35:1 artwork, page dots | "No active promotions" |
| `HmxMatchShortcutCard` | count + label + icon tile | count rendered as `—` when unavailable |
| `HmxCategoryArtCard` | image-first category tile, square logo badge, optional selected ring | neutral icon tile when no artwork |
| `HmxReferralCard` | invite code, share and WhatsApp actions | code hidden until issued |
| `HmxFilterTabs` | equal-width segmented filter with optional counts | n/a |

All clickable components use `Role.Button`/`Role.Tab`, a minimum 44–48 dp touch
target and a content description. Decorative images use `contentDescription =
null`; actionable images use the record title.

### Existing components reused

`GlassSurface`, `GlassButton`, `GlassChip`, `GlassIconButton`,
`GlassSectionHeader`, `GlassStatCell`, `GlassScaffold`, `HmxPage`,
`HmxLiquidGlassBottomBar`, `HmxLiquidGlassTopBar`, `HmxBrandMark`,
`GlassAvatar`, `GlassTextField`, `MatchFeedCard`, `TournamentCard`,
`EmptyBlock`, `InlineError`, `InlineNotice`, `LoadingBlock`, `StatusPill`.

No second design system, no parallel colour object and no new icon set were
introduced.

## 4. Adaptive layout

`core/designsystem/Adaptive.kt` adds `hmxCategoryColumns`:

| Window class | Width | Category columns |
|---|---|---|
| Compact | < 600 dp | 2 |
| Medium | 600–839 dp | 3 |
| Expanded | ≥ 840 dp | 5 |

The existing tournament grid (`hmxGridColumns`) stays 1 / 2 / 3. The category
grid is intentionally denser because each tile is square-ish artwork with a
single label.

The existing adaptive shell, navigation rail and content max-width rules are
unchanged. Screens use `LocalHmxWindowClass` rather than device checks.

## 5. Glass tiers and performance

The existing `HmxGlassTier` contract is unchanged:

| Tier | Behaviour |
|---|---|
| Full | full backdrop blur/refraction |
| Reduced | lighter blur, no extra layered highlights |
| Disabled | opaque neutral fallback, no blur |

New screens use the tier automatically through `GlassSurface` /
`HmxLiquidGlassSurface`. The redesign does not add per-frame animations to
lists, does not blur inside list items, and does not create additional
full-screen glass layers. The bottom bar remains a single capsule outside the
scrolling content layer.

## 6. Typography and spacing

- Existing Material 3 typography scale is used; no new font is bundled.
- `HmxDimens.cardPadding`, `HmxShapes.card/large/pill/field` remain the
  spacing/shape vocabulary.
- Content lists use 20 dp horizontal padding and a 124 dp bottom inset so the
  floating bar never covers the last row.
- Text uses `maxLines` + `TextOverflow.Ellipsis` on constrained rows; font
  scaling is not disabled.

## 7. Dark-theme safeguards

- Dark fill tokens keep low alpha; no white plate is inserted under glass.
- The carousel gradient is black-based (`Color.Black.copy(alpha = 0.72f)`), so
  text over imagery stays readable in both themes.
- `OnAccent` flips to near-black in Dark theme to keep contrast on the warm
  accent fill.
- No new `Color.White` surface fill was introduced.

## 8. Accessibility contract

- Every icon-only control has a meaningful `contentDescription`.
- Filter tabs expose `Role.Tab` and `selected`.
- Shortcut cards announce "Ongoing, N matches".
- Category cards announce "<name> category".
- Wallet tiles remain readable with font scaling because values use
  `maxLines = 1` plus ellipsis rather than fixed-height clipping.
- Motion is limited to the existing pressed scale; there is no continuous
  animation that cannot be disabled by the system animation scale.

## 9. Files touched

- `core/designsystem/Theme.kt`
- `core/designsystem/Glass.kt`
- `core/designsystem/Adaptive.kt`
- `ui/common/HmxContentComponents.kt`
- `ui/shell/GlassBottomBar.kt`
- `ui/home/HomeScreen.kt`
- `ui/tournament/TournamentScreens.kt`
- `ui/match/MatchScreens.kt`
- `ui/match/MyMatchesScreen.kt`
- `ui/wallet/WalletScreens.kt`
- `ui/referral/ReferralScreen.kt`
- `ui/profile/ProfileScreens.kt`
- `ui/admin/AdminScreens.kt`
- `ui/admin/AdminContentScreens.kt`
