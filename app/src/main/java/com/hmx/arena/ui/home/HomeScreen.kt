package com.hmx.arena.ui.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.hmx.arena.core.designsystem.GlassButtonVariant
import com.hmx.arena.core.designsystem.GlassIconButton
import com.hmx.arena.core.designsystem.GlassSectionHeader
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxBrandMark
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.designsystem.HmxShapes
import com.hmx.arena.core.designsystem.LocalHmxWindowClass
import com.hmx.arena.core.designsystem.hmxCategoryColumns
import com.hmx.arena.core.model.Announcement
import com.hmx.arena.core.model.LookupOption
import com.hmx.arena.core.model.Profile
import com.hmx.arena.core.model.PromotionalBanner
import com.hmx.arena.core.model.Tournament
import com.hmx.arena.core.model.WalletAccount
import com.hmx.arena.core.util.Format
import com.hmx.arena.data.repository.MatchFeedItem
import com.hmx.arena.data.repository.MatchFeedSection
import com.hmx.arena.data.repository.PlayerRepository
import com.hmx.arena.data.repository.TournamentRepository
import com.hmx.arena.data.repository.matchSectionCounts
import com.hmx.arena.ui.common.EmptyBlock
import com.hmx.arena.ui.common.GlassAvatar
import com.hmx.arena.ui.common.HmxAnnouncementTicker
import com.hmx.arena.ui.common.HmxCategoryArtCard
import com.hmx.arena.ui.common.HmxGlassBottomBar
import com.hmx.arena.ui.common.HmxMatchShortcutCard
import com.hmx.arena.ui.common.HmxPage
import com.hmx.arena.ui.common.HmxPromoCarousel
import com.hmx.arena.ui.common.HmxReferralCard
import com.hmx.arena.ui.common.InlineError
import com.hmx.arena.ui.common.LoadingBlock
import com.hmx.arena.ui.common.MatchCard
import com.hmx.arena.ui.common.TournamentCard
import com.hmx.arena.ui.common.UiState
import com.hmx.arena.ui.common.hmxMessage
import com.hmx.arena.ui.navigation.Routes
import com.hmx.arena.ui.navigation.openTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeData(
    val profile: Profile,
    val featured: List<Tournament>,
    val myMatches: List<MatchFeedItem>,
    val announcements: List<Announcement>,
    val banners: List<PromotionalBanner>,
    val categories: List<LookupOption>,
    val wallet: WalletAccount?,
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
            _state.value = _state.value.copy(
                loading = _state.value.data == null,
                refreshing = true,
                error = null,
            )
            runCatching {
                // Independent reads run concurrently. Optional reads (wallet,
                // banners, categories) degrade to an empty/absent state without
                // blanking the whole dashboard.
                coroutineScope {
                    val profile = async { player.myProfile() }
                    val featured = async {
                        runCatching { tournaments.featured() }.getOrDefault(emptyList())
                    }
                    val myMatches = async {
                        runCatching { tournaments.myMatchFeed() }.getOrDefault(emptyList())
                    }
                    val announcements = async {
                        runCatching { player.announcements() }.getOrDefault(emptyList())
                    }
                    val banners = async {
                        runCatching { player.promotionalBanners() }.getOrDefault(emptyList())
                    }
                    val categories = async {
                        runCatching { tournaments.categories() }.getOrDefault(emptyList())
                    }
                    val wallet = async {
                        runCatching { player.wallet() }.getOrNull()
                    }
                    val unread = async {
                        runCatching { player.notifications().count { it.unread } }.getOrDefault(0)
                    }
                    HomeData(
                        profile = profile.await(),
                        featured = featured.await(),
                        myMatches = myMatches.await(),
                        announcements = announcements.await(),
                        banners = banners.await(),
                        categories = categories.await(),
                        wallet = wallet.await(),
                        unread = unread.await(),
                    )
                }
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
    val state by viewModel.state.collectAsStateWithLifecycle()
    val data = state.data
    val context = LocalContext.current

    HmxPage(
        title = "HMX Arena",
        leading = { HmxBrandMark() },
        actions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "Refresh home",
                    onClick = viewModel::load,
                    enabled = !state.refreshing,
                )
                Spacer(Modifier.width(6.dp))
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
                                .background(HmxColor.Accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (unread > 9) "9+" else unread.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = HmxColor.OnAccent,
                            )
                        }
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
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 124.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { HomeHeader(data.profile, data.wallet, nav) }
                if (!data.profile.onboardingCompleted) {
                    item { SetupCard(onClick = { nav.navigate(Routes.COMPLETE_PROFILE) }) }
                }
                item {
                    HmxAnnouncementTicker(
                        announcements = data.announcements,
                        onOpen = { nav.navigate(Routes.ANNOUNCEMENTS) },
                    )
                }
                item {
                    HmxPromoCarousel(
                        banners = data.banners,
                        onBannerClick = { banner -> openBanner(context, nav, banner) },
                    )
                }
                item {
                    MyMatchesShortcuts(
                        counts = matchSectionCounts(data.myMatches),
                        onOpen = { section ->
                            nav.navigate(Routes.myMatches(section)) { launchSingleTop = true }
                        },
                    )
                }
                item {
                    CategorySection(
                        categories = data.categories,
                        onCategory = { category ->
                            // A plain navigate (not openTab) is required here:
                            // openTab restores the saved entry and would keep the
                            // previous category argument.
                            nav.navigate(Routes.tournaments(category.id)) { launchSingleTop = true }
                        },
                        onArena = { nav.openTab(Routes.TOURNAMENTS) },
                    )
                }
                item {
                    if (data.profile.referralCode.isNullOrBlank()) {
                        GlassSurface(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(HmxDimens.cardPadding),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.CardGiftcard,
                                    contentDescription = null,
                                    tint = HmxColor.InkTertiary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Your invite code has not been issued yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HmxColor.InkSecondary,
                                )
                            }
                        }
                    } else {
                        val code = data.profile.referralCode.orEmpty()
                        val inviteText = "Join HMX Arena and use my invite code $code when you sign up."
                        HmxReferralCard(
                            code = code,
                            onShare = { shareText(context, inviteText) },
                            onWhatsApp = { shareWhatsApp(context, inviteText) },
                        )
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
                    items(data.featured.take(2), key = { "featured-${it.id}" }) { tournament ->
                        TournamentCard(
                            tournament = tournament,
                            onClick = { nav.navigate(Routes.tournament(tournament.id)) },
                        )
                    }
                }
                item {
                    GlassSectionHeader(
                        title = "Your schedule",
                        action = "My matches",
                        onAction = { nav.navigate(Routes.myMatches()) },
                    )
                }
                if (data.myMatches.isEmpty()) {
                    item {
                        EmptyBlock(
                            title = "No match registrations yet",
                            message = "Register for a published match and it will appear here with its room details.",
                            icon = Icons.Rounded.EmojiEvents,
                            actionLabel = "Browse matches",
                            onAction = { nav.openTab(Routes.MATCHES) },
                        )
                    }
                } else {
                    items(data.myMatches.take(2), key = { "mine-${it.match.id}" }) { entry ->
                        MatchCard(
                            match = entry.match,
                            tournamentTitle = entry.tournament?.title ?: "Independent match",
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
                    items(data.announcements.take(2), key = { "news-${it.id}" }) { announcement ->
                        AnnouncementRow(announcement) {
                            nav.navigate(Routes.announcement(announcement.id))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    profile: Profile,
    wallet: WalletAccount?,
    nav: NavHostController,
) {
    GlassSurface(
        Modifier.fillMaxWidth(),
        shape = HmxShapes.large,
        refractive = true,
    ) {
        Column(Modifier.padding(HmxDimens.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { nav.openTab(Routes.PROFILE) }
                        .semantics { contentDescription = "Your profile" },
                ) {
                    GlassAvatar(profile.avatarUrl, profile.display, 48.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Welcome back,",
                        style = MaterialTheme.typography.labelSmall,
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
                        style = MaterialTheme.typography.labelSmall,
                        color = HmxColor.InkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GlassIconButton(
                    icon = Icons.Rounded.Language,
                    contentDescription = "Search players and tournaments",
                    onClick = { nav.navigate(Routes.SEARCH) },
                )
                Spacer(Modifier.width(6.dp))
                GlassIconButton(
                    icon = Icons.Rounded.SupportAgent,
                    contentDescription = "Support",
                    onClick = { nav.navigate(Routes.SUPPORT) },
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassSurface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(HmxShapes.pill)
                        .clickable(role = Role.Button) { nav.navigate(Routes.WALLET) }
                        .semantics { contentDescription = "Wallet balance" },
                    shape = HmxShapes.pill,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Wallet,
                            contentDescription = null,
                            tint = HmxColor.Accent,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Wallet",
                                style = MaterialTheme.typography.labelSmall,
                                color = HmxColor.InkTertiary,
                            )
                            Text(
                                wallet?.let { Format.money(it.balancePaise) } ?: "Balance unavailable",
                                style = MaterialTheme.typography.titleMedium,
                                color = HmxColor.Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                GlassIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "Add to wallet",
                    onClick = { nav.navigate(Routes.WALLET) },
                )
            }
        }
    }
}

@Composable
private fun MyMatchesShortcuts(
    counts: Map<MatchFeedSection, Int>,
    onOpen: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassSectionHeader("My matches", action = "All", onAction = { onOpen("all") })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HmxMatchShortcutCard(
                label = "Ongoing",
                count = counts[MatchFeedSection.ONGOING] ?: 0,
                icon = Icons.Rounded.PlayCircle,
                onClick = { onOpen("ongoing") },
                modifier = Modifier.weight(1f),
            )
            HmxMatchShortcutCard(
                label = "Upcoming",
                count = counts[MatchFeedSection.UPCOMING] ?: 0,
                icon = Icons.Rounded.Schedule,
                onClick = { onOpen("upcoming") },
                modifier = Modifier.weight(1f),
            )
            HmxMatchShortcutCard(
                label = "Completed",
                count = counts[MatchFeedSection.COMPLETED] ?: 0,
                icon = Icons.Rounded.CheckCircle,
                onClick = { onOpen("completed") },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CategorySection(
    categories: List<LookupOption>,
    onCategory: (LookupOption) -> Unit,
    onArena: () -> Unit,
) {
    val columns = hmxCategoryColumns(LocalHmxWindowClass.current)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassSectionHeader("Esports game", action = "Arena", onAction = onArena)
        Text(
            "Pick a category to find its matches.",
            style = MaterialTheme.typography.bodySmall,
            color = HmxColor.InkSecondary,
        )
        if (categories.isEmpty()) {
            GlassSurface(Modifier.fillMaxWidth()) {
                Text(
                    "Categories are managed by the arena team.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HmxColor.InkSecondary,
                    modifier = Modifier.padding(HmxDimens.cardPadding),
                )
            }
        } else {
            categories.chunked(columns).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { category ->
                        HmxCategoryArtCard(
                            category = category,
                            onClick = { onCategory(category) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
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
            com.hmx.arena.core.designsystem.GlassButton(
                text = "Complete setup",
                onClick = onClick,
                variant = GlassButtonVariant.Ink,
                modifier = Modifier.width(190.dp),
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
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = HmxColor.InkTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun openBanner(context: Context, nav: NavHostController, banner: PromotionalBanner) {
    val destination = banner.destination?.trim().orEmpty()
    if (destination.isBlank()) return
    if (destination.startsWith("https://") || destination.startsWith("http://")) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(destination))) }
        return
    }
    val allowed = destination == Routes.REFERRAL ||
        destination == Routes.WALLET ||
        destination == Routes.LEADERBOARD ||
        destination == Routes.PROFILE ||
        destination == Routes.SEARCH ||
        destination == Routes.TOURNAMENTS ||
        destination == Routes.MATCHES ||
        destination.startsWith("match/") ||
        destination.startsWith("tournament/") ||
        destination.startsWith("announcement/")
    if (allowed) {
        runCatching { nav.navigate(destination) }
    }
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share invite code"))
}

private fun shareWhatsApp(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        setPackage("com.whatsapp")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        shareText(context, text)
    }
}
