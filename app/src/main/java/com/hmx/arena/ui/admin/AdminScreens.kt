package com.hmx.arena.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.hmx.arena.core.designsystem.GlassButton
import com.hmx.arena.core.designsystem.GlassButtonVariant
import com.hmx.arena.core.designsystem.GlassIconButton
import com.hmx.arena.core.designsystem.GlassSectionHeader
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.designsystem.HmxShapes
import com.hmx.arena.core.model.AuditLog
import com.hmx.arena.core.model.LookupOption
import com.hmx.arena.core.model.Match
import com.hmx.arena.core.model.Tournament
import com.hmx.arena.core.util.Format
import com.hmx.arena.data.repository.AdminRepository
import com.hmx.arena.ui.common.ConfirmDialog
import com.hmx.arena.ui.common.EmptyBlock
import com.hmx.arena.ui.common.GlassActionRow
import com.hmx.arena.ui.common.GlassTextField
import com.hmx.arena.ui.common.HmxPage
import com.hmx.arena.ui.common.InlineError
import com.hmx.arena.ui.common.InlineNotice
import com.hmx.arena.ui.common.InlineSuccess
import com.hmx.arena.ui.common.LoadingBlock
import com.hmx.arena.ui.common.PickedImage
import com.hmx.arena.ui.common.StatusPill
import com.hmx.arena.ui.common.hmxMessage
import com.hmx.arena.ui.common.rememberPickedImage
import com.hmx.arena.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Dashboard
// ---------------------------------------------------------------------------

data class AdminDashboardUi(
    val loading: Boolean = true,
    val isAdmin: Boolean = false,
    val categoryCount: Int = 0,
    val modeCount: Int = 0,
    val typeCount: Int = 0,
    val tournamentCount: Int = 0,
    val matchCount: Int = 0,
    val bannerCount: Int = 0,
    val announcementCount: Int = 0,
    val recent: List<AuditLog> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val admin: AdminRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(AdminDashboardUi())
    val ui: StateFlow<AdminDashboardUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching {
                val allowed = admin.isAdmin()
                if (!allowed) return@runCatching AdminDashboardUi(isAdmin = false, loading = false)
                coroutineScope {
                    val categories = async { admin.categories() }
                    val modes = async { admin.gameModes() }
                    val types = async { admin.tournamentTypes() }
                    val tournaments = async { admin.tournaments() }
                    val matches = async { admin.matches() }
                    val banners = async {
                        runCatching { admin.banners() }.getOrDefault(emptyList())
                    }
                    val announcements = async {
                        runCatching { admin.announcements() }.getOrDefault(emptyList())
                    }
                    val audit = async { runCatching { admin.auditLogs(12) }.getOrDefault(emptyList()) }
                    AdminDashboardUi(
                        loading = false,
                        isAdmin = true,
                        categoryCount = categories.await().size,
                        modeCount = modes.await().size,
                        typeCount = types.await().size,
                        tournamentCount = tournaments.await().size,
                        matchCount = matches.await().size,
                        bannerCount = banners.await().size,
                        announcementCount = announcements.await().size,
                        recent = audit.await(),
                    )
                }
            }
                .onSuccess { _ui.value = it }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }
}

