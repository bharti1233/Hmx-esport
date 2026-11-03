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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.hmx.arena.core.designsystem.GlassButtonVariant
import com.hmx.arena.core.designsystem.GlassIconButton
import com.hmx.arena.core.designsystem.GlassSectionHeader
import com.hmx.arena.core.designsystem.GlassStatCell
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.designsystem.HmxShapes
import com.hmx.arena.core.model.Announcement
import com.hmx.arena.core.model.Profile
import com.hmx.arena.core.util.Format
import com.hmx.arena.data.repository.PlayerRepository
import com.hmx.arena.data.repository.TournamentRepository
import com.hmx.arena.data.repository.MyMatch
import com.hmx.arena.ui.common.EmptyBlock
import com.hmx.arena.ui.common.GlassAvatar
import com.hmx.arena.ui.common.HmxGlassBottomBar
import com.hmx.arena.ui.common.HmxGlassButton
import com.hmx.arena.ui.common.HmxGlassCard
import com.hmx.arena.ui.common.InlineError
import com.hmx.arena.ui.common.LoadingBlock
import com.hmx.arena.ui.common.MatchCard
import com.hmx.arena.ui.common.TournamentCard
import com.hmx.arena.ui.common.UiState
import com.hmx.arena.ui.common.hmxMessage
import com.hmx.arena.ui.navigation.Routes
import com.hmx.arena.ui.navigation.openTab
import com.hmx.arena.ui.shell.GlassBottomBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeData(
    val profile: Profile,
    val featured: List<com.hmx.arena.core.model.Tournament>,
    val upcoming: List<MyMatch>,
    val announcements: List<Announcement>,
    val unread: Int,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val player: PlayerRepository,
    private val tournaments: TournamentRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(UiState<HomeData>())
    val state: StateFlow<UiState<HomeData>> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = _state.value.data == null, refreshing = true, error = null)
            runCatching {
                val profile = player.myProfile()
                HomeData(
                    profile = profile,
                    featured = runCatching { tournaments.featured() }.getOrDefault(emptyList()),
                    upcoming = runCatching { tournaments.myMatches() }.getOrDefault(emptyList()),
                    announcements = runCatching { player.announcements() }.getOrDefault(emptyList()),
                    unread = runCatching { player.notifications().count { it.unread } }.getOrDefault(0),
                )
            }
                .onSuccess { _state.value = UiState(loading = false, data = it, refreshing = false) }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        error = e.hmxMessage(),
                    )
                }
        }
    }
}

