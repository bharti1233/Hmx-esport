# HMX Arena — Final UI Regression Report

Branch: `feature/hmx-admin-tournament-match-management`
Base: `f0ddabc`
Feature commit: `8ede8fa`
Icon compile fix: `a5c2038`
Date: 2026-11-08

---

## 1. Final result

The Liquid Glass redesign is implemented and the Android CI pipeline is green.

**CI run:** https://github.com/bharti1233/Hmx-esport/actions/runs/42513993363

| Step | Result |
|---|---|
| Set up JDK 17 | success |
| Install Android SDK packages | success |
| Unit tests (`:app:testDebugUnitTest`) | success |
| Android lint (`:app:lintDebug`) | success |
| Debug APK (`:app:assembleDebug`) | success |
| Upload debug APK | success |

The previous run (`42513561957`) failed on one unresolved icon reference
(`Icons.Rounded.Globe`). The fix uses the supported `Icons.Rounded.Language`
globe icon; the rerun above is fully green.

## 2. Delivered screens and components

### Home

- Floating header with avatar, welcome, display name, `@username · region`,
  globe/search, support, real wallet balance and add action.
- Announcement ticker from published announcements.
- Admin-controlled promotional banner carousel (empty when none exist).
- Three My Matches shortcut cards with real counts and filtered destinations.
- Dynamic category artwork grid (wide `image_url`, square logo fallback,
  neutral tile fallback).
- Referral card with system Share and WhatsApp (chooser fallback).
- Featured tournaments, user schedule and news sections.
- Explicit loading, error and empty states.

### Navigation

Bottom bar is now the required five destinations:

| Tab | Route |
|---|---|
| Earn | `referral` |
| Ranking | `leaderboard` |
| Play | `home` |
| Wallet | `wallet` |
| Account | `profile` |

Arena and Matches are reached under Play and select the Play tab. Tab state is
preserved with the existing `openTab` save/restore behaviour.

### Arena and Matches

- Arena search, dynamic categories, game modes and free-entry filter.
- Image category grid with selected-state ring.
- Published tournament cards.
- Public match feed with All / Ongoing / Upcoming / Results tabs and counts.
- 16:9 banner, separate 1:1 logo, title/status/tournament, category/mode/type/
  map/format/version, prize/per-kill/entry, capacity progress, `MATCH FULL`,
  `Registered`, date and countdown.
- My Matches is registration-only and supports the optional section argument.
- Capacity remains backend-enforced; the UI never grants a slot.

### Wallet and Account

- Wallet is a tab with a real balance hero, deposited/winning/earnings/payouts
  tiles, compliance notice, recent ledger activity and feature flags.
- Lifetime totals come from the new `my_wallet_totals()` SQL aggregate over the
  full immutable ledger, not the 50-row activity window.
- Transactions screen lists the 50-row activity window with type, status,
  reference, date and amount.
- Account keeps avatar, statistics, game accounts, team, wallet, referral,
  support, settings, theme and logout.
- Earn keeps the referral claim flow and adds WhatsApp sharing.

### Admin content

- Category logo + wide artwork upload.
- `image_url` support for categories, game modes and tournament types.
- Promotional banner CRUD with display order, active state and optional window.
- Announcement CRUD with publish state.
- Dashboard counts and "Home content" entry points.
- All writes remain SECURITY DEFINER RPCs guarded by `is_admin()`; no table
  write grants were added to clients.

## 3. Backend changes

### Migration `0019_glass_content.sql`

- `categories.image_url`, `game_modes.image_url`, `tournament_types.image_url`.
- `promotional_banners` table + indexes + RLS (public read only for active,
  in-window rows; admin manages through RPCs).
- `admin_set_lookup_image`, `admin_upsert_banner`, `admin_delete_banner`,
  `admin_upsert_announcement`, `admin_delete_announcement`.
- Grants to `authenticated` only; anon/public execute revoked.
- `category-logos` bucket limit raised to 5 MB for wide artwork.

### Migration `0020_wallet_totals.sql`

- `public.my_wallet_totals()` aggregates the full ledger for `auth.uid()`.
- Returns balance, reserved, currency, deposited, winning, earnings, payouts
  and entry count.
- `SECURITY DEFINER`, fixed `search_path`, granted to `authenticated` only.

### Live verification

- Applied to the production project through the Supabase MCP.
- Rolled-back admin transaction verified category artwork set/clear, banner
  create/update/delete and announcement create/publish/delete.
