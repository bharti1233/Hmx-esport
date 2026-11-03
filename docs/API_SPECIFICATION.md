# API Specification

Base URL: `https://ahsvxxhaowwhqermruhz.supabase.co`
Client key: publishable key from `gradle.properties` (safe to ship).
All requests send `apikey` and, when signed in, `Authorization: Bearer <access_token>`.

## 1. Authentication (`/auth/v1`)

| Method | Path | Body | Result |
| --- | --- | --- | --- |
| POST | `/signup` | `email`, `password`, `data{username,display_name}` | session or user without session (email confirmation required) |
| POST | `/token?grant_type=password` | `email`, `password` | session |
| POST | `/token?grant_type=refresh_token` | `refresh_token` | session |
| POST | `/verify` | `type=signup`, `email`, `token` | session |
| POST | `/resend` | `type=signup`, `email` | empty |
| POST | `/recover` | `email`, `redirect_to` | empty |
| PUT | `/user` | `password` or `email` | updated user |
| POST | `/logout` | — | empty |

Session shape: `access_token`, `refresh_token`, `expires_in`, `user{id,email}`.

Errors use `{ "message" | "msg" | "error_description" | "error": "..." }`.
`RemoteDataSource` normalises them into `ApiException(message)`.

## 2. PostgREST (`/rest/v1`)

Filters follow PostgREST syntax:

```
?select=*&id=eq.<uuid>&order=created_at.desc&limit=50
```

The client uses `Prefer: return=representation` for inserts/updates and
`Prefer: resolution=merge-duplicates` where upsert is required.

### Tables read directly

| Table | Filter used |
| --- | --- |
| `tournaments` | status in, id in, order starts_at |
| `tournament_rules` | tournament_id eq, order position |
| `registrations` | order created_at (RLS limits rows) |
| `matches` | tournament_id eq / id eq |
| `match_results` | match_id eq |
| `result_disputes` | result_id eq |
| `teams` | id eq |
| `team_members` | team_id eq / user_id eq |
| `team_invitations` | invitee/inviter (RLS limits rows) |
| `notifications` | user_id eq, order created_at desc |
| `announcements` | is_published eq true |
| `player_game_accounts` | user_id eq |
| `wallet_accounts` | user_id eq |
| `financial_ledger` | user_id eq, order created_at desc |
| `support_tickets` | user_id eq, order created_at desc |
| `support_messages` | ticket_id eq, order created_at asc |
| `referral_claims` | referrer_id eq |
| `app_config` | key in |
| `public_profiles` (view) | id eq, search filters |

### Tables written directly

| Table | Operation | Guard |
| --- | --- | --- |
| `profiles` | update display_name/avatar/bio/region/onboarding_completed | own row, column grant |
| `player_game_accounts` | insert/update/delete | own row |
| `support_messages` | insert | sender = self and ticket owner |
| `notifications` | update read_at | own row |
| `reports` | insert | reporter = self |

All other mutations go through RPCs.

## 3. RPCs (`/rest/v1/rpc/<name>`)

| RPC | Arguments | Returns |
| --- | --- | --- |
| `my_profile` | — | full private profile row |
| `create_team` | `p_name`, `p_tag`, `p_game` | team |
| `invite_team_member` | `p_team_id`, `p_user_id` | invitation |
| `respond_team_invitation` | `p_invitation_id`, `p_accept` | invitation |
| `leave_team` | `p_team_id` | void |
| `transfer_team_ownership` | `p_team_id`, `p_new_owner` | void |
| `register_for_tournament` | `p_tournament_id`, `p_team_id?` | registration |
| `cancel_registration` | `p_registration_id` | void |
| `get_match_credentials` | `p_match_id` | room id/password |
| `submit_match_result` | `p_match_id`, `p_placement`, `p_kills`, `p_note?`, `p_evidence_url?`, `p_evidence_size?`, `p_evidence_mime?` | result |
| `raise_dispute` | `p_result_id`, `p_reason` | dispute |
| `get_leaderboard` | `p_scope`, `p_period`, `p_region?`, `p_tournament_id?`, `p_limit`, `p_offset` | ranked rows |
| `claim_referral` | `p_code` | referral claim |
| `request_account_deletion` | — | void |
| `mark_notifications_read` | — | void |
| `submit_support_ticket` | `p_category`, `p_subject`, `p_body` | ticket |

RPC errors are plain exceptions with stable codes, for example
`not_authenticated`, `registration_closed`, `tournament_full`,
`already_registered`, `game_account_required`, `team_required`,
`referrals_disabled`, `invalid_referral_code`.

## 4. Storage

| Operation | Path |
| --- | --- |
| Upload avatar | `POST /storage/v1/object/avatars/<uid>/avatar-<ts>.jpg` |
| Upload team logo | `POST /storage/v1/object/team-logos/<teamId>/logo-<ts>` |
| Upload evidence | `POST /storage/v1/object/result-evidence/<uid>/<matchId>-<ts>` |
| Public URL | `GET /storage/v1/object/public/<bucket>/<path>` |

Uploads send the correct `Content-Type`; evidence is never made public.

## 5. Client-side conventions

- Mutating RPCs send an idempotency key header where supported.
- 401 triggers one blocking refresh and retry through the OkHttp `Authenticator`.
- Network failures fall back to the Room tournament cache where available.
- No GraphQL, realtime or Edge Functions are used in this version.
- Rate limiting is whatever Supabase Auth/PostgREST applies; the client does
  its own light debounce on search and disables buttons while a call runs.
