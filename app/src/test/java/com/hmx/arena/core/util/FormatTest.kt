package com.hmx.arena.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FormatTest {

    @Test
    fun moneyUsesRupeeFormatting() {
        assertEquals("₹0", Format.money(0))
        assertEquals("₹15", Format.money(1500))
        assertEquals("₹1,500", Format.money(150000))
        assertTrue(Format.money(10000000).startsWith("₹"))
    }

    @Test
    fun initialsHandleCommonNames() {
        assertEquals("SS", Format.initials("shadow strike"))
        assertEquals("HO", Format.initials("hmx_owner"))
        assertEquals("S", Format.initials("solo"))
        assertEquals("H", Format.initials("   "))
    }

    @Test
    fun statusAndModeLabelsAreHumanReadable() {
        assertEquals("Registration open", Format.tournamentStatusLabel("registration_open"))
        assertEquals("Live", Format.tournamentStatusLabel("live"))
        assertEquals("Completed", Format.tournamentStatusLabel("completed"))
        assertEquals("Upcoming", Format.tournamentStatusLabel("draft"))
        assertEquals("Clash Squad", Format.modeLabel("clash_squad"))
        assertEquals("Duo", Format.modeLabel("duo"))
    }

    @Test
    fun parsingRejectsInvalidTimestamps() {
        assertNull(Format.parseInstant(null))
        assertNull(Format.parseInstant(""))
        assertNull(Format.parseInstant("not-a-date"))
        assertEquals("—", Format.dateTime("not-a-date"))
        assertEquals("—", Format.countdown(null))
        assertNotNull(Format.parseInstant("2026-11-03T10:00:00Z"))
        assertTrue(Format.date("2026-11-03T10:00:00Z").isNotBlank())
    }

    @Test
    fun countdownHandlesPastAndFuture() {
        assertEquals("Starting now", Format.countdown(Instant.now().minusSeconds(60).toString()))
        val future = Format.countdown(Instant.now().plusSeconds(3600).toString())
        assertTrue(future.contains(":"))
        val further = Format.countdown(Instant.now().plusSeconds(172800).toString())
        assertTrue(further.endsWith("h"))
    }

    @Test
    fun relativeHandlesRecentTimestamps() {
        assertEquals("", Format.relative(null))
        assertEquals("just now", Format.relative(Instant.now().minusSeconds(20).toString()))
        val minutes = Format.relative(Instant.now().minusSeconds(600).toString())
        assertTrue(minutes.endsWith("m ago"))
    }
}
