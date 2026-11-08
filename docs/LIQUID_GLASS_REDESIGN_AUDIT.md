# HMX Arena — Liquid Glass UI/UX Redesign Audit (Phase 0)

Status: **Phase 0 complete — no production UI code changed yet.**
Branch: `feature/hmx-admin-tournament-match-management`
Base commit at audit time: `f0ddabc`
Audit date: 2026-11-08

---

## 1. Executive summary

HMX Arena is already a native Kotlin / Jetpack Compose application with a
working Supabase backend, a real glass design system built on
`io.github.kyant0:backdrop:1.0.0`, adaptive window classes, authoritative wallet
reads, RLS-protected match data, and an admin console for categories, game
modes, tournament types, tournaments and matches.

The redesign is therefore an **integration and presentation task**, not a
rewrite. The following parts must be preserved exactly:

- Gradle / Kotlin / Compose / AGP / Backdrop versions.
- Supabase auth, session restoration, DataStore onboarding and theme persistence.
- Repository + ViewModel + Navigation architecture.
- All RLS policies and SECURITY DEFINER admin/registration RPCs.
- Registration, cancellation, match capacity enforcement and audit logging.
- Financial read-only behaviour while compliance approval is pending.

Phase 0 found **six concrete product gaps** that require new schema, repository,
ViewModel and UI work before the reference layouts can be implemented:

1. No promotional banner model, table, RLS, admin RPC or admin screen.
2. Category tables store only `logo_url`; there is no artwork `image_url`.
3. Announcements can be read publicly but cannot be managed by an admin from
   the app (no admin RPC, no admin screen, no write policy).
4. Home does not load the wallet balance and has no floating player header,
   announcement ticker, promo carousel, My Matches counter cards, category
   artwork grid or referral invite card.
5. The bottom navigation still exposes `Home / Arena / Matches / Ranks /
   Profile`, not `Earn / Ranking / Play / Wallet / Account`.
6. Matches and My Matches have section grouping logic but no explicit
   `Ongoing / Upcoming / Results` filter UI; Wallet has no
   deposited/winning/earnings/payouts breakdown.

There is **no safe way** to invent these values client-side. Every new visual
element must be backed by a real table/column, a real admin write path, and a
real read path guarded by existing RLS.

---

## 2. Constraints and non-goals

### Hard constraints

| Constraint | Value / rule |
|---|---|
| Language / UI | Kotlin, Jetpack Compose only |
| AGP | `8.13.2` |
| Kotlin | `2.2.21` |
| KSP | `2.2.21-2.0.5` |
| Compose BOM | `2025.10.01` |
| Glass library | `io.github.kyant0:backdrop:1.0.0` (do not upgrade) |
| Coil | `2.7.0` |
| Navigation Compose | `2.9.0` |
| Hilt | `2.56.2` |
| minSdk / target | `26` / `36` |
| Supabase | Existing REST/Storage integration only; no service-role key in the APK |
| Security | Preserve RLS, SECURITY DEFINER RPCs, audit logs, capacity locking |
| Money | No deposits, withdrawals, paid entries or payouts enabled until approved |
| Branch | Keep work on the feature branch; do not merge to `main` |
| Builds | GitHub Actions; no local Android Studio / SDK requirement |

### Non-goals

- New backend provider, WebView, Flutter, React Native or Compose Multiplatform.
- Replacing real records with local mock fixtures.
- Adding fake coins, demo balances, demo banners or demo announcements.
- Weakening any `SELECT` policy to make a screen easier to implement.
- Removing any existing destination or feature.

---

## 3. Current architecture inventory

### 3.1 App entry and shell

| File | Responsibility |
|---|---|
| `app/src/main/java/com/hmx/arena/MainActivity.kt` | Edge-to-edge activity, splash, DataStore theme resolution, session bootstrap |
| `app/src/main/java/com/hmx/arena/HmxRoot.kt` | Auth gate, onboarding gate, session state, deep links, signed-in `MainNavHost` |
| `app/src/main/java/com/hmx/arena/MainNavHost` equivalent (`ui/navigation/MainNavHost.kt`) | Single `NavHost` with tab, account, tournament, match, team and admin graphs |
| `ui/navigation/Routes.kt` | Route constants and `tabs` set |
| `ui/navigation/NavExt.kt` | `openTab()` state-preserving tab navigation |
| `ui/shell/GlassBottomBar.kt` | Floating glass bottom bar + adaptive rail; hardcoded tab list |

