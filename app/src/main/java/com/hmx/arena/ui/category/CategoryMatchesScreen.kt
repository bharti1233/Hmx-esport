package com.hmx.arena.ui.category

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.hmx.arena.core.designsystem.GlassButton
import com.hmx.arena.core.designsystem.GlassButtonVariant
import com.hmx.arena.core.designsystem.GlassIconButton
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxShapes
import com.hmx.arena.core.model.LookupOption
import com.hmx.arena.core.model.MatchRegistration
import com.hmx.arena.core.model.WalletAccount
import com.hmx.arena.core.util.Format
import com.hmx.arena.data.remote.ApiException
import com.hmx.arena.data.repository.CATEGORY_MATCH_PAGE_SIZE
import com.hmx.arena.data.repository.MatchFeedItem
import com.hmx.arena.data.repository.MatchFeedSection
import com.hmx.arena.data.repository.PlayerRepository
import com.hmx.arena.data.repository.TournamentRepository
import com.hmx.arena.data.repository.matchSectionKey
import com.hmx.arena.data.repository.preferredMatchSection
import com.hmx.arena.ui.common.CategoryFallbackGlyph
import com.hmx.arena.ui.common.EmptyBlock
import com.hmx.arena.ui.common.HmxFilterOption
import com.hmx.arena.ui.common.HmxFilterTabs
import com.hmx.arena.ui.common.HmxPage
import com.hmx.arena.ui.common.InlineError
import com.hmx.arena.ui.common.LoadingBlock
import com.hmx.arena.ui.common.MatchFeedCard
import com.hmx.arena.ui.common.categoryFallbackGlyph
import com.hmx.arena.ui.common.categoryInitials
import com.hmx.arena.ui.common.categoryVisuals
import com.hmx.arena.ui.common.hmxMessage
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

internal const val CATEGORY_EMPTY_MESSAGE = "No matches available in this category yet."

