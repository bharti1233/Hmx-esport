package com.hmx.arena.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchModelTest {

    private fun match(
        id: String = "152f0458-c2fc-4e16-8bad-7603d7ec175c",
        matchNumber: String? = null,
        maxSlots: Int? = null,
        slotsFilled: Int = 0,
        entryFeePaise: Long = 0,
    ) = Match(
        id = id,
        matchNumber = matchNumber,
        scheduledAt = "2026-11-11T08:44:13+00:00",
        maxSlots = maxSlots,
        slotsFilled = slotsFilled,
        entryFeePaise = entryFeePaise,
    )

    @Test
    fun capacityIsOnlyKnownWhenSlotsAreConfigured() {
        val unknown = match()
        assertFalse(unknown.capacityKnown)
        assertFalse(unknown.isFull)
        assertEquals(0, unknown.slotsRemaining)

        val open = match(maxSlots = 48, slotsFilled = 10)
        assertTrue(open.capacityKnown)
        assertFalse(open.isFull)
        assertEquals(38, open.slotsRemaining)

        val full = match(maxSlots = 48, slotsFilled = 48)
        assertTrue(full.isFull)
        assertEquals(0, full.slotsRemaining)
    }

    @Test
    fun overfillNeverReportsNegativeRemainingSlots() {
        val overfilled = match(maxSlots = 48, slotsFilled = 55)
        assertTrue(overfilled.isFull)
        assertEquals(0, overfilled.slotsRemaining)
    }

    @Test
    fun adminMatchNumberWinsOverTheIdFallback() {
        assertEquals("Match #BR-14", match(matchNumber = "BR-14").matchNumberLabel)
        assertEquals("Match 152F0458", match().matchNumberLabel)
        assertEquals("Match 152F0458", match(matchNumber = "   ").matchNumberLabel)
    }

    @Test
    fun entryTypeDerivesFromTheEntryFeeUntilAnAdminTypeIsSet() {
        assertEquals("Free Entry", match().typeLabel)
        assertEquals("Paid Entry", match(entryFeePaise = 5000).typeLabel)
        assertEquals(
            "Custom",
            match(entryFeePaise = 5000).copy(
                tournamentType = LookupOption(id = "tt", name = "Custom"),
            ).typeLabel,
        )
    }
}
