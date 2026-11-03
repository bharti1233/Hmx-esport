# Product Requirements Document — HMX Arena

## 1. Product

HMX Arena is a native Android client for Free Fire tournaments: discovery,
registration, room credentials, result submission, disputes, teams,
leaderboards, referrals and support.

## 2. Users

| Persona | Needs |
| --- | --- |
| Solo player | Find a free tournament, register, get room details, submit results. |
| Team captain | Create a team, invite players, register the squad, manage roster. |
| Team member | Accept invitations, see the team's matches, leave the team. |
| Returning player | Track registrations, notifications, points and referral activity. |
| Support staff (out of app) | Read tickets and reply using the service role / admin tooling. |

## 3. Design requirements

- Pure white canvas, frosted glass surfaces, hairline borders, monochrome ink.
- No purple/blue/neon accents, no gradients, no dark mode, no glass blur hacks.
- Pill controls, generous spacing, large readable type.
- Every visible button must do something real. No "coming soon" handlers.
- Loading, empty, error and success states on every data screen.

## 4. Functional scope shipped

### Authentication
- Email + password sign up with unique username.
- Email verification code entry and resend.
- Sign in, sign out, password reset by email.
- Password recovery deep link (`hmxarena://auth/reset`) with fragment tokens or
  token hash, plus invalid/expired link dialog.
- Onboarding gate: new accounts must complete profile and game account.

### Discovery
- Home: featured tournaments, live/upcoming list, announcements, notifications
  entry, search entry, quick actions.
- Tournament catalogue with status filters and text search.
- Tournament detail: schedule, prize, entry type, slots, rules, participants,
  leaderboard, register/cancel actions.
- Global leaderboard with board and period filters.
- Player search by username/display name and public player profiles.
- Report player entry point.

### Competition
- My registrations with status and cancel action.
- Matches list grouped by upcoming/live/completed.
- Match detail with schedule, map, opponents and actions.
- Room credentials revealed only through the server RPC after registration.
- Result submission (placement, kills, screenshot evidence, optional note).
- Match results table and dispute creation.
- Tournament schedule and leaderboard tabs.

### Teams
- Create team (name, tag, game), view roster and invitations.
- Invite by username.
- Accept/decline invitations.
- Leave team, transfer ownership, remove member, delete team.
- Team logo upload to the public `team-logos` bucket.

### Wallet and money
- Wallet balance and immutable ledger views.
- Transaction history.
- **Funding, paid entry, refunds and payouts are disabled.** The UI explains this.
  No fake balance is ever displayed: the screens read real rows or show zero.

### Profile and account
- Profile with stats, avatar upload, edit display name/bio/region.
- Game accounts: add, edit, remove Free Fire IGN/UID/level.
- Settings, in-app notification list, help/FAQ, about, legal documents.
- Change email (confirmation link), password reset, account deletion request.
- Referral code sharing and referral claim with server-side validation.

### Support
- Ticket list, new ticket form with category, ticket thread, player replies.
- Staff replies appear in the same thread (written server-side).

## 5. Non-functional requirements

| Area | Requirement |
| --- | --- |
| Platform | Android 8.0+ (API 26), target API 36. |
| Architecture | MVVM + repositories, StateFlow UI state, Hilt DI, Room cache. |
| Security | RLS on every table, private evidence bucket, no service key in app. |
| Reliability | Explicit error and retry states; cache fallback for catalogue. |
| Accessibility | Content descriptions on icon buttons, 48dp targets, scalable text. |
| Offline | Tournament catalogue cached; all other actions require network. |

## 6. Out of scope / not shipped

- Push notifications (FCM), background reminders, email digests.
- Realtime subscriptions.
- SMS/phone OTP.
- Payment gateway, wallet top-up, refunds, prize payouts.
- iOS/web clients.
- Admin console inside the app.
- In-app chat, voice, streaming, spectating.

These are stated in the UI or docs rather than simulated.
