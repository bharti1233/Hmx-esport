package com.hmx.arena.ui.match

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.data.repository.MatchFeedItem
import com.hmx.arena.data.repository.TournamentRepository
import com.hmx.arena.ui.common.EmptyBlock
import com.hmx.arena.ui.common.HmxPage
import com.hmx.arena.ui.common.InlineError
import com.hmx.arena.ui.common.LoadingBlock
import com.hmx.arena.ui.common.MatchFeedCard
import com.hmx.arena.ui.common.hmxMessage
import com.hmx.arena.ui.navigation.Routes
import com.hmx.arena.ui.navigation.openTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyMatchesUi(
    val loading: Boolean = true,
    val items: List<MatchFeedItem> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class MyMatchesViewModel @Inject constructor(
    private val repository: TournamentRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(MyMatchesUi())
    val ui: StateFlow<MyMatchesUi> = _ui.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.items.isEmpty(), error = null) }
            runCatching { repository.myMatchFeed() }
                .onSuccess { list -> _ui.update { it.copy(loading = false, items = list) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }
}

/**
 * User-specific match list. The repository starts from match_registrations, so
 * the public catalogue can never be substituted here.
 */
@Composable
fun MyMatchesScreen(nav: NavHostController, viewModel: MyMatchesViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    HmxPage(
        title = "My matches",
        onBack = { nav.popBackStack() },
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
    ) {
        when {
            ui.loading && ui.items.isEmpty() -> LoadingBlock("Loading your match registrations")
            ui.error != null && ui.items.isEmpty() -> InlineError(ui.error!!, onRetry = viewModel::load)
            ui.items.isEmpty() -> EmptyBlock(
                title = "No match registrations",
                message = "Only matches you have registered for appear here. Browse the public feed to join one.",
                icon = Icons.Rounded.SportsEsports,
                actionLabel = "Browse matches",
                onAction = { nav.openTab(Routes.MATCHES) },
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${ui.items.size} registered",
                            style = MaterialTheme.typography.labelMedium,
                            color = HmxColor.InkTertiary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "Public matches",
                            style = MaterialTheme.typography.labelMedium,
                            color = HmxColor.Ink,
                            modifier = Modifier
                                .clickable { nav.openTab(Routes.MATCHES) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
                items(ui.items, key = { it.match.id }) { entry ->
                    MatchFeedCard(
                        match = entry.match,
                        tournamentTitle = entry.tournament?.title,
                        registered = true,
                        onClick = { nav.navigate(Routes.match(entry.match.id)) },
                    )
                }
            }
        }
    }
}