The app currently starts at `Routes.HOME` after onboarding, and `Routes.tabs`
is `{HOME, TOURNAMENTS, MATCHES, LEADERBOARD, PROFILE}`.

### 3.2 Design system (existing, reusable)

| File | Contents |
|---|---|
| `core/designsystem/Theme.kt` | `HmxTheme`, `HmxThemeMode`, `HmxColorScheme`, light/dark tokens, typography, shapes |
| `core/designsystem/ThemePreferences.kt` | DataStore-backed Light/Dark/System preference |
| `core/designsystem/Glass.kt` | `GlassSurface`, `GlassScaffold`, `GlassTopBar`, `GlassButton`, `GlassChip`, `GlassIconButton`, `GlassSectionHeader`, `GlassStatCell`, empty/error/loading/shimmer helpers |
| `core/designsystem/LiquidGlass.kt` | `Modifier.hmxLiquidGlass`, glass levels, fallback, shadow/highlight |
| `core/designsystem/HmxBackdrop.kt` | `HmxGlassHost`, `HmxLiquidBackdrop`, `HmxGlassTier`, `liquidGlassTier()` |
| `core/designsystem/Adaptive.kt` | `HmxWindowClass`, `hmxWindowClassForWidth`, `hmxContentMaxWidth`, `hmxGridColumns`, rail width, `LocalHmxWindowClass` |
| `ui/common/CommonUi.kt` | `HmxPage`, text fields, `GlassAvatar`, `StatusPill`, `InlineError`, `EmptyBlock`, `TournamentCard`, `MatchCard`, `MatchFeedCard` |
| `ui/common/HmxGlassComponents.kt` | Compatibility wrappers and older glass card/button/top-bar/bottom-bar APIs |

Important existing behaviour:

- `GlassScaffold` converts the bottom bar to a left rail on Medium/Expanded
  windows and insets content by `HmxRailWidth`.
- The bottom bar refracts recorded scrolling content only on the `Full` tier;
  `Reduced` uses the root backdrop; `Disabled` (API < 31) uses translucent
  fallback surfaces with identical layout.
- `HmxGlassHost` computes the window class from actual constraints, so split
  screen, foldables and rotation reflow automatically.
- `HmxColor` is the only allowed colour source for new UI. It has no dedicated
  red accent token today; the current CTA is the neutral `Ink` capsule.

### 3.3 Screens present today

| Area | Screen(s) | Notes |
|---|---|---|
| Auth | `ui/auth/AuthFlow.kt`, `RecoveryResetScreen` | Registration, login, recovery |
| Onboarding | `ui/onboarding/CompleteProfileScreen.kt` | Profile completion gate |
| Home | `ui/home/HomeScreen.kt` | Profile header, quick tiles, featured tournaments, 3 schedule cards, 2 announcements |
| Home secondary | `ui/home/DiscoverScreens.kt` | Notifications, announcements, announcement detail, search, player profile |
| Arena | `ui/tournament/TournamentScreens.kt` | Search, category chips, game-mode chips, free filter, tournament grid |
| Tournament detail | same file | Registration / cancellation, rules, participants, schedule, leaderboard |
| Matches | `ui/match/MatchScreens.kt` | Grouped public feed (Ongoing/Upcoming/Completed), match detail, credentials, results, submit, dispute |
| My Matches | `ui/match/MyMatchesScreen.kt` | User-specific registered matches, no section filter |
| Wallet | `ui/wallet/WalletScreens.kt` | Total balance, compliance notice, recent ledger, feature flags, transactions |
| Referral | `ui/referral/ReferralScreen.kt` | Invite code, copy/share, claim code, referral activity |
| Leaderboard | `ui/leaderboard/LeaderboardScreen.kt` | Ranked players |
| Profile | `ui/profile/ProfileScreens.kt` | Avatar, stats, game accounts, team, wallet, transactions, referral, support, about, settings, sign out |
| Settings | `ui/settings/SettingsScreens.kt` | Account, security, legal, theme, admin entry when server-confirmed |
| Team | `ui/team/*` | Create, manage, invite, invitations |
| Support | `ui/settings/SettingsScreens.kt` | Tickets and help |
| Admin | `ui/admin/AdminScreens.kt`, `AdminFormScreens.kt` | Dashboard, lookups, tournaments, matches, audit |

