# HMX Arena — Low-End Device UI Performance Report

Branch: `feature/hmx-admin-tournament-match-management`
Base: `f0ddabc`
Implementation commit: `8ede8fa`
Date: 2026-11-08

---

## 1. Status

**Static/code-level review complete. No profiler trace or low-end device
measurement was performed in this environment, and no benchmark numbers are
claimed.**

The redesign deliberately reuses the existing glass tier system instead of
adding new blur work.

## 2. What could have made this expensive

| Risk | Mitigation in this change |
|---|---|
| Extra full-screen glass layers | Home uses the same single scaffold; the floating bar stays outside the scrolling content layer |
| Blur inside long lists | List rows use standard `GlassSurface` at card tier; no per-row backdrop capture or animated blur |
| Carousel auto-advance / infinite animation | No auto-advance. The carousel only moves on user scroll; page dots derive from `LazyListState` |
| Many simultaneous image requests | Coil reuses its image pipeline; Home requests category/banner images only for rows the server returned |
| Large unprocessed artwork | Category artwork bucket limit is 5 MB; images are decoded/cropped by Coil to the target size |
| Recomposition storms from counts | Counts are computed from the loaded list with pure helpers; no polling or per-frame work |
| Heavy per-frame blur on the bottom bar | One capsule, existing `HmxGlassLevel.Chrome`, existing tier gating |
| Fixed animations ignoring accessibility | Only the existing pressed scale remains; no new continuous motion |

## 3. Glass tier behaviour

The existing `HmxGlassTier` contract is honoured automatically:

| Tier | Expected cost | New screens |
|---|---|---|
| Full | backdrop blur/refraction | use the shared glass surfaces |
| Reduced | lighter blur, fewer highlights | same components, fewer effects |
| Disabled | opaque neutral fallback, no blur | same components, no blur |

No new screen reads the tier manually or overrides it, so a reduced/disabled
device setting still applies to the redesigned Home, Arena, Matches, Wallet and
Account surfaces.

## 4. List and image strategy

- All long content uses `LazyColumn` / `LazyVerticalGrid` / `LazyRow`.
- Home sections are separate lazy items; adding banners or categories does not
  eagerly compose off-screen rows.
- Match and tournament cards are shared, so there is one card composition
  implementation rather than per-screen copies.
- Banner and category images use `AsyncImage` with explicit content scale and
  a neutral fallback. A failed/missing image does not retry in a loop.
- The wallet screen renders at most six recent ledger rows plus the summary;
  the Transactions screen is a separate lazy list.
- The admin content screens list at most 200 rows and do not prefetch images.

## 5. Network and data cost

- Home runs independent reads concurrently and treats wallet, banners,
  categories, announcements and unread count as optional. One optional failure
  does not re-fetch or blank the rest.
- The wallet summary is one aggregate RPC over the ledger instead of paging
  every historical row to the device. This is both more correct and cheaper on
  low-bandwidth devices than fetching a full ledger.
- The activity list stays capped at 50 rows.
- Match/tournament feeds keep their existing limits (60 matches, 200 admin
  rows, 100 public lookups).

## 6. Memory considerations

- No bitmaps are retained in ViewModels; images are owned by Coil's cache.
- No new global singletons or per-screen caches were added.
- The promotional banner model is small; the list is capped at 10 public rows.
- Admin editors keep form state as Compose state and release it when the
  destination leaves composition.
- The wallet totals DTO is a single row, not a ledger copy.

## 7. Accessibility / motion

- No auto-playing carousel.
- The only new motion is the existing pressed scale on `GlassButton`.
- Filter changes are immediate state updates, not animated page transitions.
- Reducing system animation scale removes the pressed animation without
  breaking layout.

## 8. Recommended device checks

Because no profiler data exists, a low-end QA pass should still measure:

1. Home first frame with 10 banners and 5 category artworks cached/uncached.
2. Scroll jank on the Matches list with 60 published matches.
3. Wallet refresh while the 50-row ledger list is on screen.
4. Memory after opening/closing Home, Wallet, Account and admin content
   screens repeatedly.
5. Glass tier `Disabled` on a low-end device to confirm the blur fallback.

Static review predicts no regression because no new blur layer, auto-play or
eager list composition was introduced. That prediction is not a substitute for
a profiler trace.

## 9. Files relevant to performance

- `ui/common/HmxContentComponents.kt`
- `ui/home/HomeScreen.kt`
- `ui/tournament/TournamentScreens.kt`
- `ui/match/MatchScreens.kt`
- `ui/match/MyMatchesScreen.kt`
- `ui/wallet/WalletScreens.kt`
- `ui/admin/AdminContentScreens.kt`
- `core/designsystem/Adaptive.kt`
- `core/util/WalletSummary.kt`
