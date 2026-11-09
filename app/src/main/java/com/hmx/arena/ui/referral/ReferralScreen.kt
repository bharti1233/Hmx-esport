package com.hmx.arena.ui.referral

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.hmx.arena.core.designsystem.GlassButton
import com.hmx.arena.core.designsystem.GlassButtonVariant
import com.hmx.arena.core.designsystem.GlassSectionHeader
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.model.ReferralClaim
import com.hmx.arena.core.util.Format
import com.hmx.arena.data.repository.PlayerRepository
import com.hmx.arena.ui.common.EmptyBlock
import com.hmx.arena.ui.common.GlassTextField
import com.hmx.arena.ui.common.HmxGlassBottomBar
import com.hmx.arena.ui.common.HmxPage
import com.hmx.arena.ui.common.InlineError
import com.hmx.arena.ui.common.InlineNotice
import com.hmx.arena.ui.common.InlineSuccess
import com.hmx.arena.ui.common.LoadingBlock
import com.hmx.arena.ui.common.StatusPill
import com.hmx.arena.ui.common.copyToClipboard
import com.hmx.arena.ui.common.hmxMessage
import com.hmx.arena.ui.navigation.Routes
import com.hmx.arena.ui.navigation.openTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReferralUi(
    val loading: Boolean = true,
    val code: String = "",
    val referred: Boolean = false,
    val claims: List<ReferralClaim> = emptyList(),
    val claimCode: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val claimError: String? = null,
    val message: String? = null,
)

@HiltViewModel
class ReferralViewModel @Inject constructor(
    private val player: PlayerRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(ReferralUi())
    val ui: StateFlow<ReferralUi> = _ui.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.code.isBlank(), error = null) }
            runCatching {
                coroutineScope {
                    val profile = async { player.myProfile() }
                    val claims = async {
                        runCatching { player.referrals() }.getOrDefault(emptyList())
                    }
                    val loaded = profile.await()
                    Triple(loaded.referralCode.orEmpty(), loaded.referredBy != null, claims.await())
                }
            }
                .onSuccess { (code, referred, claims) ->
                    _ui.update { it.copy(loading = false, code = code, referred = referred, claims = claims) }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    fun setClaimCode(value: String) = _ui.update {
        it.copy(claimCode = value.uppercase().take(12), claimError = null, error = null)
    }

    fun claim() {
        val current = _ui.value
        if (current.busy || current.referred) return
        val code = current.claimCode.trim()
        if (code.length < 4) {
            _ui.update { it.copy(claimError = "Enter the invite code you received.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, claimError = null, error = null, message = null) }
            runCatching { player.claimReferral(code) }
                .onSuccess {
                    _ui.update {
                        it.copy(
                            busy = false,
                            referred = true,
                            claimCode = "",
                            message = "Invite code applied. The referral is pending staff verification.",
                        )
                    }
                    load()
                }
                .onFailure { e -> _ui.update { it.copy(busy = false, claimError = e.hmxMessage()) } }
        }
    }
}

@Composable
fun ReferralScreen(nav: NavHostController, viewModel: ReferralViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.load() }

    HmxPage(
        title = "Earn",
        bottomBar = { HmxGlassBottomBar(Routes.REFERRAL) { nav.openTab(it) } },
    ) {
        when {
            ui.loading -> LoadingBlock("Loading referral details")
            ui.error != null && ui.code.isBlank() -> InlineError(ui.error!!, onRetry = viewModel::load)
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 124.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (ui.message != null) item { InlineSuccess(ui.message!!) }
                if (ui.error != null) item { InlineError(ui.error!!) }
                item {
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(HmxDimens.cardPadding)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.CardGiftcard,
                                    contentDescription = null,
                                    tint = HmxColor.Ink,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Your invite code",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = HmxColor.Ink,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                ui.code.ifBlank { "Not issued" },
                                style = MaterialTheme.typography.displayMedium,
                                color = HmxColor.Ink,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Share this code with a new player. They can apply it within 7 days of creating an account.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HmxColor.InkSecondary,
                            )
                            if (ui.code.isNotBlank()) {
                                val inviteText =
                                    "Join HMX Arena and use my invite code ${ui.code} when you sign up."
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    GlassButton(
                                        text = "Copy",
                                        onClick = { copyToClipboard(context, ui.code) },
                                        variant = GlassButtonVariant.Secondary,
                                        leadingIcon = Icons.Rounded.ContentCopy,
                                        modifier = Modifier.weight(1f),
                                    )
                                    GlassButton(
                                        text = "Share",
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, inviteText)
                                            }
                                            context.startActivity(
                                                Intent.createChooser(intent, "Share invite code"),
                                            )
                                        },
                                        variant = GlassButtonVariant.Secondary,
                                        leadingIcon = Icons.Rounded.Share,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                GlassButton(
                                    text = "Share to WhatsApp",
                                    onClick = { shareWhatsApp(context, inviteText) },
                                    variant = GlassButtonVariant.Accent,
                                    leadingIcon = Icons.Rounded.Share,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
                item {
                    InlineNotice(
                        "Referral activity is recorded for staff review. No cash reward, wallet credit or guaranteed prize is offered by this programme.",
                    )
                }
                item {
                    GlassSectionHeader(if (ui.referred) "Your invitation" else "Enter an invite code")
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(HmxDimens.cardPadding)) {
                            if (ui.referred) {
                                Text(
                                    "An invite code has already been applied to your account.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = HmxColor.Ink,
                                )
                            } else {
                                GlassTextField(
                                    value = ui.claimCode,
                                    onValueChange = viewModel::setClaimCode,
                                    label = "Invite code",
                                    placeholder = "ABCD1234",
                                    capitalization = KeyboardCapitalization.Characters,
                                    imeAction = ImeAction.Done,
                                    error = ui.claimError,
                                    onImeAction = viewModel::claim,
                                )
                                Spacer(Modifier.height(12.dp))
                                GlassButton(
                                    text = "Apply code",
                                    onClick = viewModel::claim,
                                    loading = ui.busy,
                                    variant = GlassButtonVariant.Ink,
                                )
                            }
                        }
                    }
                }
                item { GlassSectionHeader("Referral activity") }
                if (ui.claims.isEmpty()) {
                    item {
                        EmptyBlock(
                            title = "No referrals yet",
                            message = "When someone joins with your code, the referral appears here with its review status.",
                            icon = Icons.Rounded.CardGiftcard,
                        )
                    }
                } else {
                    items(ui.claims, key = { it.id }) { claim ->
                        GlassSurface(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Code ${claim.code}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = HmxColor.Ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        Format.relative(claim.createdAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HmxColor.InkTertiary,
                                    )
                                }
                                StatusPill(claim.status.replaceFirstChar { it.uppercase() })
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun shareWhatsApp(context: android.content.Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        setPackage("com.whatsapp")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(fallback, "Share invite code"))
    }
}