### 3.4 Data layer

| File | Responsibility |
|---|---|
| `data/remote/SupabaseApi.kt` | Retrofit endpoints, `SupabaseConfig`, storage bucket names |
| `data/remote/RemoteDataSource.kt` | PostgREST select/RPC/insert/storage helpers |
| `data/remote/SessionManager.kt` | Auth session persistence and bootstrap |
| `data/repository/PlayerRepository.kt` | Profile, game accounts, wallet, ledger, referrals, notifications, announcements, flags, avatar |
| `data/repository/TournamentRepository.kt` | Tournament feed, categories/modes, registration, matches, public feed, My Matches, match registration |
| `data/repository/AdminRepository.kt` | Admin lookups, tournaments, matches, artwork upload, audit |
| `data/repository/MatchFeedRules.kt` | Pure public-match predicate and Ongoing/Upcoming/Completed grouping |
| `core/model/Models.kt` | Serializable DTOs for every table/RPC |

### 3.5 Existing tests

- `AdaptiveLayoutTest` — window breakpoints and grid columns.
- `ThemeTokensTest` — light/dark token invariants.
- `FormatTest`, `RecoveryLinkParserTest`, `RegistrationRulesTest`,
  `ValidationTest`, `SupabaseErrorTest`.
- `MatchFeedRulesTest` — public feed visibility and grouping.

No Compose UI tests exist yet. No ViewModel tests exist yet.

---

## 4. Backend audit (live Supabase project)

Project: `https://ahsvxxhaowwhqermruhz.supabase.co`
Latest applied migration in the repo/live history: `0018_public_match_visibility`.

### 4.1 Live content counts at audit time

| Table | Rows |
|---|---|
| `public.categories` | 5 |
| `public.game_modes` | 4 |
| `public.tournament_types` | 5 |
| `public.announcements` | 3 (all published) |
| `public.tournaments` | 6 |
| `public.matches` (published, not archived) | 5 |
| `public.promotional_banners` | **table does not exist** |

### 4.2 Lookup tables

`categories`, `game_modes`, `tournament_types` all have:

`id`, `slug`, `name`, `description`, `logo_url`, `is_active`, `is_archived`,
`display_order`, `created_at`, `updated_at`.

- `SELECT` policy is `true` for all three (public catalogue).
- Only `SELECT` is granted to `anon` and `authenticated`.
- Writes happen exclusively through `admin_upsert_category`,
  `admin_upsert_game_mode`, `admin_upsert_tournament_type` and
  `admin_delete_*`, each guarded by `public.require_admin()` and audited.
- **Gap:** no `image_url` column, no RPC support for artwork, no artwork upload
  path in the client for categories (only `category-logos` square logos).

### 4.3 Announcements

Live columns: `id`, `title`, `body`, `image_url`, `is_published`,
`published_at`, `created_at`.

- `announcements_select` policy: `is_published OR is_admin()`.
- `anon`/`authenticated` have `SELECT` only. No write policy exists.
- **Gap:** no `admin_upsert_announcement` / `admin_delete_announcement` RPC,
  no admin UI, no explicit authenticated `INSERT/UPDATE/DELETE` grant (and none
  should be added — writes must remain RPC-only).

### 4.4 Wallet and ledger

- `wallet_accounts(user_id, balance_paise, reserved_paise, currency)`.
- `financial_ledger(id, user_id, entry_type credit/debit/hold/release/adjustment,
  amount_paise, balance_after_paise, reference_type, reference_id, description,
  created_at)`.