data class CategoryTabState(
    val items: List<MatchFeedItem> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

data class CategoryMatchesUi(
    val categoryId: String = "",
    val category: LookupOption? = null,
    val wallet: WalletAccount? = null,
    val section: MatchFeedSection = MatchFeedSection.ONGOING,
    val counts: Map<MatchFeedSection, Int>? = null,
    val tabs: Map<MatchFeedSection, CategoryTabState> = emptyMap(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
) {
    val currentTab: CategoryTabState
        get() = tabs[section] ?: CategoryTabState()

    val currentItems: List<MatchFeedItem>
        get() = currentTab.items
}

@HiltViewModel
class CategoryMatchesViewModel @Inject constructor(
    private val repository: TournamentRepository,
    private val player: PlayerRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(CategoryMatchesUi())
    val ui: StateFlow<CategoryMatchesUi> = _ui.asStateFlow()

    private var loadedCategoryId: String? = null
    private var userSelectedSection = false
    private var registrations: Map<String, MatchRegistration> = emptyMap()

    /**
     * Loads the category header/counts once, then the active tab. [categoryId]
     * is passed from the current nav entry on every call, so a reused
     * ViewModel can never keep a previous category after Back/forward.
     */
    fun load(categoryId: String, refresh: Boolean = false) {
        val id = categoryId.trim()
        if (id.isBlank()) {
            _ui.value = CategoryMatchesUi(error = "This category could not be found.")
            return
        }
        if (!refresh && id == loadedCategoryId && _ui.value.error == null) {
            ensureTabLoaded(_ui.value.section)
            return
        }
        // Ignore a duplicate refresh tap while the same category is already loading.
        if (refresh && _ui.value.refreshing && id == loadedCategoryId) return

        val isNewCategory = id != loadedCategoryId
        loadedCategoryId = id
        if (isNewCategory) {
            userSelectedSection = false
            registrations = emptyMap()
        }

        _ui.update { current ->
            when {
                isNewCategory -> CategoryMatchesUi(categoryId = id, loading = true, refreshing = true)
                refresh -> current.copy(
                    categoryId = id,
                    loading = current.category == null,
                    refreshing = true,
                    error = null,
                    tabs = emptyMap(),
                )
                else -> current.copy(categoryId = id, loading = current.category == null, refreshing = true, error = null)
            }
        }

        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    val categoryDeferred = async {
                        repository.category(id)
                            ?: throw ApiException("This category could not be found.")
                    }
                    val walletDeferred = async { runCatching { player.wallet() }.getOrNull() }
                    val registrationsDeferred = async {
                        runCatching { repository.myMatchRegistrations() }.getOrDefault(emptyList())
                    }
                    val countDeferred = MatchFeedSection.entries.associateWith { section ->
                        async { runCatching { repository.categoryMatchCount(id, section) }.getOrNull() }
                    }
                    InitialCategoryData(
                        category = categoryDeferred.await(),
                        wallet = walletDeferred.await(),
                        registrations = registrationsDeferred.await(),
                        counts = countDeferred.mapValues { it.value.await() },
                    )
                }
            }
                .onSuccess { data ->
                    // A slower request for a previous category must never
                    // overwrite the category the user is looking at now.
                    if (loadedCategoryId != id) return@onSuccess
                    registrations = data.registrations.associateBy { it.matchId }
                    val knownCounts = data.counts.mapNotNull { (section, count) ->
                        count?.let { section to it }
                    }.toMap()
                    val preferred = preferredMatchSection(knownCounts)
                    val selected = if (!userSelectedSection && preferred != null) {
                        preferred
                    } else {
                        _ui.value.section
                    }
                    _ui.update {
                        it.copy(
                            category = data.category,
                            wallet = data.wallet,
                            counts = knownCounts,
                            section = selected,
                            loading = false,
                            refreshing = false,
                            error = null,
                        )
                    }
                    ensureTabLoaded(selected)
                }
                .onFailure { e ->
                    if (loadedCategoryId != id) return@onFailure
                    _ui.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = e.hmxMessage(),
                        )
                    }
                }
        }
    }

    fun selectSection(key: String) {
        val section = MatchFeedSection.entries.firstOrNull { matchSectionKey(it) == key }
            ?: return
        userSelectedSection = true
        if (_ui.value.section == section) {
            ensureTabLoaded(section)
            return
        }
        _ui.update { it.copy(section = section) }
        ensureTabLoaded(section)
    }

    fun loadMore() {
        val tab = _ui.value.currentTab
        if (tab.loading || tab.loadingMore || !tab.hasMore) return
        loadTab(_ui.value.section, reset = false)
    }

    fun refresh() {
        val id = _ui.value.categoryId
        if (id.isBlank()) return
        load(id, refresh = true)
    }

    fun retry() {
        val current = _ui.value
        if (current.category == null) {
            load(current.categoryId, refresh = true)
        } else {
            loadTab(current.section, reset = current.currentTab.items.isEmpty())
        }
    }

    private fun ensureTabLoaded(section: MatchFeedSection) {
        val tab = _ui.value.tabs[section] ?: CategoryTabState()
        if (tab.loaded || tab.loading || tab.loadingMore) return
        loadTab(section, reset = true)
    }

    private fun loadTab(section: MatchFeedSection, reset: Boolean) {
        val categoryId = _ui.value.categoryId
        if (categoryId.isBlank()) return
        val existing = _ui.value.tabs[section] ?: CategoryTabState()
        if (existing.loading || existing.loadingMore) return

        val offset = if (reset) 0 else existing.items.size
        _ui.update { current ->
            val tab = current.tabs[section] ?: CategoryTabState()
            current.copy(
                tabs = current.tabs + (
                    section to tab.copy(
                        items = if (reset) emptyList() else tab.items,
                        loading = reset,
                        loadingMore = !reset,
                        hasMore = if (reset) false else tab.hasMore,
                        error = null,
                    )
                    ),
            )
        }

        viewModelScope.launch {
            runCatching {
                val matches = repository.categoryMatches(
                    categoryId = categoryId,
                    section = section,
                    limit = CATEGORY_MATCH_PAGE_SIZE,
                    offset = offset,
                )
                val mine = if (registrations.isNotEmpty()) {
                    registrations
                } else {
                    runCatching { repository.myMatchRegistrations() }
                        .getOrDefault(emptyList())
                        .associateBy { it.matchId }
                }
                matches.map { match ->
                    MatchFeedItem(
                        match = match,
                        tournament = null,
                        matchRegistration = mine[match.id],
                    )
                }
            }
                .onSuccess { page ->
                    if (_ui.value.categoryId != categoryId) return@onSuccess
                    _ui.update { current ->
                        val tab = current.tabs[section] ?: CategoryTabState()
                        val existingIds = if (reset) emptySet() else tab.items.map { it.match.id }.toSet()
                        val merged = if (reset) {
                            page
                        } else {
                            tab.items + page.filterNot { it.match.id in existingIds }
                        }
                        current.copy(
                            tabs = current.tabs + (
                                section to tab.copy(
                                    items = merged,
                                    loading = false,
                                    loadingMore = false,
                                    loaded = true,
                                    hasMore = page.size == CATEGORY_MATCH_PAGE_SIZE,
                                    error = null,
                                )
                                ),
                        )
                    }
                }
                .onFailure { e ->
                    if (_ui.value.categoryId != categoryId) return@onFailure
                    _ui.update { current ->
                        val tab = current.tabs[section] ?: CategoryTabState()
                        current.copy(
                            tabs = current.tabs + (
                                section to tab.copy(
                                    loading = false,
                                    loadingMore = false,
                                    error = e.hmxMessage(),
                                )
                                ),
                        )
                    }
                }
        }
    }
}

