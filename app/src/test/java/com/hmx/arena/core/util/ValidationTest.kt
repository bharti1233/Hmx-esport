package com.hmx.arena.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {

    @Test
    fun emailRules() {
        assertTrue(Validation.isEmailValid("player@hmxarena.app"))
        assertTrue(Validation.isEmailValid(" first.last+tag@example.co.in "))
        assertFalse(Validation.isEmailValid("player"))
        assertFalse(Validation.isEmailValid("player@"))
        assertFalse(Validation.isEmailValid("@example.com"))
        assertFalse(Validation.isEmailValid("player@example"))
    }

    @Test
    fun usernameRules() {
        assertTrue(Validation.isUsernameValid("shadow_99"))
        assertTrue(Validation.isUsernameValid("abc"))
        assertFalse(Validation.isUsernameValid("ab"))
        assertFalse(Validation.isUsernameValid("has space"))
        assertFalse(Validation.isUsernameValid("dash-name"))
        assertFalse(Validation.isUsernameValid("a".repeat(25)))
    }

    @Test
    fun passwordRules() {
        assertTrue(Validation.isPasswordValid("longenough"))
        assertFalse(Validation.isPasswordValid("short"))
        assertTrue(Validation.passwordStrength("Abcdef1!") >= 3)
        assertEquals(4, Validation.passwordStrength("VeryLongPassword1!"))
    }

    @Test
    fun gameIdentityRules() {
        assertTrue(Validation.isGameUidValid("123456789"))
        assertTrue(Validation.isGameUidValid("AB12CD34"))
        assertFalse(Validation.isGameUidValid("123"))
        assertFalse(Validation.isGameUidValid("1234 5678"))
        assertTrue(Validation.isIgnValid("Shadow Strike"))
        assertFalse(Validation.isIgnValid("A"))
        assertTrue(Validation.isLevelValid("1"))
        assertTrue(Validation.isLevelValid("100"))
        assertFalse(Validation.isLevelValid("0"))
        assertFalse(Validation.isLevelValid("101"))
        assertFalse(Validation.isLevelValid(""))
    }

    @Test
    fun teamAndDisputeRules() {
        assertTrue(Validation.isTeamNameValid("Night Owls"))
        assertFalse(Validation.isTeamNameValid("ab"))
        assertTrue(Validation.isTeamTagValid("NGTW"))
        assertFalse(Validation.isTeamTagValid("N"))
        assertFalse(Validation.isTeamTagValid("TOOLONG"))
        assertTrue(Validation.isDisputeReasonValid("Evidence shows different kills"))
        assertFalse(Validation.isDisputeReasonValid("too short"))
    }

    @Test
    fun otpRules() {
        assertTrue(Validation.isOtpValid("123456"))
        assertFalse(Validation.isOtpValid("12345"))
        assertFalse(Validation.isOtpValid("12345a"))
        assertFalse(Validation.isOtpValid(""))
    }

    @Test
    fun referralCodeNormalisation() {
        assertEquals("ABCD1234", Validation.normalizeCode("  abcd1234 "))
        assertNull(Validation.normalizeCode("   ").takeIf { it.isNotEmpty() })
    }
}
