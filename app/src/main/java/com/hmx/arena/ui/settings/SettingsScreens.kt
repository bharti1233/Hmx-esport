package com.hmx.arena.ui.settings

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.SupportAgent
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.hmx.arena.BuildConfig
import com.hmx.arena.core.designsystem.GlassActionRow
import com.hmx.arena.core.designsystem.GlassButton
import com.hmx.arena.core.designsystem.GlassButtonVariant
import com.hmx.arena.core.designsystem.GlassChip
import com.hmx.arena.core.designsystem.GlassSectionHeader
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.designsystem.HmxShapes
import com.hmx.arena.core.model.Profile
import com.hmx.arena.core.model.SupportMessage
import com.hmx.arena.core.model.SupportTicket
import com.hmx.arena.core.util.Format
import com.hmx.arena.core.util.Validation
import com.hmx.arena.data.remote.SessionManager
import com.hmx.arena.data.repository.AuthRepository
import com.hmx.arena.data.repository.PlayerRepository
import com.hmx.arena.ui.common.ConfirmDialog
import com.hmx.arena.ui.common.EmptyBlock
import com.hmx.arena.ui.common.GlassTextField
import com.hmx.arena.ui.common.HmxPage
import com.hmx.arena.ui.common.InlineError
import com.hmx.arena.ui.common.InlineNotice
import com.hmx.arena.ui.common.InlineSuccess
import com.hmx.arena.ui.common.LegalText
import com.hmx.arena.ui.common.LoadingBlock
import com.hmx.arena.ui.common.StatusPill
import com.hmx.arena.ui.common.hmxMessage
import com.hmx.arena.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun SettingsScreen(nav: NavHostController, viewModel: SettingsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    HmxPage(title = "Settings", onBack = { nav.popBackStack() }) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                GlassSectionHeader("Account")
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        GlassActionRow(
                            title = "Account settings",
                            subtitle = ui.email?.let { "Signed in as $it" } ?: "Email, password and account removal",
                            icon = Icons.Rounded.Person,
                            onClick = { nav.navigate(Routes.ACCOUNT_SETTINGS) },
                        )
                        GlassActionRow(
                            title = "In-app notifications",
                            subtitle = "View match and result alerts",
                            icon = Icons.Rounded.Notifications,
                            onClick = { nav.navigate(Routes.NOTIFICATIONS) },
                        )
                    }
                }
            }
            item {
                InlineNotice(
                    "This build delivers notifications inside the app only. Push notifications, background reminders and email digests are not enabled, so the app will not alert you when closed.",
                )
            }
            item {
                GlassSectionHeader("Help")
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        GlassActionRow(
                            title = "Support tickets",
                            subtitle = "Contact the HMX team",
                            icon = Icons.Rounded.SupportAgent,
                            onClick = { nav.navigate(Routes.SUPPORT) },
                        )
                        GlassActionRow(
                            title = "Help and FAQ",
                            subtitle = "Registration, rooms and results",
                            icon = Icons.Rounded.HelpOutline,
                            onClick = { nav.navigate(Routes.HELP) },
                        )
                        GlassActionRow(
                            title = "About HMX Arena",
                            subtitle = "Version ${BuildConfig.VERSION_NAME}",
                            icon = Icons.Rounded.Info,
                            onClick = { nav.navigate(Routes.ABOUT) },
                        )
                    }
                }
            }
            item {
                GlassSectionHeader("Legal")
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        GlassActionRow(
                            title = "Terms of Service",
                            icon = Icons.Rounded.Policy,
                            onClick = { nav.navigate(Routes.legal("terms")) },
                        )
                        GlassActionRow(
                            title = "Privacy Policy",
                            icon = Icons.Rounded.Lock,
                            onClick = { nav.navigate(Routes.legal("privacy")) },
                        )
                        GlassActionRow(
                            title = "Fair Play Policy",
                            icon = Icons.Rounded.Gavel,
                            onClick = { nav.navigate(Routes.legal("fair-play")) },
                        )
                        GlassActionRow(
                            title = "Refund Policy",
                            icon = Icons.Rounded.ReceiptLong,
                            onClick = { nav.navigate(Routes.legal("refunds")) },
                        )
                    }
                }
            }
            item {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { viewModel.signOut() }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Logout, contentDescription = null, tint = HmxColor.Danger, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(14.dp))
                        Text("Sign out", style = MaterialTheme.typography.bodyLarge, color = HmxColor.Danger)
                    }
                }
            }
            item {
                Text(
                    "HMX Arena ${BuildConfig.VERSION_NAME} · White glass edition",
                    style = MaterialTheme.typography.labelSmall,
                    color = HmxColor.InkTertiary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

data class SettingsUi(
    val email: String? = null,
    val profile: Profile? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val player: PlayerRepository,
    private val auth: AuthRepository,
    private val session: SessionManager,
) : ViewModel() {
    private val _ui = MutableStateFlow(SettingsUi(email = session.snapshotEmail()))
    val ui: StateFlow<SettingsUi> = _ui.asStateFlow()

    fun load() {
        viewModelScope.launch {
            runCatching { player.myProfile() }
                .onSuccess { profile -> _ui.update { it.copy(profile = profile, email = profile.email ?: it.email) } }
        }
    }

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }
}

data class AccountSettingsUi(
    val loading: Boolean = true,
    val profile: Profile? = null,
    val email: String = "",
    val changingEmail: Boolean = false,
    val sendingReset: Boolean = false,
    val error: String? = null,
    val emailError: String? = null,
    val message: String? = null,
)

@HiltViewModel
class AccountSettingsViewModel @Inject constructor(
    private val player: PlayerRepository,
    private val auth: AuthRepository,
    private val session: SessionManager,
) : ViewModel() {
    private val _ui = MutableStateFlow(AccountSettingsUi(email = session.snapshotEmail().orEmpty()))
    val ui: StateFlow<AccountSettingsUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { player.myProfile() }
                .onSuccess { profile ->
                    _ui.update {
                        it.copy(loading = false, profile = profile, email = profile.email ?: session.snapshotEmail().orEmpty())
                    }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    fun changeEmail(newEmail: String) {
        if (_ui.value.changingEmail) return
        if (!Validation.isEmailValid(newEmail)) {
            _ui.update { it.copy(emailError = "Enter a valid email address.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(changingEmail = true, error = null, emailError = null, message = null) }
            runCatching { auth.updateEmail(newEmail) }
                .onSuccess {
                    _ui.update {
                        it.copy(
                            changingEmail = false,
                            message = "Confirmation sent to $newEmail. Open the link in that inbox before the change takes effect.",
                        )
                    }
                }
                .onFailure { e -> _ui.update { it.copy(changingEmail = false, error = e.hmxMessage()) } }
        }
    }

    fun sendPasswordReset() {
        val email = _ui.value.email.ifBlank { session.snapshotEmail().orEmpty() }
        if (!Validation.isEmailValid(email)) {
            _ui.update { it.copy(error = "No valid email is on file. Contact support.") }
            return
        }
        if (_ui.value.sendingReset) return
        viewModelScope.launch {
            _ui.update { it.copy(sendingReset = true, error = null, message = null) }
            runCatching { auth.requestPasswordReset(email) }
                .onSuccess {
                    _ui.update { it.copy(sendingReset = false, message = "Password reset link sent to $email.") }
                }
                .onFailure { e -> _ui.update { it.copy(sendingReset = false, error = e.hmxMessage()) } }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _ui.update { it.copy(error = null, message = null) }
            runCatching {
                player.requestAccountDeletion()
                auth.signOut()
            }
                .onFailure { e -> _ui.update { it.copy(error = e.hmxMessage()) } }
        }
    }

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }

    fun clearMessage() = _ui.update { it.copy(message = null, error = null, emailError = null) }
}

@Composable
fun AccountSettingsScreen(nav: NavHostController, viewModel: AccountSettingsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    var showEmailDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    HmxPage(title = "Account settings", onBack = { nav.popBackStack() }) {
        when {
            ui.loading -> LoadingBlock("Loading account")
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (ui.message != null) item { InlineSuccess(ui.message!!) }
                if (ui.error != null) item { InlineError(ui.error!!) }
                item {
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(HmxDimens.cardPadding)) {
                            GlassSectionHeader("Identity")
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "@${ui.profile?.username ?: "player"}",
                                style = MaterialTheme.typography.titleLarge,
                                color = HmxColor.Ink,
                            )
                            Text(
                                ui.email.ifBlank { "No email on file" },
                                style = MaterialTheme.typography.bodySmall,
                                color = HmxColor.InkSecondary,
                            )
                            Spacer(Modifier.height(14.dp))
                            GlassButton(
                                text = "Change email",
                                onClick = { showEmailDialog = true },
                                variant = GlassButtonVariant.Secondary,
                                leadingIcon = Icons.Rounded.Email,
                            )
                        }
                    }
                }
                item {
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(HmxDimens.cardPadding)) {
                            Text("Password", style = MaterialTheme.typography.titleMedium, color = HmxColor.Ink)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "We never ask for your Free Fire password. A reset link is sent to your account email.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HmxColor.InkSecondary,
                            )
                            Spacer(Modifier.height(14.dp))
                            GlassButton(
                                text = "Send password reset link",
                                onClick = viewModel::sendPasswordReset,
                                loading = ui.sendingReset,
                                variant = GlassButtonVariant.Secondary,
                                leadingIcon = Icons.Rounded.Lock,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "The reset link opens HMX Arena on this device. Links expire after one hour.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HmxColor.InkTertiary,
                            )
                        }
                    }
                }
                item {
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(HmxDimens.cardPadding)) {
                            Text("Delete account", style = MaterialTheme.typography.titleMedium, color = HmxColor.Ink)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Requests account removal and signs you out. Records required for fraud prevention, audit or legal obligations may be retained as described in the Privacy Policy.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HmxColor.InkSecondary,
                            )
                            Spacer(Modifier.height(14.dp))
                            GlassButton(
                                text = "Request account deletion",
                                onClick = { confirmDelete = true },
                                variant = GlassButtonVariant.Danger,
                                leadingIcon = Icons.Rounded.DeleteOutline,
                            )
                        }
                    }
                }
                item {
                    GlassButton(
                        text = "Sign out",
                        onClick = viewModel::signOut,
                        variant = GlassButtonVariant.Ink,
                        leadingIcon = Icons.Rounded.Logout,
                    )
                }
            }
        }
    }

    if (showEmailDialog) {
        var email by remember { mutableStateOf(ui.email) }
        var localEmailError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = {
                showEmailDialog = false
                viewModel.clearMessage()
            },
            containerColor = HmxColor.White,
            shape = HmxShapes.dialog,
            title = { Text("Change email", style = MaterialTheme.typography.titleLarge, color = HmxColor.Ink) },
            text = {
                Column {
                    Text(
                        "A confirmation link is sent to the new address. The change is applied after you confirm it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HmxColor.InkSecondary,
                    )
                    Spacer(Modifier.height(14.dp))
                    GlassTextField(
                        value = email,
                        onValueChange = { email = it; localEmailError = null; viewModel.clearMessage() },
                        label = "New email",
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done,
                        error = localEmailError ?: ui.emailError,
                        onImeAction = {
                            if (Validation.isEmailValid(email)) {
                                viewModel.changeEmail(email)
                                showEmailDialog = false
                            } else {
                                localEmailError = "Enter a valid email address."
                            }
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !ui.changingEmail,
                    onClick = {
                        if (Validation.isEmailValid(email)) {
                            viewModel.changeEmail(email)
                            showEmailDialog = false
                        } else {
                            localEmailError = "Enter a valid email address."
                        }
                    },
                ) { Text(if (ui.changingEmail) "Sending…" else "Send confirmation", color = HmxColor.Ink) }
            },
            dismissButton = {
                TextButton(onClick = { showEmailDialog = false }) { Text("Cancel", color = HmxColor.InkSecondary) }
            },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Request account deletion?",
            message = "This signs you out and starts account removal. This cannot be undone once processed.",
            confirmLabel = "Request deletion",
            onConfirm = {
                confirmDelete = false
                viewModel.deleteAccount()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

private val ticketCategories = listOf(
    "account" to "Account",
    "tournament" to "Tournament",
    "technical" to "Technical",
    "payment" to "Payment",
    "report" to "Report",
    "other" to "Other",
)

data class SupportUi(
    val loading: Boolean = true,
    val tickets: List<SupportTicket> = emptyList(),
    val error: String? = null,
    val showForm: Boolean = false,
    val category: String = "technical",
    val subject: String = "",
    val body: String = "",
    val submitting: Boolean = false,
    val formError: String? = null,
    val message: String? = null,
)

@HiltViewModel
class SupportViewModel @Inject constructor(
    private val player: PlayerRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(SupportUi())
    val ui: StateFlow<SupportUi> = _ui.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.tickets.isEmpty(), error = null) }
            runCatching { player.myTickets() }
                .onSuccess { tickets -> _ui.update { it.copy(loading = false, tickets = tickets) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    fun toggleForm() = _ui.update { it.copy(showForm = !it.showForm, formError = null) }
    fun setCategory(value: String) = _ui.update { it.copy(category = value, formError = null) }
    fun setSubject(value: String) = _ui.update { it.copy(subject = value.take(120), formError = null) }
    fun setBody(value: String) = _ui.update { it.copy(body = value.take(2000), formError = null) }

    fun submit() {
        val current = _ui.value
        if (current.submitting) return
        if (current.subject.trim().length < 3) {
            _ui.update { it.copy(formError = "Enter a subject of at least 3 characters.") }
            return
        }
        if (current.body.trim().length < 5) {
            _ui.update { it.copy(formError = "Describe the issue in at least 5 characters.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(submitting = true, formError = null, error = null) }
            runCatching { player.submitSupportTicket(current.category, current.subject.trim(), current.body.trim()) }
                .onSuccess {
                    _ui.update {
                        it.copy(
                            submitting = false,
                            showForm = false,
                            subject = "",
                            body = "",
                            message = "Ticket created. Our team replies in the app.",
                        )
                    }
                    load()
                }
                .onFailure { e -> _ui.update { it.copy(submitting = false, formError = e.hmxMessage()) } }
        }
    }
}

@Composable
fun SupportScreen(nav: NavHostController, viewModel: SupportViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    HmxPage(
        title = "Support",
        onBack = { nav.popBackStack() },
        actions = {
            Text(
                if (ui.showForm) "Close" else "New",
                style = MaterialTheme.typography.labelMedium,
                color = HmxColor.Ink,
                modifier = Modifier.clickable { viewModel.toggleForm() }.padding(10.dp),
            )
        },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ui.message != null) item { InlineSuccess(ui.message!!) }
            if (ui.error != null) item { InlineError(ui.error!!, onRetry = viewModel::load) }
            if (ui.showForm) {
                item {
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(HmxDimens.cardPadding)) {
                            Text("New ticket", style = MaterialTheme.typography.titleLarge, color = HmxColor.Ink)
                            Spacer(Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(ticketCategories) { (key, label) ->
                                    GlassChip(label, ui.category == key) { viewModel.setCategory(key) }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            GlassTextField(
                                value = ui.subject,
                                onValueChange = viewModel::setSubject,
                                label = "Subject",
                                placeholder = "Short summary",
                            )
                            Spacer(Modifier.height(12.dp))
                            GlassTextField(
                                value = ui.body,
                                onValueChange = viewModel::setBody,
                                label = "What happened?",
                                maxLines = 6,
                                error = ui.formError,
                            )
                            Spacer(Modifier.height(12.dp))
                            GlassButton(
                                text = "Submit ticket",
                                onClick = viewModel::submit,
                                loading = ui.submitting,
                                variant = GlassButtonVariant.Ink,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Include the tournament name, match time and any error text. Never send your game password.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HmxColor.InkTertiary,
                            )
                        }
                    }
                }
            }
            item {
                InlineNotice(
                    "Support is handled in the app. Replies from the HMX team appear on the ticket. Email ${LegalText.SUPPORT_EMAIL} if you cannot sign in.",
                )
            }
            item { GlassSectionHeader("Your tickets") }
            if (ui.loading && ui.tickets.isEmpty()) {
                item { LoadingBlock("Loading tickets") }
            } else if (ui.tickets.isEmpty()) {
                item {
                    EmptyBlock(
                        title = "No tickets yet",
                        message = "Open a ticket when something goes wrong with a registration, room or result.",
                        icon = Icons.Rounded.SupportAgent,
                        actionLabel = "Open a ticket",
                        onAction = viewModel::toggleForm,
                    )
                }
            } else {
                items(ui.tickets, key = { it.id }) { ticket ->
                    GlassSurface(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) {
                            nav.navigate(Routes.supportTicket(ticket.id))
                        },
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    ticket.subject,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = HmxColor.Ink,
                                    maxLines = 2,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "${ticket.category.replaceFirstChar { it.uppercase() }} · ${Format.relative(ticket.createdAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HmxColor.InkTertiary,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            StatusPill(ticket.status.replaceFirstChar { it.uppercase() })
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = HmxColor.InkTertiary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

data class TicketUi(
    val loading: Boolean = true,
    val ticket: SupportTicket? = null,
    val messages: List<SupportMessage> = emptyList(),
    val reply: String = "",
    val sending: Boolean = false,
    val error: String? = null,
    val replyError: String? = null,
)

@HiltViewModel
class TicketViewModel @Inject constructor(
    private val player: PlayerRepository,
    private val session: SessionManager,
) : ViewModel() {
    private val _ui = MutableStateFlow(TicketUi())
    val ui: StateFlow<TicketUi> = _ui.asStateFlow()
    private var ticketId: String? = null

    fun isMine(message: SupportMessage): Boolean = message.senderId == session.snapshotUserId()

    fun load(id: String) {
        ticketId = id
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.ticket == null, error = null) }
            runCatching {
                val ticket = player.supportTicket(id)
                val messages = if (ticket == null) emptyList() else player.ticketMessages(id)
                ticket to messages
            }
                .onSuccess { (ticket, messages) ->
                    _ui.update { it.copy(loading = false, ticket = ticket, messages = messages) }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    fun setReply(value: String) = _ui.update { it.copy(reply = value.take(2000), replyError = null) }

    fun send() {
        val id = ticketId ?: return
        val body = _ui.value.reply.trim()
        if (body.length < 2) {
            _ui.update { it.copy(replyError = "Write a message first.") }
            return
        }
        if (_ui.value.sending) return
        viewModelScope.launch {
            _ui.update { it.copy(sending = true, replyError = null, error = null) }
            runCatching { player.sendSupportMessage(id, body) }
                .onSuccess {
                    _ui.update { it.copy(sending = false, reply = "") }
                    load(id)
                }
                .onFailure { e -> _ui.update { it.copy(sending = false, replyError = e.hmxMessage()) } }
        }
    }
}

@Composable
fun TicketScreen(nav: NavHostController, ticketId: String, viewModel: TicketViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    LaunchedEffect(ticketId) { viewModel.load(ticketId) }

    HmxPage(title = "Ticket", onBack = { nav.popBackStack() }) {
        when {
            ui.loading -> LoadingBlock("Loading ticket")
            ui.error != null && ui.ticket == null -> InlineError(ui.error!!, onRetry = { viewModel.load(ticketId) })
            ui.ticket == null -> EmptyBlock(
                title = "Ticket not found",
                message = "This ticket does not belong to your account or no longer exists.",
                icon = Icons.Rounded.SupportAgent,
            )
            else -> {
                val ticket = ui.ticket!!
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        GlassSurface(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(HmxDimens.cardPadding)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        ticket.subject,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = HmxColor.Ink,
                                        modifier = Modifier.weight(1f),
                                    )
                                    StatusPill(ticket.status.replaceFirstChar { it.uppercase() })
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${ticket.category.replaceFirstChar { it.uppercase() }} · opened ${Format.dateTime(ticket.createdAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HmxColor.InkTertiary,
                                )
                            }
                        }
                    }
                    items(ui.messages, key = { it.id }) { message ->
                        GlassSurface(
                            Modifier.fillMaxWidth(),
                            fill = if (viewModel.isMine(message)) HmxColor.GlassStrong else HmxColor.Glass,
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    if (viewModel.isMine(message)) "You" else "HMX support",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = HmxColor.InkTertiary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(message.body, style = MaterialTheme.typography.bodyMedium, color = HmxColor.Ink)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    Format.relative(message.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HmxColor.InkTertiary,
                                )
                            }
                        }
                    }
                    if (ticket.status != "closed") {
                        item {
                            GlassSurface(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(HmxDimens.cardPadding)) {
                                    GlassTextField(
                                        value = ui.reply,
                                        onValueChange = viewModel::setReply,
                                        label = "Add a reply",
                                        maxLines = 4,
                                        error = ui.replyError,
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    GlassButton(
                                        text = "Send reply",
                                        onClick = viewModel::send,
                                        loading = ui.sending,
                                        variant = GlassButtonVariant.Ink,
                                    )
                                }
                            }
                        }
                    } else {
                        item { InlineNotice("This ticket is closed. Open a new ticket if you need more help.") }
                    }
                }
            }
        }
    }
}

@Composable
fun HelpScreen(nav: NavHostController) {
    val entries = listOf(
        "How do I register for a tournament?" to
            "Open Arena, choose a tournament in registration-open status and tap Register. Solo events need only your Free Fire IGN and UID. Team events require a team of the right size.",
        "Where are the room ID and password?" to
            "Register first. Once tournament staff release the room, open Matches, choose the match and tap View room credentials. Details are shown only to registered participants.",
        "How are results verified?" to
            "Submit your placement, kills and a screenshot of the in-game result. Staff compare submissions and evidence. Points are awarded after verification.",
        "How do I raise a dispute?" to
            "Open the match results, find your result and tap Raise a dispute. Explain the issue in at least 10 characters within the dispute window.",
        "Why can't I pay for a tournament?" to
            "Paid tournaments, wallet funding, refunds and payouts are disabled while compliance approval is pending. All live tournaments in this build are free to enter.",
        "Can I change my username?" to
            "Usernames are permanent to keep match history and leaderboards honest. Contact support if your username breaches policy or impersonates someone.",
        "How do I leave or delete a team?" to
            "Open My team. Members can leave; the owner can delete the team or transfer ownership from Manage. Deleting a team does not erase completed tournament history.",
        "Why is there no push notification?" to
            "This build shows alerts inside the app only. Push notifications and background reminders are not configured, so check the app for match updates.",
        "How do I delete my account?" to
            "Open Settings, Account settings and choose Request account deletion. You are signed out and the request is recorded for processing.",
        "How do I report a player?" to
            "Open the player profile and tap Report player, or open a support ticket with the report category. Include match details and evidence.",
    )
    HmxPage(title = "Help and FAQ", onBack = { nav.popBackStack() }) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Answers to the questions players ask most. Still stuck? Open a support ticket.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HmxColor.InkSecondary,
                )
            }
            items(entries) { (question, answer) ->
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text(question, style = MaterialTheme.typography.titleMedium, color = HmxColor.Ink)
                        Spacer(Modifier.height(6.dp))
                        Text(answer, style = MaterialTheme.typography.bodySmall, color = HmxColor.InkSecondary)
                    }
                }
            }
            item {
                GlassButton(
                    text = "Open support",
                    onClick = { nav.navigate(Routes.SUPPORT) },
                    variant = GlassButtonVariant.Ink,
                )
            }
        }
    }
}

