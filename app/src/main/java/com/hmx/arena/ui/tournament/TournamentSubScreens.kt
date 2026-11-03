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
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
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
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.model.Match
import com.hmx.arena.core.model.Participant
import com.hmx.arena.core.model.Tournament
import com.hmx.arena.core.model.TournamentRule
import com.hmx.arena.core.util.Format
import com.hmx.arena.data.repository.PlayerRepository
import com.hmx.arena.data.repository.TournamentRepository
import com.hmx.arena.ui.common.EmptyBlock
import com.hmx.arena.ui.common.GlassAvatar
import com.hmx.arena.ui.common.HmxPage
import com.hmx.arena.ui.common.InlineError
import com.hmx.arena.ui.common.LoadingBlock
import com.hmx.arena.ui.common.MatchCard
import com.hmx.arena.ui.common.StatusPill
import com.hmx.arena.ui.common.hmxMessage
import com.hmx.arena.ui.leaderboard.LeaderboardList as RankList
import com.hmx.arena.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RulesUi(
    val loading: Boolean = true,
    val tournament: Tournament? = null,
    val rules: List<TournamentRule> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val repository: TournamentRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(RulesUi())
    val ui: StateFlow<RulesUi> = _ui.asStateFlow()

    fun load(id: String) {
        viewModelScope.launch {
            _ui.value = RulesUi()
            runCatching {
                val tournament = repository.tournament(id)
                    ?: throw com.hmx.arena.data.remote.ApiException("Tournament not found.")
                tournament to repository.rules(id)
            }
                .onSuccess { (tournament, rules) ->
                    _ui.value = RulesUi(false, tournament, rules)
                }
                .onFailure { e -> _ui.value = RulesUi(false, null, emptyList(), e.hmxMessage()) }
        }
    }
}

