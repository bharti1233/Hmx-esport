package com.hmx.arena.ui.tournament

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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.hmx.arena.core.designsystem.GlassButton
import com.hmx.arena.core.designsystem.GlassButtonVariant
import com.hmx.arena.core.designsystem.GlassSectionHeader
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.model.Registration
import com.hmx.arena.core.model.Tournament
import com.hmx.arena.core.util.Format
import com.hmx.arena.core.util.RegistrationRules
import com.hmx.arena.data.remote.ApiException
import com.hmx.arena.data.repository.PlayerRepository
import com.hmx.arena.data.repository.TeamDetail
import com.hmx.arena.data.repository.TeamRepository
import com.hmx.arena.data.repository.TournamentRepository
import com.hmx.arena.ui.common.ConfirmDialog
import com.hmx.arena.ui.common.EmptyBlock
import com.hmx.arena.ui.common.HmxPage
import com.hmx.arena.ui.common.InlineError
import com.hmx.arena.ui.common.InlineNotice
import com.hmx.arena.ui.common.InlineSuccess
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

data class RegisterData(
    val tournament: Tournament,
    val team: TeamDetail?,
    val hasGameAccount: Boolean,
    val alreadyRegistered: Boolean,
)

data class RegisterUi(
    val loading: Boolean = true,
    val data: RegisterData? = null,
    val error: String? = null,
    val busy: Boolean = false,
    val actionError: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val tournaments: TournamentRepository,
    private val teams: TeamRepository,
    private val player: PlayerRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(RegisterUi())
    val ui: StateFlow<RegisterUi> = _ui.asStateFlow()

    fun load(id: String) {
        viewModelScope.launch {
            _ui.value = RegisterUi()
            runCatching {
                val tournament = tournaments.tournament(id)
                    ?: throw ApiException("This tournament could not be found.")
                val registrations = runCatching { tournaments.registrations() }.getOrDefault(emptyList())
                RegisterData(
                    tournament = tournament,
                    team = runCatching { teams.myTeam() }.getOrNull(),
                    hasGameAccount = runCatching { player.gameAccounts().isNotEmpty() }.getOrDefault(false),
                    alreadyRegistered = registrations.any { it.tournamentId == id && it.status != "cancelled" },
                )
            }
                .onSuccess { data -> _ui.value = RegisterUi(false, data) }
                .onFailure { e -> _ui.value = RegisterUi(false, null, e.hmxMessage()) }
        }
    }

    fun register() {
        val data = _ui.value.data ?: return
        if (_ui.value.busy) return
        val needsTeam = data.tournament.maxTeamSize > 1
        val blocker = RegistrationRules.blocker(
            tournament = data.tournament,
            hasGameAccount = data.hasGameAccount,
            hasTeam = data.team != null,
            alreadyRegistered = data.alreadyRegistered,
        )
        if (blocker != null) {
            _ui.update { it.copy(actionError = RegistrationRules.message(blocker)) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, actionError = null) }
            runCatching {
                tournaments.register(
                    tournamentId = data.tournament.id,
                    teamId = if (needsTeam) data.team?.team?.id else null,
                )
            }
                .onSuccess { _ui.update { it.copy(busy = false, done = true) } }
                .onFailure { e -> _ui.update { it.copy(busy = false, actionError = e.hmxMessage()) } }
        }
    }
}