- Wallet is read-authoritative; there is no client write path.
- Feature flags already gate `free_tournaments_enabled`,
  `paid_tournaments_enabled`, `payment_orders_enabled`, `refunds_enabled`,
  `payouts_enabled`.
- **Gap:** no derived summary of deposited/winning/earnings/payouts. Any such
  breakdown must be computed from real ledger rows by an explicit, documented
  rule, not fabricated.

### 4.5 Matches and capacity

- `matches` includes banner/logo URLs, prize/per-kill/entry fee, version,
  format, map, mode, max slots, slots filled, publication and archive fields,
  category/game-mode/tournament-type foreign keys.
- `matches_select`: `is_admin() OR (is_published AND archived_at IS NULL)`.
- `register_for_match()` locks the row and enforces capacity server-side;
  `match_registrations` has no client `INSERT` policy.
- Public feed predicate already excludes draft/archived/cancelled records.
- **Gap:** only presentation — no explicit three-tab filter, and match cards need
  the reference layout (three reward columns, configuration row, capacity bar,
  MATCH FULL state).

### 4.6 Storage

| Bucket | Limit | Access |
|---|---|---|
| `avatars` | 2 MB | public read, owner write |
| `team-logos` | 2 MB | public read, owner/admin write |
| `tournament-banners` | 5 MB | public read, admin write |
| `category-logos` | 2 MB | public read, admin write, `categories/*` |
| `match-assets` | 5 MB | public read, admin write, `matches/*` and `tournaments/*` |
| `result-evidence` | 10 MB | private, participant/staff |

**Gap:** no dedicated bucket or path convention for wide category artwork and
promotional banners. Reuse is preferred:
`category-artwork` or `category-logos/categories/<id>/artwork-*` and
`tournament-banners/banners/<id>-*` must be decided before migration 0019.

### 4.7 Admin RPC surface

Existing: lookup upserts/deletes, tournament upsert/status/duplicate/delete,
match upsert/status/publish/duplicate/delete, audit log.

Missing: announcement CRUD, promotional banner CRUD, lookup artwork field
updates.

---

## 5. Gap matrix against the redesign brief

| Requirement | Current state | Gap / action |
|---|---|---|
| Floating Home header with avatar, welcome, username, globe, support, wallet, add | Profile header exists but shows points, no globe/support/wallet/add | Rebuild Home header; add wallet read to `HomeViewModel` |
| Announcement ticker | Only two static announcement rows | New ticker component driven by published announcements |
| Promotional banner carousel | No table/model/UI | Migration 0019 + model + repo + admin CRUD + carousel |
| Three My Matches shortcuts with real counts | One "My matches" tile + a schedule list | Count Ongoing/Upcoming/Completed from real feed; filtered destinations |
| Dynamic category artwork grid | Chip filters only | Add `image_url`, artwork upload, adaptive image grid |
| Tournament/Solo tabs | No product concept of Solo/Tournament at category level | Only show tabs if backed by real data; otherwise use category/mode filters |
| Referral card with WhatsApp share | Referral screen supports generic share; Home has a tile | Add invite card on Home + WhatsApp target intent |
| Arena search + dynamic categories/modes | Search + chips exist | Add image category grid, cleaner header, retain filters |
| Real published tournament cards | Exists, but basic | Restyle to Liquid Glass reference, keep real data |
| Matches Ongoing/Upcoming/Results filters | Grouping exists, no filter UI | Add filter state and sections; Results = completed |
| 16:9 banner + 1:1 logo | Already correct in `MatchFeedCard` | Restyle, keep aspect ratios and fallback |
| Title/number/date/tournament | Exists | Keep and align |
| Prize / per-kill / entry | Exists as three cells | Restyle to premium columns |
| Type/version/map/category/mode | Partially in one joined string | Split into labelled configuration row |
| Capacity progress + MATCH FULL | Exists | Restyle; keep backend enforcement |
| Wallet total + deposited/winning/earnings/payouts | Total only | Add documented ledger-derived summary + tiles; keep real data |
| Transaction history/status/date/type/amount | Exists | Add status/type labels, empty/error/loading polish |
| Bottom nav Earn/Ranking/Play/Wallet/Account | Home/Arena/Matches/Ranks/Profile | Remap routes; preserve existing destinations |
| Account/Profile parity | All features exist | Restyle; keep avatar/stats/team/wallet/referral/support/settings/logout |
| Admin categories/artwork/logos | Logos only | Add artwork field + upload |
| Admin banners | None | Add screens + RPCs |
| Admin announcements | None | Add screens + RPCs |
| Admin display order/active status | Exists for lookups | Preserve; expose artwork ordering in player UI |
| Responsive grids | `hmxGridColumns` 1/2/3 | Add dedicated art/category columns (2/3/4/5) |
| Glass tiers | Full/Reduced/Disabled exist | Reuse; avoid per-row blur |
| Accessibility | Basic content descriptions | Audit new cards, touch targets, contrast |
| Tests | Unit rules only | Add wallet-summary, category-column, nav-route, filter tests |