@Composable
fun AdminDashboardScreen(nav: NavHostController, viewModel: AdminDashboardViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    HmxPage(title = "Admin console", onBack = { nav.popBackStack() }) {
        when {
            ui.loading -> LoadingBlock("Checking admin access")
            !ui.isAdmin -> EmptyBlock(
                title = "Administrator access required",
                message = "Your account does not have an administrator role. Ask an existing admin to grant access.",
                icon = Icons.Rounded.Tune,
            )
            ui.error != null -> InlineError(ui.error!!, onRetry = viewModel::load)
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(HmxDimens.cardPadding)) {
                            Text("Manage the arena", style = MaterialTheme.typography.titleLarge, color = HmxColor.Ink)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Every change is authorised on the server and written to the audit log.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HmxColor.InkSecondary,
                            )
                        }
                    }
                }
                item {
                    GlassSectionHeader("Lookups")
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            GlassActionRow(
                                title = "Categories",
                                subtitle = "${ui.categoryCount} configured",
                                icon = Icons.Rounded.Category,
                                onClick = { nav.navigate(Routes.adminLookup("categories")) },
                            )
                            GlassActionRow(
                                title = "Game modes",
                                subtitle = "${ui.modeCount} configured",
                                icon = Icons.Rounded.SportsEsports,
                                onClick = { nav.navigate(Routes.adminLookup("game-modes")) },
                            )
                            GlassActionRow(
                                title = "Tournament types",
                                subtitle = "${ui.typeCount} configured",
                                icon = Icons.Rounded.Extension,
                                onClick = { nav.navigate(Routes.adminLookup("tournament-types")) },
                            )
                        }
                    }
                }
                item {
                    GlassSectionHeader("Competition")
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            GlassActionRow(
                                title = "Tournaments",
                                subtitle = "${ui.tournamentCount} total",
                                icon = Icons.Rounded.EmojiEvents,
                                onClick = { nav.navigate(Routes.ADMIN_TOURNAMENTS) },
                            )
                            GlassActionRow(
                                title = "Matches",
                                subtitle = "${ui.matchCount} total",
                                icon = Icons.Rounded.SportsEsports,
                                onClick = { nav.navigate(Routes.ADMIN_MATCHES) },
                            )
                            GlassActionRow(
                                title = "Audit log",
                                subtitle = "Recent administrative changes",
                                icon = Icons.Rounded.History,
                                onClick = { nav.navigate(Routes.ADMIN_AUDIT) },
                            )
                        }
                    }
                }
                item {
                    GlassSectionHeader("Home content")
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            GlassActionRow(
                                title = "Promotional banners",
                                subtitle = "${ui.bannerCount} banner(s), carousel on Home",
                                icon = Icons.Rounded.Image,
                                onClick = { nav.navigate(Routes.ADMIN_BANNERS) },
                            )
                            GlassActionRow(
                                title = "Announcements",
                                subtitle = "${ui.announcementCount} total, ticker and news",
                                icon = Icons.Rounded.Campaign,
                                onClick = { nav.navigate(Routes.ADMIN_ANNOUNCEMENTS) },
                            )
                        }
                    }
                }
                if (ui.recent.isNotEmpty()) {
                    item {
                        GlassSectionHeader("Recent activity")
                        GlassSurface(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                ui.recent.take(6).forEach { log ->
                                    AuditRow(log)
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
private fun AuditRow(log: AuditLog) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(log.action, style = MaterialTheme.typography.bodyMedium, color = HmxColor.Ink)
            Text(
                log.entityType + (log.entityId?.take(8)?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = HmxColor.InkTertiary,
            )
        }
        Text(Format.dateTime(log.createdAt), style = MaterialTheme.typography.labelSmall, color = HmxColor.InkTertiary)
    }
}

// ---------------------------------------------------------------------------
// Lookup management (categories / game modes / tournament types)
// ---------------------------------------------------------------------------

private fun lookupKindTitle(kind: String): String = when (kind) {
    "categories" -> "Categories"
    "game-modes" -> "Game modes"
    else -> "Tournament types"
}

private fun lookupSupportsLogo(kind: String): Boolean = kind == "categories"

data class AdminLookupUi(
    val kind: String,
    val loading: Boolean = true,
    val items: List<LookupOption> = emptyList(),
    val editing: LookupOption? = null,
    val saving: Boolean = false,
    val busyId: String? = null,
    val error: String? = null,
    val actionError: String? = null,
    val message: String? = null,
)

@HiltViewModel
class AdminLookupViewModel @Inject constructor(
    private val admin: AdminRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val kind: String = savedStateHandle.get<String>("kind") ?: "categories"
    private val _ui = MutableStateFlow(AdminLookupUi(kind = kind))
    val ui: StateFlow<AdminLookupUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.items.isEmpty(), error = null) }
            runCatching {
                when (kind) {
                    "categories" -> admin.categories()
                    "game-modes" -> admin.gameModes()
                    else -> admin.tournamentTypes()
                }
            }
                .onSuccess { list -> _ui.update { it.copy(loading = false, items = list) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    fun startCreate() = _ui.update { it.copy(editing = null, actionError = null, message = null) }

    fun startEdit(item: LookupOption) = _ui.update {
        it.copy(editing = item, actionError = null, message = null)
    }

    fun clearForm() = _ui.update { it.copy(editing = null, actionError = null) }

    fun save(
        name: String,
        slug: String,
        description: String,
        displayOrder: Int,
        active: Boolean,
        archived: Boolean,
        logoUrl: String?,
        image: PickedImage?,
        artwork: PickedImage? = null,
    ) {
        if (_ui.value.saving) return
        viewModelScope.launch {
            _ui.update { it.copy(saving = true, actionError = null, message = null) }
            runCatching {
                val payload = buildJsonObject {
                    put("name", name.trim())
                    put("slug", slug.trim())
                    put("description", description.trim())
                    put("display_order", displayOrder)
                    put("is_active", active)
                    put("is_archived", archived)
                    if (lookupSupportsLogo(kind)) put("logo_url", logoUrl ?: "")
                }
                var saved = when (kind) {
                    "categories" -> admin.upsertCategory(_ui.value.editing?.id, payload)
                    "game-modes" -> admin.upsertGameMode(_ui.value.editing?.id, payload)
                    else -> admin.upsertTournamentType(_ui.value.editing?.id, payload)
                }
                if (lookupSupportsLogo(kind) && image != null) {
                    val url = admin.uploadCategoryLogo(saved.id, image)
                    saved = admin.upsertCategory(
                        saved.id,
                        buildJsonObject {
                            put("name", name.trim())
                            put("slug", slug.trim())
                            put("description", description.trim())
                            put("display_order", displayOrder)
                            put("is_active", active)
                            put("is_archived", archived)
                            put("logo_url", url)
                        },
                    )
                }
                if (lookupSupportsLogo(kind) && artwork != null) {
                    val url = admin.uploadCategoryArtwork(saved.id, artwork)
                    admin.setLookupImage(kind, saved.id, url)
                }
                saved
            }
                .onSuccess {
                    _ui.update {
                        it.copy(
                            saving = false,
                            editing = null,
                            message = "Saved ${it.kind.replace('-', ' ')}.",
                        )
                    }
                    load()
                }
                .onFailure { e -> _ui.update { it.copy(saving = false, actionError = e.hmxMessage()) } }
        }
    }

    fun delete(item: LookupOption) {
        if (_ui.value.busyId != null) return
        viewModelScope.launch {
            _ui.update { it.copy(busyId = item.id, actionError = null, message = null) }
            runCatching {
                when (kind) {
                    "categories" -> admin.deleteCategory(item.id)
                    "game-modes" -> admin.deleteGameMode(item.id)
                    else -> admin.deleteTournamentType(item.id)
                }
            }
                .onSuccess {
                    _ui.update { it.copy(busyId = null, message = "${item.name} deleted.") }
                    load()
                }
                .onFailure { e -> _ui.update { it.copy(busyId = null, actionError = e.hmxMessage()) } }
        }
    }

    fun clearMessage() = _ui.update { it.copy(message = null, actionError = null) }
}

@Composable
fun AdminLookupScreen(
    nav: NavHostController,
    viewModel: AdminLookupViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val kindTitle = lookupKindTitle(ui.kind)
    var confirmDelete by remember { mutableStateOf<LookupOption?>(null) }
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var slug by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var order by remember { mutableStateOf("0") }
    var active by remember { mutableStateOf(true) }
    var archived by remember { mutableStateOf(false) }
    var logoUrl by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<PickedImage?>(null) }
    var artworkUrl by remember { mutableStateOf<String?>(null) }
    var artworkPicked by remember { mutableStateOf<PickedImage?>(null) }
    val (picker, launchPicker) = rememberPickedImage()
    val (artworkPicker, launchArtworkPicker) = rememberPickedImage()

    LaunchedEffect(picker) { picker?.let { picked = it } }
    LaunchedEffect(artworkPicker) { artworkPicker?.let { artworkPicked = it } }

    fun begin(item: LookupOption?) {
        name = item?.name.orEmpty()
        slug = item?.slug.orEmpty()
        description = item?.description.orEmpty()
        order = item?.displayOrder?.toString() ?: "0"
        active = item?.isActive ?: true
        archived = item?.isArchived ?: false
        logoUrl = item?.logoUrl
        artworkUrl = item?.imageUrl
        picked = null
        artworkPicked = null
        editing = true
        if (item == null) viewModel.startCreate() else viewModel.startEdit(item)
    }

    HmxPage(
        title = kindTitle,
        onBack = { nav.popBackStack() },
        actions = {
            GlassIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "Add $kindTitle",
                onClick = { begin(null) },
            )
        },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ui.message != null) item { InlineSuccess(ui.message!!) }
            if (ui.actionError != null) item { InlineError(ui.actionError!!) }

            if (editing) {
                item {
                    GlassSectionHeader(if (ui.editing == null) "New ${kindTitle.dropLast(1)}" else "Edit ${kindTitle.dropLast(1)}")
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(HmxDimens.cardPadding),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            GlassTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = "Name",
                                placeholder = "e.g. Battle Royale",
                            )
                            GlassTextField(
                                value = slug,
                                onValueChange = { slug = it },
                                label = "Identifier",
                                placeholder = "battle-royale",
                            )
                            GlassTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = "Description",
                                maxLines = 3,
                            )
                            GlassTextField(
                                value = order,
                                onValueChange = { order = it.filter(Char::isDigit) },
                                label = "Display order",
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            )
                            if (lookupSupportsLogo(ui.kind)) {
                                ArtworkRow(
                                    label = "Category logo (1:1)",
                                    url = picked?.uri?.toString() ?: logoUrl,
                                    square = true,
                                    onPick = launchPicker,
                                    onRemove = {
                                        picked = null
                                        logoUrl = null
                                    },
                                )
                                ArtworkRow(
                                    label = "Category artwork (wide)",
                                    url = artworkPicked?.uri?.toString() ?: artworkUrl,
                                    square = false,
                                    onPick = launchArtworkPicker,
                                    onRemove = {
                                        artworkPicked = null
                                        artworkUrl = null
                                    },
                                )
                                if (ui.editing == null) {
                                    InlineNotice("Save the category first, then reopen it to upload a logo or artwork.")
                                }
                            }
                            SwitchRow("Active", active) { active = it }
                            SwitchRow("Archived", archived) { archived = it }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GlassButton(
                                    text = if (ui.saving) "Saving…" else "Save",
                                    onClick = {
                                        viewModel.save(
                                            name = name,
                                            slug = slug,
                                            description = description,
                                            displayOrder = order.toIntOrNull() ?: 0,
                                            active = active,
                                            archived = archived,
                                            logoUrl = logoUrl,
                                            image = picked,
                                            artwork = artworkPicked,
                                        )
                                    },
                                    enabled = name.isNotBlank() && !ui.saving,
                                    variant = GlassButtonVariant.Ink,
                                    modifier = Modifier.weight(1f),
                                )
                                GlassButton(
                                    text = "Cancel",
                                    onClick = {
                                        editing = false
                                        picked = null
                                        artworkPicked = null
                                        viewModel.clearForm()
                                    },
                                    variant = GlassButtonVariant.Secondary,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            when {
                ui.loading -> item { LoadingBlock("Loading $kindTitle") }
                ui.error != null -> item { InlineError(ui.error!!, onRetry = viewModel::load) }
                ui.items.isEmpty() -> item {
                    EmptyBlock(
                        title = "No ${kindTitle.lowercase()} yet",
                        message = "Create the first entry to make it available to tournaments and matches.",
                        icon = Icons.Rounded.Category,
                        actionLabel = "Add",
                        onAction = { begin(null) },
                    )
                }
                else -> items(ui.items, key = { it.id }) { item ->
                    LookupRow(
                        item = item,
                        supportsLogo = lookupSupportsLogo(ui.kind),
                        busy = ui.busyId == item.id,
                        onEdit = { begin(item) },
                        onDelete = { confirmDelete = item },
                    )
                }
            }
        }
    }

    if (confirmDelete != null) {
        ConfirmDialog(
            title = "Delete ${confirmDelete!!.name}?",
            message = "Deletion is only allowed when nothing references this value. Otherwise deactivate or archive it.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.delete(confirmDelete!!)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun LookupRow(
    item: LookupOption,
    supportsLogo: Boolean,
    busy: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    GlassSurface(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onEdit)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (supportsLogo) {
                AsyncImage(
                    model = item.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(46.dp).clip(HmxShapes.field),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, color = HmxColor.Ink)
                Text(
                    "${item.slug} · order ${item.displayOrder}",
                    style = MaterialTheme.typography.labelSmall,
                    color = HmxColor.InkTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(if (item.isActive) "Active" else "Inactive", filled = item.isActive)
                    if (item.isArchived) StatusPill("Archived")
                }
            }
            if (busy) {
                Text("Working…", style = MaterialTheme.typography.labelSmall, color = HmxColor.InkTertiary)
            } else {
                GlassIconButton(
                    icon = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete ${item.name}",
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = HmxColor.Ink, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ArtworkRow(
    label: String,
    url: String?,
    square: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = HmxColor.InkTertiary)
        Spacer(Modifier.height(6.dp))
        AsyncImage(
            model = url,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (square) 120.dp else 160.dp)
                .clip(HmxShapes.field),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                text = if (url == null) "Upload" else "Replace",
                onClick = onPick,
                variant = GlassButtonVariant.Secondary,
                modifier = Modifier.weight(1f),
            )
            if (url != null) {
                GlassButton(
                    text = "Remove",
                    onClick = onRemove,
                    variant = GlassButtonVariant.Secondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tournament list
// ---------------------------------------------------------------------------

data class AdminTournamentsUi(
    val loading: Boolean = true,
    val items: List<Tournament> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val actionError: String? = null,
    val busyId: String? = null,
)

@HiltViewModel
class AdminTournamentsViewModel @Inject constructor(
    private val admin: AdminRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(AdminTournamentsUi())
    val ui: StateFlow<AdminTournamentsUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.items.isEmpty(), error = null) }
            runCatching { admin.tournaments() }
                .onSuccess { list -> _ui.update { it.copy(loading = false, items = list) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    fun setStatus(id: String, status: String) {
        if (_ui.value.busyId != null) return
        viewModelScope.launch {
            _ui.update { it.copy(busyId = id, actionError = null, message = null) }
            runCatching { admin.setTournamentStatus(id, status) }
                .onSuccess { _ui.update { it.copy(busyId = null, message = "Tournament updated.") }; load() }
                .onFailure { e -> _ui.update { it.copy(busyId = null, actionError = e.hmxMessage()) } }
        }
    }

    fun delete(id: String) {
        if (_ui.value.busyId != null) return
        viewModelScope.launch {
            _ui.update { it.copy(busyId = id, actionError = null, message = null) }
            runCatching { admin.deleteTournament(id) }
                .onSuccess { _ui.update { it.copy(busyId = null, message = "Tournament deleted.") }; load() }
                .onFailure { e -> _ui.update { it.copy(busyId = null, actionError = e.hmxMessage()) } }
        }
    }
}

@Composable
fun AdminTournamentsScreen(nav: NavHostController, viewModel: AdminTournamentsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<Tournament?>(null) }
    var statusTarget by remember { mutableStateOf<Pair<Tournament, String>?>(null) }

    HmxPage(
        title = "Tournaments",
        onBack = { nav.popBackStack() },
        actions = {
            GlassIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "Create tournament",
                onClick = { nav.navigate(Routes.adminTournamentEdit("new")) },
            )
        },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (ui.message != null) item { InlineSuccess(ui.message!!) }
            if (ui.actionError != null) item { InlineError(ui.actionError!!) }
            if (ui.loading) item { LoadingBlock("Loading tournaments") }
            if (ui.error != null) item { InlineError(ui.error!!, onRetry = viewModel::load) }
            if (!ui.loading && ui.error == null && ui.items.isEmpty()) {
                item {
                    EmptyBlock(
                        title = "No tournaments",
                        message = "Create a tournament to open registration.",
                        icon = Icons.Rounded.EmojiEvents,
                        actionLabel = "Create tournament",
                        onAction = { nav.navigate(Routes.adminTournamentEdit("new")) },
                    )
                }
            }
            items(ui.items, key = { it.id }) { tournament ->
                AdminTournamentRow(
                    tournament = tournament,
                    busy = ui.busyId == tournament.id,
                    onClick = { nav.navigate(Routes.adminTournamentEdit(tournament.id)) },
                    onStatus = { statusTarget = tournament to it },
                    onDelete = { confirmDelete = tournament },
                )
            }
        }
    }

    statusTarget?.let { (tournament, status) ->
        ConfirmDialog(
            title = "Change tournament status?",
            message = "${tournament.title} will move to ${status.replace('_', ' ')}.",
            confirmLabel = "Confirm",
            onConfirm = {
                viewModel.setStatus(tournament.id, status)
                statusTarget = null
            },
            onDismiss = { statusTarget = null },
        )
    }

    confirmDelete?.let { tournament ->
        ConfirmDialog(
            title = "Delete tournament?",
            message = "Only a draft with no registrations can be deleted. ${tournament.title} will be removed permanently.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.delete(tournament.id)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun AdminTournamentRow(
    tournament: Tournament,
    busy: Boolean,
    onClick: () -> Unit,
    onStatus: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    GlassSurface(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Column {
            if (!tournament.bannerUrl.isNullOrBlank()) {
                AsyncImage(
                    model = tournament.bannerUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                )
            }
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tournament.title, style = MaterialTheme.typography.titleMedium, color = HmxColor.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${tournament.categoryLabel} · ${tournament.modeLabel} · ${tournament.slotsFilled}/${tournament.maxSlots}",
                        style = MaterialTheme.typography.labelSmall,
                        color = HmxColor.InkTertiary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(tournament.status.replace('_', ' ').replaceFirstChar { it.uppercase() }, filled = tournament.status == "registration_open")
                        if (tournament.archivedAt != null) StatusPill("Archived")
                    }
                }
                Box {
                    GlassIconButton(
                        icon = Icons.Rounded.Tune,
                        contentDescription = "Tournament actions",
                        onClick = { menu = true },
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Open registration") }, onClick = { menu = false; onStatus("registration_open") })
                        DropdownMenuItem(text = { Text("Close registration") }, onClick = { menu = false; onStatus("registration_closed") })
                        DropdownMenuItem(text = { Text("Mark live") }, onClick = { menu = false; onStatus("live") })
                        DropdownMenuItem(text = { Text("Mark completed") }, onClick = { menu = false; onStatus("completed") })
                        DropdownMenuItem(text = { Text("Cancel tournament") }, onClick = { menu = false; onStatus("cancelled") })
                        DropdownMenuItem(text = { Text("Delete draft") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            if (busy) {
                Text(
                    "Working…",
                    style = MaterialTheme.typography.labelSmall,
                    color = HmxColor.InkTertiary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Match list
// ---------------------------------------------------------------------------

data class AdminMatchesUi(
    val loading: Boolean = true,
    val items: List<Match> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val actionError: String? = null,
    val busyId: String? = null,
)

@HiltViewModel
class AdminMatchesViewModel @Inject constructor(
    private val admin: AdminRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(AdminMatchesUi())
    val ui: StateFlow<AdminMatchesUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.items.isEmpty(), error = null) }
            runCatching { admin.matches() }
                .onSuccess { list -> _ui.update { it.copy(loading = false, items = list) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    fun setPublished(id: String, published: Boolean) {
        if (_ui.value.busyId != null) return
        viewModelScope.launch {
            _ui.update { it.copy(busyId = id, actionError = null, message = null) }
            runCatching { admin.setMatchPublished(id, published) }
                .onSuccess { _ui.update { it.copy(busyId = null, message = if (published) "Match published." else "Match unpublished.") }; load() }
                .onFailure { e -> _ui.update { it.copy(busyId = null, actionError = e.hmxMessage()) } }
        }
    }

    fun duplicate(id: String) {
        if (_ui.value.busyId != null) return
        viewModelScope.launch {
            _ui.update { it.copy(busyId = id, actionError = null, message = null) }
            runCatching { admin.duplicateMatch(id) }
                .onSuccess { _ui.update { it.copy(busyId = null, message = "Match duplicated as a draft.") }; load() }
                .onFailure { e -> _ui.update { it.copy(busyId = null, actionError = e.hmxMessage()) } }
        }
    }

    fun delete(id: String) {
        if (_ui.value.busyId != null) return
        viewModelScope.launch {
            _ui.update { it.copy(busyId = id, actionError = null, message = null) }
            runCatching { admin.deleteMatch(id) }
                .onSuccess { _ui.update { it.copy(busyId = null, message = "Match deleted.") }; load() }
                .onFailure { e -> _ui.update { it.copy(busyId = null, actionError = e.hmxMessage()) } }
        }
    }
}

@Composable
fun AdminMatchesScreen(nav: NavHostController, viewModel: AdminMatchesViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<Match?>(null) }

    HmxPage(
        title = "Matches",
        onBack = { nav.popBackStack() },
        actions = {
            GlassIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "Create match",
                onClick = { nav.navigate(Routes.adminMatchEdit("new")) },
            )
        },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (ui.message != null) item { InlineSuccess(ui.message!!) }
            if (ui.actionError != null) item { InlineError(ui.actionError!!) }
            if (ui.loading) item { LoadingBlock("Loading matches") }
            if (ui.error != null) item { InlineError(ui.error!!, onRetry = viewModel::load) }
            if (!ui.loading && ui.error == null && ui.items.isEmpty()) {
                item {
                    EmptyBlock(
                        title = "No matches",
                        message = "Create an independent match or attach one to a tournament.",
                        icon = Icons.Rounded.SportsEsports,
                        actionLabel = "Create match",
                        onAction = { nav.navigate(Routes.adminMatchEdit("new")) },
                    )
                }
            }
            items(ui.items, key = { it.id }) { match ->
                AdminMatchRow(
                    match = match,
                    busy = ui.busyId == match.id,
                    onClick = { nav.navigate(Routes.adminMatchEdit(match.id)) },
                    onPublish = { viewModel.setPublished(match.id, !match.isPublished) },
                    onDuplicate = { viewModel.duplicate(match.id) },
                    onDelete = { confirmDelete = match },
                )
            }
        }
    }

    confirmDelete?.let { match ->
        ConfirmDialog(
            title = "Delete match?",
            message = "Matches with registrations cannot be deleted. ${match.name} will be removed permanently.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.delete(match.id)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun AdminMatchRow(
    match: Match,
    busy: Boolean,
    onClick: () -> Unit,
    onPublish: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    GlassSurface(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(HmxColor.CanvasTint)) {
                if (!match.bannerUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = match.bannerUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.SportsEsports,
                            contentDescription = null,
                            tint = HmxColor.InkTertiary,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
                if (match.isFull) {
                    Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                        StatusPill("MATCH FULL", filled = true)
                    }
                }
                if (!match.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = match.logoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .size(54.dp)
                            .clip(HmxShapes.field),
                    )
                }
            }
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(match.name, style = MaterialTheme.typography.titleMedium, color = HmxColor.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            match.matchNumber,
                            Format.dateTime(match.scheduledAt),
                            match.maxSlots?.let { "${match.slotsFilled}/$it" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = HmxColor.InkTertiary,
                    )
                    Text(
                        listOfNotNull(
                            match.categoryLabel.takeIf { it != "—" },
                            match.modeLabel.takeIf { it != "—" },
                            match.typeLabel,
                            match.map?.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = HmxColor.InkTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Prize ${if (match.prizePoolPaise > 0) Format.money(match.prizePoolPaise) else "—"}" +
                            " · Per kill ${if (match.perKillPaise > 0) Format.money(match.perKillPaise) else "—"}" +
                            " · Entry ${if (match.entryFeePaise > 0) Format.money(match.entryFeePaise) else "Free"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = HmxColor.InkTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(if (match.isPublished) "Published" else "Draft", filled = match.isPublished)
                        StatusPill(match.status.replaceFirstChar { it.uppercase() })
                    }
                }
                Box {
                    GlassIconButton(
                        icon = Icons.Rounded.Tune,
                        contentDescription = "Match actions",
                        onClick = { menu = true },
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(if (match.isPublished) "Unpublish" else "Publish") }, onClick = { menu = false; onPublish() })
                        DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            if (busy) {
                Text(
                    "Working…",
                    style = MaterialTheme.typography.labelSmall,
                    color = HmxColor.InkTertiary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Audit log
// ---------------------------------------------------------------------------

data class AdminAuditUi(
    val loading: Boolean = true,
    val items: List<AuditLog> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class AdminAuditViewModel @Inject constructor(
    private val admin: AdminRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(AdminAuditUi())
    val ui: StateFlow<AdminAuditUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.items.isEmpty(), error = null) }
            runCatching { admin.auditLogs(100) }
                .onSuccess { list -> _ui.update { it.copy(loading = false, items = list) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }
}

@Composable
fun AdminAuditScreen(nav: NavHostController, viewModel: AdminAuditViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    HmxPage(title = "Audit log", onBack = { nav.popBackStack() }) {
        when {
            ui.loading -> LoadingBlock("Loading audit log")
            ui.error != null -> InlineError(ui.error!!, onRetry = viewModel::load)
            ui.items.isEmpty() -> EmptyBlock(
                title = "No admin activity",
                message = "Administrative changes will appear here with the actor, action and timestamp.",
                icon = Icons.Rounded.History,
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ui.items, key = { it.id }) { log ->
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.History, contentDescription = null, tint = HmxColor.InkTertiary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(log.action, style = MaterialTheme.typography.bodyMedium, color = HmxColor.Ink)
                                Text(
                                    "${log.entityType} · ${log.entityId?.take(8) ?: "—"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HmxColor.InkTertiary,
                                )
                            }
                            Text(Format.dateTime(log.createdAt), style = MaterialTheme.typography.labelSmall, color = HmxColor.InkTertiary)
                        }
                    }
                }
            }
        }
    }
}
