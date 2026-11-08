package com.hmx.arena.ui.admin

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.MaterialTheme
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
import com.hmx.arena.core.designsystem.GlassSectionHeader
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.designsystem.HmxShapes
import com.hmx.arena.core.model.Announcement
import com.hmx.arena.core.model.PromotionalBanner
import com.hmx.arena.core.util.Format
import com.hmx.arena.data.repository.AdminRepository
import com.hmx.arena.ui.common.ConfirmDialog
import com.hmx.arena.ui.common.EmptyBlock
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val windowInput = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private const val INVALID_WINDOW = "__invalid__"

/** Admin-entered local time, converted to the UTC instant Supabase stores. */
internal fun windowInputToIso(input: String): String? {
    if (input.isBlank()) return null
    return runCatching {
        LocalDateTime.parse(input.trim(), windowInput)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toString()
    }.getOrElse { INVALID_WINDOW }
}

internal fun isoToWindowInput(iso: String?): String {
    val instant = Format.parseInstant(iso) ?: return ""
    return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(windowInput)
}

internal fun parseWindowInput(input: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(input.trim(), windowInput) }.getOrNull()

// ---------------------------------------------------------------------------
// Promotional banners
// ---------------------------------------------------------------------------

data class AdminBannersUi(
    val loading: Boolean = true,
    val items: List<PromotionalBanner> = emptyList(),
    val saving: Boolean = false,
    val deletingId: String? = null,
    val error: String? = null,
    val actionError: String? = null,
    val message: String? = null,
)