---

## 6. Screen-by-screen findings

### 6.1 Home

Current `HomeViewModel` loads profile, featured tournaments, `myMatchFeed()`,
announcements and unread notifications in parallel. It does **not** load the
wallet. `ProfileHeader` shows points, played and wins. Quick tiles are
Tournaments / My matches / Wallet / Refer & Earn. The schedule section shows up
to three registered matches but has no counts or section-specific destinations.

Redesign work:

- Add wallet read (`player.wallet()`) and show authoritative balance only.
- Replace `ProfileHeader` with a floating glass header containing avatar,
  welcome, username, globe (language/support surface), support icon, wallet
  capsule and add button.
- Add announcement ticker, promo carousel, three counter shortcut cards,
  category artwork grid, referral invite card.
- Keep setup card, featured arenas and schedule content; do not drop existing
  destinations.
- Handle loading, empty and error states per section rather than blanking the
  whole page.

### 6.2 Arena / Tournaments

`TournamentsViewModel` already performs server-side filtering by query,
category, game mode and free entry. `TournamentsScreen` uses
`hmxGridColumns()` (1/2/3). Categories are rendered as text chips only.

Redesign work:

- Keep search and all filters; add an image-first category grid above the
  tournament list.
- Category cards read `image_url` (new), `logo_url`, `name`, `description`,
  `display_order`, active/archived state.
- Category artwork grid needs 2 columns on compact, 3 on medium, 4–5 on
  expanded; do not reuse the 1-column tournament card column count.
- Tournament cards restyled but backed by the same query.
- Do not invent "Tournament/Solo" tabs. If the product needs them, they must be
  backed by real lookup data or an explicit product decision.

### 6.3 Matches

`groupMatchFeed()` already computes Ongoing (`live`/`check_in`), Upcoming
(`scheduled`) and Completed (`completed`). `MatchesScreen` renders section
headers but has no explicit filter control. `MatchFeedCard` already implements
the required 16:9 banner, 1:1 logo, reward cells, capacity bar and MATCH FULL
badge.

Redesign work:

- Add `Ongoing / Upcoming / Results` glass filter tabs with counts.
- Preserve server-side/public visibility rules and `registered` flag.
- Restyle the card to the reference composition; keep real fields.
- Add labelled configuration row (type, version, map, category, mode).
- Ensure long titles and translated strings wrap without clipping.

### 6.4 My Matches

`MyMatchesViewModel` calls `repository.myMatchFeed()`, which starts from
confirmed/waitlisted `match_registrations`. No section filters exist.

Redesign work:

- Add Ongoing/Upcoming/Completed filters and counts.
- Accept the same section query argument used by Home shortcuts.
- Keep the empty state pointing to the public feed.

### 6.5 Wallet

`WalletScreen` reads `wallet_accounts` and `financial_ledger` and shows the real
balance, reserved amount, compliance notice, four recent entries and feature
flags. Financial operations are intentionally disabled.

Redesign work:

- Premium balance hero with total and deposited/winning/earnings/payouts tiles.
- A documented derivation rule must be added to a pure helper and unit-tested.
  No new table or fabricated amount.
