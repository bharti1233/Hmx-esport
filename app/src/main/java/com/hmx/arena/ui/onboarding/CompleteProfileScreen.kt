package com.hmx.arena.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hmx.arena.core.designsystem.GlassButtonVariant
import com.hmx.arena.core.designsystem.GlassScreenBackground
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxShapes
import com.hmx.arena.core.model.PlayerGameAccount
import com.hmx.arena.core.model.Profile
import com.hmx.arena.core.util.Validation
import com.hmx.arena.data.repository.PlayerRepository
import com.hmx.arena.ui.common.HmxGlassButton
import com.hmx.arena.ui.common.HmxGlassTextField
import com.hmx.arena.ui.common.InlineError
import com.hmx.arena.ui.common.InlineNotice
import com.hmx.arena.ui.common.hmxMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUi(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val displayName: String = "",
    val ign: String = "",
    val gameUid: String = "",
    val level: String = "",
    val username: String = "",
    val displayError: String? = null,
    val ignError: String? = null,
    val uidError: String? = null,
    val levelError: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class CompleteProfileViewModel @Inject constructor(
    private val player: PlayerRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(OnboardingUi())
    val ui: StateFlow<OnboardingUi> = _ui.asStateFlow()
    private var existing: PlayerGameAccount? = null

    init {
        viewModelScope.launch {
            runCatching { player.myProfile() }
                .onSuccess { profile ->
                    existing = runCatching { player.gameAccounts().firstOrNull() }.getOrNull()
                    _ui.update {
                        it.copy(
                            loading = false,
                            displayName = profile.displayName.ifBlank { profile.username },
                            username = profile.username,
                            ign = existing?.ign.orEmpty(),
                            gameUid = existing?.gameUid.orEmpty(),
                            level = existing?.level?.toString().orEmpty(),
                        )
                    }
                }
                .onFailure { e ->
                    _ui.update { it.copy(loading = false, error = e.hmxMessage()) }
                }
        }
    }

    fun setDisplayName(value: String) = _ui.update { it.copy(displayName = value, displayError = null, error = null) }
    fun setIgn(value: String) = _ui.update { it.copy(ign = value, ignError = null, error = null) }
    fun setUid(value: String) = _ui.update { it.copy(gameUid = value.trim(), uidError = null, error = null) }
    fun setLevel(value: String) = _ui.update { it.copy(level = value.filter(Char::isDigit).take(3), levelError = null, error = null) }

    fun submit(withGameAccount: Boolean = true) {
        val current = _ui.value
        if (current.saving) return
        val displayError = if (current.displayName.trim().length !in 2..30) {
            "Display name must be 2-30 characters."
        } else null
        val ignError = if (withGameAccount && !Validation.isIgnValid(current.ign)) {
            "Enter your in-game name (2-24 characters)."
        } else null
        val uidError = if (withGameAccount && !Validation.isGameUidValid(current.gameUid)) {
            "Enter the UID shown in your Free Fire profile."
        } else null
        val levelError = if (withGameAccount && !Validation.isLevelValid(current.level)) {
            "Enter a level between 1 and 100."
        } else null
        if (displayError != null || ignError != null || uidError != null || levelError != null) {
            _ui.update {
                it.copy(
                    displayError = displayError,
                    ignError = ignError,
                    uidError = uidError,
                    levelError = levelError,
                )
            }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(saving = true, error = null) }
            runCatching {
                // Single transactional RPC: profile + game account + welcome
                // notification. No sequential writes, no service role.
                player.completeOnboarding(
                    displayName = current.displayName.trim(),
                    bio = null,
                    region = "IN",
                    gameAccountId = existing?.id,
                    ign = if (withGameAccount) current.ign.trim() else null,
                    gameUid = if (withGameAccount) current.gameUid.trim() else null,
                    level = if (withGameAccount) current.level.toIntOrNull() else null,
                )
            }
                .onSuccess { _ui.update { it.copy(saving = false, done = true) } }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = e.hmxMessage()) } }
        }
    }
}

@Composable
fun CompleteProfileScreen(
    onCompleted: () -> Unit,
    onSignOut: (() -> Unit)? = null,
    viewModel: CompleteProfileViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(ui.done) {
        if (ui.done) onCompleted()
    }

    Box(Modifier.fillMaxSize()) {
        GlassScreenBackground()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(HmxShapes.small)
                        .background(HmxColor.Ink),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.SportsEsports,
                        contentDescription = null,
                        tint = HmxColor.OnInk,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.size(10.dp))
                Column {
                    Text("PLAYER SETUP", style = MaterialTheme.typography.labelSmall, color = HmxColor.InkTertiary)
                    Text("@${ui.username}", style = MaterialTheme.typography.titleMedium, color = HmxColor.Ink)
                }
            }
            Spacer(Modifier.height(32.dp))
            Text("Set up your arena card.", style = MaterialTheme.typography.displayMedium, color = HmxColor.Ink)
            Spacer(Modifier.height(8.dp))
            Text(
                "This is what opponents and tournament staff see. Your game UID is used only for eligibility checks.",
                style = MaterialTheme.typography.bodyMedium,
                color = HmxColor.InkSecondary,
            )
            Spacer(Modifier.height(26.dp))
            if (ui.loading) {
                com.hmx.arena.ui.common.LoadingBlock("Loading your profile")
            } else {
                HmxGlassTextField(
                    value = ui.displayName,
                    onValueChange = viewModel::setDisplayName,
                    label = "Display name",
                    error = ui.displayError,
                )
                Spacer(Modifier.height(20.dp))
                Text("FREE FIRE ACCOUNT", style = MaterialTheme.typography.labelSmall, color = HmxColor.InkTertiary)
                Spacer(Modifier.height(10.dp))
                HmxGlassTextField(
                    value = ui.ign,
                    onValueChange = viewModel::setIgn,
                    label = "In-game name (IGN)",
                    error = ui.ignError,
                )
                Spacer(Modifier.height(14.dp))
                HmxGlassTextField(
                    value = ui.gameUid,
                    onValueChange = viewModel::setUid,
                    label = "Free Fire UID",
                    keyboardType = KeyboardType.Number,
                    error = ui.uidError,
                )
                Spacer(Modifier.height(14.dp))
                HmxGlassTextField(
                    value = ui.level,
                    onValueChange = viewModel::setLevel,
                    label = "In-game level",
                    keyboardType = KeyboardType.Number,
                    error = ui.levelError,
                    imeAction = ImeAction.Done,
                    onImeAction = { viewModel.submit(withGameAccount = true) },
                )
                Spacer(Modifier.height(16.dp))
                InlineNotice(
                    "HMX Arena never asks for your Free Fire password. Room credentials are shared inside the app after you register.",
                )
                if (ui.error != null) {
                    Spacer(Modifier.height(12.dp))
                    InlineError(ui.error!!)
                }
                Spacer(Modifier.height(24.dp))
                HmxGlassButton(
                    text = "Enter the arena",
                    onClick = { viewModel.submit(withGameAccount = true) },
                    loading = ui.saving,
                    variant = GlassButtonVariant.Ink,
                )
                Spacer(Modifier.height(12.dp))
                HmxGlassButton(
                    text = "Add game account later",
                    onClick = { viewModel.submit(withGameAccount = false) },
                    variant = GlassButtonVariant.Secondary,
                    enabled = !ui.saving,
                )
                if (onSignOut != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Sign out",
                        style = MaterialTheme.typography.labelMedium,
                        color = HmxColor.InkTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(HmxShapes.pill)
                            .clickable(onClick = onSignOut)
                            .padding(12.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
