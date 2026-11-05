package com.hmx.arena.ui.team

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.hmx.arena.core.designsystem.GlassButton
import com.hmx.arena.core.designsystem.GlassButtonVariant
import com.hmx.arena.core.designsystem.GlassSectionHeader
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.designsystem.HmxShapes
import com.hmx.arena.core.model.Team
import com.hmx.arena.core.model.TeamInvitation
import com.hmx.arena.core.model.TeamMember
import com.hmx.arena.core.util.Validation
import com.hmx.arena.data.remote.SessionManager
import com.hmx.arena.data.repository.TeamDetail
import com.hmx.arena.data.repository.TeamRepository
import com.hmx.arena.ui.common.ConfirmDialog
import com.hmx.arena.ui.common.EmptyBlock
import com.hmx.arena.ui.common.GlassAvatar
import com.hmx.arena.ui.common.GlassTextField
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TeamUi(
    val loading: Boolean = true,
    val team: TeamDetail? = null,
    val invitations: List<TeamInvitation> = emptyList(),
    val error: String? = null,
    val busy: Boolean = false,
    val actionError: String? = null,
    val message: String? = null,
)

@HiltViewModel
class TeamViewModel @Inject constructor(
    private val repository: TeamRepository,
    private val session: SessionManager,
) : ViewModel() {
    private val _ui = MutableStateFlow(TeamUi())
    val ui: StateFlow<TeamUi> = _ui.asStateFlow()

    val currentUserId: String? get() = session.snapshotUserId()

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.team == null, error = null) }
            runCatching {
                coroutineScope {
                    val team = async { repository.myTeam() }
                    val invitations = async {
                        runCatching { repository.myInvitations() }.getOrDefault(emptyList())
                    }
                    team.await() to invitations.await()
                }
            }
                .onSuccess { (team, invitations) ->
                    _ui.update { it.copy(loading = false, team = team, invitations = invitations) }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    fun leave() {
        val team = _ui.value.team ?: return
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, actionError = null, message = null) }
            runCatching { repository.leave(team.team.id) }
                .onSuccess { _ui.update { it.copy(busy = false, team = null, message = "You left the team.") } }
                .onFailure { e -> _ui.update { it.copy(busy = false, actionError = e.hmxMessage()) } }
        }
    }

    fun delete() {
        val team = _ui.value.team ?: return
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, actionError = null, message = null) }
            runCatching { repository.deleteTeam(team.team.id) }
                .onSuccess { _ui.update { it.copy(busy = false, team = null, message = "Team deleted.") } }
                .onFailure { e -> _ui.update { it.copy(busy = false, actionError = e.hmxMessage()) } }
        }
    }

    fun clearMessage() = _ui.update { it.copy(message = null, actionError = null) }
}