@Composable
fun RegisterTournamentScreen(
    nav: NavHostController,
    id: String,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    LaunchedEffect(id) { viewModel.load(id) }
    LaunchedEffect(ui.done) { if (ui.done) nav.popBackStack() }

    HmxPage(title = "Register", onBack = { nav.popBackStack() }) {
        when {
            ui.loading -> LoadingBlock("Checking eligibility")
            ui.error != null -> InlineError(ui.error!!, onRetry = { viewModel.load(id) })
            ui.data == null -> InlineError("This tournament could not be loaded.")
            else -> {
                val data = ui.data!!
                val needsTeam = data.tournament.maxTeamSize > 1
                val teamDetail = data.team
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Text(
                            data.tournament.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = HmxColor.Ink,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${data.tournament.modeLabel} · ${if (data.tournament.isFree) "Free entry" else Format.money(data.tournament.entryFeePaise)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = HmxColor.InkSecondary,
                        )
                    }
                    item {
                        GlassSurface(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(HmxDimens.cardPadding)) {
                                GlassSectionHeader("Entry checklist")
                                Spacer(Modifier.height(8.dp))
                                ChecklistRow(
                                    label = "Free Fire account linked",
                                    ok = data.hasGameAccount,
                                    detail = if (data.hasGameAccount) "IGN and UID are on file" else "Required for eligibility",
                                )
                                Spacer(Modifier.height(12.dp))
                                ChecklistRow(
                                    label = if (needsTeam) "Team ready" else "Solo entry",
                                    ok = if (needsTeam) teamDetail != null else true,
                                    detail = when {
                                        !needsTeam -> "No team needed for solo"
                                        teamDetail != null -> "${teamDetail.team.name} · ${teamDetail.members.size} players"
                                        else -> "Create or join a team first"
                                    },
                                )
                                Spacer(Modifier.height(12.dp))
                                ChecklistRow(
                                    label = "Registration window",
                                    ok = data.tournament.status == "registration_open",
                                    detail = if (data.tournament.slotsRemaining > 0) {
                                        "${data.tournament.slotsRemaining} slots left"
                                    } else {
                                        "Full — joining places you on the waitlist"
                                    },
                                )
                            }
                        }
                    }
                    if (!data.hasGameAccount) {
                        item {
                            GlassButton(
                                text = "Add game account",
                                onClick = { nav.navigate(Routes.GAME_ACCOUNTS) },
                                variant = GlassButtonVariant.Secondary,
                            )
                        }
                    }
                    if (needsTeam && teamDetail == null) {
                        item {
                            GlassButton(
                                text = "Go to my team",
                                onClick = { nav.navigate(Routes.TEAM) },
                                variant = GlassButtonVariant.Secondary,
                            )
                        }
                    }
                    if (data.alreadyRegistered) {
                        item {
                            InlineSuccess("You are already registered for this tournament.")
                        }
                        item {
                            GlassButton(
                                text = "Back to tournament",
                                onClick = { nav.popBackStack() },
                                variant = GlassButtonVariant.Ink,
                            )
                        }
                    } else {
                        if (ui.actionError != null) item { InlineError(ui.actionError!!) }
                        item {
                            GlassButton(
                                text = if (data.tournament.slotsRemaining > 0) "Confirm registration" else "Join waitlist",
                                onClick = viewModel::register,
                                loading = ui.busy,
                                enabled = data.hasGameAccount && (!needsTeam || teamDetail != null),
                                variant = GlassButtonVariant.Ink,
                            )
                        }
                        item {
                            InlineNotice(
                                "Free entry. HMX Arena never charges you in the app while paid features are disabled.",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistRow(label: String, ok: Boolean, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = if (ok) HmxColor.Ink else HmxColor.Danger,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = HmxColor.Ink)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = HmxColor.InkSecondary)
        }
    }
}

data class RegRow(val registration: Registration, val tournament: Tournament)

data class MyRegistrationsUi(
    val loading: Boolean = true,
    val items: List<RegRow> = emptyList(),
    val error: String? = null,
    val busyId: String? = null,
    val actionError: String? = null,
    val message: String? = null,
)

@HiltViewModel
class MyRegistrationsViewModel @Inject constructor(
    private val repository: TournamentRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(MyRegistrationsUi())
    val ui: StateFlow<MyRegistrationsUi> = _ui.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.items.isEmpty(), error = null) }
            runCatching {
                val registrations = repository.registrations()
                val tournaments = repository
                    .tournamentsByIds(registrations.map { it.tournamentId }.distinct())
                    .associateBy { it.id }
                registrations.mapNotNull { registration ->
                    tournaments[registration.tournamentId]?.let { RegRow(registration, it) }
                }
            }
                .onSuccess { items -> _ui.update { it.copy(loading = false, items = items) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    fun cancel(registrationId: String) {
        if (_ui.value.busyId != null) return
        viewModelScope.launch {
            _ui.update { it.copy(busyId = registrationId, actionError = null, message = null) }
            runCatching { repository.cancelRegistration(registrationId) }
                .onSuccess {
                    _ui.update { it.copy(busyId = null, message = "Registration cancelled.") }
                    load()
                }
                .onFailure { e -> _ui.update { it.copy(busyId = null, actionError = e.hmxMessage()) } }
        }
    }
}

@Composable
fun MyRegistrationsScreen(nav: NavHostController, viewModel: MyRegistrationsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    var confirm by remember { mutableStateOf<RegRow?>(null) }
    LaunchedEffect(Unit) { viewModel.load() }

    HmxPage(title = "My registrations", onBack = { nav.popBackStack() }) {
        when {
            ui.loading && ui.items.isEmpty() -> LoadingBlock("Loading registrations")
            ui.error != null && ui.items.isEmpty() -> InlineError(ui.error!!, onRetry = viewModel::load)
            ui.items.isEmpty() -> EmptyBlock(
                title = "No registrations yet",
                message = "Your solo and team registrations appear here. Open the arena to find a tournament.",
                icon = Icons.Rounded.EmojiEvents,
                actionLabel = "Browse tournaments",
                onAction = { nav.navigate(Routes.TOURNAMENTS) },
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (ui.message != null) item { InlineSuccess(ui.message!!) }
                if (ui.actionError != null) item { InlineError(ui.actionError!!) }
                items(ui.items, key = { it.registration.id }) { row ->
                    GlassSurface(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) {
                            nav.navigate(Routes.tournament(row.tournament.id))
                        },
                    ) {
                        Column(Modifier.padding(HmxDimens.cardPadding)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    row.tournament.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = HmxColor.Ink,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                StatusPill(row.registration.status.replaceFirstChar { it.uppercase() })
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                listOfNotNull(
                                    row.registration.slotNo?.let { "Slot #$it" },
                                    row.registration.registrationType.replaceFirstChar { it.uppercase() },
                                    Format.dateTime(row.tournament.startsAt),
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = HmxColor.InkSecondary,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GlassButton(
                                    text = "Open",
                                    onClick = { nav.navigate(Routes.tournament(row.tournament.id)) },
                                    variant = GlassButtonVariant.Secondary,
                                    modifier = Modifier.weight(1f),
                                )
                                if (row.registration.status == "confirmed" || row.registration.status == "waitlisted") {
                                    GlassButton(
                                        text = if (ui.busyId == row.registration.id) "Cancelling…" else "Cancel",
                                        onClick = { confirm = row },
                                        variant = GlassButtonVariant.Secondary,
                                        enabled = ui.busyId == null,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirm?.let { row ->
        ConfirmDialog(
            title = "Cancel registration?",
            message = "Release your slot for ${row.tournament.title}? A waitlisted player may be promoted.",
            confirmLabel = "Cancel registration",
            onConfirm = {
                viewModel.cancel(row.registration.id)
                confirm = null
            },
            onDismiss = { confirm = null },
        )
    }
}
