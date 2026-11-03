package com.hmx.arena.core.util

import com.hmx.arena.core.model.Tournament
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrationRulesTest {

    private fun tournament(
        id: String = "t1",
        maxSlots: Int = 64,
        filled: Int = 0,
        status: String = "registration_open",
        maxTeamSize: Int = 1,
        entryFeePaise: Long = 0,
    ) = Tournament(
        id = id,
        slug = id,
        title = "Test Cup",
        maxSlots = maxSlots,
        slotsFilled = filled,
        status = status,
        maxTeamSize = maxTeamSize,
        entryFeePaise = entryFeePaise,
        entryType = if (entryFeePaise == 0L) "free" else "paid",
    )

    @Test
    fun happySoloRegistrationHasNoBlocker() {
        assertNull(
            RegistrationRules.blocker(
                tournament = tournament(),
                hasGameAccount = true,
                hasTeam = false,
                alreadyRegistered = false,
            ),
        )
    }

    @Test
    fun duplicateRegistrationWinsOverOtherChecks() {
        assertEquals(
            RegistrationBlocker.AlreadyRegistered,
            RegistrationRules.blocker(tournament(), hasGameAccount = false, hasTeam = false, alreadyRegistered = true),
        )
    }

    @Test
    fun gameAccountIsRequired() {
        assertEquals(
            RegistrationBlocker.GameAccountRequired,
            RegistrationRules.blocker(tournament(), hasGameAccount = false, hasTeam = true, alreadyRegistered = false),
        )
    }

    @Test
    fun teamIsRequiredOnlyForTeamModes() {
        assertEquals(
            RegistrationBlocker.TeamRequired,
            RegistrationRules.blocker(
                tournament(maxTeamSize = 4),
                hasGameAccount = true,
                hasTeam = false,
                alreadyRegistered = false,
            ),
        )
        assertNull(
            RegistrationRules.blocker(
                tournament(maxTeamSize = 1),
                hasGameAccount = true,
                hasTeam = false,
                alreadyRegistered = false,
            ),
        )
    }

    @Test
    fun closedRegistrationIsRejected() {
        assertEquals(
            RegistrationBlocker.RegistrationClosed,
            RegistrationRules.blocker(
                tournament(status = "live"),
                hasGameAccount = true,
                hasTeam = true,
                alreadyRegistered = false,
            ),
        )
    }

    @Test
    fun blockerMessagesAreActionable() {
        assertTrue(RegistrationRules.message(RegistrationBlocker.GameAccountRequired).contains("Free Fire"))
        assertTrue(RegistrationRules.message(RegistrationBlocker.TeamRequired).contains("team"))
    }

    @Test
    fun tournamentDerivedFields() {
        val full = tournament(maxSlots = 10, filled = 10)
        assertEquals(0, full.slotsRemaining)
        assertTrue(full.isFree)
        assertFalse(tournament(entryFeePaise = 5000).isFree)
        assertEquals(7, tournament(maxSlots = 10, filled = 3).slotsRemaining)
        assertEquals(0, tournament(maxSlots = 10, filled = 99).slotsRemaining)
    }
}