private data class InitialCategoryData(
    val category: LookupOption,
    val wallet: WalletAccount?,
    val registrations: List<MatchRegistration>,
    val counts: Map<MatchFeedSection, Int?>,
)

@Composable
fun CategoryMatchesScreen(
    nav: NavHostController,
    categoryId: String,
    viewModel: CategoryMatchesViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(categoryId) { viewModel.load(categoryId) }

    val category = ui.category
    HmxPage(
        title = category?.name ?: "Category",
        onBack = { nav.popBackStack() },
        actions = {
            GlassIconButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Refresh category matches",
                onClick = viewModel::refresh,
                enabled = !ui.refreshing,
            )
        },
    ) {
        when {
            ui.loading && category == null -> LoadingBlock("Loading category")
            ui.error != null && category == null -> InlineError(ui.error!!, onRetry = viewModel::retry)
            category != null -> {
                val tab = ui.currentTab
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "category-header") {
                        CategoryHero(
                            category = category,
                            wallet = ui.wallet,
                            onWallet = { nav.navigate(Routes.WALLET) },
                        )
                    }
                    item(key = "category-tabs") {
                        HmxFilterTabs(
                            options = MatchFeedSection.entries.map { section ->
                                HmxFilterOption(
                                    key = matchSectionKey(section),
                                    label = section.label,
                                    count = ui.counts?.get(section),
                                )
                            },
                            selectedKey = matchSectionKey(ui.section),
                            onSelect = viewModel::selectSection,
                        )
                    }
                    when {
                        tab.error != null && tab.items.isEmpty() -> item(key = "tab-error") {
                            InlineError(tab.error!!, onRetry = viewModel::retry)
                        }
                        tab.loading && tab.items.isEmpty() -> item(key = "tab-loading") {
                            LoadingBlock("Loading ${ui.section.label.lowercase()} matches")
                        }
                        !tab.loaded && tab.items.isEmpty() -> item(key = "tab-pending") {
                            LoadingBlock("Loading ${ui.section.label.lowercase()} matches")
                        }
                        tab.items.isEmpty() -> item(key = "tab-empty") {
                            EmptyBlock(
                                title = CATEGORY_EMPTY_MESSAGE,
                                message = "New ${category.name} matches appear here as soon as the arena team publishes them.",
                                icon = Icons.Rounded.SportsEsports,
                                actionLabel = "Refresh",
                                onAction = viewModel::refresh,
                            )
                        }
                        else -> {
                            items(tab.items, key = { it.match.id }) { entry ->
                                MatchFeedCard(
                                    match = entry.match,
                                    tournamentTitle = entry.tournament?.title,
                                    registered = entry.registered,
                                    onClick = { nav.navigate(Routes.match(entry.match.id)) },
                                )
                            }
                            if (tab.loadingMore) {
                                item(key = "loading-more") { LoadingBlock("Loading more matches") }
                            } else if (tab.hasMore) {
                                item(key = "load-more") {
                                    GlassButton(
                                        text = "Load more",
                                        onClick = viewModel::loadMore,
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
}

@Composable
private fun CategoryHero(
    category: LookupOption,
    wallet: WalletAccount?,
    onWallet: () -> Unit,
) {
    val visuals = categoryVisuals(category.imageUrl, category.logoUrl)
    val artwork = visuals.artwork
    val logo = visuals.logo
    GlassSurface(Modifier.fillMaxWidth(), shape = HmxShapes.large) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(2.1f)) {
                if (artwork != null) {
                    AsyncImage(
                        model = artwork,
                        contentDescription = "${category.name} artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(HmxColor.AccentSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (logo != null) {
                            AsyncImage(
                                model = logo,
                                contentDescription = "${category.name} logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(HmxColor.CanvasTint),
                            )
                        } else {
                            CategoryHeroFallback(category)
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, HmxColor.Canvas.copy(alpha = 0.92f)),
                            ),
                        ),
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                ) {
                    if (artwork != null && logo != null) {
                        AsyncImage(
                            model = logo,
                            contentDescription = "${category.name} logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(HmxColor.CanvasTint),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        category.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = HmxColor.Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (category.description.isNotBlank()) {
                        Text(
                            category.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = HmxColor.InkSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (wallet != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassSurface(
                        modifier = Modifier
                            .clip(HmxShapes.pill)
                            .clickable(role = Role.Button, onClick = onWallet),
                        shape = HmxShapes.pill,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Wallet,
                                contentDescription = null,
                                tint = HmxColor.Accent,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Wallet",
                                style = MaterialTheme.typography.labelSmall,
                                color = HmxColor.InkTertiary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                Format.money(wallet.balancePaise),
                                style = MaterialTheme.typography.titleSmall,
                                color = HmxColor.Ink,
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

@Composable
private fun CategoryHeroFallback(category: LookupOption) {
    when (categoryFallbackGlyph(category.slug)) {
        CategoryFallbackGlyph.BattleRoyale -> HeroIcon(Icons.Rounded.SportsEsports)
        CategoryFallbackGlyph.ClashSquad -> HeroIcon(Icons.Rounded.Group)
        CategoryFallbackGlyph.FullMap -> HeroIcon(Icons.Rounded.Category)
        CategoryFallbackGlyph.SpecialTournament -> HeroIcon(Icons.Rounded.Star)
        CategoryFallbackGlyph.CustomTournament -> HeroIcon(Icons.Rounded.Tune)
        CategoryFallbackGlyph.Letter -> Text(
            categoryInitials(category.name),
            style = MaterialTheme.typography.displaySmall,
            color = HmxColor.Accent,
        )
    }
}

@Composable
private fun HeroIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(
        icon,
        contentDescription = null,
        tint = HmxColor.Accent,
        modifier = Modifier.size(56.dp),
    )
}