@Composable
fun AboutScreen(nav: NavHostController) {
    HmxPage(title = "About", onBack = { nav.popBackStack() }) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Text("HMX Arena", style = MaterialTheme.typography.displayMedium, color = HmxColor.Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "Competitive Free Fire tournaments with verified results and a clean, white-glass interface.",
                style = MaterialTheme.typography.bodyMedium,
                color = HmxColor.InkSecondary,
            )
            Spacer(Modifier.height(20.dp))
            GlassSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(HmxDimens.cardPadding)) {
                    AboutRow("Version", BuildConfig.VERSION_NAME)
                    AboutRow("Build", "Free entry preview")
                    AboutRow("Payments", "Disabled pending compliance")
                    AboutRow("Support", LegalText.SUPPORT_EMAIL)
                }
            }
            Spacer(Modifier.height(16.dp))
            InlineNotice(
                "The app uses a live Supabase backend. Tournament schedules, room credentials and results come from the server, not from local sample data.",
            )
            Spacer(Modifier.height(16.dp))
            GlassSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    GlassActionRow("Terms of Service", onClick = { nav.navigate(Routes.legal("terms")) })
                    GlassActionRow("Privacy Policy", onClick = { nav.navigate(Routes.legal("privacy")) })
                    GlassActionRow("Fair Play Policy", onClick = { nav.navigate(Routes.legal("fair-play")) })
                    GlassActionRow("Refund Policy", onClick = { nav.navigate(Routes.legal("refunds")) })
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "© 2026 HMX Arena. Free Fire is a trademark of its respective owner; HMX Arena is not affiliated with the publisher.",
                style = MaterialTheme.typography.labelSmall,
                color = HmxColor.InkTertiary,
            )
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = HmxColor.InkSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = HmxColor.Ink)
    }
}

@Composable
fun LegalScreen(nav: NavHostController, kind: String) {
    val (title, body) = LegalText.forKind(kind)
    HmxPage(title = title, onBack = { nav.popBackStack() }) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            GlassSurface(Modifier.fillMaxWidth()) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = HmxColor.InkSecondary,
                    modifier = Modifier.padding(HmxDimens.cardPadding),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Questions? ${LegalText.SUPPORT_EMAIL}",
                style = MaterialTheme.typography.bodySmall,
                color = HmxColor.InkTertiary,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
