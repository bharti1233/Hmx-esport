package com.hmx.arena.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Security regression tests for the password-recovery deep link.
 *
 * The previous implementation accepted any `hmxarena://` URI and saved the
 * tokens it found, so a link crafted by another app could replace the signed-in
 * session. These tests pin the exact scheme/host/path/type and token rules.
 */
class RecoveryLinkParserTest {

    @Test
    fun acceptsSupabaseFragmentSessionLink() {
        val link = RecoveryLinkParser.parse(
            "hmxarena://auth/reset#access_token=abc.def.ghi&expires_in=3600" +
                "&refresh_token=xyz&token_type=bearer&type=recovery",
        )
        assertNotNull(link)
        assertEquals("abc.def.ghi", link!!.accessToken)
        assertEquals("xyz", link.refreshToken)
        assertNull(link.tokenHash)
        assertTrue(link.hasSessionTokens)
        assertTrue(link.isUsable)
    }

    @Test
    fun acceptsTokenHashQueryLink() {
        val link = RecoveryLinkParser.parse(
            "hmxarena://auth/reset?type=recovery&token_hash=hashed-token",
        )
        assertNotNull(link)
        assertEquals("hashed-token", link!!.tokenHash)
        assertFalse(link.hasSessionTokens)
        assertTrue(link.isUsable)
    }

    @Test
    fun decodesPercentEncodedValues() {
        val link = RecoveryLinkParser.parse(
            "hmxarena://auth/reset?type=recovery&token_hash=a%2Bb%2Fc%3D",
        )
        assertEquals("a+b/c=", link!!.tokenHash)
    }

    @Test
    fun rejectsNullAndBlank() {
        assertNull(RecoveryLinkParser.parse(null))
        assertNull(RecoveryLinkParser.parse(""))
        assertNull(RecoveryLinkParser.parse("   "))
        assertNull(RecoveryLinkParser.parse("not a uri"))
    }

    @Test
    fun rejectsWrongScheme() {
        assertNull(
            RecoveryLinkParser.parse(
                "https://auth/reset?type=recovery&token_hash=abc",
            ),
        )
        assertNull(
            RecoveryLinkParser.parse(
                "evil://auth/reset?type=recovery&token_hash=abc",
            ),
        )
    }

    @Test
    fun rejectsWrongHost() {
        assertNull(
            RecoveryLinkParser.parse(
                "hmxarena://evil/reset?type=recovery&token_hash=abc",
            ),
        )
    }

    @Test
    fun rejectsWrongPath() {
        assertNull(
            RecoveryLinkParser.parse(
                "hmxarena://auth/steal?type=recovery&token_hash=abc",
            ),
        )
    }

    @Test
    fun rejectsMissingOrWrongType() {
        assertNull(RecoveryLinkParser.parse("hmxarena://auth/reset?token_hash=abc"))
        assertNull(
            RecoveryLinkParser.parse(
                "hmxarena://auth/reset?type=signup&token_hash=abc",
            ),
        )
        assertNull(
            RecoveryLinkParser.parse(
                "hmxarena://auth/reset?token_hash=abc&access_token=a&refresh_token=b",
            ),
        )
    }

    @Test
    fun rejectsIncompleteOrForgedSessionPair() {
        assertNull(
            RecoveryLinkParser.parse(
                "hmxarena://auth/reset#type=recovery&access_token=only-one",
            ),
        )
        assertNull(
            RecoveryLinkParser.parse(
                "hmxarena://auth/reset#type=recovery&refresh_token=only-one",
            ),
        )
        // Real access and refresh tokens are always different values.
        assertNull(
            RecoveryLinkParser.parse(
                "hmxarena://auth/reset#type=recovery&access_token=same&refresh_token=same",
            ),
        )
    }

    @Test
    fun rejectsLinkWithNoTokenAtAll() {
        assertNull(RecoveryLinkParser.parse("hmxarena://auth/reset?type=recovery"))
    }

    @Test
    fun fragmentTakesPrecedenceOverQuery() {
        val link = RecoveryLinkParser.parse(
            "hmxarena://auth/reset?type=signup&token_hash=query-token" +
                "#type=recovery&token_hash=fragment-token",
        )
        assertEquals("fragment-token", link!!.tokenHash)
    }
}
