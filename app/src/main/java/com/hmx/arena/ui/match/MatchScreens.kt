package com.hmx.arena.ui.match

import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SportsEsports
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.hmx.arena.core.designsystem.GlassButton
import com.hmx.arena.core.designsystem.GlassButtonVariant
import com.hmx.arena.core.designsystem.GlassSectionHeader
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.model.Dispute
import com.hmx.arena.core.model.Match
import com.hmx.arena.core.model.MatchCredentials
import com.hmx.arena.core.model.MatchResult
import com.hmx.arena.core.model.Registration
import com.hmx.arena.core.model.Tournament
import com.hmx.arena.core.util.Format
import com.hmx.arena.data.remote.ApiException
import com.hmx.arena.data.remote.SessionManager
import com.hmx.arena.data.repository.MyMatch
import com.hmx.arena.data.repository.PlayerRepository
import com.hmx.arena.data.repository.TournamentRepository
import com.hmx.arena.ui.common.CopyRow
import com.hmx.arena.ui.common.EmptyBlock
import com.hmx.arena.ui.common.GlassTextField
import com.hmx.arena.ui.common.GlassActionRow
import com.hmx.arena.ui.common.HmxPage
import com.hmx.arena.ui.common.InlineError
import com.hmx.arena.ui.common.InlineNotice
import com.hmx.arena.ui.common.InlineSuccess
import com.hmx.arena.ui.common.KeyValueRow
import com.hmx.arena.ui.common.LoadingBlock
import com.hmx.arena.ui.common.MatchCard
import com.hmx.arena.ui.common.PickedImage
import com.hmx.arena.ui.common.StatusPill
import com.hmx.arena.ui.common.TickingCountdown
import com.hmx.arena.ui.common.copyToClipboard
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

data class MatchesUi(
    val loading: Boolean = true,
    val items: List<MyMatch> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class MatchesViewModel @Inject constructor(
    private val repository: TournamentRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(MatchesUi())
    val ui: StateFlow<MatchesUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.items.isEmpty(), error = null) }
            runCatching { repository.myMatches() }
                .onSuccess { list -> _ui.update { it.copy(loading = false, items = list) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }
}

