-- 0020: authoritative wallet totals for the Account tab.
--
-- The app reads the 50 newest ledger rows for the activity list, which is the
-- right size for a screen but cannot be used to compute lifetime totals. This
-- RPC aggregates the whole immutable ledger for the signed-in user in the
-- database, so the Wallet tiles never depend on how many rows the client
-- happened to fetch.
--
-- The RPC is read-only and SECURITY DEFINER only so the aggregate has a stable
-- search_path and a single owner. It filters on auth.uid() itself, so a caller
-- can never ask for another player's totals. No write path is added.
--
-- Buckets intentionally mirror `core/util/WalletSummary.kt`:
--   deposited = credits from a payment/top-up flow
--   winning   = credits for prizes or match rewards
--   earnings  = every other credit (referral, bonus, adjustment, unlabelled)
--   payouts   = absolute value of debits for payout/withdrawal flows
-- Hold/release rows are reserved states and stay out of every bucket.

create or replace function public.my_wallet_totals()
returns table (
    balance_paise bigint,
    reserved_paise bigint,
    currency text,
    deposited_paise bigint,
    winning_paise bigint,
    earnings_paise bigint,
    payouts_paise bigint,
    entry_count bigint
)
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
declare
    v_uid uuid := auth.uid();
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    return query
    with totals as (
        select
            coalesce(sum(l.amount_paise) filter (
                where l.entry_type = 'credit'
                  and lower(coalesce(l.reference_type, '')) in (
                      'deposit', 'topup', 'top_up', 'add_coins',
                      'payment', 'payment_order', 'payment_orders'
                  )
            ), 0)::bigint as deposited,
            coalesce(sum(l.amount_paise) filter (
                where l.entry_type = 'credit'
                  and lower(coalesce(l.reference_type, '')) in (
                      'prize', 'prize_allocation', 'prize_allocations',
                      'match_reward', 'match_win', 'winning', 'winnings'
                  )
            ), 0)::bigint as winning,
            coalesce(sum(l.amount_paise) filter (
                where l.entry_type = 'credit'
                  and lower(coalesce(l.reference_type, '')) not in (
                      'deposit', 'topup', 'top_up', 'add_coins',
                      'payment', 'payment_order', 'payment_orders',
                      'prize', 'prize_allocation', 'prize_allocations',
                      'match_reward', 'match_win', 'winning', 'winnings'
                  )
            ), 0)::bigint as earnings,
            coalesce(sum(abs(l.amount_paise)) filter (
                where l.entry_type = 'debit'
                  and lower(coalesce(l.reference_type, '')) in (
                      'payout', 'payout_record', 'payout_records',
                      'withdrawal', 'withdraw', 'redeem'
                  )
            ), 0)::bigint as payouts,
            count(l.id)::bigint as entry_count
        from public.financial_ledger l
        where l.user_id = v_uid
    )
    select
        greatest(coalesce(w.balance_paise, 0), 0)::bigint,
        greatest(coalesce(w.reserved_paise, 0), 0)::bigint,
        coalesce(w.currency, 'INR'),
        greatest(t.deposited, 0),
        greatest(t.winning, 0),
        greatest(t.earnings, 0),
        greatest(t.payouts, 0),
        t.entry_count
    from totals t
    left join public.wallet_accounts w on w.user_id = v_uid;
end;
$$;

comment on function public.my_wallet_totals() is
    'Lifetime wallet totals for the signed-in player, aggregated over the full immutable ledger.';

revoke all on function public.my_wallet_totals() from public, anon;
grant execute on function public.my_wallet_totals() to authenticated;
