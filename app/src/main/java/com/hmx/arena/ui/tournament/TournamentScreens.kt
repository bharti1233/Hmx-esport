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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.hmx.arena.core.designsystem.GlassChip
import com.hmx.arena.core.designsystem.GlassIconButton
import com.hmx.arena.core.designsystem.GlassSectionHeader
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.designsystem.HmxShapes
import com.hmx.arena.core.designsystem.LocalHmxWindowClass
import com.hmx.arena.core.designsystem.hmxGridColumns
import com.hmx.arena.core.model.LookupOption
import com.hmx.arena.core.model.Registration
import com.hmx.arena.core.model.Tournament
import com.hmx.arena.core.util.Format
import com.hmx.arena.data.remote.ApiException
import com.hmx.arena.data.repository.TournamentFilter
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TournamentsUi(
    val query: String = "",
    val categoryId: String? = null,
    val gameModeId: String? = null,
    val freeOnly: Boolean = false,
    val categories: List<LookupOption> = emptyList(),
    val gameModes: List<LookupOption> = emptyList(),
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

    fun setCategory(categoryId: String?) {
        _ui.update { it.copy(categoryId = categoryId) }
        load()
    }

    fun setGameMode(gameModeId: String?) {
        _ui.update { it.copy(gameModeId = gameModeId) }
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
                coroutineScope {
                    // Lookup failures must not blank the Arena; keep the last
                    // known chips and still show the tournament feed.
                    val categories = async {
                        runCatching { repository.categories() }
                            .getOrDefault(current.categories)
                    }
                    val gameModes = async {
                        runCatching { repository.gameModes() }
                            .getOrDefault(current.gameModes)
                    }
                    val items = async {
                        repository.tournaments(
                            TournamentFilter(
                                query = current.query,
                                categoryId = current.categoryId,
                                gameModeId = current.gameModeId,
                                freeOnly = current.freeOnly,
                            ),
                        )
                    }
                    Triple(categories.await(), gameModes.await(), items.await())
                }
            }
                .onSuccess { (categories, gameModes, list) ->
                    _ui.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            categories = categories,
                            gameModes = gameModes,
                            items = list,
                        )
                    }
                }
                .onFailure { e ->
                    _ui.update { it.copy(loading = false, refreshing = false, error = e.hmxMessage()) }
                }
        }
    }
}

@Composable
fun TournamentsScreen(nav: NavHostController, viewModel: TournamentsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

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
        val columns = hmxGridColumns(LocalHmxWindowClass.current)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 124.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
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
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        GlassChip("All categories", ui.categoryId == null) {
                            viewModel.setCategory(null)
                        }
                    }
                    items(ui.categories, key = { it.id }) { category ->
                        GlassChip(category.name, ui.categoryId == category.id) {
                            viewModel.setCategory(category.id)
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        GlassChip("All modes", ui.gameModeId == null) {
                            viewModel.setGameMode(null)
                        }
                    }
                    items(ui.gameModes, key = { it.id }) { mode ->
                        GlassChip(mode.name, ui.gameModeId == mode.id) {
                            viewModel.setGameMode(mode.id)
                        }
                    }
                    item { GlassChip("Free entry", ui.freeOnly) { viewModel.toggleFree() } }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${ui.items.size} tournaments",
                        style = MaterialTheme.typography.labelMedium,
                        color = HmxColor.InkTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "My matches",
                        style = MaterialTheme.typography.labelMedium,
                        color = HmxColor.Ink,
                        modifier = Modifier
                            .clip(HmxShapes.pill)
                            .clickable { nav.navigate(Routes.MY_MATCHES) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
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
                item(span = { GridItemSpan(maxLineSpan) }) {
                    InlineError(ui.error!!, onRetry = viewModel::load)
                }
            }
            if (ui.loading && ui.items.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { LoadingBlock("Loading tournaments") }
            }
            if (!ui.loading && ui.items.isEmpty() && ui.error == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyBlock(
                        title = "No tournaments match",
                        message = "Try clearing a filter or searching a shorter word.",
                        icon = Icons.Rounded.EmojiEvents,
                        actionLabel = "Clear filters",
                        onAction = {
                            viewModel.setQuery("")
                            viewModel.setCategory(null)
                            viewModel.setGameMode(null)
                            if (ui.freeOnly) viewModel.toggleFree()
                            viewModel.load()
                        },
                    )
                }
            }
            gridItems(ui.items, key = { it.id }) { tournament ->
                TournamentCard(tournament) { nav.navigate(Routes.tournament(tournament.id)) }
            }
        }
    }
}