@Composable
fun TournamentRulesScreen(nav: NavHostController, id: String, viewModel: RulesViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    LaunchedEffect(id) { viewModel.load(id) }
    HmxPage(title = "Rules", onBack = { nav.popBackStack() }) {
        when {
            ui.loading -> LoadingBlock("Loading rules")
            ui.error != null -> InlineError(ui.error!!)
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        ui.tournament?.title ?: "Tournament",
                        style = MaterialTheme.typography.headlineMedium,
                        color = HmxColor.Ink,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "These rules are enforced by tournament staff.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HmxColor.InkSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (ui.rules.isEmpty()) {
                    item {
                        EmptyBlock(
                            title = "No extra rules",
                            message = "The standard HMX Arena Fair Play policy applies to this tournament.",
                            icon = Icons.Rounded.List,
                        )
                    }
                }
                items(ui.rules, key = { it.id }) { rule ->
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(HmxDimens.cardPadding)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    rule.position.toString().padStart(2, '0'),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = HmxColor.InkTertiary,
                                    modifier = Modifier.width(28.dp),
                                )
                                Text(
                                    rule.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = HmxColor.Ink,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                rule.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = HmxColor.InkSecondary,
                                modifier = Modifier.padding(start = 28.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

data class ParticipantsUi(
    val loading: Boolean = true,
    val items: List<Participant> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ParticipantsViewModel @Inject constructor(
    private val player: PlayerRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(ParticipantsUi())
    val ui: StateFlow<ParticipantsUi> = _ui.asStateFlow()

    fun load(tournamentId: String) {
        viewModelScope.launch {
            _ui.value = ParticipantsUi()
            runCatching { player.participants(tournamentId, 200) }
                .onSuccess { list -> _ui.value = ParticipantsUi(false, list) }
                .onFailure { e -> _ui.value = ParticipantsUi(false, emptyList(), e.hmxMessage()) }
        }
    }
}

@Composable
fun TournamentParticipantsScreen(
    nav: NavHostController,
    id: String,
    viewModel: ParticipantsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    LaunchedEffect(id) { viewModel.load(id) }
    HmxPage(title = "Participants", onBack = { nav.popBackStack() }) {
        when {
            ui.loading -> LoadingBlock("Loading participants")
            ui.error != null -> InlineError(ui.error!!)
            ui.items.isEmpty() -> EmptyBlock(
                title = "No confirmed players yet",
                message = "Be the first to register for this tournament.",
                icon = Icons.Rounded.SportsEsports,
                actionLabel = "Open tournament",
                onAction = { nav.navigate(Routes.tournament(id)) },
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ui.items, key = { it.registrationId + it.ign }) { participant ->
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlassAvatar(participant.avatarUrl, participant.ign, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    participant.ign,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = HmxColor.Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOfNotNull(
                                        participant.teamName,
                                        participant.username?.let { "@$it" },
                                    ).joinToString(" · ").ifBlank { "Solo entry" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HmxColor.InkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (participant.isCaptain) StatusPill("Captain")
                        }
                    }
                }
            }
        }
    }
}

data class ScheduleUi(
    val loading: Boolean = true,
    val tournament: Tournament? = null,
    val matches: List<Match> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: TournamentRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(ScheduleUi())
    val ui: StateFlow<ScheduleUi> = _ui.asStateFlow()

    fun load(id: String) {
        viewModelScope.launch {
            _ui.value = ScheduleUi()
            runCatching {
                val tournament = repository.tournament(id)
                    ?: throw com.hmx.arena.data.remote.ApiException("Tournament not found.")
                tournament to repository.tournamentMatches(id)
            }
                .onSuccess { (tournament, matches) ->
                    _ui.value = ScheduleUi(false, tournament, matches)
                }
                .onFailure { e -> _ui.value = ScheduleUi(false, null, emptyList(), e.hmxMessage()) }
        }
    }
}

@Composable
fun TournamentScheduleScreen(nav: NavHostController, id: String, viewModel: ScheduleViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    LaunchedEffect(id) { viewModel.load(id) }
    HmxPage(title = "Schedule", onBack = { nav.popBackStack() }) {
        when {
            ui.loading -> LoadingBlock("Loading schedule")
            ui.error != null -> InlineError(ui.error!!)
            ui.matches.isEmpty() -> EmptyBlock(
                title = "Schedule not published",
                message = "Match times appear here once tournament staff publish the bracket.",
                icon = Icons.Rounded.EmojiEvents,
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(ui.matches, key = { it.id }) { match ->
                    MatchCard(
                        match = match,
                        tournamentTitle = ui.tournament?.title ?: "Tournament",
                        onClick = { nav.navigate(Routes.match(match.id)) },
                    )
                }
            }
        }
    }
}

data class TournamentRankUi(
    val loading: Boolean = true,
    val rows: List<com.hmx.arena.core.model.LeaderboardRow> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class TournamentRankViewModel @Inject constructor(
    private val repository: TournamentRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(TournamentRankUi())
    val ui: StateFlow<TournamentRankUi> = _ui.asStateFlow()

    fun load(id: String) {
        viewModelScope.launch {
            _ui.value = TournamentRankUi()
            runCatching { repository.leaderboard(scope = "player", tournamentId = id, limit = 100) }
                .onSuccess { rows -> _ui.value = TournamentRankUi(false, rows) }
                .onFailure { e -> _ui.value = TournamentRankUi(false, emptyList(), e.hmxMessage()) }
        }
    }
}

@Composable
fun TournamentLeaderboardScreen(
    nav: NavHostController,
    id: String,
    viewModel: TournamentRankViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    LaunchedEffect(id) { viewModel.load(id) }
    HmxPage(title = "Tournament ranks", onBack = { nav.popBackStack() }) {
        when {
            ui.loading -> LoadingBlock("Ranking players")
            ui.error != null -> InlineError(ui.error!!)
            ui.rows.isEmpty() -> EmptyBlock(
                title = "No verified results",
                message = "This leaderboard fills up as results are verified.",
                icon = Icons.Rounded.Star,
            )
            else -> RankList(
                rows = ui.rows,
                onPlayerClick = { playerId -> nav.navigate(Routes.player(playerId)) },
            )
        }
    }
}
