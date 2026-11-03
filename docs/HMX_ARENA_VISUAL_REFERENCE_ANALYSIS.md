# HMX Arena — Visual Reference Analysis

Reference: `docs/design/reference/hmx-arena-reference-27-screens.png`
(original supplied composite: `file_0000000030208211a128454e9b6c4809.png`)

This document is the visual contract for the Android app. The composite is
authoritative: it is a single 9 × 3 board of 27 iPhone-class panels. No other
reference imagery was supplied, and no stock or generated imagery is used as a
substitute.

---

## 1. Board inventory

Reading left to right, top to bottom:

| # | Panel | # | Panel | # | Panel |
|---|-------|---|-------|---|-------|
| 1 | Splash | 10 | Tournaments (list) | 19 | Wallet |
| 2 | Onboarding 1 | 11 | Tournament Details | 20 | Add Money |
| 3 | Onboarding 2 | 12 | Join Tournament (confirm) | 21 | Withdraw |
| 4 | Onboarding 3 | 13 | Payment Success | 22 | Transactions |
| 5 | Get Started | 14 | My Tournaments | 23 | Refer & Earn |
| 6 | Sign Up | 15 | Match Lobby | 24 | Profile |
| 7 | Log In | 16 | Live Match | 25 | Game Details |
| 8 | OTP Verification | 17 | Match Results | 26 | Settings |
| 9 | Home (dashboard) | 18 | Leaderboard | 27 | Help & Support |

All 27 have an equivalent route in `MainNavHost`; the mapping is recorded in
`docs/HMX_ARENA_VISUAL_AUDIT.md`.

---

## 2. Palette

The board is strictly monochromatic. There is no purple, blue, neon, coloured
gradient, or dark gaming theme anywhere.

| Role | Reference value | App token |
|------|-----------------|-----------|
| Page / frosted canvas | near-white with cloudy grey shading | `HmxColor.Canvas` `#FDFDFE` |
| Neutral surface tint | very light warm grey | `HmxColor.CanvasTint` `#F6F6F8` |
| Glass fill | translucent white, ~45–80 % | `HmxColor.Glass` / `GlassStrong` |
| Primary ink | near-black | `HmxColor.Ink` `#0B0B0C` |
| Secondary text | mid grey | `HmxColor.InkSecondary` `#6E6E74` |
| Tertiary text / hints | light grey | `HmxColor.InkTertiary` `#9B9BA2` |
| Border hairline | black at 10–20 % | `HmxColor.Hairline` / `HairlineStrong` |
| CTA fill | black, or light grey for secondary | `Ink` / `GlassStrong` |
| Shadow | soft neutral grey | `HmxColor.Shadow` |

Rules derived from the board:

- White is the only saturated colour. Photos are the only full-colour assets
  (Free Fire banner in panels 11 and 17); everything else is grey-scale.
- Selected chips and primary CTAs invert to black with white text.
- Destructive actions (Withdraw / Delete account) are still neutral; only the
  copy communicates risk. The app keeps a muted `Danger` token for inline errors.
- No glow, no coloured drop shadow, no gradient text.

---

## 3. Typography

- Large, tight display headings for screen titles ("Log In", "Verify OTP",
  "Wallet", "Leaderboard").
- Sentence-case labels; numbers are the visual anchor on dashboard cards.
- Small uppercase eyebrow labels with wide tracking ("FREE FIRE ACCOUNT",
  "TOTAL BALANCE", "RECENT TRANSACTIONS").
- Numerals use tabular alignment in lists (points, ₹ amounts, slots).
- Body copy is short: one or two lines under each heading.

The current `HmxTypography` scale already follows this: 34 sp display, 19 sp
title, 14 sp body, 10 sp label with 0.4 sp tracking.

---

## 4. Layout language

- 20 px horizontal page padding; 24–32 px vertical breathing room.
- Cards are large-radius rounded rectangles (≈22–28 px) with a 1 px hairline
  and a very soft shadow.
- Controls are pill-shaped (buttons, chips, search) or 16 px fields.
- The bottom navigation is a floating translucent pill with five items; the
  active item is a black pill with a white glyph.
- Top bars are almost invisible: title, a circular icon button on the right,
  and a back chevron on detail screens.
- Lists use a single column of full-width cards separated by 10–12 px.
- Dashboard tiles are a 2 × 2 grid of equal-width cards, each with a glyph,
  a label, and a chevron.
