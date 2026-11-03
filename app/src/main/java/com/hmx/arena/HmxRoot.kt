package com.hmx.arena

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmx.arena.data.remote.SessionManager
import com.hmx.arena.data.remote.SessionState
import com.hmx.arena.data.repository.AuthRepository
import com.hmx.arena.data.repository.PlayerRepository
import com.hmx.arena.ui.auth.AuthFlow
import com.hmx.arena.ui.auth.RecoveryResetScreen
import com.hmx.arena.ui.common.InlineError
import com.hmx.arena.ui.common.LoadingScreen
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

private data class RecoveryLink(
    val tokenHash: String?,
    val accessToken: String?,
    val refreshToken: String?,
)

private fun parseRecoveryLink(link: String): RecoveryLink? {
    val uri = runCatching { Uri.parse(link) }.getOrNull() ?: return null
    val fragment = uri.fragment.orEmpty()
        .split("&")
        .mapNotNull { part ->
            val pieces = part.split("=", limit = 2)
            if (pieces.size == 2) pieces[0] to Uri.decode(pieces[1]) else null
        }
        .toMap()
    val type = uri.getQueryParameter("type") ?: fragment["type"]
    if (type != "recovery" && uri.host != "auth") return null
    val tokenHash = uri.getQueryParameter("token_hash") ?: fragment["token_hash"]
    val access = fragment["access_token"] ?: uri.getQueryParameter("access_token")
    val refresh = fragment["refresh_token"] ?: uri.getQueryParameter("refresh_token")
    return RecoveryLink(tokenHash, access, refresh)
}

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
) {
    val sessionState by session.state.collectAsState()
    var recoveryMode by remember { mutableStateOf(false) }
    var recoveryToken by remember { mutableStateOf<String?>(null) }
    var processingLink by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(deepLink) {
        val raw = deepLink ?: return@LaunchedEffect
        val link = parseRecoveryLink(raw)
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

    when {
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
            SignedInGate(userId = userId)
        }
    }

    linkError?.let { message ->
        AlertDialog(
            onDismissRequest = { linkError = null },
            containerColor = Color.White,
            title = { Text("Reset link problem") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { linkError = null }) { Text("OK") }
            },
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
) : ViewModel() {
    private val _ui = MutableStateFlow(GateUi())
    val ui: StateFlow<GateUi> = _ui.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching { player.myProfile() }
                .onSuccess { profile ->
                    _ui.value = GateUi(loading = false, needsOnboarding = !profile.onboardingCompleted)
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
    viewModel: SignedInGateViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    LaunchedEffect(userId) { viewModel.load() }

    when {
        ui.loading -> LoadingScreen()
        ui.error != null -> InlineError(ui.error!!, onRetry = viewModel::load)
        else -> MainNavHost(
            startDestination = if (ui.needsOnboarding) Routes.COMPLETE_PROFILE else Routes.HOME,
        )
    }
}