- Add refresh, loading skeleton, empty state and error retry.
- Keep compliance notice and flags visible; do not add enabled deposit /
  withdrawal / redeem buttons.
- Keep the `TransactionsScreen` path.

### 6.6 Referral

Existing screen has code, copy, generic share, claim flow and activity list.
Home needs an invite card. WhatsApp sharing can be implemented as a targeted
`Intent` with the existing text; no new reward logic.

### 6.7 Profile / Account

All required features exist: avatar upload, stats, game accounts, team, wallet,
transactions, referral, support, about, settings, theme and logout.

Redesign work is mostly structural and visual. Preserve `ProfileViewModel`
actions and account ownership checks. Rename the tab destination label to
"Account" while keeping `Routes.PROFILE`.

### 6.8 Admin

Existing dashboard and lookup screens manage categories, game modes,
tournament types, tournaments and matches, including logo/banner uploads.
Missing admin content surfaces are banners and announcements; category artwork
is also missing.

---

## 7. Theme, performance and accessibility findings

### Theme

- Light and dark tokens are complete and deliberately distinct.
- No purple/blue/neon tokens exist; new work must stay neutral + restrained red.
- There is no `accent` token. A restrained esports red must be added to both
  schemes (not hardcoded) before use.
- System theme selection is persisted in DataStore and applied before first
  frame; preserve this path.

### Performance

- `Full` tier records a second content layer only for screens with a bottom bar.
- `Reduced` tier skips card/control blur and uses fallback surfaces.
- `Disabled` tier is used below API 31.
- Existing list rows use cheap fills; only chrome uses refraction.
- New work must:
  - keep per-row surfaces non-refractive;
  - use stable keys in lazy lists/grids;
  - avoid nested vertical scrolling;
  - avoid continuous animations on the Reduced tier;
  - use Coil's default memory/disk caching, not custom bitmap decoding.

### Accessibility

- Existing icon buttons mostly have content descriptions.
- New artwork images need meaningful descriptions only when they convey
  information; decorative banners can be null.
- Shortcut cards and filter tabs need `Role.Button`/`Role.Tab` and selected
  semantics.
- Touch targets must remain at least 48dp even when visual size is smaller.
- Font scaling must be tested in Compact/Medium/Expanded.

---

## 8. Phased implementation plan

### Phase 1 — Design system additions

New shared components (no one-off glass implementations):

- `HmxAccent` token pair in `Theme.kt`.
- `HmxGlassTicker` / `HmxAnnouncementTicker`.
- `HmxPromoCarousel` + `HmxPromoBanner`.
- `HmxMatchShortcutCard` (count, icon, label, destination).
- `HmxCategoryArtCard` and `HmxCategoryGrid`.
- `HmxReferralCard` (artwork, description, share, WhatsApp).
- `HmxWalletSummary` helper + summary tiles.
- `HmxFilterTabs` (Ongoing/Upcoming/Results).
- Dedicated column helpers in `Adaptive.kt`
  (`hmxCategoryColumns`, `hmxShortcutColumns`).

### Phase 2 — Home and navigation

- Rework `HomeViewModel` to load wallet + counts + banners.
- Rebuild `HomeScreen` sections.
- Remap `Routes.tabs`, `HmxLiquidGlassBottomBar` labels/icons/routes:
  - Earn → `Routes.REFERRAL`
  - Ranking → `Routes.LEADERBOARD`
  - Play → `Routes.HOME`
  - Wallet → `Routes.WALLET`
  - Account → `Routes.PROFILE`
- Ensure `Arena`/`Matches` screens still treat `Routes.HOME` (Play) as the
  selected tab when reached from within Play.
- Preserve `openTab()` state saving.

### Phase 3 — Arena and Matches

- Add category artwork rendering + admin-managed images.
- Add Match filter tabs with counts.
- Restyle `MatchFeedCard` while preserving all real fields and fallbacks.
- Add My Matches section filters and route argument support.

### Phase 4 — Wallet and Profile

- Add ledger-derived summary helper and wallet tiles.
- Add refresh/error/empty polish.
- Restyle Profile sections while preserving every action.