- The home hero is a wide banner card (Free Fire artwork + "Play · Compete ·
  Win") that spans the content width.

---

## 5. Screen-by-screen anatomy

### 5.1 Splash (1)
Centered HMX wordmark above "HMX Arena" and "Free Fire Tournaments" on a cloudy
white field. No spinner, no colour. System splash should hand off to this
composition without a flash.

### 5.2 Onboarding (2–4)
Three full-bleed slides, each with a single line icon (controller, trophy,
lightning), a headline, one supporting line, a pagination dot row, and a
circular next button. No illustration, no colour. The final slide leads to Get
Started.

### 5.3 Get Started (5)
Centered logo, three stacked actions: "Continue with Google" (light glass,
Google glyph), "Continue with Phone" (light glass), "Create Account" (black).
Legal copy below. The visual hierarchy is: brand → methods → legal.

### 5.4 Sign Up (6) / Log In (7)
Form-first screens. Fields are light glass pills with a leading glyph and a
trailing eye toggle. Primary CTA is a black pill ("Sign Up" / "Log In").
Secondary pathways are separated by an "or" rule and a Google button.
"Forgot Password?" sits as a right-aligned text action above the CTA.
The Android implementation keeps email + password (the product's Supabase auth
contract) and exposes the same hierarchy.

### 5.5 OTP (8)
Centered heading, supporting copy with the masked phone, six large OTP cells,
a resend countdown, and a full-width Verify button. The app uses the same
six-digit grouping and countdown treatment for email verification.

### 5.6 Home (9)
- Greeting ("Hello, Player") and a notification bell.
- Wallet balance card (₹250.00) with an add action.
- Four dashboard tiles in a 2 × 2 grid: Tournaments, My Matches, Wallet,
  Refer & Earn.
- Wide Free Fire banner card.
- Floating five-item bottom bar.

### 5.7 Tournaments (10)
Segmented chips (All / Free / Paid / Upcoming) then full-width tournament
cards. Each card: title, prize pool, entry fee or "Free", slots filled,
countdown, and a small right arrow. No banner imagery in the list.

### 5.8 Tournament Details (11)
Hero banner with the tournament name overlaid, then a prize-pool block,
a metadata grid (Mode / Type / Slots / Entry Fee / Map), and a sticky black
"Join Tournament" CTA.

### 5.9 Join Tournament (12)
Confirmation summary card (title, entry fee, payable amount) followed by a
payment-method list (UPI, Wallet, Card / Net Banking) and a black pay button.
Selecting a method expands an inline detail field.

### 5.10 Payment Success (13)
Centered check mark, "Payment Successful", the tournament name, a two-button
stack ("View Tournament", "Back to Home"). No confetti, no colour.

### 5.11 My Tournaments (14)
Three chips (Upcoming / Live / Completed) then compact cards with tournament
name, schedule, and a status label.

### 5.12 Match Lobby (15)
Credential card with Match ID, Room ID (masked until release), password,
"Room opens in" countdown, and a black "Join in Free Fire" CTA. Below it a
collapsible "Match Rules" row.

### 5.13 Live Match (16)
Large countdown/status hero, then "Live Standings" and a "Match in Progress"
row. The app mirrors this with live match state and result submission entry.

### 5.14 Match Results (17)
Podium for the top three (rank, team name, kills), then a dense table of rank,
team, kills, and points with a black header row. Numbers dominate.

### 5.15 Leaderboard (18)
Two scope chips (Overall / Monthly) and ranked rows. Top three ranks are black
circles; the rest are light grey. Each row carries avatar, name, points.

### 5.16 Wallet (19)
Balance card, three equal action tiles (Add Money / Withdraw / Transactions),
then a recent-transactions list with signed amounts. Debits are ink, credits
are dark grey with a "+" prefix (no green).

### 5.17 Add Money (20)
Amount field with quick chips (₹100 / ₹200 / ₹500 / ₹1,000), payment-method
list, and a black "Add ₹100" CTA that reflects the entered amount.

### 5.18 Withdraw (21)
Amount field, minimum-withdrawal hint, UPI ID field, and a black Withdraw CTA.

### 5.19 Transactions (22)
Filter chips (All / Add Money / Withdraw / Entry) and a grouped ledger list.
Each row has an icon, description, timestamp, and signed amount.

### 5.20 Refer & Earn (23)
Gift glyph, headline, explanation, a referral-code card with a copy action,
a "Share Invite Link" pill, and two stat cells (Total Earned / Successful
Referrals).

### 5.21 Profile (24)
Centered avatar with edit badge, display name, `@username · ID`, then a grouped
settings list: Edit Profile, Game Details, Settings, Help & Support, About,
Log Out. "Delete Account" sits apart in muted text.

### 5.22 Game Details (25)
Free Fire UID and IGN fields, optional level, and a black Save CTA. This is the
same data the onboarding RPC validates.

### 5.23 Settings (26)
Grouped rows with trailing values/switches: Notifications, App Language,
Theme, Privacy Policy, Terms & Conditions, Delete Account. Mostly light-grey
glyph chips.

### 5.24 Help & Support (27)
FAQ, Contact Support, Report a Problem, Feedback rows, each with a chevron.

---

## 6. Motion and feedback

- Button press: scale to ~0.98 with no ripple colour.
- Sheet/dialog: standard slide/fade, white scrim at ~20 %.
- Countdowns tick once per second only while visible.
- Loading is a thin black circular indicator; no skeleton shimmer on lists
  (keeps low-end devices cheap).
- No sound, no haptics required by the reference.

---

## 7. Accessibility and content rules

- Minimum touch target 48 dp; icon buttons are 44 dp with 48 dp semantics.
- Text contrast: ink on white ≈ 19:1; secondary grey on white ≈ 5.3:1.
- Every icon-only control has a content description.
- Selected tabs expose `selected` semantics.
- Amounts always carry the ₹ symbol and thousands separators.
- Error text is inline, never a colour-only signal.
- The layout must remain usable with system font scale up to 1.3×; long names
  use ellipsis rather than clipping.

---

## 8. What the Android app intentionally adapts

- **Email-first auth** instead of phone OTP, because the deployed Supabase
  project uses email confirmation. The OTP panel informs the six-cell layout,
  not the transport.
- **Persistent floating bottom bar** on the five primary destinations; detail
  screens use the same bar for continuity.
- **Leaderboard** remains a primary tab (the reference shows Wallet). Wallet is
  still reachable from the home grid and profile, and the leaderboard is a core
  competitive surface.
- **Photos**: the reference's Free Fire banner is a licensed game asset that was
  not supplied. Tournament banners are loaded from the tournament record when
  present; otherwise the card falls back to a neutral typographic treatment.
  No internet image is substituted.
