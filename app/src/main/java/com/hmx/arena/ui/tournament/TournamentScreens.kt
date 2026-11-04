package com.hmx.arena.ui.tournament

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
import com.hmx.arena.core.designsystem.GlassChip
import com.hmx.arena.core.designsystem.GlassIconButton
import com.hmx.arena.core.designsystem.GlassSectionHeader
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.designsystem.HmxShapes
import com.hmx.arena.core.model.Match
import com.hmx.arena.core.model.Participant
import com.hmx.arena.core.model.Registration
import com.hmx.arena.core.model.Tournament
import com.hmx.arena.core.model.TournamentRule
import com.hmx.arena.core.util.Format
import com.hmx.arena.data.remote.ApiException
import com.hmx.arena.data.repository.TournamentFilter
import com.hmx.arena.data.repository.PlayerRepository
import com.hmx.arena.data.repository.TournamentRepository
import com.hmx.arena.ui.common.ConfirmDialog
import com.hmx.arena.ui.common.EmptyBlock
import com.hmx.arena.ui.common.GlassAvatar
import com.hmx.arena.ui.common.GlassTextField
import com.hmx.arena.ui.common.GlassActionRow
import com.hmx.arena.ui.common.HmxPage
import com.hmx.arena.ui.common.InlineError
import com.hmx.arena.ui.common.InlineNotice
import com.hmx.arena.ui.common.InlineSuccess
import com.hmx.arena.ui.common.KeyValueRow
import com.hmx.arena.ui.common.LoadingBlock
import com.hmx.arena.ui.common.StatusPill
import com.hmx.arena.ui.common.TickingCountdown
import com.hmx.arena.ui.common.TournamentCard
import com.hmx.arena.ui.common.UiState
import com.hmx.arena.ui.common.hmxMessage
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

data class TournamentsUi(
    val query: String = "",
    val mode: String? = null,
    val freeOnly: Boolean = false,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val items: List<Tournament> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class TournamentsViewModel @Inject constructor(
    private val repository: TournamentRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(TournamentsUi())
    val ui: StateFlow<TournamentsUi> = _ui.asStateFlow()

    init { load() }

    fun setQuery(value: String) = _ui.update { it.copy(query = value) }

    fun setMode(mode: String?) {
        _ui.update { it.copy(mode = mode) }
        load()
    }

    fun toggleFree() {
        _ui.update { it.copy(freeOnly = !it.freeOnly) }
        load()
    }

    fun load() {
        val current = _ui.value
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.items.isEmpty(), refreshing = true, error = null) }
            runCatching {
                repository.tournaments(
                    TournamentFilter(
                        query = current.query,
                        mode = current.mode,
                        freeOnly = current.freeOnly,
                    ),
                )
            }
                .onSuccess { list ->
                    _ui.update { it.copy(loading = false, refreshing = false, items = list) }
                }
                .onFailure { e ->
                    _ui.update { it.copy(loading = false, refreshing = false, error = e.hmxMessage()) }
                }
        }
    }
}