data class TournamentDetailData(
    val tournament: Tournament,
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
) : ViewModel() {
    private val _ui = MutableStateFlow(TournamentDetailUi())
    val ui: StateFlow<TournamentDetailUi> = _ui.asStateFlow()

    fun load(id: String) {
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.data == null, error = null) }
            runCatching {
                val tournament = repository.tournament(id)
                    ?: throw ApiException("This tournament could not be found.")
                val registration = runCatching {
                    repository.registrations().firstOrNull {
                        it.tournamentId == id &&
                            (it.status == "confirmed" || it.status == "waitlisted")
                    }
                }.getOrNull()
                TournamentDetailData(tournament = tournament, registration = registration)
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
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirmCancel by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(id) { viewModel.load(id) }

    HmxPage(
        title = "Tournament",
        onBack = { nav.popBackStack() },
        actions = {
            GlassIconButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Refresh tournament",
                onClick = { viewModel.load(id) },
            )
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
                    if (!tournament.bannerUrl.isNullOrBlank()) {
                        item {
                            AsyncImage(
                                model = tournament.bannerUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(HmxShapes.card),
                            )
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(
                                Format.tournamentStatusLabel(tournament.status),
                                filled = tournament.status == "registration_open",
                            )
                            Spacer(Modifier.width(6.dp))
                            StatusPill(tournament.modeLabel)
                            Spacer(Modifier.width(6.dp))
                            StatusPill(tournament.categoryLabel)
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!tournament.logoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = tournament.logoUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(58.dp)
                                        .clip(HmxShapes.field)
                                        .background(HmxColor.CanvasTint),
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    tournament.title,
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = HmxColor.Ink,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    listOfNotNull(
                                        tournament.game.replace("_", " ").replaceFirstChar { it.uppercase() },
                                        tournament.categoryLabel.takeIf { it != "—" },
                                        tournament.modeLabel,
                                        tournament.tournamentTypeLabel,
                                        tournament.map?.takeIf { it.isNotBlank() },
                                        tournament.format?.takeIf { it.isNotBlank() },
                                        "v${tournament.version}",
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HmxColor.InkSecondary,
                                )
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DetailStat(
                                "Prize pool",
                                if (tournament.prizePoolPaise > 0) Format.money(tournament.prizePoolPaise) else "—",
                                Modifier.weight(1f),
                            )
                            DetailStat(
                                "Entry",
                                if (tournament.isFree) "Free" else Format.money(tournament.entryFeePaise),
                                Modifier.weight(1f),
                            )
                            if (tournament.perKillPaise > 0) {
                                DetailStat("Per kill", Format.money(tournament.perKillPaise), Modifier.weight(1f))
                            }
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
                                    if (tournament.isFull) {
                                        StatusPill("FULL", filled = true)
                                    } else {
                                        Text(
                                            "${tournament.slotsRemaining} left",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HmxColor.InkTertiary,
                                        )
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
                            }
                        }
                    }
                    item {
                        GlassSurface(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(HmxDimens.cardPadding)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "Match date and time",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HmxColor.InkTertiary,
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            Format.dateTime(tournament.startsAt),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = HmxColor.Ink,
                                        )
                                    }
                                    TickingCountdown(tournament.startsAt)
                                }
                                if (tournament.registrationClosesAt != null) {
                                    Spacer(Modifier.height(8.dp))
                                    KeyValueRow("Registration closes", Format.dateTime(tournament.registrationClosesAt))
                                }
                            }
                        }
                    }
                    item { GlassSectionHeader("Tournament hub") }
                    item {
                        ActionLine("Match schedule", Icons.Rounded.CalendarMonth) {
                            nav.navigate(Routes.tournamentSchedule(tournament.id))
                        }
                    }
                    item {
                        ActionLine("Participants", Icons.Rounded.SportsEsports) {
                            nav.navigate(Routes.tournamentParticipants(tournament.id))
                        }
                    }
                    item {
                        ActionLine("Rules", Icons.Rounded.List) {
                            nav.navigate(Routes.tournamentRules(tournament.id))
                        }
                    }
                    item {
                        ActionLine("Leaderboard", Icons.Rounded.Leaderboard) {
                            nav.navigate(Routes.tournamentLeaderboard(tournament.id))
                        }
                    }
                    if (ui.message != null) item { InlineSuccess(ui.message!!) }
                    if (ui.actionError != null) item { InlineError(ui.actionError!!) }
                    val myRegistration = data.registration
                    if (myRegistration != null) {
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
                                                tint = HmxColor.OnInk,
                                                modifier = Modifier.size(17.dp),
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                if (myRegistration.status == "waitlisted") "You are on the waitlist" else "You are registered",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = HmxColor.Ink,
                                            )
                                            Text(
                                                myRegistration.slotNo?.let { "Slot #$it" }
                                                    ?: myRegistration.status.replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = HmxColor.InkSecondary,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    GlassButton(
                                        text = if (ui.busy) "Cancelling…" else "Cancel registration",
                                        onClick = { confirmCancel = myRegistration.id },
                                        variant = GlassButtonVariant.Secondary,
                                        enabled = !ui.busy,
                                    )
                                }
                            }
                        }
                    } else if (tournament.status == "registration_open") {
                        item {
                            GlassButton(
                                text = if (tournament.isFull) "Join the waitlist" else "Register now",
                                onClick = { nav.navigate(Routes.tournamentRegister(tournament.id)) },
                                variant = GlassButtonVariant.Ink,
                            )
                        }
                        item {
                            InlineNotice(
                                "Registration is confirmed by the server. Eligibility and team size are checked automatically.",
                            )
                        }
                    } else {
                        item {
                            InlineNotice(
                                when (tournament.status) {
                                    "live" -> "This tournament is live. Room details are in your match list."
                                    "completed" -> "This tournament has ended."
                                    "cancelled" -> "This tournament was cancelled."
                                    else -> "Registration is not open for this tournament."
                                },
                            )
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
