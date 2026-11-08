# HMX Arena — Home Screen Implementation Report

Branch: `feature/hmx-admin-tournament-match-management`
Base: `f0ddabc`
Implementation commit: `8ede8fa`
Date: 2026-11-08

---

## 1. Result

Home is now a single, real-data dashboard. Every card is backed by a Supabase
read; there are no hard-coded banners, announcements, counts, balances or
category artwork.

`HomeScreen` loads these reads concurrently and degrades optional sections
without blanking the screen:

| Data | Source | Failure behaviour |
|---|---|---|
| Profile | `my_profile` RPC | screen-level error |
| Featured tournaments | published tournament feed | empty featured section |
| My matches | `match_registrations` feed | empty schedule state |
| Announcements | published announcements | "no announcements" state |
| Promotional banners | `promotional_banners` RLS-filtered read | "no active promotions" |
| Categories | active, non-archived lookup rows | managed-by-team message |
| Wallet | `wallet_accounts` row | "Balance unavailable" |
| Unread count | notifications | no badge |

## 2. Header

The floating header contains:

- Avatar (opens Account).
- "Welcome back," + display name + `@username · region`.
- Globe button (global search).
- Support button (support screen).
- Wallet balance capsule (opens Wallet), showing the real
  `balance_paise` value through `Format.money`, or "Balance unavailable" when
  no wallet row exists.
- Add button (opens Wallet). Paid funding remains server-disabled.

The header is a `GlassSurface` with `HmxShapes.large` and `refractive = true`.

## 3. Announcement ticker

`HmxAnnouncementTicker` shows the newest published announcement, its body as a
single line, and a `+N` counter when more exist. Tapping opens the full
announcement list. When the list is empty the strip says
"No announcements right now" instead of inventing copy.

## 4. Promotional banner carousel

`HmxPromoCarousel` renders only rows returned by the public read policy
(active + inside the optional start/end window). It uses a 2.35:1 image area,
a black-bottom gradient for text contrast, and page dots. A banner is
clickable only when it has a destination.

Destination handling is allow-listed in the client:

- `https://` / `http://` opens the browser.
- `referral`, `wallet`, `leaderboard`, `profile`, `search`, `tournaments`,
  `matches` navigate in-app.
- `match/<id>`, `tournament/<id>`, `announcement/<id>` navigate in-app.
- Anything else is ignored rather than guessed.

## 5. My Matches shortcuts

Three cards show real counts from the user's match registrations:

| Card | Section | Destination |
|---|---|---|
| Ongoing | `live`, `check_in` | `my-matches?section=ongoing` |
| Upcoming | `scheduled` | `my-matches?section=upcoming` |
| Completed | `completed` | `my-matches?section=completed` |

The counts use the same `matchSectionCounts` helper as the Matches filters, so
Home and Matches can never disagree. The "All" action opens
`my-matches` with no section argument.

The Home shortcut uses a direct `navigate` with `launchSingleTop` rather than
`openTab`, because `openTab` restores the saved entry and would keep the
previous section argument.

## 6. Category artwork grid

`HmxCategoryArtCard` renders each real category with:

- wide `image_url` artwork when uploaded;
- otherwise the square `logo_url`;
- otherwise a neutral icon tile;
- the category name always visible.

The grid uses 2 columns on Compact, 3 on Medium and 5 on Expanded. Selecting a
category navigates to `tournaments?category=<id>`; the Arena ViewModel reads
the argument from `SavedStateHandle` and applies the server-side category
filter.

When no categories exist, the section says they are managed by the arena team.

## 7. Referral card and WhatsApp

When the profile has a referral code, Home shows `HmxReferralCard` with:

- the real code;
- a factual line that referrals are reviewed by staff;
- a system Share action;
- a WhatsApp action that targets `com.whatsapp` and falls back to the system
  chooser when WhatsApp is not installed.

No cash reward is promised. When no code has been issued the card is replaced
by "Your invite code has not been issued yet."

## 8. Featured, schedule and news

- Featured arenas: up to two published tournaments with a "See all" action.
- Your schedule: up to two real registrations with room-state-aware cards, or
  an empty state that links to the public match feed.
- Arena news: up to two published announcements, or a neutral placeholder.

## 9. Navigation changes used by Home

| Action | Route |
|---|---|
| Play tab / Home | `home` |
| Arena | `tournaments` (optional category argument) |
| Public matches | `matches` |
| My matches | `my-matches` (optional section argument) |
| Wallet | `wallet` |
| Account | `profile` |
| Earn | `referral` |
| Ranking | `leaderboard` |

## 10. States and accessibility

- Loading: `LoadingBlock` while the first profile read is in flight.
- Error: `InlineError` with retry when no cached data exists.
- Optional failures: inline error plus the last usable data.
- Empty: explicit copy for each section.
- Icon-only actions have content descriptions.
- The wallet capsule is announced as "Wallet balance".
- All lists reserve a 124 dp bottom inset for the floating bar.
- Text is ellipsised on constrained rows and never clipped by fixed heights.

## 11. Files

- `app/src/main/java/com/hmx/arena/ui/home/HomeScreen.kt`
- `app/src/main/java/com/hmx/arena/ui/common/HmxContentComponents.kt`
- `app/src/main/java/com/hmx/arena/data/repository/PlayerRepository.kt`
- `app/src/main/java/com/hmx/arena/data/repository/TournamentRepository.kt`
- `app/src/main/java/com/hmx/arena/ui/navigation/Routes.kt`
- `app/src/main/java/com/hmx/arena/ui/navigation/MainNavHost.kt`

## 12. Verification and limitations

- `HomeViewModel`, the route helpers and the section helpers are covered by
  unit tests added in this change (`MatchFeedRulesTest`,
  `NavigationRoutesTest`).
- The live database currently has no promotional banners, so the production
  Home carousel shows its documented empty state until an admin creates one.
- No physical device or emulator was available in this environment. Rendering,
  scroll behaviour and font-scale layout were not device-tested; CI compiles
  the code and runs unit tests/lint.
