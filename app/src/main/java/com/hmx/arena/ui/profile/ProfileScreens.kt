package com.hmx.arena.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.hmx.arena.ui.common.GlassActionRow
import com.hmx.arena.core.designsystem.GlassButton
import com.hmx.arena.core.designsystem.GlassButtonVariant
import com.hmx.arena.core.designsystem.GlassSectionHeader
import com.hmx.arena.core.designsystem.GlassStatCell
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.designsystem.HmxShapes
import com.hmx.arena.core.model.PlayerGameAccount
import com.hmx.arena.core.model.Profile
import com.hmx.arena.core.util.Format
import com.hmx.arena.core.util.Validation
import com.hmx.arena.data.remote.SessionManager
import com.hmx.arena.data.repository.AuthRepository
import com.hmx.arena.data.repository.PlayerRepository
import com.hmx.arena.ui.common.ConfirmDialog
import com.hmx.arena.ui.common.GlassAvatar
import com.hmx.arena.ui.common.GlassTextField
import com.hmx.arena.ui.common.HmxLiquidGlassFormDialog
import com.hmx.arena.ui.common.HmxPage
import com.hmx.arena.ui.common.InlineError
import com.hmx.arena.ui.common.InlineNotice
import com.hmx.arena.ui.common.InlineSuccess
import com.hmx.arena.ui.common.LoadingBlock
import com.hmx.arena.ui.common.PickedImage
import com.hmx.arena.ui.common.StatusPill
import com.hmx.arena.ui.common.hmxMessage
import com.hmx.arena.ui.common.rememberPickedImage
import com.hmx.arena.ui.navigation.Routes
import com.hmx.arena.ui.navigation.openTab
import com.hmx.arena.ui.common.HmxGlassBottomBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUi(
    val loading: Boolean = true,
    val profile: Profile? = null,
    val error: String? = null,
    val uploading: Boolean = false,
    val actionError: String? = null,
    val message: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val player: PlayerRepository,
    private val auth: AuthRepository,
    private val session: SessionManager,
) : ViewModel() {
    private val _ui = MutableStateFlow(ProfileUi())
    val ui: StateFlow<ProfileUi> = _ui.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.profile == null, error = null) }
            runCatching { player.myProfile() }
                .onSuccess { profile -> _ui.update { it.copy(loading = false, profile = profile) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    fun uploadAvatar(image: PickedImage) {
        if (_ui.value.uploading) return
        if (!image.mime.startsWith("image/")) {
            _ui.update { it.copy(actionError = "Choose an image file.") }
            return
        }
        if (image.bytes.size > 4 * 1024 * 1024) {
            _ui.update { it.copy(actionError = "Avatar must be smaller than 4 MB.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(uploading = true, actionError = null, message = null) }
            runCatching { player.uploadAvatar(image.bytes, image.mime) }
                .onSuccess { url ->
                    _ui.update {
                        it.copy(
                            uploading = false,
                            profile = it.profile?.copy(avatarUrl = url),
                            message = "Avatar updated.",
                        )
                    }
                }
                .onFailure { e -> _ui.update { it.copy(uploading = false, actionError = e.hmxMessage()) } }
        }
    }

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }

    fun email(): String? = session.snapshotEmail()
}

@Composable
fun ProfileScreen(nav: NavHostController, viewModel: ProfileViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val (picked, pick) = rememberPickedImage()
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(picked) { picked?.let(viewModel::uploadAvatar) }

    HmxPage(
        title = "Account",
        actions = {
            com.hmx.arena.core.designsystem.GlassIconButton(
                icon = Icons.Rounded.Settings,
                contentDescription = "Settings",
                onClick = { nav.navigate(Routes.SETTINGS) },
            )
        },
        bottomBar = { HmxGlassBottomBar(Routes.PROFILE) { nav.openTab(it) } },
    ) {
        when {
            ui.loading && ui.profile == null -> LoadingBlock("Loading your profile")
            ui.error != null && ui.profile == null -> InlineError(ui.error!!, onRetry = viewModel::load)
            ui.profile == null -> InlineError("Your profile could not be loaded.")
            else -> {
                val profile = ui.profile!!
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 124.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (ui.message != null) item { InlineSuccess(ui.message!!) }
                    if (ui.actionError != null) item { InlineError(ui.actionError!!) }
                    item {
                        GlassSurface(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.fillMaxWidth().padding(HmxDimens.cardPadding),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box {
                                    GlassAvatar(profile.avatarUrl, profile.display, 88.dp)
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(HmxColor.Ink)
                                            .border(HmxDimens.hairline, HmxColor.OnInk.copy(alpha = 0.7f), CircleShape)
                                            .clickable(enabled = !ui.uploading, role = Role.Button, onClick = pick),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            if (ui.uploading) Icons.Rounded.Edit else Icons.Rounded.Add,
                                            contentDescription = "Change avatar",
                                            tint = HmxColor.OnInk,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(profile.display, style = MaterialTheme.typography.headlineMedium, color = HmxColor.Ink)
                                Text(
                                    "@${profile.username} · ${profile.region}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HmxColor.InkSecondary,
                                )
                                if (!profile.bio.isNullOrBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        profile.bio,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HmxColor.InkSecondary,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                Spacer(Modifier.height(18.dp))
                                Row(Modifier.fillMaxWidth()) {
                                    GlassStatCell("Points", Format.points(profile.points.toLong()), Modifier.weight(1f))
                                    GlassStatCell("Played", profile.matchesPlayed.toString(), Modifier.weight(1f))
                                    GlassStatCell("Wins", profile.wins.toString(), Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(14.dp))
                                GlassButton(
                                    text = "Edit profile",
                                    onClick = { nav.navigate(Routes.EDIT_PROFILE) },
                                    variant = GlassButtonVariant.Secondary,
                                    leadingIcon = Icons.Rounded.Edit,
                                )
                            }
                        }
                    }
                    item {
                        GlassSurface(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                GlassActionRow(
                                    title = "Game accounts",
                                    subtitle = "Free Fire IGN and UID",
                                    icon = Icons.Rounded.SportsEsports,
                                    onClick = { nav.navigate(Routes.GAME_ACCOUNTS) },
                                )
                                GlassActionRow(
                                    title = "My team",
                                    subtitle = "Squad roster and invitations",
                                    icon = Icons.Rounded.Group,
                                    onClick = { nav.navigate(Routes.TEAM) },
                                )
                                GlassActionRow(
                                    title = "Wallet",
                                    subtitle = "Balance and ledger",
                                    icon = Icons.Rounded.Wallet,
                                    onClick = { nav.navigate(Routes.WALLET) },
                                )
                                GlassActionRow(
                                    title = "Transactions",
                                    subtitle = "Immutable financial history",
                                    icon = Icons.Rounded.ReceiptLong,
                                    onClick = { nav.navigate(Routes.TRANSACTIONS) },
                                )
                            }
                        }
                    }
                    item {
                        GlassSurface(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                GlassActionRow(
                                    title = "Refer a player",
                                    subtitle = "Share your invite code",
                                    icon = Icons.Rounded.CardGiftcard,
                                    onClick = { nav.navigate(Routes.REFERRAL) },
                                )
                                GlassActionRow(
                                    title = "Support",
                                    subtitle = "Tickets and help",
                                    icon = Icons.Rounded.HelpOutline,
                                    onClick = { nav.navigate(Routes.SUPPORT) },
                                )
                                GlassActionRow(
                                    title = "About HMX Arena",
                                    subtitle = "Version and legal",
                                    icon = Icons.Rounded.Info,
                                    onClick = { nav.navigate(Routes.ABOUT) },
                                )
                                GlassActionRow(
                                    title = "Settings",
                                    subtitle = "Account, security and legal",
                                    icon = Icons.Rounded.Settings,
                                    onClick = { nav.navigate(Routes.SETTINGS) },
                                )
                                GlassActionRow(
                                    title = "Sign out",
                                    icon = Icons.Rounded.Logout,
                                    onClick = viewModel::signOut,
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            "Member since ${Format.date(profile.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = HmxColor.InkTertiary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

data class EditProfileUi(
    val loading: Boolean = true,
    val displayName: String = "",
    val bio: String = "",
    val region: String = "IN",
    val avatarUrl: String? = null,
    val username: String = "",
    val saving: Boolean = false,
    val uploading: Boolean = false,
    val error: String? = null,
    val displayError: String? = null,
    val bioError: String? = null,
    val regionError: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val player: PlayerRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(EditProfileUi())
    val ui: StateFlow<EditProfileUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { player.myProfile() }
                .onSuccess { profile ->
                    _ui.update {
                        it.copy(
                            loading = false,
                            displayName = profile.display,
                            bio = profile.bio.orEmpty(),
                            region = profile.region,
                            avatarUrl = profile.avatarUrl,
                            username = profile.username,
                        )
                    }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    fun setDisplayName(value: String) = _ui.update { it.copy(displayName = value.take(30), displayError = null, error = null) }
    fun setBio(value: String) = _ui.update { it.copy(bio = value.take(160), bioError = null, error = null) }
    fun setRegion(value: String) = _ui.update { it.copy(region = value.uppercase().take(8), regionError = null, error = null) }

    fun uploadAvatar(image: PickedImage) {
        if (_ui.value.uploading) return
        if (!image.mime.startsWith("image/")) {
            _ui.update { it.copy(error = "Choose an image file.") }
            return
        }
        if (image.bytes.size > 4 * 1024 * 1024) {
            _ui.update { it.copy(error = "Avatar must be smaller than 4 MB.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(uploading = true, error = null) }
            runCatching { player.uploadAvatar(image.bytes, image.mime) }
                .onSuccess { url ->
                    _ui.update { it.copy(uploading = false, avatarUrl = url) }
                }
                .onFailure { e -> _ui.update { it.copy(uploading = false, error = e.hmxMessage()) } }
        }
    }

    fun save() {
        val current = _ui.value
        if (current.saving) return
        val displayError = if (current.displayName.trim().length !in 2..30) {
            "Display name must be 2-30 characters."
        } else null
        val bioError = if (current.bio.length > 160) "Bio must be 160 characters or fewer." else null
        val regionError = if (current.region.trim().length !in 2..8) "Use a short region code such as IN." else null
        if (displayError != null || bioError != null || regionError != null) {
            _ui.update { it.copy(displayError = displayError, bioError = bioError, regionError = regionError) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(saving = true, error = null) }
            runCatching {
                player.updateProfile(current.displayName.trim(), current.bio.trim(), current.region.trim().uppercase())
            }
                .onSuccess { _ui.update { it.copy(saving = false, done = true) } }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = e.hmxMessage()) } }
        }
    }
}

@Composable
fun EditProfileScreen(nav: NavHostController, viewModel: EditProfileViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val (picked, pick) = rememberPickedImage()
    LaunchedEffect(picked) { picked?.let(viewModel::uploadAvatar) }
    LaunchedEffect(ui.done) { if (ui.done) nav.popBackStack() }

    HmxPage(title = "Edit profile", onBack = { nav.popBackStack() }) {
        when {
            ui.loading -> LoadingBlock("Loading profile")
            else -> Column(
                Modifier.fillMaxSize().padding(20.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    GlassAvatar(ui.avatarUrl, ui.displayName.ifBlank { ui.username }, 82.dp)
                    Spacer(Modifier.height(10.dp))
                    GlassButton(
                        text = if (ui.uploading) "Uploading…" else "Change avatar",
                        onClick = pick,
                        enabled = !ui.uploading,
                        variant = GlassButtonVariant.Secondary,
                        modifier = Modifier.width(190.dp),
                    )
                }
                Spacer(Modifier.height(22.dp))
                GlassTextField(
                    value = ui.displayName,
                    onValueChange = viewModel::setDisplayName,
                    label = "Display name",
                    error = ui.displayError,
                )
                Spacer(Modifier.height(14.dp))
                GlassTextField(
                    value = ui.bio,
                    onValueChange = viewModel::setBio,
                    label = "Bio",
                    placeholder = "Optional. 160 characters.",
                    maxLines = 3,
                    error = ui.bioError,
                )
                Spacer(Modifier.height(14.dp))
                GlassTextField(
                    value = ui.region,
                    onValueChange = viewModel::setRegion,
                    label = "Region code",
                    placeholder = "IN",
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters,
                    error = ui.regionError,
                    imeAction = ImeAction.Done,
                    onImeAction = viewModel::save,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Username @${ui.username} is permanent. Contact support if it must change.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HmxColor.InkTertiary,
                )
                if (ui.error != null) {
                    Spacer(Modifier.height(12.dp))
                    InlineError(ui.error!!)
                }
                Spacer(Modifier.height(22.dp))
                GlassButton(
                    text = "Save profile",
                    onClick = viewModel::save,
                    loading = ui.saving,
                    variant = GlassButtonVariant.Ink,
                )
            }
        }
    }
}

data class GameAccountsUi(
    val loading: Boolean = true,
    val accounts: List<PlayerGameAccount> = emptyList(),
    val error: String? = null,
    val busy: Boolean = false,
    val actionError: String? = null,
    val message: String? = null,
)

@HiltViewModel
class GameAccountsViewModel @Inject constructor(
    private val player: PlayerRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(GameAccountsUi())
    val ui: StateFlow<GameAccountsUi> = _ui.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.accounts.isEmpty(), error = null) }
            runCatching { player.gameAccounts() }
                .onSuccess { accounts -> _ui.update { it.copy(loading = false, accounts = accounts) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    fun save(existing: PlayerGameAccount?, ign: String, gameUid: String, level: String) {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, actionError = null, message = null) }
            runCatching { player.saveGameAccount(existing, ign.trim(), gameUid.trim(), level.toIntOrNull()) }
                .onSuccess {
                    _ui.update { it.copy(busy = false, message = if (existing == null) "Game account added." else "Game account updated.") }
                    load()
                }
                .onFailure { e -> _ui.update { it.copy(busy = false, actionError = e.hmxMessage()) } }
        }
    }

    fun delete(id: String) {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, actionError = null, message = null) }
            runCatching { player.deleteGameAccount(id) }
                .onSuccess {
                    _ui.update { it.copy(busy = false, message = "Game account removed.") }
                    load()
                }
                .onFailure { e -> _ui.update { it.copy(busy = false, actionError = e.hmxMessage()) } }
        }
    }

    fun clearFeedback() = _ui.update { it.copy(actionError = null, message = null) }
}

@Composable
fun GameAccountsScreen(nav: NavHostController, viewModel: GameAccountsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PlayerGameAccount?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PlayerGameAccount?>(null) }
    LaunchedEffect(Unit) { viewModel.load() }

    HmxPage(title = "Game accounts", onBack = { nav.popBackStack() }) {
        when {
            ui.loading -> LoadingBlock("Loading game accounts")
            ui.error != null && ui.accounts.isEmpty() -> InlineError(ui.error!!, onRetry = viewModel::load)
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    InlineNotice(
                        "Add the Free Fire IGN and UID that tournament staff will verify. HMX Arena never asks for your game password.",
                    )
                }
                if (ui.message != null) item { InlineSuccess(ui.message!!) }
                if (ui.actionError != null) item { InlineError(ui.actionError!!) }
                if (ui.accounts.isEmpty()) {
                    item {
                        GlassSurface(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(Icons.Rounded.SportsEsports, contentDescription = null, tint = HmxColor.Ink, modifier = Modifier.size(30.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("No game account linked", style = MaterialTheme.typography.titleMedium, color = HmxColor.Ink)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Link your Free Fire account so you can register for tournaments.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HmxColor.InkSecondary,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                items(ui.accounts, key = { it.id }) { account ->
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(HmxDimens.cardPadding)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        account.ign,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = HmxColor.Ink,
                                    )
                                    Text(
                                        "UID ${account.gameUid} · ${account.game.replace("_", " ").replaceFirstChar { it.uppercase() }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HmxColor.InkSecondary,
                                    )
                                }
                                StatusPill(if (account.verified) "Verified" else "Unverified")
                            }
                            if (account.level != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Level ${account.level}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HmxColor.InkTertiary,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GlassButton(
                                    text = "Edit",
                                    onClick = { editing = account },
                                    variant = GlassButtonVariant.Secondary,
                                    modifier = Modifier.weight(1f),
                                )
                                GlassButton(
                                    text = "Remove",
                                    onClick = { deleting = account },
                                    variant = GlassButtonVariant.Danger,
                                    enabled = !ui.busy,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                item {
                    GlassButton(
                        text = if (ui.accounts.isEmpty()) "Add game account" else "Add another account",
                        onClick = { adding = true },
                        variant = GlassButtonVariant.Ink,
                        leadingIcon = Icons.Rounded.Add,
                    )
                }
            }
        }
    }

    if (adding || editing != null) {
        GameAccountDialog(
            existing = editing,
            busy = ui.busy,
            error = ui.actionError,
            onDismiss = {
                adding = false
                editing = null
                viewModel.clearFeedback()
            },
            onSave = { ign, uid, level ->
                viewModel.save(editing, ign, uid, level)
                adding = false
                editing = null
            },
        )
    }

    deleting?.let { account ->
        ConfirmDialog(
            title = "Remove game account?",
            message = "Remove ${account.ign} (${account.gameUid})? You must relink an account before registering for team tournaments.",
            confirmLabel = "Remove",
            onConfirm = {
                viewModel.delete(account.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun GameAccountDialog(
    existing: PlayerGameAccount?,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (ign: String, uid: String, level: String) -> Unit,
) {
    var ign by remember(existing) { mutableStateOf(existing?.ign.orEmpty()) }
    var uid by remember(existing) { mutableStateOf(existing?.gameUid.orEmpty()) }
    var level by remember(existing) { mutableStateOf(existing?.level?.toString().orEmpty()) }
    var localError by remember { mutableStateOf<String?>(null) }

    HmxLiquidGlassFormDialog(
        visible = true,
        onDismiss = onDismiss,
        title = if (existing == null) "Add game account" else "Edit game account",
        confirmLabel = if (busy) "Saving…" else "Save",
        confirmEnabled = !busy,
        onConfirm = {
            localError = when {
                !Validation.isIgnValid(ign) -> "Enter an in-game name of 2-24 characters."
                !Validation.isGameUidValid(uid) -> "Enter the UID shown in your Free Fire profile."
                level.isNotBlank() && !Validation.isLevelValid(level) -> "Level must be between 1 and 100."
                else -> null
            }
            if (localError == null) onSave(ign, uid, level)
        },
    ) {
        GlassTextField(
            value = ign,
            onValueChange = { ign = it.take(24); localError = null },
            label = "In-game name",
        )
        Spacer(Modifier.height(12.dp))
        GlassTextField(
            value = uid,
            onValueChange = { value ->
                uid = value.filter(Char::isLetterOrDigit).take(24)
                localError = null
            },
            label = "Free Fire UID",
            keyboardType = KeyboardType.Number,
        )
        Spacer(Modifier.height(12.dp))
        GlassTextField(
            value = level,
            onValueChange = { level = it.filter(Char::isDigit).take(3); localError = null },
            label = "Level (optional)",
            keyboardType = KeyboardType.Number,
        )
        if (localError != null) {
            Spacer(Modifier.height(8.dp))
            Text(localError!!, style = MaterialTheme.typography.bodySmall, color = HmxColor.Danger)
        } else if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, style = MaterialTheme.typography.bodySmall, color = HmxColor.Danger)
        }
    }
}
