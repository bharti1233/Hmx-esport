package com.hmx.arena.core.util

import com.hmx.arena.core.model.LedgerEntry

/**
 * Ledger-derived wallet tiles.
 *
 * The ledger is the only authoritative financial record, and it is immutable.
 * These aggregates never mutate money and never estimate: every value is the
 * sum of real ledger rows whose `reference_type` falls into a documented bucket.
 *
 * Rules:
 *  * `deposited`  – credit rows for money added through a payment/top-up flow.
 *  * `winnings`   – credit rows for prize or match-reward allocations.
 *  * `earnings`   – all other credit rows (referral review credits, bonuses,
 *                   adjustments). Unlabelled credits are deliberately not
 *                   counted as a deposit or a prize.
 *  * `payouts`    – absolute value of debit rows for payout/withdrawal flows.
 *
 * Debit rows for entry fees are real spending, not payouts, so they are
 * excluded from the payout tile. If a future ledger uses a new reference type,
 * the value stays out of the four buckets until the rule is extended and
 * tested — the screen must not guess.
 */
data class WalletSummary(
    val depositedPaise: Long = 0,
    val winningPaise: Long = 0,
    val earningsPaise: Long = 0,
    val payoutsPaise: Long = 0,
)

private val depositReferences = setOf(
    "deposit",
    "topup",
    "top_up",
    "add_coins",
    "payment",
    "payment_order",
    "payment_orders",
)

private val winningReferences = setOf(
    "prize",
    "prize_allocation",
    "prize_allocations",
    "match_reward",
    "match_win",
    "winning",
    "winnings",
)

private val payoutReferences = setOf(
    "payout",
    "payout_record",
    "payout_records",
    "withdrawal",
    "withdraw",
    "redeem",
)

private fun LedgerEntry.reference(): String =
    referenceType?.trim()?.lowercase().orEmpty()

fun walletSummary(entries: List<LedgerEntry>): WalletSummary {
    var deposited = 0L
    var winnings = 0L
    var earnings = 0L
    var payouts = 0L

    entries.forEach { entry ->
        val reference = entry.reference()
        when (entry.entryType.lowercase()) {
            "credit" -> when {
                reference in depositReferences -> deposited += entry.amountPaise
                reference in winningReferences -> winnings += entry.amountPaise
                else -> earnings += entry.amountPaise
            }
            "debit" -> if (reference in payoutReferences) {
                payouts += kotlin.math.abs(entry.amountPaise)
            }
            // hold/release/adjustment are reserved states, not settled money.
        }
    }

    return WalletSummary(
        depositedPaise = deposited.coerceAtLeast(0),
        winningPaise = winnings.coerceAtLeast(0),
        earningsPaise = earnings.coerceAtLeast(0),
        payoutsPaise = payouts.coerceAtLeast(0),
    )
}
