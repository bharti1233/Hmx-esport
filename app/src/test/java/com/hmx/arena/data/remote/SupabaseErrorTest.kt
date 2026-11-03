package com.hmx.arena.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupabaseErrorTest {

    @Test
    fun onboardingRpcErrorsArePlayerFacing() {
        assertEquals(
            "That game account does not belong to you.",
            mapDbMessage("invalid_game_identity"),
        )
        assertEquals(
            "Display name must be 2-30 characters.",
            mapDbMessage("invalid_display_name"),
        )
        assertEquals(
            "That in-game UID is already linked to another account.",
            mapDbMessage("duplicate key value violates unique constraint \"player_game_accounts_game_uid_key\""),
        )
    }

    @Test
    fun columnGrantFailureStillReadsAsPermission() {
        assertEquals(
            "You do not have permission to do that.",
            mapDbMessage("permission denied for table profiles"),
        )
        assertEquals(
            "You do not have permission to do that.",
            mapDbMessage("42501: permission denied for function is_admin"),
        )
    }

    @Test
    fun parseErrorMessageReadsPostgrestAndAuthShapes() {
        assertEquals(
            "invalid_game_identity",
            parseErrorMessage("""{"code":"P0001","message":"invalid_game_identity"}"""),
        )
        assertEquals(
            "Invalid login credentials",
            parseErrorMessage("""{"error_description":"Invalid login credentials"}"""),
        )
        assertNull(parseErrorMessage("not json"))
    }
}
