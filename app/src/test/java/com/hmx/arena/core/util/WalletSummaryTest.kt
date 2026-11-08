package com.hmx.arena.core.util

import com.hmx.arena.core.model.LedgerEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Wallet tiles are derived from the immutable ledger. These tests pin the
 * documented reference-type rules so a future change cannot silently relabel
 * deposits as winnings (or invent money).
 */
class WalletSummaryTest {

    private fun entry(
        type: String,
        amount: Long,
        reference: String? = null,
        description: String = "",
    ) = LedgerEntry(
        id = "ledger-${type}-$amount-$reference",
        userId = "user-1",
        entryType = type,
        amountPaise = amount,
        balanceAfterPaise = 0,
        referenceType = reference,
        description = description,
    )

    @Test
    fun emptyLedgerProducesZeroTiles() {
        val summary = walletSummary(emptyList())
        assertEquals(0L, summary.depositedPaise)
        assertEquals(0L, summary.winningPaise)
        assertEquals(0L, summary.earningsPaise)
        assertEquals(0L, summary.payoutsPaise)
    }

    @Test
    fun depositsUsePaymentReferenceTypes() {
        val summary = walletSummary(
            listOf(
                entry("credit", 10_000, "deposit"),
                entry("credit", 25_000, "topup"),
                entry("credit", 5_000, "payment_order"),
            ),
        )
        assertEquals(40_000L, summary.depositedPaise)
        assertEquals(0L, summary.winningPaise)
        assertEquals(0L, summary.earningsPaise)
    }

    @Test
    fun prizesUsePrizeAndMatchRewardReferenceTypes() {
        val summary = walletSummary(
            listOf(
                entry("credit", 50_000, "prize"),
                entry("credit", 15_000, "match_reward"),
                entry("credit", 7_500, "Winnings"),
            ),
        )
        assertEquals(72_500L, summary.winningPaise)
        assertEquals(0L, summary.depositedPaise)
    }

    @Test
    fun unlabelledCreditsStayInEarnings() {
        val summary = walletSummary(
            listOf(
                entry("credit", 2_000, "referral"),
                entry("credit", 1_000, null),
                entry("adjustment", 9_000, "adjustment"),
            ),
        )
        assertEquals(3_000L, summary.earningsPaise)
        assertEquals(0L, summary.depositedPaise)
        assertEquals(0L, summary.winningPaise)
    }

    @Test
    fun payoutTilesUseAbsoluteDebitAmounts() {
        val summary = walletSummary(
            listOf(
                entry("debit", -30_000, "payout"),
                entry("debit", -12_000, "withdrawal"),
                // Entry fees are spending, not payouts.
                entry("debit", -5_000, "match_entry"),
                // Reserved money is not settled yet.
                entry("hold", -10_000, "payout"),
                entry("release", 10_000, "payout"),
            ),
        )
        assertEquals(42_000L, summary.payoutsPaise)
        assertEquals(0L, summary.depositedPaise)
    }

    @Test
    fun unknownFutureReferencesAreNotGuessed() {
        val summary = walletSummary(
            listOf(
                entry("credit", 99_000, "season_pass_bonus"),
                entry("debit", -99_000, "marketplace"),
            ),
        )
        assertEquals(99_000L, summary.earningsPaise)
        assertEquals(0L, summary.payoutsPaise)
        assertEquals(0L, summary.depositedPaise)
        assertEquals(0L, summary.winningPaise)
    }
}