@Composable
fun TeamScreen(nav: NavHostController, viewModel: TeamViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.load() }

    HmxPage(
        title = "My team",
        onBack = { nav.popBackStack() },
        actions = {
            Text(
                "Refresh",
                style = MaterialTheme.typography.labelMedium,
                color = HmxColor.Ink,
                modifier = Modifier.clickable { viewModel.load() }.padding(10.dp),
            )
        },
    ) {
        when {
            ui.loading && ui.team == null -> LoadingBlock("Loading your team")
            ui.error != null && ui.team == null -> InlineError(ui.error!!, onRetry = viewModel::load)
            ui.team == null -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (ui.message != null) item { InlineSuccess(ui.message!!) }
                if (ui.actionError != null) item { InlineError(ui.actionError!!) }
                item {
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.Group,
                                contentDescription = null,
                                tint = HmxColor.Ink,
                                modifier = Modifier.size(34.dp),
                            )
                            Spacer(Modifier.height(14.dp))
                            Text("No team yet", style = MaterialTheme.typography.headlineMedium, color = HmxColor.Ink)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Create a squad for team tournaments, or accept an invitation from a captain.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HmxColor.InkSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(Modifier.height(18.dp))
                            GlassButton(
                                text = "Create a team",
                                onClick = { nav.navigate(Routes.CREATE_TEAM) },
                                variant = GlassButtonVariant.Ink,
                                leadingIcon = Icons.Rounded.Add,
                            )
                        }
                    }
                }
                item {
                    GlassSectionHeader(
                        "Invitations",
                        action = if (ui.invitations.isEmpty()) null else "View all",
                        onAction = if (ui.invitations.isEmpty()) null else {
                            { nav.navigate(Routes.INVITATIONS) }
                        },
                    )
                }
                if (ui.invitations.isEmpty()) {
                    item {
                        InlineNotice("Pending team invitations will appear here.")
                    }
                } else {
                    items(ui.invitations.take(3), key = { it.id }) { invitation ->
                        GlassSurface(
                            Modifier.fillMaxWidth().clickable(role = Role.Button) {
                                nav.navigate(Routes.INVITATIONS)
                            },
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.PersonAdd, contentDescription = null, tint = HmxColor.InkSecondary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Team invitation · ${invitation.status}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = HmxColor.Ink,
                                    modifier = Modifier.weight(1f),
                                )
                                Text("Open", style = MaterialTheme.typography.labelMedium, color = HmxColor.Ink)
                            }
                        }
                    }
                }
            }
            else -> {
                val detail = ui.team!!
                val owner = detail.team.ownerId == viewModel.currentUserId
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (ui.message != null) item { InlineSuccess(ui.message!!) }
                    if (ui.actionError != null) item { InlineError(ui.actionError!!) }
                    item { TeamHeaderCard(detail) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (owner) {
                                GlassButton(
                                    text = "Invite",
                                    onClick = { nav.navigate(Routes.invite(detail.team.id)) },
                                    variant = GlassButtonVariant.Secondary,
                                    leadingIcon = Icons.Rounded.PersonAdd,
                                    modifier = Modifier.weight(1f),
                                )
                                GlassButton(
                                    text = "Manage",
                                    onClick = { nav.navigate(Routes.MANAGE_TEAM) },
                                    variant = GlassButtonVariant.Secondary,
                                    leadingIcon = Icons.Rounded.Settings,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                GlassButton(
                                    text = "Leave team",
                                    onClick = { confirmLeave = true },
                                    variant = GlassButtonVariant.Secondary,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    if (ui.invitations.isNotEmpty()) {
                        item {
                            GlassSurface(
                                Modifier.fillMaxWidth().clickable(role = Role.Button) {
                                    nav.navigate(Routes.INVITATIONS)
                                },
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${ui.invitations.size} pending team invitation(s)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = HmxColor.Ink,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text("View", style = MaterialTheme.typography.labelMedium, color = HmxColor.Ink)
                                }
                            }
                        }
                    }
                    item {
                        GlassSectionHeader("Roster (${detail.members.size})")
                        GlassSurface(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                detail.members.forEach { member ->
                                    MemberRow(
                                        member = member,
                                        detail = detail,
                                        isMe = member.userId == viewModel.currentUserId,
                                    )
                                }
                            }
                        }
                    }
                    if (owner) {
                        item {
                            GlassButton(
                                text = "Delete team",
                                onClick = { confirmDelete = true },
                                variant = GlassButtonVariant.Danger,
                                enabled = !ui.busy,
                            )
                        }
                        item {
                            InlineNotice("Deleting a team removes its roster. Completed tournament history is retained for audit.")
                        }
                    }
                }
            }
        }
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = "Leave team?",
            message = "You will leave the roster and lose access to team-only tournaments with this squad.",
            confirmLabel = "Leave team",
            onConfirm = { confirmLeave = false; viewModel.leave() },
            onDismiss = { confirmLeave = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete team?",
            message = "This permanently disbands the team. This cannot be undone.",
            confirmLabel = "Delete team",
            onConfirm = { confirmDelete = false; viewModel.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun TeamHeaderCard(detail: TeamDetail) {
    GlassSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(HmxDimens.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassAvatar(detail.team.logoUrl, detail.team.name, 62.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(detail.team.name, style = MaterialTheme.typography.headlineMedium, color = HmxColor.Ink)
                    Text(
                        "[${detail.team.tag}] · ${detail.team.region}",
                        style = MaterialTheme.typography.bodySmall,
                        color = HmxColor.InkSecondary,
                    )
                    Spacer(Modifier.height(5.dp))
                    StatusPill(detail.team.game.replace("_", " ").replaceFirstChar { it.uppercase() })
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Owner: ${detail.owner?.display ?: "Team owner"}",
                style = MaterialTheme.typography.bodySmall,
                color = HmxColor.InkSecondary,
            )
        }
    }
}

@Composable
private fun MemberRow(member: TeamMember, detail: TeamDetail, isMe: Boolean) {
    val profile = detail.profileFor(member.userId)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassAvatar(profile?.avatarUrl, profile?.display ?: member.userId.take(6), 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                profile?.display ?: "Player",
                style = MaterialTheme.typography.bodyLarge,
                color = HmxColor.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(if (profile != null) "@${profile.username}" else member.userId.take(8))
                    if (isMe) append(" · you")
                },
                style = MaterialTheme.typography.bodySmall,
                color = HmxColor.InkTertiary,
            )
        }
        StatusPill(member.role.replaceFirstChar { it.uppercase() })
    }
}

