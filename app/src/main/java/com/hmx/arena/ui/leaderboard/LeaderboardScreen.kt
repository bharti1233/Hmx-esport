package com.hmx.arena.ui.leaderboard

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.hmx.arena.core.designsystem.GlassChip
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.designsystem.HmxShapes
import com.hmx.arena.core.model.LeaderboardRow
import com.hmx.arena.core.util.Format
import com.hmx.arena.data.repository.TournamentRepository
import com.hmx.arena.ui.common.EmptyBlock
import com.hmx.arena.ui.common.HmxGlassBottomBar
import com.hmx.arena.ui.common.HmxGlassLeaderboardItem
import com.hmx.arena.ui.common.HmxPage
import com.hmx.arena.ui.common.InlineError
import com.hmx.arena.ui.common.LoadingBlock
import com.hmx.arena.ui.common.hmxMessage
import com.hmx.arena.ui.navigation.Routes
import com.hmx.arena.ui.navigation.openTab
import com.hmx.arena.ui.shell.GlassBottomBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeaderboardUi(
    val loading: Boolean = true,
    val scope: String = "player",
    val monthly: Boolean = false,
    val rows: List<LeaderboardRow> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val tournaments: TournamentRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(LeaderboardUi())
    val ui: StateFlow<LeaderboardUi> = _ui.asStateFlow()

    init { load() }

    fun setScope(scope: String) {
        if (_ui.value.scope == scope) return
        _ui.update { it.copy(scope = scope) }
        load()
    }

    fun toggleMonthly() {
        _ui.update { it.copy(monthly = !it.monthly) }
        load()
    }

    fun load() {
        val current = _ui.value
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching {
                tournaments.leaderboard(
                    scope = current.scope,
                    monthly = current.monthly,
                    limit = 100,
                )
            }
                .onSuccess { rows -> _ui.update { it.copy(loading = false, rows = rows) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }
}

@Composable
fun LeaderboardScreen(nav: NavHostController, viewModel: LeaderboardViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    HmxPage(
        title = "Leaderboard",
        actions = {
            Text(
                "Refresh",
                style = MaterialTheme.typography.labelMedium,
                color = HmxColor.Ink,
                modifier = Modifier
                    .clickable { viewModel.load() }
                    .padding(10.dp),
            )
        },
        bottomBar = { HmxGlassBottomBar(Routes.LEADERBOARD) { nav.openTab(it) } },
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassChip("Players", ui.scope == "player") { viewModel.setScope("player") }
                GlassChip("Teams", ui.scope == "team") { viewModel.setScope("team") }
                Spacer(Modifier.weight(1f))
                GlassChip(if (ui.monthly) "This month" else "All time", ui.monthly) {
                    viewModel.toggleMonthly()
                }
            }
            when {
                ui.loading && ui.rows.isEmpty() -> LoadingBlock("Ranking players")
                ui.error != null && ui.rows.isEmpty() -> InlineError(ui.error!!, onRetry = viewModel::load)
                ui.rows.isEmpty() -> EmptyBlock(
                    title = "No ranked results yet",
                    message = "Verified match results feed the leaderboard. Play a tournament to get on the board.",
                    icon = Icons.Rounded.Star,
                )
                else -> LeaderboardList(
                    rows = ui.rows,
                    onPlayerClick = { id -> nav.navigate(Routes.player(id)) },
                    clickable = ui.scope == "player",
                )
            }
        }
    }
}

@Composable
fun LeaderboardList(
    rows: List<LeaderboardRow>,
    onPlayerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    clickable: Boolean = true,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rows, key = { it.entityId + it.rankNo }) { row ->
            HmxGlassLeaderboardItem(
                row = row,
                clickable = clickable,
                onClick = { onPlayerClick(row.entityId) },
            )
        }
    }
}