- Anonymous execute of `my_wallet_totals()` is denied
  (`permission denied for function my_wallet_totals`).
- Rolled-back ledger test produced the documented bucket values
  (`deposited 20000`, `winning 50000`, `earnings 3000`, `payouts 30000`,
  `entry_count 8`).
- Public anon reads return only active, in-window banners.

## 4. Regression checklist

| Area | Status | Notes |
|---|---|---|
| Native Android/Kotlin/Compose | preserved | no rewrite, no framework change |
| Gradle/AGP/Kotlin/Backdrop versions | preserved | no version bump |
| Supabase auth/session/DataStore | preserved | untouched |
| Theme Light/Dark/System | preserved | three new colour tokens only |
| Repository/ViewModel/Navigation | preserved | additive routes and arguments |
| RLS policies | preserved | no weakening; new table has its own strict policy |
| Registration/cancellation | preserved | existing RPCs unchanged |
| Capacity enforcement | preserved | server-side, unchanged |
| Admin audit logging | preserved | new admin RPCs call `admin_log` |
| Paid/financial operations | still disabled | server flags unchanged |
| Existing destinations | preserved | no screen removed |
| Bottom navigation | remapped as required | five destinations |
| Wallet balance source | authoritative | `wallet_accounts` |
| Wallet lifetime totals | authoritative | new full-ledger aggregate |
| Referral claim flow | preserved | Earn tab |
| Settings/logout/theme | preserved | Account tab |
| Admin category/mode/type/tournament/match screens | preserved | artwork fields added |
| CI unit tests | pass | see run above |
| CI lint | pass | `abortOnError = true` satisfied |
| Debug APK | builds | uploaded as artifact |

## 5. Tests added or updated

- `app/src/test/java/com/hmx/arena/core/util/WalletSummaryTest.kt`
  - bucket rules, unlabelled credits, payout absolutes, hold/release and
    entry-fee exclusions, unknown reference types.
- `app/src/test/java/com/hmx/arena/ui/navigation/NavigationRoutesTest.kt`
  - five-tab contract, Play=Home, optional route arguments, admin routes.
- `app/src/test/java/com/hmx/arena/data/repository/MatchFeedRulesTest.kt`
  - section counts and section lookup.
- `app/src/test/java/com/hmx/arena/core/designsystem/AdaptiveLayoutTest.kt`
  - category column density (2/3/5).

Existing tests continue to run in the same `:app:testDebugUnitTest` task.

## 6. Limitations and honest non-claims

- **No physical device or emulator testing.** Rendering, gestures, dark-mode
  appearance, carousel snap, font scaling, foldable posture and landscape were
  not device-tested.
- **No profiler/benchmark run.** Low-end performance conclusions are based on
  static review (no new blur layers, no autoplay, lazy lists).
- **No production admin account.** Admin end-to-end UI testing was limited to
  rolled-back role transactions at the SQL layer. The admin screens compile and
  lint, but were not exercised against a live admin login on a device.
- **No promotional banners in production yet.** Home shows the documented
  empty carousel state until an admin creates an active banner.
- **No ledger rows in production.** The populated wallet state was verified
  with rolled-back synthetic rows at the SQL layer, not with real money.
- The 50-row activity list is intentionally a window, not a history export;
  lifetime tiles use the server aggregate.
- The branch was not merged to `main` and no force-push was used.

## 7. Artifacts and references

- CI run: https://github.com/bharti1233/Hmx-esport/actions/runs/42513993363
- Debug APK artifact: `hmx-arena-debug-apk` on the run above.
- Design system: `docs/LIQUID_GLASS_DESIGN_SYSTEM.md`
- Home report: `docs/HOME_SCREEN_IMPLEMENTATION_REPORT.md`
- Arena/Matches report: `docs/ARENA_AND_MATCHES_UI_REPORT.md`
- Wallet/Account report: `docs/WALLET_AND_PROFILE_UI_REPORT.md`
- Responsive report: `docs/RESPONSIVE_LAYOUT_TEST_REPORT.md`
- Performance report: `docs/LOW_END_DEVICE_UI_PERFORMANCE_REPORT.md`
- Phase 0 audit: `docs/LIQUID_GLASS_REDESIGN_AUDIT.md`

## 8. Recommended next steps

1. Device QA pass using the checklist in the responsive report.
2. Create one real promotional banner and category artwork in production, then
   verify Home/Arena propagation after refresh.
3. Run a low-end profiler pass for the Home carousel and Matches list.
4. Merge only after the product owner accepts the device QA results.
