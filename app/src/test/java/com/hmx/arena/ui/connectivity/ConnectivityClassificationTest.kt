package com.hmx.arena.ui.connectivity

import com.hmx.arena.data.connectivity.BackendHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Startup gate classification. A backend timeout must stay a distinct,
 * retryable state from "server returned an error", and only Reachable may let
 * the app render.
 */
class ConnectivityClassificationTest {

    @Test
    fun reachableMapsToReady() {
        val status = connectionStatusFor(BackendHealth.Reachable)
        assertEquals(ConnectionStatus.Ready, status)
        assertTrue(ConnectionUi(status).ready)
        assertFalse(ConnectionUi(status).checking)
    }

    @Test
    fun unavailableAndTimeoutRemainDistinct() {
        assertEquals(
            ConnectionStatus.BackendUnavailable,
            connectionStatusFor(BackendHealth.Unavailable),
        )
        assertEquals(
            ConnectionStatus.Timeout,
            connectionStatusFor(BackendHealth.Timeout),
        )
    }

    @Test
    fun onlyReadyIsNonBlocking() {
        ConnectionStatus.entries
            .filter { it != ConnectionStatus.Ready }
            .forEach { status ->
                val ui = ConnectionUi(status)
                assertFalse("$status must keep the gate closed", ui.ready)
            }
    }

    @Test
    fun defaultStateIsChecking() {
        assertTrue(ConnectionUi().checking)
        assertFalse(ConnectionUi().ready)
    }
}
