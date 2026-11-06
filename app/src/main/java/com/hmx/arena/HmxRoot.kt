package com.hmx.arena

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hmx.arena.data.remote.SessionManager
import com.hmx.arena.data.remote.SessionState
import com.hmx.arena.data.repository.AuthRepository
import com.hmx.arena.core.designsystem.HmxGlassHost
import com.hmx.arena.core.util.RecoveryLinkParser
import com.hmx.arena.data.repository.PlayerRepository
import com.hmx.arena.ui.auth.AuthFlow
import com.hmx.arena.ui.auth.RecoveryResetScreen
import com.hmx.arena.ui.common.InlineError
import com.hmx.arena.ui.common.LoadingScreen
import com.hmx.arena.ui.common.HmxLiquidGlassDialog
import com.hmx.arena.ui.connectivity.ConnectionRequiredScreen
import com.hmx.arena.ui.connectivity.ConnectivityGateViewModel
import com.hmx.arena.ui.navigation.MainNavHost
import com.hmx.arena.ui.navigation.Routes
import com.hmx.arena.ui.common.hmxMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Root of the app. Owns session gating, password-recovery deep links and the
 * onboarding decision. UI never renders a screen that assumes a session.
 */
@Composable
fun HmxRoot(
    session: SessionManager,
    auth: AuthRepository,
    deepLink: String?,
    onDeepLinkHandled: () -> Unit,
    connectivity: ConnectivityGateViewModel = hiltViewModel(),
) {
    val sessionState by session.state.collectAsStateWithLifecycle()
    val connection by connectivity.ui.collectAsStateWithLifecycle()
    var recoveryMode by remember { mutableStateOf(false) }
    var recoveryToken by remember { mutableStateOf<String?>(null) }
    var processingLink by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf<String?>(null) }

    // Re-check when the player comes back from network settings or from the
    // background. The gate itself never polls: every retry is event-driven.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        connectivity.onResume()
    }

    LaunchedEffect(deepLink, connection.ready) {
        val raw = deepLink ?: return@LaunchedEffect
        // Wait for the connectivity gate: verifying a recovery session needs
        // the network. The link stays queued instead of being consumed while
        // the player is offline.
        if (!connection.ready) return@LaunchedEffect
        val link = RecoveryLinkParser.parse(raw)
        onDeepLinkHandled()
        if (link == null) return@LaunchedEffect
        processingLink = true
        linkError = null
        val restored = when {
            !link.tokenHash.isNullOrBlank() -> {
                recoveryToken = link.tokenHash
                true
            }
            !link.accessToken.isNullOrBlank() && !link.refreshToken.isNullOrBlank() -> {
                auth.restoreFromRecovery(link.accessToken, link.refreshToken)
            }
            else -> false
        }
        if (restored) {
            recoveryMode = true
        } else {
            linkError = "This password reset link is invalid or has expired. Request a new link from the sign-in screen."
        }
        processingLink = false
    }

    HmxGlassHost {
        when {
            // Connectivity is verified before any session/profile request is
            // allowed to render. The gate never mutates the session.
            connection.checking -> LoadingScreen()
            !connection.ready -> ConnectionRequiredScreen(
                ui = connection,
                onRetry = connectivity::retry,
            )
            sessionState is SessionState.Loading || processingLink -> LoadingScreen()
            recoveryMode -> RecoveryResetScreen(
                tokenHash = recoveryToken,
                onDone = {
                    recoveryMode = false
                    recoveryToken = null
                },
            )
            sessionState is SessionState.SignedOut -> AuthFlow()
            sessionState is SessionState.SignedIn -> {
                val userId = (sessionState as SessionState.SignedIn).userId
                SignedInGate(userId = userId, session = session)
            }
        }
    }

    linkError?.let { message ->
        HmxLiquidGlassDialog(
            visible = true,
            onDismiss = { linkError = null },
            title = "Reset link problem",
            message = message,
            confirmLabel = "OK",
            onConfirm = { linkError = null },
            dismissLabel = null,
        )
    }
}

data class GateUi(
    val loading: Boolean = true,
    val needsOnboarding: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SignedInGateViewModel @Inject constructor(
    private val player: PlayerRepository,
    private val session: SessionManager,
) : ViewModel() {
    private val _ui = MutableStateFlow(GateUi())
    val ui: StateFlow<GateUi> = _ui.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching { player.myProfile() }
                .onSuccess { profile ->
                    val needsOnboarding = !profile.onboardingCompleted
                    // Keep the local decision in sync for the next launch.
                    session.setOnboardingCompleted(!needsOnboarding)
                    _ui.value = GateUi(loading = false, needsOnboarding = needsOnboarding)
                }
                .onFailure { e ->
                    _ui.value = GateUi(loading = false, error = e.hmxMessage())
                }
        }
    }
}

@Composable
private fun SignedInGate(
    userId: String,
    session: SessionManager,
    viewModel: SignedInGateViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val cachedOnboarding = remember(userId) { session.snapshotOnboardingCompleted() }
    LaunchedEffect(userId) { viewModel.load() }

    when {
        // A cached completed profile lets the dashboard render on the first
        // frame; the profile refresh still runs and redirects if the server
        // says onboarding is actually incomplete.
        ui.loading && !cachedOnboarding -> LoadingScreen()
        ui.error != null && !cachedOnboarding -> InlineError(ui.error!!, onRetry = viewModel::load)
        // If the server disagrees with the cache, recreate the graph with the
        // new start destination: a NavHost ignores a changed startDestination
        // on an existing controller.
        else -> key(ui.needsOnboarding) {
            MainNavHost(
                startDestination = if (ui.needsOnboarding) Routes.COMPLETE_PROFILE else Routes.HOME,
            )
        }
    }
}