@Composable
fun TournamentsScreen(nav: NavHostController, viewModel: TournamentsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()

    HmxPage(
        title = "Arena",
        actions = {
            Text(
                "Refresh",
                style = MaterialTheme.typography.labelMedium,
                color = HmxColor.Ink,
                modifier = Modifier.clickable { viewModel.load() }.padding(10.dp),
            )
        },
        bottomBar = { HmxGlassBottomBar(Routes.TOURNAMENTS) { nav.openTab(it) } },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 124.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GlassTextField(
                    value = ui.query,
                    onValueChange = viewModel::setQuery,
                    label = "Search tournaments",
                    placeholder = "Title or keyword",
                    leadingIcon = Icons.Rounded.Search,
                    imeAction = ImeAction.Search,
                    onImeAction = viewModel::load,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { GlassChip("All modes", ui.mode == null) { viewModel.setMode(null) } }
                    item { GlassChip("Solo", ui.mode == "solo") { viewModel.setMode("solo") } }
                    item { GlassChip("Duo", ui.mode == "duo") { viewModel.setMode("duo") } }
                    item { GlassChip("Squad", ui.mode == "squad") { viewModel.setMode("squad") } }
                    item { GlassChip("Clash Squad", ui.mode == "clash_squad") { viewModel.setMode("clash_squad") } }
                    item { GlassChip("Free entry", ui.freeOnly) { viewModel.toggleFree() } }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${ui.items.size} tournaments",
                        style = MaterialTheme.typography.labelMedium,
                        color = HmxColor.InkTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "My registrations",
                        style = MaterialTheme.typography.labelMedium,
                        color = HmxColor.Ink,
                        modifier = Modifier
                            .clip(HmxShapes.pill)
                            .clickable { nav.navigate(Routes.MY_REGISTRATIONS) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            if (ui.error != null && ui.items.isEmpty()) {
                item { InlineError(ui.error!!, onRetry = viewModel::load) }
            }
            if (ui.loading && ui.items.isEmpty()) {
                item { LoadingBlock("Loading tournaments") }
            }
            if (!ui.loading && ui.items.isEmpty() && ui.error == null) {
                item {
                    EmptyBlock(
                        title = "No tournaments match",
                        message = "Try clearing a filter or searching a shorter word.",
                        icon = Icons.Rounded.EmojiEvents,
                        actionLabel = "Clear filters",
                        onAction = {
                            viewModel.setQuery("")
                            viewModel.setMode(null)
                            if (ui.freeOnly) viewModel.toggleFree()
                            viewModel.load()
                        },
                    )
                }
            }
            items(ui.items, key = { it.id }) { tournament ->
                TournamentCard(tournament) { nav.navigate(Routes.tournament(tournament.id)) }
            }
        }
    }
}

data class TournamentDetailData(
    val tournament: Tournament,
    val rules: List<TournamentRule>,
    val participants: List<Participant>,
    val matches: List<Match>,
    val registration: Registration?,
)

data class TournamentDetailUi(
    val loading: Boolean = true,
    val data: TournamentDetailData? = null,
    val error: String? = null,
    val busy: Boolean = false,
    val actionError: String? = null,
    val message: String? = null,
)

@HiltViewModel
class TournamentDetailViewModel @Inject constructor(
    private val repository: TournamentRepository,
    private val player: PlayerRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(TournamentDetailUi())
    val ui: StateFlow<TournamentDetailUi> = _ui.asStateFlow()

    fun load(id: String) {
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.data == null, error = null) }
            runCatching {
                val tournament = repository.tournament(id)
                    ?: throw ApiException("This tournament could not be found.")
                TournamentDetailData(
                    tournament = tournament,
                    rules = runCatching { repository.rules(id) }.getOrDefault(emptyList()),
                    participants = runCatching { player.participants(id) }.getOrDefault(emptyList()),
                    matches = runCatching { repository.tournamentMatches(id) }.getOrDefault(emptyList()),
                    registration = runCatching { repository.registrations() }
                        .getOrDefault(emptyList())
                        .firstOrNull { it.tournamentId == id },
                )
            }
                .onSuccess { data -> _ui.update { it.copy(loading = false, data = data) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    fun cancel(registrationId: String, tournamentId: String) {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, actionError = null, message = null) }
            runCatching { repository.cancelRegistration(registrationId) }
                .onSuccess {
                    _ui.update { it.copy(busy = false, message = "Registration cancelled.") }
                    load(tournamentId)
                }
                .onFailure { e -> _ui.update { it.copy(busy = false, actionError = e.hmxMessage()) } }
        }
    }

    fun clearMessage() = _ui.update { it.copy(message = null, actionError = null) }
}

