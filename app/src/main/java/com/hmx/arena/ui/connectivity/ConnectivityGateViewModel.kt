package com.hmx.arena.ui.connectivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmx.arena.data.connectivity.BackendHealth
import com.hmx.arena.data.connectivity.BackendHealthChecker
import com.hmx.arena.data.connectivity.ConnectivityMonitor
import com.hmx.arena.data.connectivity.NetworkAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ConnectionStatus {
    Checking,
    Ready,
    NoNetwork,
    NoInternet,
    BackendUnavailable,
    Timeout,
}

data class ConnectionUi(
    val status: ConnectionStatus = ConnectionStatus.Checking,
) {
    val checking: Boolean get() = status == ConnectionStatus.Checking
    val ready: Boolean get() = status == ConnectionStatus.Ready
}

/** Maps one backend probe result to the state the gate renders. */
fun connectionStatusFor(health: BackendHealth): ConnectionStatus = when (health) {
    BackendHealth.Reachable -> ConnectionStatus.Ready
    BackendHealth.Timeout -> ConnectionStatus.Timeout
    BackendHealth.Unavailable -> ConnectionStatus.BackendUnavailable
}

/**
 * Owns the startup connectivity gate.
 *
 * - The OS capability is read first; no request is sent when there is no
 *   network or no validated internet.
 * - One lightweight health request classifies the backend. There is no
 *   polling loop: re-checks happen on a capabilities change, app resume or an
 *   explicit user retry.
 * - Session state is never touched here, so a temporarily unreachable backend
 *   cannot sign the player out.
 */
@HiltViewModel
class ConnectivityGateViewModel @Inject constructor(
    private val monitor: ConnectivityMonitor,
    private val health: BackendHealthChecker,
) : ViewModel() {

    private val _ui = MutableStateFlow(ConnectionUi())
    val ui: StateFlow<ConnectionUi> = _ui.asStateFlow()

    private var checkJob: Job? = null

    init {
        viewModelScope.launch {
            monitor.observe().collect(::onAvailability)
        }
    }

    fun retry() {
        checkJob?.cancel()
        checkBackend()
    }

    fun onResume() {
        if (_ui.value.status == ConnectionStatus.NoNetwork ||
            _ui.value.status == ConnectionStatus.NoInternet ||
            _ui.value.status == ConnectionStatus.BackendUnavailable ||
            _ui.value.status == ConnectionStatus.Timeout
        ) {
            retry()
        }
    }

    private fun onAvailability(availability: NetworkAvailability) {
        when (availability) {
            NetworkAvailability.NoNetwork -> {
                checkJob?.cancel()
                _ui.value = ConnectionUi(ConnectionStatus.NoNetwork)
            }
            NetworkAvailability.NoInternet -> {
                checkJob?.cancel()
                _ui.value = ConnectionUi(ConnectionStatus.NoInternet)
            }
            NetworkAvailability.Available -> {
                // Only probe when the gate is not already satisfied; repeated
                // capability callbacks must not create duplicate requests.
                if (_ui.value.status != ConnectionStatus.Ready) checkBackend()
            }
        }
    }

    private fun checkBackend() {
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            when (monitor.current()) {
                NetworkAvailability.NoNetwork -> {
                    _ui.value = ConnectionUi(ConnectionStatus.NoNetwork)
                    return@launch
                }
                NetworkAvailability.NoInternet -> {
                    _ui.value = ConnectionUi(ConnectionStatus.NoInternet)
                    return@launch
                }
                NetworkAvailability.Available -> Unit
            }
            _ui.value = ConnectionUi(ConnectionStatus.Checking)
            _ui.value = ConnectionUi(connectionStatusFor(health.check()))
        }
    }
}