### Phase 5 — Admin content

Migration `0019_glass_content.sql`:

- `image_url` on `categories`, `game_modes`, `tournament_types`.
- `promotional_banners` table:
  `id`, `title`, `subtitle`, `image_url`, `destination`, `display_order`,
  `is_active`, `starts_at`, `ends_at`, timestamps.
- Public `SELECT` policy: active and within window.
- Admin-only writes via RPC; no client write grants.
- `admin_set_lookup_image(p_id, p_image_url)`.
- `admin_upsert_banner`, `admin_delete_banner`.
- `admin_upsert_announcement`, `admin_delete_announcement`.
- Grants limited to `authenticated` execute; admin check inside each function.
- Reuse `category-logos`/`tournament-banners` or add a dedicated
  `promotional-banners` bucket with public read + admin write.

Admin UI:

- `AdminBannersScreen` + editor.
- `AdminAnnouncementsScreen` + editor.
- Category artwork upload in `AdminLookupScreen`.
- Dashboard tiles and routes for both.

### Phase 6 — Responsive / performance

- Verify Compact/Medium/Expanded and landscape.
- Verify font scaling 0.85–2.0.
- Verify Full/Reduced/Disabled tiers on low-RAM configuration.

### Phase 7 — Tests, CI, reports

- Pure unit tests: wallet summary, category columns, match filter rules,
  navigation mapping.
- GitHub Actions: unit tests → lint → assembleDebug.
- Write all required reports listed in the brief.

---

## 9. Data-integrity decisions required before migration 0019

1. **Banner artwork bucket.** Reuse `tournament-banners` (already 5 MB, public
   read, admin write) or create `promotional-banners`. Reuse is lower-risk and
   avoids new storage policy drift.
2. **Category artwork bucket.** Extend `category-logos` with an `artwork-*`
   object path and raise its limit only if needed (currently 2 MB, adequate for
   a compressed wide JPEG/WebP).
3. **Wallet breakdown rule.** Define deposited/winning/earnings/payouts as
   explicit aggregates over `financial_ledger.entry_type` and
   `reference_type`, then document and unit-test. If the ledger cannot support a
   category, the tile must show "—" rather than a fabricated number.
4. **"Tournament / Solo" tabs.** Only implement if backed by a real lookup or
   product field; otherwise omit and use existing category/mode filters.

---

## 10. Verification plan

| Check | Method |
|---|---|
| Compile / lint | GitHub Actions `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug` |
| RLS regression | Rolled-back SQL transactions: anon cannot read drafts; non-admin cannot write lookups/announcements/banners; admin RPCs succeed only with admin role |
| Capacity | Existing `register_for_match()` tests + UI MATCH FULL state |
| Wallet | Pure summary tests + live read check against `wallet_accounts` |
| Navigation | Static route-mapping test + manual review of all tab destinations |
| Responsive | Compose previews/screenshots if available; otherwise code-level column tests and explicit limitation note |
| Physical device | **Not available.** No claim of device testing will be made. |

---

## 11. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Live Supabase migration affects production data | Additive columns/table only; no destructive changes; apply through MCP with verification queries |
| Banner table has no seed data | UI must render loading/empty state; admin can create real records; do not seed in production without approval |
| Wallet breakdown misrepresents money | Derive from ledger in a pure helper; unknown categories display "—"; unit tests |
| Bottom nav remap breaks deep links | Keep all route constants; only change which routes the five tabs point to; Arena/Matches remain reachable |
| Home load becomes slower | Keep parallel loading; wallet/banners are independent and optional-failure |
| Glass overuse on low-end devices | New list surfaces stay non-refractive; carousel/header use Chrome sparingly |
| No local Android build | Rely on CI; do not claim local APK validation |

---

## 12. Phase 0 conclusion

The application is a strong, secure base for the redesign. The glass system,
adaptive shell, repositories and security model are already in place and must be
extended, not replaced. The redesign can proceed safely once the missing
content tables/columns and admin write paths are added and verified.

**Next step:** Phase 1 + migration `0019_glass_content.sql`.