@Composable
fun MatchesScreen(nav: NavHostController, viewModel: MatchesViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    HmxPage(
        title = "Matches",
        actions = {
            Text(
                "Refresh",
                style = MaterialTheme.typography.labelMedium,
                color = HmxColor.Ink,
                modifier = Modifier.clickable { viewModel.load() }.padding(10.dp),
            )
        },
        bottomBar = { HmxGlassBottomBar(Routes.MATCHES) { nav.openTab(it) } },
    ) {
        when {
            ui.loading && ui.items.isEmpty() -> LoadingBlock("Loading your matches")
            ui.error != null && ui.items.isEmpty() -> InlineError(ui.error!!, onRetry = viewModel::load)
            ui.items.isEmpty() -> EmptyBlock(
                title = "No matches scheduled",
                message = "Register for a tournament to see rooms, times and result windows here.",
                icon = Icons.Rounded.SportsEsports,
                actionLabel = "Browse tournaments",
                onAction = { nav.openTab(Routes.TOURNAMENTS) },
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 124.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${ui.items.size} scheduled",
                            style = MaterialTheme.typography.labelMedium,
                            color = HmxColor.InkTertiary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "My registrations",
                            style = MaterialTheme.typography.labelMedium,
                            color = HmxColor.Ink,
                            modifier = Modifier
                                .clickable { nav.navigate(Routes.MY_REGISTRATIONS) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
                items(ui.items, key = { it.match.id }) { entry ->
                    MatchCard(
                        match = entry.match,
                        tournamentTitle = entry.tournament.title,
                        onClick = { nav.navigate(Routes.match(entry.match.id)) },
                    )
                }
            }
        }
    }
}

data class MatchDetailData(
    val match: Match,
    val tournament: Tournament?,
    val registration: Registration?,
    val credentials: MatchCredentials?,
    val results: List<MatchResult>,
    val myResult: MatchResult?,
)

data class MatchDetailUi(
    val loading: Boolean = true,
    val data: MatchDetailData? = null,
    val error: String? = null,
)

@HiltViewModel
class MatchDetailViewModel @Inject constructor(
    private val repository: TournamentRepository,
    private val session: SessionManager,
) : ViewModel() {
    private val _ui = MutableStateFlow(MatchDetailUi())
    val ui: StateFlow<MatchDetailUi> = _ui.asStateFlow()

    fun load(id: String) {
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.data == null, error = null) }
            runCatching {
                val match = repository.match(id)
                    ?: throw ApiException("This match could not be found.")
                val results = runCatching { repository.resultsForMatch(id) }.getOrDefault(emptyList())
                MatchDetailData(
                    match = match,
                    tournament = runCatching { repository.tournament(match.tournamentId) }.getOrNull(),
                    registration = runCatching {
                        repository.registrations().firstOrNull { it.tournamentId == match.tournamentId }
                    }.getOrNull(),
                    credentials = runCatching { repository.credentials(id) }.getOrNull(),
                    results = results,
                    myResult = results.firstOrNull { it.submittedBy == session.snapshotUserId() },
                )
            }
                .onSuccess { data -> _ui.update { it.copy(loading = false, data = data) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }
}

@Composable
fun MatchDetailScreen(nav: NavHostController, id: String, viewModel: MatchDetailViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    LaunchedEffect(id) { viewModel.load(id) }

    HmxPage(title = "Match", onBack = { nav.popBackStack() }) {
        when {
            ui.loading && ui.data == null -> LoadingBlock("Loading match")
            ui.error != null && ui.data == null -> InlineError(ui.error!!, onRetry = { viewModel.load(id) })
            ui.data != null -> {
                val data = ui.data!!
                val match = data.match
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(match.status.replaceFirstChar { it.uppercase() })
                            Spacer(Modifier.width(6.dp))
                            if (match.round > 0) StatusPill("Round ${match.round}")
                            Spacer(Modifier.weight(1f))
                            TickingCountdown(match.scheduledAt)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(match.name, style = MaterialTheme.typography.headlineMedium, color = HmxColor.Ink)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            data.tournament?.title ?: "Tournament",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HmxColor.InkSecondary,
                        )
                    }
                    item {
                        GlassSurface(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(HmxDimens.cardPadding)) {
                                KeyValueRow("Scheduled", Format.dateTime(match.scheduledAt))
                                KeyValueRow("Check-in opens", Format.dateTime(match.checkInOpensAt))
                                KeyValueRow("Map", match.map ?: "TBD")
                                KeyValueRow("Mode", match.mode?.replaceFirstChar { it.uppercase() } ?: "—")
                                KeyValueRow("Room", if (match.status == "live") "Released" else "Locked")
                            }
                        }
                    }
                    item {
                        GlassSectionHeader("Room credentials")
                        if (data.credentials != null) {
                            GlassSurface(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(HmxDimens.cardPadding)) {
                                    Text(
                                        "Your private room details are ready.",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = HmxColor.Ink,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Only registered participants can open them.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HmxColor.InkSecondary,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    GlassButton(
                                        text = "View room credentials",
                                        onClick = { nav.navigate(Routes.matchCredentials(id)) },
                                        variant = GlassButtonVariant.Ink,
                                    )
                                }
                            }
                        } else {
                            GlassSurface(Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(HmxDimens.cardPadding), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.Lock,
                                        contentDescription = null,
                                        tint = HmxColor.InkTertiary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        if (data.registration == null) {
                                            "Room details unlock for registered players only."
                                        } else {
                                            "Room details are not released yet. Check back closer to the match."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HmxColor.InkSecondary,
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassButton(
                                text = "Results",
                                onClick = { nav.navigate(Routes.matchResults(id)) },
                                variant = GlassButtonVariant.Secondary,
                                modifier = Modifier.weight(1f),
                            )
                            GlassButton(
                                text = if (data.myResult == null) "Submit result" else "Result submitted",
                                onClick = { nav.navigate(Routes.matchSubmit(id)) },
                                enabled = data.registration != null &&
                                    (match.status == "live" || match.status == "completed") &&
                                    data.myResult == null,
                                variant = GlassButtonVariant.Secondary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (data.myResult != null) {
                        item {
                            GlassSurface(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(HmxDimens.cardPadding)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "Your submitted result",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = HmxColor.Ink,
                                            modifier = Modifier.weight(1f),
                                        )
                                        StatusPill(data.myResult.status.replaceFirstChar { it.uppercase() })
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Placement #${data.myResult.placement} · ${data.myResult.kills} kills · ${data.myResult.points} points",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HmxColor.InkSecondary,
                                    )
                                }
                            }
                        }
                    }
                    if (data.tournament != null) {
                        item {
                            GlassSurface(Modifier.fillMaxWidth()) {
                                GlassActionRow(
                                    title = "Open tournament",
                                    icon = Icons.Rounded.EmojiEvents,
                                    onClick = { nav.navigate(Routes.tournament(data.tournament.id)) },
                                )
                            }
                        }
                    }
                    item {
                        InlineNotice(
                            "Keep the in-game result screen until staff verify your submission. Evidence is stored privately.",
                        )
                    }
                }
            }
        }
    }
}