@Composable
fun TournamentDetailScreen(
    nav: NavHostController,
    id: String,
    viewModel: TournamentDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    var confirmCancel by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(id) { viewModel.load(id) }

    HmxPage(
        title = "Tournament",
        onBack = { nav.popBackStack() },
        actions = {
            val data = ui.data
            if (data != null) {
                GlassIconButton(
                    icon = Icons.Rounded.Share,
                    contentDescription = "Share tournament",
                    onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Join ${data.tournament.title} on HMX Arena (${data.tournament.slug}). Open the app to register.",
                            )
                        }
                        context.startActivity(Intent.createChooser(share, "Share tournament"))
                    },
                )
            }
        },
    ) {
        when {
            ui.loading && ui.data == null -> LoadingBlock("Loading tournament")
            ui.error != null && ui.data == null -> InlineError(ui.error!!, onRetry = { viewModel.load(id) })
            ui.data != null -> {
                val data = ui.data!!
                val tournament = data.tournament
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(Format.tournamentStatusLabel(tournament.status))
                            Spacer(Modifier.width(6.dp))
                            StatusPill(tournament.modeLabel)
                            Spacer(Modifier.width(6.dp))
                            StatusPill(if (tournament.isFree) "Free entry" else Format.money(tournament.entryFeePaise))
                        }
                    }
                    item {
                        Text(tournament.title, style = MaterialTheme.typography.headlineLarge, color = HmxColor.Ink)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            listOfNotNull(
                                tournament.game.replace("_", " ").replaceFirstChar { it.uppercase() },
                                tournament.map,
                                tournament.region,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = HmxColor.InkSecondary,
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DetailStat("Prize pool", if (tournament.prizePoolPaise > 0) Format.money(tournament.prizePoolPaise) else "—", Modifier.weight(1f))
                            DetailStat("Entry", if (tournament.isFree) "Free" else Format.money(tournament.entryFeePaise), Modifier.weight(1f))
                            DetailStat("Format", "${tournament.minTeamSize}-${tournament.maxTeamSize}", Modifier.weight(1f))
                        }
                    }
                    item {
                        GlassSurface(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(HmxDimens.cardPadding)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${tournament.slotsFilled} of ${tournament.maxSlots} slots filled",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = HmxColor.Ink,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (tournament.slotsRemaining > 0) {
                                        Text(
                                            "${tournament.slotsRemaining} left",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HmxColor.InkTertiary,
                                        )
                                    } else {
                                        StatusPill("Waitlist", filled = true)
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = {
                                        if (tournament.maxSlots > 0) {
                                            (tournament.slotsFilled.toFloat() / tournament.maxSlots).coerceIn(0f, 1f)
                                        } else 0f
                                    },
                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                                    color = HmxColor.Ink,
                                    trackColor = HmxColor.CanvasTint,
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Starts in", style = MaterialTheme.typography.bodySmall, color = HmxColor.InkSecondary)
                                    Spacer(Modifier.weight(1f))
                                    TickingCountdown(tournament.startsAt)
                                }
                            }
                        }
                    }
                    if (ui.message != null) {
                        item { InlineSuccess(ui.message!!) }
                    }
                    if (ui.actionError != null) {
                        item { InlineError(ui.actionError!!) }
                    }
                    if (data.registration != null) {
                        item {
                            GlassSurface(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(HmxDimens.cardPadding)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(34.dp).clip(CircleShape).background(HmxColor.Ink),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Rounded.EmojiEvents,
                                                contentDescription = null,
                                                tint = HmxColor.White,
                                                modifier = Modifier.size(17.dp),
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                if (data.registration.status == "waitlisted") "You are on the waitlist" else "You are registered",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = HmxColor.Ink,
                                            )
                                            Text(
                                                data.registration.slotNo?.let { "Slot #$it" } ?: data.registration.status.replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = HmxColor.InkSecondary,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    GlassButton(
                                        text = "Cancel registration",
                                        onClick = { confirmCancel = data.registration.id },
                                        variant = GlassButtonVariant.Secondary,
                                        enabled = !ui.busy,
                                    )
                                }
                            }
                        }
                    } else if (tournament.status == "registration_open") {
                        item {
                            GlassButton(
                                text = if (tournament.slotsRemaining > 0) "Register now" else "Join the waitlist",
                                onClick = { nav.navigate(Routes.tournamentRegister(tournament.id)) },
                                variant = GlassButtonVariant.Ink,
                            )
                        }
                        item {
                            InlineNotice(
                                "Registration is atomic: your slot is confirmed by the server. Eligibility and team size are checked automatically.",
                            )
                        }
                    } else {
                        item {
                            InlineNotice(
                                when (tournament.status) {
                                    "live" -> "This tournament is live. Room details are in your match list."
                                    "completed" -> "This tournament has ended. Final standings are on the leaderboard."
                                    "cancelled" -> "This tournament was cancelled."
                                    else -> "Registration is not open for this tournament."
                                },
                            )
                        }
                    }
                    item {
                        GlassSurface(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                ActionLine("Tournament rules", Icons.Rounded.List) {
                                    nav.navigate(Routes.tournamentRules(tournament.id))
                                }
                                ActionLine("Participants (${data.participants.size})", Icons.Rounded.SportsEsports) {
                                    nav.navigate(Routes.tournamentParticipants(tournament.id))
                                }
                                ActionLine("Match schedule (${data.matches.size})", Icons.Rounded.EmojiEvents) {
                                    nav.navigate(Routes.tournamentSchedule(tournament.id))
                                }
                                ActionLine("Tournament leaderboard", Icons.Rounded.Leaderboard) {
                                    nav.navigate(Routes.tournamentLeaderboard(tournament.id))
                                }
                            }
                        }
                    }
                    if (tournament.description.isNotBlank()) {
                        item {
                            GlassSectionHeader("About")
                            GlassSurface(Modifier.fillMaxWidth()) {
                                Text(
                                    tournament.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = HmxColor.InkSecondary,
                                    modifier = Modifier.padding(HmxDimens.cardPadding),
                                )
                            }
                        }
                    }
                    item { GlassSectionHeader("Schedule") }
                    item {
                        GlassSurface(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(HmxDimens.cardPadding)) {
                                KeyValueRow("Registration opens", Format.dateTime(tournament.registrationOpensAt))
                                KeyValueRow("Registration closes", Format.dateTime(tournament.registrationClosesAt))
                                KeyValueRow("Starts", Format.dateTime(tournament.startsAt))
                                if (tournament.endsAt != null) {
                                    KeyValueRow("Ends", Format.dateTime(tournament.endsAt))
                                }
                            }
                        }
                    }
                    if (data.rules.isNotEmpty()) {
                        item {
                            GlassSectionHeader(
                                "Rules",
                                action = "All ${data.rules.size}",
                                onAction = { nav.navigate(Routes.tournamentRules(tournament.id)) },
                            )
                        }
                        items(data.rules.take(3), key = { it.id }) { rule ->
                            GlassSurface(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(
                                        "${rule.position}. ${rule.title}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = HmxColor.Ink,
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        rule.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HmxColor.InkSecondary,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    if (data.participants.isNotEmpty()) {
                        item {
                            GlassSectionHeader(
                                "Registered players",
                                action = "See all",
                                onAction = { nav.navigate(Routes.tournamentParticipants(tournament.id)) },
                            )
                        }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(data.participants.take(10), key = { it.registrationId }) { participant ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                                        GlassAvatar(participant.avatarUrl, participant.ign, 46.dp)
                                        Spacer(Modifier.height(5.dp))
                                        Text(
                                            participant.ign,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HmxColor.InkSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val registration = ui.data?.registration
    if (confirmCancel != null && registration != null) {
        ConfirmDialog(
            title = "Cancel registration?",
            message = "Your slot will be released. If there is a waitlist, the next player is promoted automatically.",
            confirmLabel = if (ui.busy) "Cancelling…" else "Cancel registration",
            onConfirm = {
                if (!ui.busy) {
                    viewModel.cancel(confirmCancel!!, id)
                    confirmCancel = null
                }
            },
            onDismiss = { confirmCancel = null },
        )
    }
}

@Composable
private fun DetailStat(label: String, value: String, modifier: Modifier = Modifier) {
    GlassSurface(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = HmxColor.Ink)
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = HmxColor.InkTertiary)
        }
    }
}

@Composable
private fun ActionLine(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    GlassActionRow(
        title = title,
        icon = icon,
        onClick = onClick,
    )
}