@Composable
fun HomeScreen(
    nav: NavHostController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val data = state.data

    com.hmx.arena.ui.common.HmxPage(
        title = "HMX Arena",
        actions = {
            Box {
                GlassIconButton(
                    icon = Icons.Rounded.Notifications,
                    contentDescription = "Notifications",
                    onClick = { nav.navigate(Routes.NOTIFICATIONS) },
                )
                val unread = data?.unread ?: 0
                if (unread > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(HmxColor.Ink),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (unread > 9) "9+" else unread.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = HmxColor.White,
                        )
                    }
                }
            }
        },
        bottomBar = { HmxGlassBottomBar(Routes.HOME) { nav.openTab(it) } },
    ) {
        when {
            state.loading && data == null -> LoadingBlock("Loading your arena")
            state.error != null && data == null -> InlineError(state.error!!, onRetry = viewModel::load)
            data != null -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item { ProfileHeader(data.profile) }
                if (!data.profile.onboardingCompleted) {
                    item {
                        SetupCard(onClick = { nav.navigate(Routes.COMPLETE_PROFILE) })
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickTile("Tournaments", Icons.Rounded.EmojiEvents, Modifier.weight(1f)) {
                                nav.openTab(Routes.TOURNAMENTS)
                            }
                            QuickTile("My matches", Icons.Rounded.SportsEsports, Modifier.weight(1f)) {
                                nav.openTab(Routes.MATCHES)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickTile("Wallet", Icons.Rounded.Wallet, Modifier.weight(1f)) {
                                nav.navigate(Routes.WALLET)
                            }
                            QuickTile("Refer & Earn", Icons.Rounded.CardGiftcard, Modifier.weight(1f)) {
                                nav.navigate(Routes.REFERRAL)
                            }
                        }
                    }
                }
                if (data.featured.isNotEmpty()) {
                    item {
                        GlassSectionHeader(
                            title = "Featured arenas",
                            action = "See all",
                            onAction = { nav.openTab(Routes.TOURNAMENTS) },
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(data.featured, key = { it.id }) { tournament ->
                                TournamentCard(
                                    tournament = tournament,
                                    onClick = { nav.navigate(Routes.tournament(tournament.id)) },
                                    modifier = Modifier.width(300.dp),
                                )
                            }
                        }
                    }
                }
                item {
                    GlassSectionHeader(
                        title = "Your schedule",
                        action = "All matches",
                        onAction = { nav.openTab(Routes.MATCHES) },
                    )
                }
                if (data.upcoming.isEmpty()) {
                    item {
                        EmptyBlock(
                            title = "No matches yet",
                            message = "Register for a tournament and your matches will appear here with room details.",
                            icon = Icons.Rounded.EmojiEvents,
                            actionLabel = "Find a tournament",
                            onAction = { nav.openTab(Routes.TOURNAMENTS) },
                        )
                    }
                } else {
                    items(data.upcoming.take(3), key = { it.match.id }) { entry ->
                        MatchCard(
                            match = entry.match,
                            tournamentTitle = entry.tournament.title,
                            onClick = { nav.navigate(Routes.match(entry.match.id)) },
                        )
                    }
                }
                item {
                    GlassSectionHeader(
                        title = "Arena news",
                        action = "All news",
                        onAction = { nav.navigate(Routes.ANNOUNCEMENTS) },
                    )
                }
                if (data.announcements.isEmpty()) {
                    item {
                        GlassSurface(Modifier.fillMaxWidth()) {
                            Text(
                                "Announcements from HMX Arena will appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HmxColor.InkSecondary,
                                modifier = Modifier.padding(18.dp),
                            )
                        }
                    }
                } else {
                    items(data.announcements.take(2), key = { it.id }) { announcement ->
                        AnnouncementRow(announcement) { nav.navigate(Routes.announcement(announcement.id)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(profile: Profile) {
    GlassSurface(Modifier.fillMaxWidth(), refractive = true) {
        Column(Modifier.padding(HmxDimens.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassAvatar(profile.avatarUrl, profile.display, 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Welcome back,",
                        style = MaterialTheme.typography.labelMedium,
                        color = HmxColor.InkTertiary,
                    )
                    Text(
                        profile.display,
                        style = MaterialTheme.typography.titleLarge,
                        color = HmxColor.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "@${profile.username} · ${profile.region}",
                        style = MaterialTheme.typography.bodySmall,
                        color = HmxColor.InkSecondary,
                    )
                }
                Text(
                    Format.points(profile.points.toLong()),
                    style = MaterialTheme.typography.headlineMedium,
                    color = HmxColor.Ink,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                GlassStatCell("Played", profile.matchesPlayed.toString(), Modifier.weight(1f))
                GlassStatCell("Wins", profile.wins.toString(), Modifier.weight(1f))
                GlassStatCell("Points", Format.points(profile.points.toLong()), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SetupCard(onClick: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(HmxDimens.cardPadding)) {
            Text("Finish your player setup", style = MaterialTheme.typography.titleMedium, color = HmxColor.Ink)
            Spacer(Modifier.height(4.dp))
            Text(
                "Add your Free Fire IGN and UID so tournament eligibility can be checked automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = HmxColor.InkSecondary,
            )
            Spacer(Modifier.height(14.dp))
            HmxGlassButton(
                text = "Complete setup",
                onClick = onClick,
                variant = GlassButtonVariant.Ink,
                modifier = Modifier.width(190.dp),
            )
        }
    }
}

@Composable
private fun QuickTile(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    HmxGlassCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = HmxColor.Ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = HmxColor.Ink,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = HmxColor.InkTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun AnnouncementRow(announcement: Announcement, onClick: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    announcement.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = HmxColor.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    Format.relative(announcement.publishedAt ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = HmxColor.InkTertiary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text("Read", style = MaterialTheme.typography.labelMedium, color = HmxColor.Ink)
        }
    }
}
