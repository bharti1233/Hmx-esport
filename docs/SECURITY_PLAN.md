# Security Plan

## 1. Trust boundaries

| Boundary | Control |
| --- | --- |
| Device ↔ Supabase | TLS; publishable key only. |
| User ↔ user | Row Level Security on every table and storage object. |
| Player ↔ room credentials | Security-definer RPC checks registration. |
| Player ↔ evidence | Private bucket + owner/party policy. |
| Staff ↔ platform | Role table + `is_admin()` checks; no admin UI in the app. |
| CI ↔ GitHub | Repository secrets; no key material in source. |

The service-role key never leaves the Supabase dashboard/server. The Android
build embeds only the publishable key, which is designed to be public.

## 2. Authentication

- Email + password with Supabase Auth; minimum 8 characters enforced client-side
  and by the auth server.
- Email verification is required before sign-in is useful; the UI handles the
  "confirm your email" state and resend.
- Password reset uses emailed links. The app accepts
  `hmxarena://auth/reset` and supports both fragment tokens and `token_hash`.
- Access tokens live in memory; refresh tokens are stored in app-private
  DataStore. `allowBackup=false`, so they are not backed up.
- Sign out revokes the refresh token server-side and clears local state.
- Account deletion requests soft-delete the profile and sign the user out;
  audit/financial records are retained as described in the Privacy Policy.

## 3. Database security

- RLS is enabled on all 33 public tables.
- Policies follow least privilege: own row, team membership, participant, admin.
- `profiles` contact columns are revoked from `authenticated`; only
  `my_profile()` returns them. `public_profiles` is the public projection.
- Security-definer functions pin `search_path = public, pg_temp` and validate
  `auth.uid()` themselves.
- RPC execute grants are explicit; `anon` can read the public catalogue and
  leaderboard only.
- `audit_logs` is append-only: update/delete are blocked by trigger/policy.
- `idempotency_keys` prevents duplicate registrations/results on retries.
- Capacity is computed inside `register_for_tournament` under a transaction;
  the client never writes `slots_filled`.

## 4. Storage

- `avatars`, `team-logos`, `tournament-banners`: public read; write restricted
  to the owner/staff via policies and path conventions.
- `result-evidence`: private. Read is limited to the uploader, dispute parties
  and admins. The app stores object metadata, not public URLs.
- Uploads are size-checked client-side (avatars ≤ 4 MB) and MIME-checked.
- No executable content types are accepted by the UI pickers.

## 5. Input validation

Client-side (`core/util/Validation.kt`):

- email format, username `^[A-Za-z0-9_]{3,24}$`, password length/strength,
- Free Fire UID `[A-Za-z0-9]{4,24}`, IGN 2-24, level 1-100,
- team name 3-24, tag 2-5, dispute reason 10-1000, 6-digit OTP.

Server-side validation is authoritative: constraints, enums, and RPC checks.
The client never constructs SQL; PostgREST filters are typed and encoded.

## 6. Money and compliance

- `paid_tournaments_enabled`, `payment_orders_enabled`, `refunds_enabled`,
  `payouts_enabled` are `false` in `app_config`.
- No gateway SDK, card data, UPI intent or fake transaction generator exists.
- Wallet screens display only server rows and an explicit disabled notice.
- Ledger rows are immutable to clients; only service-role/staff flows write them.
- Enabling paid features requires a separate compliance review.

## 7. Secrets

- No PAT, service key, JWT secret or signing key is committed.
- `.gitignore` excludes `.env*`, keystores and APKs.
- The GitHub PAT used to push this branch must be revoked after delivery.
- CI has no production credentials beyond the public Supabase endpoint.

## 8. Threats considered

| Threat | Mitigation |
| --- | --- |
| Read another player's email/phone | Column revoke + `my_profile()` + RLS tests. |
| Modify another profile/team | Owner checks in policies and RPCs. |
| Steal room credentials | RPC registration check; credentials table RLS. |
| Fake match result | One result per player/match, staff verification, evidence, disputes. |
| Duplicate registration/result | Unique constraints + idempotency + transactional RPC. |
| Double-spend wallet | Reserved balance and ledger constraints (flows disabled). |
| Referral abuse | 7-day window, self-referral block, one claim per user. |
| Evidence leak | Private bucket + party-scoped policy. |
| Brute force | Supabase Auth rate limits; no custom OTP endpoint. |
| Token theft via backup | `allowBackup=false`; refresh token in private storage. |

## 9. Known gaps

- No certificate pinning (Supabase rotates certificates).
- No device attestation / Play Integrity.
- No admin MFA flow is documented because there is no admin UI.
- Email template and redirect allowlist are deployment configuration, not code.
- No automated dependency scanning beyond GitHub/Dependabot defaults.
- Client-side rate limiting only; server limits come from Supabase.
