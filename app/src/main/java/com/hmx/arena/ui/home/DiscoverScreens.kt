package com.hmx.arena.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.hmx.arena.core.designsystem.GlassButton
import com.hmx.arena.core.designsystem.GlassButtonVariant
import com.hmx.arena.core.designsystem.GlassSectionHeader
import com.hmx.arena.core.designsystem.GlassStatCell
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.designsystem.HmxShapes
import com.hmx.arena.core.model.Announcement
import com.hmx.arena.core.model.NotificationItem
import com.hmx.arena.core.model.Profile
import com.hmx.arena.core.model.Tournament
import com.hmx.arena.core.util.Format
import com.hmx.arena.data.repository.PlayerRepository
import com.hmx.arena.data.repository.TournamentRepository
import com.hmx.arena.ui.common.EmptyBlock
import com.hmx.arena.ui.common.GlassAvatar
import com.hmx.arena.ui.common.GlassTextField
import com.hmx.arena.ui.common.HmxGlassNotificationCard
import com.hmx.arena.ui.common.HmxPage
import com.hmx.arena.ui.common.InlineError
import com.hmx.arena.ui.common.InlineSuccess
import com.hmx.arena.ui.common.LoadingBlock
import com.hmx.arena.ui.common.TournamentCard
import com.hmx.arena.ui.common.UiState
import com.hmx.arena.ui.common.hmxMessage
import com.hmx.arena.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUi(
    val loading: Boolean = true,
    val items: List<NotificationItem> = emptyList(),
    val error: String? = null,
    val marking: Boolean = false,
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val player: PlayerRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(NotificationsUi())
    val ui: StateFlow<NotificationsUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching { player.notifications() }
                .onSuccess { list -> _ui.update { it.copy(loading = false, items = list) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    fun markAllRead() {
        if (_ui.value.marking || _ui.value.items.none { it.unread }) return
        viewModelScope.launch {
            _ui.update { it.copy(marking = true, error = null) }
            runCatching { player.markNotificationsRead() }
                .onSuccess { _ui.update { it.copy(items = it.items.map { n -> n.copy(readAt = "now") }) } }
                .onFailure { e -> _ui.update { it.copy(error = e.hmxMessage()) } }
            _ui.update { it.copy(marking = false) }
        }
    }
}

@Composable
fun NotificationsScreen(nav: NavHostController, viewModel: NotificationsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    HmxPage(
        title = "Notifications",
        onBack = { nav.popBackStack() },
        actions = {
            val unread = ui.items.any { it.unread }
            if (unread) {
                Text(
                    if (ui.marking) "Marking…" else "Mark read",
                    style = MaterialTheme.typography.labelMedium,
                    color = HmxColor.Ink,
                    modifier = Modifier
                        .clickable(enabled = !ui.marking) { viewModel.markAllRead() }
                        .padding(10.dp),
                )
            }
        },
    ) {
        when {
            ui.loading && ui.items.isEmpty() -> LoadingBlock("Loading notifications")
            ui.items.isEmpty() && ui.error != null -> InlineError(ui.error!!, onRetry = viewModel::load)
            ui.items.isEmpty() -> EmptyBlock(
                title = "Nothing new",
                message = "Match reminders, result decisions and announcements will land here.",
                icon = Icons.Rounded.Notifications,
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(ui.items, key = { it.id }) { item ->
                    HmxGlassNotificationCard(item = item)
                }
            }
        }
    }
}

data class AnnouncementsUi(
    val loading: Boolean = true,
    val items: List<Announcement> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class AnnouncementsViewModel @Inject constructor(
    private val player: PlayerRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(AnnouncementsUi())
    val ui: StateFlow<AnnouncementsUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { player.announcements() }
                .onSuccess { list -> _ui.value = AnnouncementsUi(false, list) }
                .onFailure { e -> _ui.value = AnnouncementsUi(false, emptyList(), e.hmxMessage()) }
        }
    }
}

@Composable
fun AnnouncementsScreen(nav: NavHostController, viewModel: AnnouncementsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    HmxPage(title = "Announcements", onBack = { nav.popBackStack() }) {
        when {
            ui.loading -> LoadingBlock("Loading announcements")
            ui.items.isEmpty() && ui.error != null -> InlineError(ui.error!!)
            ui.items.isEmpty() -> EmptyBlock(
                title = "No announcements",
                message = "Arena updates will be published here.",
                icon = Icons.Rounded.Campaign,
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(ui.items, key = { it.id }) { item ->
                    GlassSurface(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) {
                            nav.navigate(Routes.announcement(item.id))
                        },
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleLarge, color = HmxColor.Ink)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                item.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = HmxColor.InkSecondary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                Format.relative(item.publishedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = HmxColor.InkTertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}

data class AnnouncementUi(
    val loading: Boolean = true,
    val item: Announcement? = null,
    val error: String? = null,
)

@HiltViewModel
class AnnouncementViewModel @Inject constructor(
    private val player: PlayerRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(AnnouncementUi())
    val ui: StateFlow<AnnouncementUi> = _ui.asStateFlow()
    private var loadedId: String? = null

    fun load(id: String) {
        if (loadedId == id) return
        loadedId = id
        viewModelScope.launch {
            _ui.value = AnnouncementUi()
            runCatching { player.announcement(id) }
                .onSuccess { item -> _ui.value = AnnouncementUi(false, item) }
                .onFailure { e -> _ui.value = AnnouncementUi(false, null, e.hmxMessage()) }
        }
    }
}

@Composable
fun AnnouncementScreen(nav: NavHostController, id: String, viewModel: AnnouncementViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    LaunchedEffect(id) { viewModel.load(id) }
    HmxPage(title = "Announcement", onBack = { nav.popBackStack() }) {
        when {
            ui.loading -> LoadingBlock("Loading")
            ui.error != null -> InlineError(ui.error!!)
            ui.item == null -> EmptyBlock("Not found", "This announcement is no longer published.", icon = Icons.Rounded.Campaign)
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            ) {
                item {
                    Text(ui.item!!.title, style = MaterialTheme.typography.headlineMedium, color = HmxColor.Ink)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        Format.dateTime(ui.item!!.publishedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = HmxColor.InkTertiary,
                    )
                    Spacer(Modifier.height(20.dp))
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Text(
                            ui.item!!.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = HmxColor.InkSecondary,
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                }
            }
        }
    }
}

data class SearchUi(
    val query: String = "",
    val loading: Boolean = false,
    val searched: Boolean = false,
    val tournaments: List<Tournament> = emptyList(),
    val players: List<Profile> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val tournaments: TournamentRepository,
    private val player: PlayerRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(SearchUi())
    val ui: StateFlow<SearchUi> = _ui.asStateFlow()

    fun setQuery(value: String) = _ui.update { it.copy(query = value, error = null) }

    fun search() {
        val query = _ui.value.query.trim()
        if (query.isBlank()) {
            _ui.update { it.copy(error = "Type a tournament, player or username.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, searched = true) }
            val tournamentResults = runCatching {
                tournaments.tournaments(com.hmx.arena.data.repository.TournamentFilter(query = query))
            }.getOrDefault(emptyList())
            val playerResults = runCatching { player.searchPlayers(query) }.getOrDefault(emptyList())
            _ui.update {
                it.copy(
                    loading = false,
                    tournaments = tournamentResults,
                    players = playerResults,
                )
            }
        }
    }
}

@Composable
fun SearchScreen(nav: NavHostController, viewModel: SearchViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    HmxPage(title = "Search", onBack = { nav.popBackStack() }) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GlassTextField(
                    value = ui.query,
                    onValueChange = viewModel::setQuery,
                    label = "Search",
                    placeholder = "Tournament, player or username",
                    leadingIcon = Icons.Rounded.Search,
                    imeAction = ImeAction.Search,
                    onImeAction = viewModel::search,
                )
            }
            item {
                GlassButton(
                    text = if (ui.loading) "Searching…" else "Search",
                    onClick = viewModel::search,
                    loading = ui.loading,
                    variant = GlassButtonVariant.Ink,
                )
            }
            if (ui.error != null) item { InlineError(ui.error!!) }
            if (ui.searched && !ui.loading && ui.error == null) {
                if (ui.tournaments.isNotEmpty()) {
                    item { GlassSectionHeader("Tournaments") }
                    items(ui.tournaments, key = { it.id }) { tournament ->
                        TournamentCard(tournament) { nav.navigate(Routes.tournament(tournament.id)) }
                    }
                }
                if (ui.players.isNotEmpty()) {
                    item { GlassSectionHeader("Players") }
                    items(ui.players, key = { it.id }) { profile ->
                        GlassSurface(
                            Modifier.fillMaxWidth().clickable(role = Role.Button) {
                                nav.navigate(Routes.player(profile.id))
                            },
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                GlassAvatar(profile.avatarUrl, profile.display, 42.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(profile.display, style = MaterialTheme.typography.titleMedium, color = HmxColor.Ink)
                                    Text(
                                        "@${profile.username} · ${Format.points(profile.points.toLong())} pts",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HmxColor.InkSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
                if (ui.tournaments.isEmpty() && ui.players.isEmpty()) {
                    item {
                        EmptyBlock(
                            title = "No results",
                            message = "Try a shorter search term or check the spelling.",
                            icon = Icons.Rounded.Search,
                        )
                    }
                }
            }
        }
    }
}

data class PlayerProfileUi(
    val loading: Boolean = true,
    val profile: Profile? = null,
    val error: String? = null,
    val reporting: Boolean = false,
    val reported: Boolean = false,
    val reportError: String? = null,
)

@HiltViewModel
class PlayerProfileViewModel @Inject constructor(
    private val player: PlayerRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(PlayerProfileUi())
    val ui: StateFlow<PlayerProfileUi> = _ui.asStateFlow()
    private var loadedId: String? = null

    fun load(id: String) {
        if (loadedId == id) return
        loadedId = id
        viewModelScope.launch {
            _ui.value = PlayerProfileUi()
            runCatching { player.publicProfile(id) }
                .onSuccess { profile -> _ui.value = PlayerProfileUi(false, profile) }
                .onFailure { e -> _ui.value = PlayerProfileUi(false, null, e.hmxMessage()) }
        }
    }

    fun report(reason: String) {
        val id = loadedId ?: return
        if (reason.trim().length < 10) {
            _ui.update { it.copy(reportError = "Describe the issue in at least 10 characters.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(reporting = true, reportError = null) }
            runCatching {
                player.reportProblem("player", reason.trim(), targetType = "user", targetId = id)
            }
                .onSuccess { _ui.update { it.copy(reporting = false, reported = true) } }
                .onFailure { e -> _ui.update { it.copy(reporting = false, reportError = e.hmxMessage()) } }
        }
    }

    fun clearReportError() = _ui.update { it.copy(reportError = null) }
}

@Composable
fun PlayerProfileScreen(nav: NavHostController, id: String, viewModel: PlayerProfileViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    var showReport by remember { mutableStateOf(false) }
    LaunchedEffect(id) { viewModel.load(id) }

    HmxPage(title = "Player", onBack = { nav.popBackStack() }) {
        when {
            ui.loading -> LoadingBlock("Loading player")
            ui.error != null -> InlineError(ui.error!!)
            ui.profile == null -> EmptyBlock("Player not found", "This player may have left the arena.")
            else -> {
                val profile = ui.profile!!
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        GlassSurface(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.fillMaxWidth().padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                GlassAvatar(profile.avatarUrl, profile.display, 82.dp)
                                Spacer(Modifier.height(12.dp))
                                Text(profile.display, style = MaterialTheme.typography.headlineMedium, color = HmxColor.Ink)
                                Text(
                                    "@${profile.username} · ${profile.region}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HmxColor.InkSecondary,
                                )
                                if (!profile.bio.isNullOrBlank()) {
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        profile.bio,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HmxColor.InkSecondary,
                                    )
                                }
                                Spacer(Modifier.height(18.dp))
                                Row(Modifier.fillMaxWidth()) {
                                    GlassStatCell("Points", Format.points(profile.points.toLong()), Modifier.weight(1f))
                                    GlassStatCell("Played", profile.matchesPlayed.toString(), Modifier.weight(1f))
                                    GlassStatCell("Wins", profile.wins.toString(), Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    if (ui.reported) {
                        item { InlineSuccess("Report submitted. Our team will review it.") }
                    }
                    item {
                        GlassButton(
                            text = "Report player",
                            onClick = { showReport = true },
                            variant = GlassButtonVariant.Secondary,
                        )
                    }
                }
            }
        }
    }

    if (showReport) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReport = false },
            containerColor = HmxColor.White,
            shape = HmxShapes.dialog,
            title = { Text("Report player", style = MaterialTheme.typography.titleLarge, color = HmxColor.Ink) },
            text = {
                Column {
                    Text(
                        "Describe cheating, harassment or impersonation. False reports can restrict your account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HmxColor.InkSecondary,
                    )
                    Spacer(Modifier.height(14.dp))
                    GlassTextField(
                        value = reason,
                        onValueChange = { reason = it; viewModel.clearReportError() },
                        label = "What happened?",
                        maxLines = 4,
                        error = ui.reportError,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !ui.reporting,
                    onClick = {
                        viewModel.report(reason)
                        if (reason.trim().length >= 10) showReport = false
                    },
                ) { Text(if (ui.reporting) "Sending…" else "Submit", color = HmxColor.Ink) }
            },
            dismissButton = {
                TextButton(onClick = { showReport = false }) { Text("Cancel", color = HmxColor.InkSecondary) }
            },
        )
    }
}