data class CredentialsUi(
    val loading: Boolean = true,
    val credentials: MatchCredentials? = null,
    val error: String? = null,
)

@HiltViewModel
class MatchCredentialsViewModel @Inject constructor(
    private val repository: TournamentRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(CredentialsUi())
    val ui: StateFlow<CredentialsUi> = _ui.asStateFlow()

    fun load(id: String) {
        viewModelScope.launch {
            _ui.value = CredentialsUi()
            runCatching { repository.credentials(id) }
                .onSuccess { credentials ->
                    _ui.value = if (credentials == null) {
                        CredentialsUi(false, null, "Room details are not released yet.")
                    } else {
                        CredentialsUi(false, credentials)
                    }
                }
                .onFailure { e -> _ui.value = CredentialsUi(false, null, e.hmxMessage()) }
        }
    }
}

@Composable
fun MatchCredentialsScreen(nav: NavHostController, id: String, viewModel: MatchCredentialsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(id) { viewModel.load(id) }

    HmxPage(title = "Room credentials", onBack = { nav.popBackStack() }) {
        when {
            ui.loading -> LoadingBlock("Opening secure room details")
            ui.error != null -> InlineError(ui.error!!, onRetry = { viewModel.load(id) })
            ui.credentials == null -> EmptyBlock(
                title = "Not available",
                message = "Room details unlock only for registered players after staff release them.",
                icon = Icons.Rounded.Lock,
            )
            else -> Column(Modifier.padding(20.dp)) {
                InlineNotice("Do not share these details. Room access is logged for fair play.")
                Spacer(Modifier.height(14.dp))
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(HmxDimens.cardPadding)) {
                        CopyRow("Room ID", ui.credentials!!.roomId) {
                            copyToClipboard(context, ui.credentials!!.roomId)
                            Toast.makeText(context, "Room ID copied", Toast.LENGTH_SHORT).show()
                        }
                        CopyRow("Password", ui.credentials!!.roomPassword) {
                            copyToClipboard(context, ui.credentials!!.roomPassword)
                            Toast.makeText(context, "Password copied", Toast.LENGTH_SHORT).show()
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Visible until ${Format.dateTime(ui.credentials!!.visibleUntil)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = HmxColor.InkTertiary,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Join the custom room in Free Fire using these details. If they do not work, contact support before the match starts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HmxColor.InkSecondary,
                )
            }
        }
    }
}

data class MatchResultsUi(
    val loading: Boolean = true,
    val results: List<MatchResult> = emptyList(),
    val disputes: Map<String, List<Dispute>> = emptyMap(),
    val error: String? = null,
)

@HiltViewModel
class MatchResultsViewModel @Inject constructor(
    private val repository: TournamentRepository,
    private val player: PlayerRepository,
    private val session: SessionManager,
) : ViewModel() {
    private val _ui = MutableStateFlow(MatchResultsUi())
    val ui: StateFlow<MatchResultsUi> = _ui.asStateFlow()

    fun load(id: String) {
        viewModelScope.launch {
            _ui.value = MatchResultsUi()
            runCatching {
                val results = repository.resultsForMatch(id)
                val disputes = results.associate { result ->
                    result.id to runCatching { player.disputesForResult(result.id) }.getOrDefault(emptyList())
                }
                results to disputes
            }
                .onSuccess { (results, disputes) ->
                    _ui.value = MatchResultsUi(false, results, disputes)
                }
                .onFailure { e -> _ui.value = MatchResultsUi(false, emptyList(), emptyMap(), e.hmxMessage()) }
        }
    }

    fun isMine(result: MatchResult): Boolean = result.submittedBy == session.snapshotUserId()
}

@Composable
fun MatchResultsScreen(nav: NavHostController, id: String, viewModel: MatchResultsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    LaunchedEffect(id) { viewModel.load(id) }

    HmxPage(title = "Results", onBack = { nav.popBackStack() }) {
        when {
            ui.loading -> LoadingBlock("Loading results")
            ui.error != null -> InlineError(ui.error!!, onRetry = { viewModel.load(id) })
            ui.results.isEmpty() -> EmptyBlock(
                title = "No submissions yet",
                message = "Results appear after participants submit screenshot evidence and staff verify them.",
                icon = Icons.Rounded.EmojiEvents,
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    InlineNotice("Results are provisional until verified. Points update the leaderboard after verification.")
                }
                items(ui.results, key = { it.id }) { result ->
                    val mine = viewModel.isMine(result)
                    val disputes = ui.disputes[result.id].orEmpty()
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(HmxDimens.cardPadding)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (mine) "Your result" else "Opponent result",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = HmxColor.Ink,
                                    modifier = Modifier.weight(1f),
                                )
                                StatusPill(result.status.replaceFirstChar { it.uppercase() })
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Placement #${result.placement} · ${result.kills} kills · ${result.points} points",
                                style = MaterialTheme.typography.bodySmall,
                                color = HmxColor.InkSecondary,
                            )
                            if (!result.notes.isNullOrBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    result.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HmxColor.InkTertiary,
                                )
                            }
                            if (disputes.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                disputes.forEach { dispute ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        StatusPill("Dispute ${dispute.status}")
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            dispute.reason,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = HmxColor.InkSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            if (mine && disputes.isEmpty() && result.status != "rejected") {
                                Spacer(Modifier.height(10.dp))
                                GlassButton(
                                    text = "Raise a dispute",
                                    onClick = { nav.navigate(Routes.dispute(result.id)) },
                                    variant = GlassButtonVariant.Secondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class SubmitResultUi(
    val loading: Boolean = true,
    val match: Match? = null,
    val placement: String = "",
    val kills: String = "0",
    val notes: String = "",
    val evidence: PickedImage? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val actionError: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class SubmitResultViewModel @Inject constructor(
    private val repository: TournamentRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(SubmitResultUi())
    val ui: StateFlow<SubmitResultUi> = _ui.asStateFlow()

    fun load(id: String) {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            runCatching { repository.match(id) }
                .onSuccess { match -> _ui.update { it.copy(loading = false, match = match) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    fun setPlacement(value: String) = _ui.update {
        it.copy(placement = value.filter(Char::isDigit).take(3), actionError = null)
    }

    fun setKills(value: String) = _ui.update {
        it.copy(kills = value.filter(Char::isDigit).take(3), actionError = null)
    }

    fun setNotes(value: String) = _ui.update { it.copy(notes = value.take(500)) }

    fun setEvidence(image: PickedImage?) = _ui.update { it.copy(evidence = image, actionError = null) }

    fun submit() {
        val current = _ui.value
        val match = current.match ?: return
        val placement = current.placement.toIntOrNull()
        val kills = current.kills.toIntOrNull() ?: 0
        val evidence = current.evidence
        val message = when {
            placement == null || placement < 1 -> "Enter your placement (1 or higher)."
            kills < 0 -> "Kills cannot be negative."
            evidence == null -> "Attach the in-game result screenshot."
            !evidence.mime.startsWith("image/") -> "Evidence must be an image."
            evidence.bytes.size > 6 * 1024 * 1024 -> "Evidence must be smaller than 6 MB."
            else -> null
        }
        if (message != null) {
            _ui.update { it.copy(actionError = message) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, actionError = null) }
            runCatching {
                repository.submitResult(
                    matchId = match.id,
                    placement = placement!!,
                    kills = kills,
                    evidenceBytes = evidence!!.bytes,
                    evidenceMime = evidence.mime,
                    notes = current.notes.trim().ifBlank { null },
                )
            }
                .onSuccess { _ui.update { it.copy(busy = false, done = true) } }
                .onFailure { e -> _ui.update { it.copy(busy = false, actionError = e.hmxMessage()) } }
        }
    }
}

@Composable
fun SubmitResultScreen(nav: NavHostController, id: String, viewModel: SubmitResultViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    val (picked, pick) = rememberPickedImage()
    LaunchedEffect(id) { viewModel.load(id) }
    LaunchedEffect(picked) { viewModel.setEvidence(picked) }
    LaunchedEffect(ui.done) { if (ui.done) nav.popBackStack() }
    val context = LocalContext.current

    HmxPage(title = "Submit result", onBack = { nav.popBackStack() }) {
        if (ui.loading) {
            LoadingBlock("Loading match")
        } else if (ui.error != null) {
            InlineError(ui.error!!)
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text(
                        ui.match?.name ?: "Match",
                        style = MaterialTheme.typography.headlineMedium,
                        color = HmxColor.Ink,
                    )
                    Text(
                        "Submit one result. Staff verify screenshots before points are awarded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HmxColor.InkSecondary,
                    )
                }
                item {
                    GlassTextField(
                        value = ui.placement,
                        onValueChange = viewModel::setPlacement,
                        label = "Placement",
                        keyboardType = KeyboardType.Number,
                        placeholder = "1",
                    )
                }
                item {
                    GlassTextField(
                        value = ui.kills,
                        onValueChange = viewModel::setKills,
                        label = "Kills",
                        keyboardType = KeyboardType.Number,
                    )
                }
                item {
                    GlassTextField(
                        value = ui.notes,
                        onValueChange = viewModel::setNotes,
                        label = "Notes (optional)",
                        maxLines = 3,
                    )
                }
                item {
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(HmxDimens.cardPadding)) {
                            Text("Evidence", style = MaterialTheme.typography.titleMedium, color = HmxColor.Ink)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Upload the full in-game result screen. Max 6 MB, image only.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HmxColor.InkSecondary,
                            )
                            if (picked != null) {
                                Spacer(Modifier.height(12.dp))
                                AsyncImage(
                                    model = picked.uri,
                                    contentDescription = "Selected evidence",
                                    modifier = Modifier.fillMaxWidth().height(170.dp),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "${picked.bytes.size / 1024} KB · ${picked.mime}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HmxColor.InkTertiary,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            GlassButton(
                                text = if (picked == null) "Choose screenshot" else "Replace screenshot",
                                onClick = pick,
                                variant = GlassButtonVariant.Secondary,
                            )
                        }
                    }
                }
                if (ui.actionError != null) item { InlineError(ui.actionError!!) }
                item {
                    GlassButton(
                        text = "Submit evidence",
                        onClick = viewModel::submit,
                        loading = ui.busy,
                        variant = GlassButtonVariant.Ink,
                    )
                }
                item {
                    InlineNotice("False evidence can disqualify you. Evidence is visible only to participants and staff.")
                }
            }
        }
    }
}

data class DisputeUi(
    val reason: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class DisputeViewModel @Inject constructor(
    private val repository: TournamentRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(DisputeUi())
    val ui: StateFlow<DisputeUi> = _ui.asStateFlow()

    fun setReason(value: String) = _ui.update { it.copy(reason = value.take(1000), error = null) }

    fun submit(resultId: String) {
        val reason = _ui.value.reason.trim()
        if (reason.length < 10) {
            _ui.update { it.copy(error = "Describe the issue in at least 10 characters.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            runCatching { repository.raiseDispute(resultId, reason) }
                .onSuccess { _ui.update { it.copy(busy = false, done = true) } }
                .onFailure { e -> _ui.update { it.copy(busy = false, error = e.hmxMessage()) } }
        }
    }
}

@Composable
fun DisputeScreen(nav: NavHostController, resultId: String, viewModel: DisputeViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    var showSuccess by remember { mutableStateOf(false) }
    LaunchedEffect(ui.done) { if (ui.done) showSuccess = true }

    HmxPage(title = "Dispute result", onBack = { nav.popBackStack() }) {
        if (showSuccess) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = HmxColor.Ink, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(16.dp))
                Text("Dispute submitted", style = MaterialTheme.typography.headlineMedium, color = HmxColor.Ink)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tournament staff will review the result and evidence. You can track the status on the results screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HmxColor.InkSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                GlassButton(
                    text = "Back to results",
                    onClick = { nav.popBackStack() },
                    variant = GlassButtonVariant.Ink,
                )
            }
        } else {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Text("What is wrong with this result?", style = MaterialTheme.typography.titleLarge, color = HmxColor.Ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Explain the placement, kills or evidence issue. Be specific; bad-faith disputes can be penalised.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HmxColor.InkSecondary,
                )
                Spacer(Modifier.height(16.dp))
                GlassTextField(
                    value = ui.reason,
                    onValueChange = viewModel::setReason,
                    label = "Reason",
                    maxLines = 6,
                    error = ui.error,
                )
                Spacer(Modifier.height(16.dp))
                GlassButton(
                    text = "Submit dispute",
                    onClick = { viewModel.submit(resultId) },
                    loading = ui.busy,
                    variant = GlassButtonVariant.Ink,
                )
                Spacer(Modifier.height(10.dp))
                InlineNotice("Disputes are reviewed against submitted evidence. The decision recorded by staff is final for the match.")
            }
        }
    }
}