@HiltViewModel
class AdminBannersViewModel @Inject constructor(
    private val admin: AdminRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(AdminBannersUi())
    val ui: StateFlow<AdminBannersUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.items.isEmpty(), error = null) }
            runCatching { admin.banners() }
                .onSuccess { list -> _ui.update { it.copy(loading = false, items = list) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    fun save(
        id: String?,
        title: String,
        subtitle: String,
        destination: String,
        displayOrder: Int,
        active: Boolean,
        startsAt: String,
        endsAt: String,
        image: PickedImage?,
    ) {
        if (_ui.value.saving) return
        viewModelScope.launch {
            _ui.update { it.copy(saving = true, actionError = null, message = null) }
            runCatching {
                val payload = buildJsonObject {
                    put("title", title.trim())
                    put("subtitle", subtitle.trim())
                    put("destination", destination.trim())
                    put("display_order", displayOrder)
                    put("is_active", active)
                    put("starts_at", startsAt)
                    put("ends_at", endsAt)
                }
                var saved = admin.upsertBanner(id, payload)
                if (image != null) {
                    val url = admin.uploadPromotionalBanner(saved.id, image)
                    saved = admin.upsertBanner(
                        saved.id,
                        buildJsonObject {
                            put("title", title.trim())
                            put("subtitle", subtitle.trim())
                            put("destination", destination.trim())
                            put("display_order", displayOrder)
                            put("is_active", active)
                            put("starts_at", startsAt)
                            put("ends_at", endsAt)
                            put("image_url", url)
                        },
                    )
                }
                saved
            }
                .onSuccess { _ui.update { it.copy(saving = false, message = "Banner saved.") }; load() }
                .onFailure { e -> _ui.update { it.copy(saving = false, actionError = e.hmxMessage()) } }
        }
    }

    fun delete(id: String) {
        if (_ui.value.deletingId != null) return
        viewModelScope.launch {
            _ui.update { it.copy(deletingId = id, actionError = null, message = null) }
            runCatching { admin.deleteBanner(id) }
                .onSuccess {
                    _ui.update { it.copy(deletingId = null, message = "Banner deleted.") }
                    load()
                }
                .onFailure { e ->
                    _ui.update { it.copy(deletingId = null, actionError = e.hmxMessage()) }
                }
        }
    }

    fun clearMessage() = _ui.update { it.copy(message = null, actionError = null) }
}

@Composable
fun AdminBannersScreen(nav: NavHostController, viewModel: AdminBannersViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var order by remember { mutableStateOf("0") }
    var active by remember { mutableStateOf(true) }
    var startsAt by remember { mutableStateOf("") }
    var endsAt by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<PickedImage?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<PromotionalBanner?>(null) }
    val (picker, launchPicker) = rememberPickedImage()

    LaunchedEffect(picker) { picker?.let { picked = it } }

    fun begin(item: PromotionalBanner?) {
        editId = item?.id
        title = item?.title.orEmpty()
        subtitle = item?.subtitle.orEmpty()
        destination = item?.destination.orEmpty()
        order = item?.displayOrder?.toString() ?: "0"
        active = item?.isActive ?: true
        startsAt = isoToWindowInput(item?.startsAt)
        endsAt = isoToWindowInput(item?.endsAt)
        imageUrl = item?.imageUrl
        picked = null
        formError = null
        editing = true
    }

    HmxPage(
        title = "Promotional banners",
        onBack = { nav.popBackStack() },
        actions = {
            GlassIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "Add banner",
                onClick = { begin(null) },
            )
        },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ui.message != null) item { InlineSuccess(ui.message!!) }
            if (ui.actionError != null) item { InlineError(ui.actionError!!) }

            if (editing) {
                item {
                    GlassSectionHeader(if (editId == null) "New banner" else "Edit banner")
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(HmxDimens.cardPadding),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            GlassTextField(
                                value = title,
                                onValueChange = { title = it.take(120) },
                                label = "Title",
                                placeholder = "Weekend clash",
                            )
                            GlassTextField(
                                value = subtitle,
                                onValueChange = { subtitle = it.take(240) },
                                label = "Subtitle",
                                placeholder = "Optional supporting line",
                            )
                            GlassTextField(
                                value = destination,
                                onValueChange = { destination = it.take(500) },
                                label = "Destination",
                                placeholder = "tournaments, wallet, https://…",
                            )
                            GlassTextField(
                                value = order,
                                onValueChange = { order = it.filter(Char::isDigit) },
                                label = "Display order",
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            )
                            GlassTextField(
                                value = startsAt,
                                onValueChange = { startsAt = it },
                                label = "Starts (YYYY-MM-DD HH:MM)",
                                placeholder = "Leave blank to start immediately",
                            )
                            GlassTextField(
                                value = endsAt,
                                onValueChange = { endsAt = it },
                                label = "Ends (YYYY-MM-DD HH:MM)",
                                placeholder = "Leave blank to run indefinitely",
                            )
                            ArtworkRow(
                                label = "Banner artwork (16:9)",
                                url = picked?.uri?.toString() ?: imageUrl,
                                square = false,
                                onPick = launchPicker,
                                onRemove = {
                                    picked = null
                                    imageUrl = null
                                },
                            )
                            if (editId == null) {
                                InlineNotice("Save the banner first, then reopen it to upload artwork.")
                            }
                            SwitchRow("Active", active) { active = it }
                            if (formError != null) InlineError(formError!!)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GlassButton(
                                    text = if (ui.saving) "Saving…" else "Save",
                                    onClick = {
                                        val startLocal =
                                            if (startsAt.isBlank()) null else parseWindowInput(startsAt)
                                        val endLocal =
                                            if (endsAt.isBlank()) null else parseWindowInput(endsAt)
                                        val validation = when {
                                            startsAt.isNotBlank() && startLocal == null ->
                                                "Use YYYY-MM-DD HH:MM for the start time."
                                            endsAt.isNotBlank() && endLocal == null ->
                                                "Use YYYY-MM-DD HH:MM for the end time."
                                            startLocal != null && endLocal != null &&
                                                !endLocal.isAfter(startLocal) ->
                                                "The end time must be after the start time."
                                            else -> null
                                        }
                                        formError = validation
                                        if (validation == null) {
                                            viewModel.save(
                                                id = editId,
                                                title = title,
                                                subtitle = subtitle,
                                                destination = destination,
                                                displayOrder = order.toIntOrNull() ?: 0,
                                                active = active,
                                                startsAt = windowInputToIso(startsAt).orEmpty(),
                                                endsAt = windowInputToIso(endsAt).orEmpty(),
                                                image = picked,
                                            )
                                        }
                                    },
                                    enabled = title.isNotBlank() && !ui.saving,
                                    variant = GlassButtonVariant.Ink,
                                    modifier = Modifier.weight(1f),
                                )
                                GlassButton(
                                    text = "Cancel",
                                    onClick = {
                                        editing = false
                                        picked = null
                                        formError = null
                                        viewModel.clearMessage()
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
                ui.loading -> item { LoadingBlock("Loading banners") }
                ui.error != null -> item { InlineError(ui.error!!, onRetry = viewModel::load) }
                ui.items.isEmpty() -> item {
                    EmptyBlock(
                        title = "No promotional banners",
                        message = "The Home carousel stays empty until an active banner with an image is created.",
                        icon = Icons.Rounded.Image,
                        actionLabel = "Add banner",
                        onAction = { begin(null) },
                    )
                }
                else -> items(ui.items, key = { it.id }) { banner ->
                    BannerRow(
                        banner = banner,
                        busy = ui.deletingId == banner.id,
                        onEdit = { begin(banner) },
                        onDelete = { confirmDelete = banner },
                    )
                }
            }
        }
    }

    if (confirmDelete != null) {
        ConfirmDialog(
            title = "Delete ${confirmDelete!!.title}?",
            message = "The Home carousel will stop showing this banner immediately.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.delete(confirmDelete!!.id)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun BannerRow(
    banner: PromotionalBanner,
    busy: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    GlassSurface(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onEdit)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = banner.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .width(84.dp)
                    .height(48.dp)
                    .clip(HmxShapes.field),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    banner.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = HmxColor.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Order ${banner.displayOrder}" +
                        if (banner.destination.isNullOrBlank()) "" else " · ${banner.destination}",
                    style = MaterialTheme.typography.labelSmall,
                    color = HmxColor.InkTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(if (banner.isActive) "Active" else "Inactive", filled = banner.isActive)
                    if (!banner.startsAt.isNullOrBlank() || !banner.endsAt.isNullOrBlank()) {
                        StatusPill("Scheduled")
                    }
                }
            }
            if (busy) {
                Text("Working…", style = MaterialTheme.typography.labelSmall, color = HmxColor.InkTertiary)
            } else {
                GlassIconButton(
                    icon = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete ${banner.title}",
                    onClick = onDelete,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Announcements
// ---------------------------------------------------------------------------

data class AdminAnnouncementsUi(
    val loading: Boolean = true,
    val items: List<Announcement> = emptyList(),
    val saving: Boolean = false,
    val deletingId: String? = null,
    val error: String? = null,
    val actionError: String? = null,
    val message: String? = null,
)

@HiltViewModel
class AdminAnnouncementsViewModel @Inject constructor(
    private val admin: AdminRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(AdminAnnouncementsUi())
    val ui: StateFlow<AdminAnnouncementsUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.items.isEmpty(), error = null) }
            runCatching { admin.announcements() }
                .onSuccess { list -> _ui.update { it.copy(loading = false, items = list) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    fun save(
        id: String?,
        title: String,
        body: String,
        published: Boolean,
        imageUrl: String?,
        image: PickedImage?,
    ) {
        if (_ui.value.saving) return
        viewModelScope.launch {
            _ui.update { it.copy(saving = true, actionError = null, message = null) }
            runCatching {
                val payload = buildJsonObject {
                    put("title", title.trim())
                    put("body", body.trim())
                    put("is_published", published)
                }
                var saved = admin.upsertAnnouncement(id, payload)
                if (image != null) {
                    val url = admin.uploadAnnouncementImage(saved.id, image)
                    saved = admin.upsertAnnouncement(
                        saved.id,
                        buildJsonObject {
                            put("title", title.trim())
                            put("body", body.trim())
                            put("is_published", published)
                            put("image_url", url)
                        },
                    )
                } else if (imageUrl.isNullOrBlank() && id != null) {
                    // Explicitly clear a previously attached image.
                    saved = admin.upsertAnnouncement(
                        saved.id,
                        buildJsonObject {
                            put("title", title.trim())
                            put("body", body.trim())
                            put("is_published", published)
                            put("image_url", "")
                        },
                    )
                }
                saved
            }
                .onSuccess {
                    _ui.update { it.copy(saving = false, message = "Announcement saved.") }
                    load()
                }
                .onFailure { e -> _ui.update { it.copy(saving = false, actionError = e.hmxMessage()) } }
        }
    }

    fun delete(id: String) {
        if (_ui.value.deletingId != null) return
        viewModelScope.launch {
            _ui.update { it.copy(deletingId = id, actionError = null, message = null) }
            runCatching { admin.deleteAnnouncement(id) }
                .onSuccess {
                    _ui.update { it.copy(deletingId = null, message = "Announcement deleted.") }
                    load()
                }
                .onFailure { e ->
                    _ui.update { it.copy(deletingId = null, actionError = e.hmxMessage()) }
                }
        }
    }

    fun clearMessage() = _ui.update { it.copy(message = null, actionError = null) }
}

@Composable
fun AdminAnnouncementsScreen(
    nav: NavHostController,
    viewModel: AdminAnnouncementsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var published by remember { mutableStateOf(false) }
    var imageUrl by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<PickedImage?>(null) }
    var confirmDelete by remember { mutableStateOf<Announcement?>(null) }
    val (picker, launchPicker) = rememberPickedImage()

    LaunchedEffect(picker) { picker?.let { picked = it } }

    fun begin(item: Announcement?) {
        editId = item?.id
        title = item?.title.orEmpty()
        body = item?.body.orEmpty()
        published = item?.isPublished ?: false
        imageUrl = item?.imageUrl
        picked = null
        editing = true
    }

    HmxPage(
        title = "Announcements",
        onBack = { nav.popBackStack() },
        actions = {
            GlassIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "Add announcement",
                onClick = { begin(null) },
            )
        },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ui.message != null) item { InlineSuccess(ui.message!!) }
            if (ui.actionError != null) item { InlineError(ui.actionError!!) }

            if (editing) {
                item {
                    GlassSectionHeader(if (editId == null) "New announcement" else "Edit announcement")
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(HmxDimens.cardPadding),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            GlassTextField(
                                value = title,
                                onValueChange = { title = it.take(120) },
                                label = "Title",
                                placeholder = "Server maintenance",
                            )
                            GlassTextField(
                                value = body,
                                onValueChange = { body = it.take(2000) },
                                label = "Body",
                                maxLines = 5,
                            )
                            ArtworkRow(
                                label = "Announcement image (optional)",
                                url = picked?.uri?.toString() ?: imageUrl,
                                square = false,
                                onPick = launchPicker,
                                onRemove = {
                                    picked = null
                                    imageUrl = null
                                },
                            )
                            if (editId == null) {
                                InlineNotice("Save the announcement first, then reopen it to upload an image.")
                            }
                            SwitchRow("Published", published) { published = it }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GlassButton(
                                    text = if (ui.saving) "Saving…" else "Save",
                                    onClick = {
                                        viewModel.save(
                                            id = editId,
                                            title = title,
                                            body = body,
                                            published = published,
                                            imageUrl = imageUrl,
                                            image = picked,
                                        )
                                    },
                                    enabled = title.isNotBlank() &&
                                        body.isNotBlank() &&
                                        !ui.saving,
                                    variant = GlassButtonVariant.Ink,
                                    modifier = Modifier.weight(1f),
                                )
                                GlassButton(
                                    text = "Cancel",
                                    onClick = {
                                        editing = false
                                        picked = null
                                        viewModel.clearMessage()
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
                ui.loading -> item { LoadingBlock("Loading announcements") }
                ui.error != null -> item { InlineError(ui.error!!, onRetry = viewModel::load) }
                ui.items.isEmpty() -> item {
                    EmptyBlock(
                        title = "No announcements",
                        message = "Published announcements appear in the Home ticker and the news list.",
                        icon = Icons.Rounded.Campaign,
                        actionLabel = "Add announcement",
                        onAction = { begin(null) },
                    )
                }
                else -> items(ui.items, key = { it.id }) { announcement ->
                    AnnouncementAdminRow(
                        announcement = announcement,
                        busy = ui.deletingId == announcement.id,
                        onEdit = { begin(announcement) },
                        onDelete = { confirmDelete = announcement },
                    )
                }
            }
        }
    }

    if (confirmDelete != null) {
        ConfirmDialog(
            title = "Delete ${confirmDelete!!.title}?",
            message = "The announcement will be removed from the ticker and the news list.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.delete(confirmDelete!!.id)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun AnnouncementAdminRow(
    announcement: Announcement,
    busy: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    GlassSurface(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onEdit)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    announcement.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = HmxColor.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    Format.dateTime(announcement.publishedAt ?: announcement.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = HmxColor.InkTertiary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(
                        if (announcement.isPublished) "Published" else "Draft",
                        filled = announcement.isPublished,
                    )
                    if (!announcement.imageUrl.isNullOrBlank()) StatusPill("Image")
                }
            }
            if (busy) {
                Text("Working…", style = MaterialTheme.typography.labelSmall, color = HmxColor.InkTertiary)
            } else {
                GlassIconButton(
                    icon = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete ${announcement.title}",
                    onClick = onDelete,
                )
            }
        }
    }
}
