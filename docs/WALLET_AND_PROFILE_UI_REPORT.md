# HMX Arena — Wallet and Account UI Report

Branch: `feature/hmx-admin-tournament-match-management`
Base: `f0ddabc`
Implementation commit: `8ede8fa`
Date: 2026-11-08

---

## 1. Result

Wallet and Account are now first-class tab destinations (Wallet and Account)
instead of secondary screens. Every financial value is read-only and comes
from the database. No funding, withdrawal, refund or payout action is enabled
while the compliance flags remain off.

## 2. Wallet

### Layout

The Wallet tab (`wallet`) uses:

1. A refractive balance hero:
   - authoritative `wallet_accounts.balance_paise` through `Format.money`;
   - currency label;
   - reserved amount when greater than zero;
   - deposited and winning hero stats.
2. A four-tile summary grid: Deposited, Winning, Earnings, Payouts.
3. A compliance notice explaining that paid tournaments, funding,
   withdrawals, refunds and payouts are server-disabled.
4. Recent activity (six newest rows) with a "See all" link.
5. Feature-status rows read from `app_config`.
6. A support link for balance questions.

The refresh action is a `GlassIconButton` with a content description and a
disabled state while a refresh is running.

### Authoritative totals

The activity list intentionally reads only the 50 newest ledger rows
(`PlayerRepository.ledger()`), which is right for a screen but is **not** a
lifetime total. To avoid presenting a partial sum as lifetime money, the
redesign adds:

- migration `0020_wallet_totals.sql`;
- RPC `public.my_wallet_totals()`;
- model `WalletTotals`;
- `PlayerRepository.walletTotals()`.

The RPC aggregates the full immutable `financial_ledger` for `auth.uid()` and
returns balance, reserved, currency, the four buckets and an entry count. It is
`SECURITY DEFINER` with a fixed `search_path`, filters on `auth.uid()` itself,
and is granted to `authenticated` only. Anonymous execution is denied
(verified: `permission denied for function my_wallet_totals`).

Bucket rules (mirrored by `core/util/WalletSummary.kt`):

| Bucket | Rule |
|---|---|
| Deposited | credit rows whose `reference_type` is a payment/top-up flow |
| Winning | credit rows for prize or match-reward allocations |
| Earnings | every other credit (referral, bonus, adjustment, unlabelled) |
| Payouts | absolute value of debit rows for payout/withdrawal flows |

Hold/release rows are reserved states and are excluded. Entry-fee debits are
spending, not payouts. Unknown future reference types are deliberately not
guessed.

If the RPC is unavailable, the tiles show `—` and an inline notice says
lifetime totals are temporarily unavailable; the hero balance still comes
from the wallet account. The 50-row list is never substituted as a lifetime
total.

Rolled-back SQL verification (8 synthetic rows):

```
deposited = 20000, winning = 50000, earnings = 3000,
payouts = 30000, entry_count = 8
```

which matches the documented rules (referral 2,000 + unlabelled 1,000 in
earnings; hold excluded from payouts).

### Ledger rows

Each row shows:

- description (or a humanised entry type);
- entry-type pill (Credit/Debit/Hold/Release/Adjustment);
- status pill (Settled/Reserved/Released/Recorded);
- reference type when present;
- date/time;
- signed amount and balance after.

The Transactions screen (`transactions`) lists the full 50-row window with the
same row component and a balance header.

### States

- loading: `LoadingBlock("Loading wallet")`;
- hard error with no wallet: `InlineError` + retry;
- refresh error with existing data: inline error, data preserved;
- empty ledger: explicit "No wallet activity" state (free tournaments do not
  move money);
- no financial action buttons are rendered at all, so there is nothing to
  accidentally enable client-side.

## 3. Account / Profile

`ProfileScreen` keeps its route (`profile`) and all existing actions, with the
visible title changed to "Account" and the `Account` tab selected.

Preserved:

- avatar and display identity;
- points / matches / wins statistics;
- game accounts;
- team and invitations;
- wallet and transaction links;
- referral link (also available as the Earn tab);
- support and settings;
- theme selection (Light / Dark / System) and logout;
- edit profile.

The Account tab adds the floating bottom bar selected on `profile`. No action
was removed and no destination was redirected away from its existing screen.

## 4. Earn tab

`ReferralScreen` is now the Earn tab (`referral`):

- title "Earn", no back arrow, bottom bar selected on `referral`;
- real invite code and referral status;
- Copy, system Share and "Share to WhatsApp";
- WhatsApp uses `setPackage("com.whatsapp")` and falls back to the system
  chooser when WhatsApp is absent, so the button never dead-ends;
- claim flow and existing referral records are unchanged;
- factual copy: referrals are reviewed by staff, no cash reward promised.

## 5. Navigation and state

| Destination | Route | Tab |
|---|---|---|
| Wallet | `wallet` | Wallet |
| Transactions | `transactions` | drill-in |
| Account | `profile` | Account |
| Earn | `referral` | Earn |

`openTab` keeps `saveState`/`restoreState`, so switching between Wallet,
Account and the other tabs preserves each tab's state.

## 6. Tests

- `WalletSummaryTest`
  - empty ledger -> zero tiles;
  - deposits use payment reference types;
  - prizes match prize/reward reference types;
  - unlabelled credits stay in earnings;
  - payouts use absolute debit values;
  - entry fees and hold/release do not become payouts;
  - unknown future references are not guessed.
- `NavigationRoutesTest`
  - Wallet and Account are part of the five-tab contract;
  - Earn maps to `referral`.

## 7. Limitations

- The live database has no ledger rows and no production admin wallet flow, so
  the screen's populated state was verified with rolled-back synthetic rows at
  the SQL layer, not with real money.
- Paid operations remain disabled by server flags. The UI deliberately offers
  no client-side way to bypass them.
- No device/emulator was available; font-scale and dark-mode rendering were
  not physically tested.