data class CreateTeamUi(
    val name: String = "",
    val tag: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val nameError: String? = null,
    val tagError: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class CreateTeamViewModel @Inject constructor(
    private val repository: TeamRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(CreateTeamUi())
    val ui: StateFlow<CreateTeamUi> = _ui.asStateFlow()

    fun setName(value: String) = _ui.update { it.copy(name = value, nameError = null, error = null) }
    fun setTag(value: String) = _ui.update { it.copy(tag = value.uppercase(), tagError = null, error = null) }

    fun submit() {
        val current = _ui.value
        if (current.loading) return
        val nameError = if (!Validation.isTeamNameValid(current.name)) "Team name must be 3-24 characters." else null
        val tagError = if (!Validation.isTeamTagValid(current.tag)) "Tag must be 2-5 characters." else null
        if (nameError != null || tagError != null) {
            _ui.update { it.copy(nameError = nameError, tagError = tagError) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching { repository.createTeam(current.name.trim(), current.tag.trim()) }
                .onSuccess { _ui.update { it.copy(loading = false, done = true) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }
}

@Composable
fun CreateTeamScreen(nav: NavHostController, viewModel: CreateTeamViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(ui.done) { if (ui.done) nav.popBackStack() }
    HmxPage(title = "Create team", onBack = { nav.popBackStack() }) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("Build your squad.", style = MaterialTheme.typography.headlineMedium, color = HmxColor.Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "You become the owner and can invite players after creating the team.",
                style = MaterialTheme.typography.bodySmall,
                color = HmxColor.InkSecondary,
            )
            Spacer(Modifier.height(22.dp))
            GlassTextField(
                value = ui.name,
                onValueChange = viewModel::setName,
                label = "Team name",
                placeholder = "e.g. Night Owls",
                error = ui.nameError,
            )
            Spacer(Modifier.height(14.dp))
            GlassTextField(
                value = ui.tag,
                onValueChange = viewModel::setTag,
                label = "Team tag",
                placeholder = "e.g. NGTW",
                error = ui.tagError,
                imeAction = ImeAction.Done,
                onImeAction = viewModel::submit,
            )
            if (ui.error != null) {
                Spacer(Modifier.height(12.dp))
                InlineError(ui.error!!)
            }
            Spacer(Modifier.height(22.dp))
            GlassButton(
                text = "Create team",
                onClick = viewModel::submit,
                loading = ui.loading,
                variant = GlassButtonVariant.Ink,
            )
            Spacer(Modifier.height(14.dp))
            InlineNotice("Team names and tags must be unique and must not impersonate other organisations.")
        }
    }
}

data class InviteUi(
    val teamName: String = "",
    val username: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class InviteViewModel @Inject constructor(
    private val repository: TeamRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(InviteUi())
    val ui: StateFlow<InviteUi> = _ui.asStateFlow()
    private var teamId: String? = null

    fun load(id: String) {
        teamId = id
        viewModelScope.launch {
            runCatching { repository.teamDetail(id) }
                .onSuccess { detail -> _ui.update { it.copy(teamName = detail?.team?.name ?: "") } }
        }
    }

    fun setUsername(value: String) = _ui.update { it.copy(username = value, error = null, message = null) }

    fun invite() {
        if (_ui.value.busy) return
        val id = teamId ?: return
        val username = _ui.value.username.trim().removePrefix("@")
        if (!Validation.isUsernameValid(username)) {
            _ui.update { it.copy(error = "Enter a valid username.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null, message = null) }
            runCatching { repository.invite(id, username) }
                .onSuccess {
                    _ui.update { it.copy(busy = false, username = "", message = "Invitation sent to @$username.") }
                }
                .onFailure { e -> _ui.update { it.copy(busy = false, error = e.hmxMessage()) } }
        }
    }
}

@Composable
fun InvitePlayerScreen(nav: NavHostController, teamId: String, viewModel: InviteViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(teamId) { viewModel.load(teamId) }
    HmxPage(title = "Invite player", onBack = { nav.popBackStack() }) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text(
                "Invite to ${ui.teamName.ifBlank { "your team" }}",
                style = MaterialTheme.typography.headlineMedium,
                color = HmxColor.Ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Enter the player's HMX username. They must accept before joining the roster.",
                style = MaterialTheme.typography.bodySmall,
                color = HmxColor.InkSecondary,
            )
            Spacer(Modifier.height(22.dp))
            GlassTextField(
                value = ui.username,
                onValueChange = viewModel::setUsername,
                label = "Username",
                placeholder = "shadowstrike",
                imeAction = ImeAction.Done,
                onImeAction = viewModel::invite,
                error = ui.error,
            )
            if (ui.message != null) {
                Spacer(Modifier.height(12.dp))
                InlineSuccess(ui.message!!)
            }
            Spacer(Modifier.height(18.dp))
            GlassButton(
                text = "Send invitation",
                onClick = viewModel::invite,
                loading = ui.busy,
                variant = GlassButtonVariant.Ink,
            )
            Spacer(Modifier.height(10.dp))
            InlineNotice("The invited player sees this in My team → Invitations. Invitations do not reserve a tournament slot.")
        }
    }
}

data class TeamInvitesUi(
    val loading: Boolean = true,
    val rows: List<Pair<TeamInvitation, Team?>> = emptyList(),
    val error: String? = null,
    val busyId: String? = null,
    val actionError: String? = null,
)

@HiltViewModel
class InvitationsViewModel @Inject constructor(
    private val repository: TeamRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(TeamInvitesUi())
    val ui: StateFlow<TeamInvitesUi> = _ui.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.rows.isEmpty(), error = null) }
            runCatching {
                repository.myInvitations().map { invitation ->
                    invitation to runCatching { repository.teamDetail(invitation.teamId)?.team }.getOrNull()
                }
            }
                .onSuccess { rows -> _ui.update { it.copy(loading = false, rows = rows) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    fun respond(invitationId: String, accept: Boolean) {
        if (_ui.value.busyId != null) return
        viewModelScope.launch {
            _ui.update { it.copy(busyId = invitationId, actionError = null) }
            runCatching { repository.respond(invitationId, accept) }
                .onSuccess { load() }
                .onFailure { e -> _ui.update { it.copy(actionError = e.hmxMessage()) } }
            _ui.update { it.copy(busyId = null) }
        }
    }
}

@Composable
fun InvitationsScreen(nav: NavHostController, viewModel: InvitationsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    HmxPage(title = "Invitations", onBack = { nav.popBackStack() }) {
        when {
            ui.loading && ui.rows.isEmpty() -> LoadingBlock("Loading invitations")
            ui.error != null && ui.rows.isEmpty() -> InlineError(ui.error!!, onRetry = viewModel::load)
            ui.rows.isEmpty() -> EmptyBlock(
                title = "No invitations",
                message = "When a captain invites your username it will appear here.",
                icon = Icons.Rounded.PersonAdd,
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (ui.actionError != null) item { InlineError(ui.actionError!!) }
                items(ui.rows, key = { it.first.id }) { row ->
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(HmxDimens.cardPadding)) {
                            Text(
                                row.second?.name ?: "Team invitation",
                                style = MaterialTheme.typography.titleMedium,
                                color = HmxColor.Ink,
                            )
                            Text(
                                row.second?.let { "[${it.tag}] · ${it.region}" } ?: "Team details unavailable",
                                style = MaterialTheme.typography.bodySmall,
                                color = HmxColor.InkSecondary,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GlassButton(
                                    text = if (ui.busyId == row.first.id) "Working…" else "Decline",
                                    onClick = { viewModel.respond(row.first.id, false) },
                                    variant = GlassButtonVariant.Secondary,
                                    enabled = ui.busyId == null,
                                    modifier = Modifier.weight(1f),
                                )
                                GlassButton(
                                    text = "Accept",
                                    onClick = { viewModel.respond(row.first.id, true) },
                                    variant = GlassButtonVariant.Ink,
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

data class ManageTeamUi(
    val loading: Boolean = true,
    val detail: TeamDetail? = null,
    val invites: List<TeamInvitation> = emptyList(),
    val name: String = "",
    val tag: String = "",
    val saving: Boolean = false,
    val uploading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val deleted: Boolean = false,
)

@HiltViewModel
class ManageTeamViewModel @Inject constructor(
    private val repository: TeamRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(ManageTeamUi())
    val ui: StateFlow<ManageTeamUi> = _ui.asStateFlow()
    private var teamId: String? = null

    fun load() {
        viewModelScope.launch {
            val id = teamId ?: runCatching { repository.myTeam()?.team?.id }.getOrNull() ?: return@launch
            teamId = id
            _ui.update { it.copy(loading = it.detail == null, error = null) }
            runCatching {
                coroutineScope {
                    val detail = async {
                        repository.teamDetail(id)
                            ?: throw com.hmx.arena.data.remote.ApiException("Team not found.")
                    }
                    val invites = async { repository.invitationsForTeam(id) }
                    detail.await() to invites.await()
                }
            }
                .onSuccess { (detail, invites) ->
                    _ui.update {
                        it.copy(
                            loading = false,
                            detail = detail,
                            invites = invites,
                            name = detail.team.name,
                            tag = detail.team.tag,
                        )
                    }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    fun setName(value: String) = _ui.update { it.copy(name = value, message = null) }
    fun setTag(value: String) = _ui.update { it.copy(tag = value.uppercase(), message = null) }

    fun save() {
        val current = _ui.value
        if (current.saving) return
        val detail = current.detail ?: return
        if (!Validation.isTeamNameValid(current.name) || !Validation.isTeamTagValid(current.tag)) {
            _ui.update { it.copy(error = "Team name must be 3-24 characters and tag 2-5 characters.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(saving = true, error = null, message = null) }
            runCatching { repository.updateTeam(detail.team.id, current.name.trim(), current.tag.trim()) }
                .onSuccess {
                    _ui.update { it.copy(saving = false, message = "Team details updated.") }
                    load()
                }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = e.hmxMessage()) } }
        }
    }

    fun uploadLogo(image: PickedImage) {
        val detail = _ui.value.detail ?: return
        if (_ui.value.uploading) return
        viewModelScope.launch {
            _ui.update { it.copy(uploading = true, error = null, message = null) }
            runCatching { repository.uploadLogo(detail.team.id, image.bytes, image.mime) }
                .onSuccess {
                    _ui.update { it.copy(uploading = false, message = "Team logo updated.") }
                    load()
                }
                .onFailure { e -> _ui.update { it.copy(uploading = false, error = e.hmxMessage()) } }
        }
    }

    fun transfer(newOwner: String) {
        val detail = _ui.value.detail ?: return
        viewModelScope.launch {
            _ui.update { it.copy(saving = true, error = null, message = null) }
            runCatching { repository.transferOwnership(detail.team.id, newOwner) }
                .onSuccess {
                    _ui.update { it.copy(saving = false, message = "Ownership transferred.") }
                    load()
                }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = e.hmxMessage()) } }
        }
    }

    fun delete() {
        val detail = _ui.value.detail ?: return
        viewModelScope.launch {
            _ui.update { it.copy(saving = true, error = null) }
            runCatching { repository.deleteTeam(detail.team.id) }
                .onSuccess { _ui.update { it.copy(saving = false, deleted = true) } }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = e.hmxMessage()) } }
        }
    }
}

@Composable
fun ManageTeamScreen(nav: NavHostController, viewModel: ManageTeamViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val (logo, pickLogo) = rememberPickedImage()
    var confirmDelete by remember { mutableStateOf(false) }
    var transferTarget by remember { mutableStateOf<TeamMember?>(null) }
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(logo) { if (logo != null) viewModel.uploadLogo(logo) }
    LaunchedEffect(ui.deleted) { if (ui.deleted) nav.popBackStack() }

    HmxPage(title = "Manage team", onBack = { nav.popBackStack() }) {
        when {
            ui.loading && ui.detail == null -> LoadingBlock("Loading team")
            ui.detail == null -> InlineError(ui.error ?: "Team not found.")
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (ui.message != null) item { InlineSuccess(ui.message!!) }
                if (ui.error != null) item { InlineError(ui.error!!) }
                item {
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(HmxDimens.cardPadding), horizontalAlignment = Alignment.CenterHorizontally) {
                            GlassAvatar(ui.detail!!.team.logoUrl, ui.detail!!.team.name, 76.dp)
                            Spacer(Modifier.height(10.dp))
                            GlassButton(
                                text = if (ui.uploading) "Uploading…" else "Change logo",
                                onClick = pickLogo,
                                variant = GlassButtonVariant.Secondary,
                                enabled = !ui.uploading,
                                modifier = Modifier.width(180.dp),
                            )
                        }
                    }
                }
                item {
                    GlassTextField(
                        value = ui.name,
                        onValueChange = viewModel::setName,
                        label = "Team name",
                    )
                }
                item {
                    GlassTextField(
                        value = ui.tag,
                        onValueChange = viewModel::setTag,
                        label = "Team tag",
                    )
                }
                item {
                    GlassButton(
                        text = "Save changes",
                        onClick = viewModel::save,
                        loading = ui.saving,
                        variant = GlassButtonVariant.Ink,
                    )
                }
                item {
                    GlassSectionHeader("Team invitations (${ui.invites.size})")
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            if (ui.invites.isEmpty()) {
                                Text(
                                    "No pending invitations.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HmxColor.InkSecondary,
                                )
                            } else {
                                ui.invites.forEach { invite ->
                                    Text(
                                        "Invitation ${invite.id.take(8)} · ${invite.status}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HmxColor.InkSecondary,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            GlassButton(
                                text = "Invite another player",
                                onClick = { nav.navigate(Routes.invite(ui.detail!!.team.id)) },
                                variant = GlassButtonVariant.Secondary,
                            )
                        }
                    }
                }
                item {
                    GlassSectionHeader("Ownership")
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Transfer the captain role to another roster member. You will become a regular member.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HmxColor.InkSecondary,
                            )
                            Spacer(Modifier.height(10.dp))
                            val others = ui.detail!!.members.filter { it.userId != ui.detail!!.team.ownerId }
                            if (others.isEmpty()) {
                                Text(
                                    "Invite at least one more player to transfer ownership.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HmxColor.InkTertiary,
                                )
                            } else {
                                others.forEach { member ->
                                    val name = ui.detail!!.profileFor(member.userId)?.display ?: member.userId.take(8)
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable(role = Role.Button) { transferTarget = member }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = HmxColor.Ink,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text("Make owner", style = MaterialTheme.typography.labelMedium, color = HmxColor.Ink)
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    GlassButton(
                        text = "Delete team",
                        onClick = { confirmDelete = true },
                        variant = GlassButtonVariant.Danger,
                        enabled = !ui.saving,
                    )
                }
            }
        }
    }

    transferTarget?.let { target ->
        ConfirmDialog(
            title = "Transfer ownership?",
            message = "Make ${ui.detail?.profileFor(target.userId)?.display ?: "this player"} the team owner? You will no longer manage the team.",
            confirmLabel = "Transfer",
            onConfirm = {
                viewModel.transfer(target.userId)
                transferTarget = null
            },
            onDismiss = { transferTarget = null },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete team?",
            message = "This permanently disbands the team and releases its roster.",
            confirmLabel = "Delete team",
            onConfirm = { confirmDelete = false; viewModel.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}
