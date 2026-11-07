package com.hmx.arena.ui.admin

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.hmx.arena.core.designsystem.GlassButton
import com.hmx.arena.core.designsystem.GlassButtonVariant
import com.hmx.arena.core.designsystem.GlassSectionHeader
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.designsystem.HmxShapes
import com.hmx.arena.core.model.LookupOption
import com.hmx.arena.core.model.Match
import com.hmx.arena.core.model.Tournament
import com.hmx.arena.core.util.Format
import com.hmx.arena.data.repository.AdminRepository
import com.hmx.arena.ui.common.ConfirmDialog
import com.hmx.arena.ui.common.GlassTextField
import com.hmx.arena.ui.common.HmxPage
import com.hmx.arena.ui.common.InlineError
import com.hmx.arena.ui.common.InlineNotice
import com.hmx.arena.ui.common.InlineSuccess
import com.hmx.arena.ui.common.LoadingBlock
import com.hmx.arena.ui.common.PickedImage
import com.hmx.arena.ui.common.hmxMessage
import com.hmx.arena.ui.common.rememberPickedImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.roundToLong

// ---------------------------------------------------------------------------
// Shared form helpers
// ---------------------------------------------------------------------------

private val GAMES = listOf("free_fire" to "Free Fire", "bgmi" to "BGMI", "cod_mobile" to "COD Mobile", "valorant" to "Valorant")
private val TOURNAMENT_STATUSES = listOf(
    "draft" to "Draft",
    "registration_open" to "Registration open",
    "registration_closed" to "Registration closed",
    "live" to "Live",
    "completed" to "Completed",
    "cancelled" to "Cancelled",
)
private val MATCH_STATUSES = listOf(
    "scheduled" to "Scheduled",
    "check_in" to "Check-in",
    "live" to "Live",
    "completed" to "Completed",
    "cancelled" to "Cancelled",
)

private fun rupees(paise: Long): String = if (paise <= 0) "0" else (paise / 100.0).toString().trimEnd('0').trimEnd('.')

private fun paise(input: String): Long =
    ((input.trim().toDoubleOrNull() ?: 0.0) * 100).roundToLong().coerceAtLeast(0)

private fun isoToInput(iso: String?): String = Format.dateTime(iso)

private val isoFormatter = DateTimeFormatter.ISO_INSTANT

@Composable
private fun DateTimeField(
    label: String,
    value: String?,
    onChange: (String?) -> Unit,
    optional: Boolean = false,
) {
    val context = LocalContext.current
    val zoned = remember(value) {
        Format.parseInstant(value)?.atZone(ZoneId.systemDefault()) ?: ZonedDateTime.now()
    }
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = HmxColor.InkTertiary)
        Spacer(Modifier.height(6.dp))
        GlassSurface(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) {
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        val picked = ZonedDateTime.of(
                                            year, month + 1, day, hour, minute, 0, 0,
                                            ZoneId.systemDefault(),
                                        )
                                        onChange(isoFormatter.format(picked.toInstant()))
                                    },
                                    zoned.hour,
                                    zoned.minute,
                                    false,
                                ).show()
                            },
                            zoned.year,
                            zoned.monthValue - 1,
                            zoned.dayOfMonth,
                        ).show()
                    }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = HmxColor.InkTertiary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (value == null) (if (optional) "Not set" else "Choose date and time")
                    else isoToInput(value),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (value == null) HmxColor.InkTertiary else HmxColor.Ink,
                    modifier = Modifier.weight(1f),
                )
                if (value != null) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clickable(role = Role.Button) { onChange(null) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Clear $label",
                            tint = HmxColor.InkTertiary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = HmxColor.InkTertiary)
        Spacer(Modifier.height(6.dp))
        Box {
            GlassSurface(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { expanded = true }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        options.firstOrNull { it.first == value }?.second ?: value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = HmxColor.Ink,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = HmxColor.InkTertiary)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (key, title) ->
                    DropdownMenuItem(
                        text = { Text(title) },
                        onClick = {
                            onSelect(key)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LookupDropdown(
    label: String,
    selectedId: String?,
    options: List<LookupOption>,
    noneLabel: String = "None",
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId }
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = HmxColor.InkTertiary)
        Spacer(Modifier.height(6.dp))
        Box {
            GlassSurface(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { expanded = true }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        selected?.name ?: noneLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected == null) HmxColor.InkTertiary else HmxColor.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected != null && selected.isArchived) {
                        Text("archived", style = MaterialTheme.typography.labelSmall, color = HmxColor.InkTertiary)
                    }
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = HmxColor.InkTertiary)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(noneLabel) },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    },
                )
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option.name + when {
                                    option.isArchived -> " (archived)"
                                    !option.isActive -> " (inactive)"
                                    else -> ""
                                },
                            )
                        },
                        onClick = {
                            onSelect(option.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** Keeps only rows that can still be assigned, plus the currently selected one. */
private fun assignable(options: List<LookupOption>, selectedId: String?): List<LookupOption> =
    options.filter { it.available || it.id == selectedId }

@Composable
private fun MoneyField(label: String, value: String, onChange: (String) -> Unit) {
    GlassTextField(
        value = value,
        onValueChange = { raw ->
            onChange(raw.filter { it.isDigit() || it == '.' })
        },
        label = label,
        keyboardType = KeyboardType.Decimal,
        placeholder = "0",
    )
}

// ---------------------------------------------------------------------------
// Tournament create / edit
// ---------------------------------------------------------------------------

data class AdminTournamentForm(
    val title: String = "",
    val slug: String = "",
    val game: String = "free_fire",
    val categoryId: String? = null,
    val gameModeId: String? = null,
    val tournamentTypeId: String? = null,
    val map: String = "",
    val region: String = "IN",
    val description: String = "",
    val format: String = "",
    val version: String = "1.0",
    val entryFee: String = "0",
    val prizePool: String = "0",
    val perKill: String = "0",
    val maxSlots: String = "64",
    val minTeamSize: String = "1",
    val maxTeamSize: String = "1",
    val status: String = "draft",
    val registrationOpensAt: String? = Instant.now().toString(),
    val registrationClosesAt: String? = null,
    val startsAt: String? = null,
    val endsAt: String? = null,
    val bannerUrl: String? = null,
    val logoUrl: String? = null,
    val isFeatured: Boolean = false,
    val slotsFilled: Int = 0,
)

data class AdminTournamentEditUi(
    val loading: Boolean = true,
    val id: String? = null,
    val form: AdminTournamentForm = AdminTournamentForm(),
    val categories: List<LookupOption> = emptyList(),
    val modes: List<LookupOption> = emptyList(),
    val types: List<LookupOption> = emptyList(),
    val saving: Boolean = false,
    val error: String? = null,
    val actionError: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class AdminTournamentEditViewModel @Inject constructor(
    private val admin: AdminRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val routeId: String = savedStateHandle.get<String>("id") ?: "new"
    private val existingId: String? = routeId.takeIf { it != "new" }

    private val _ui = MutableStateFlow(AdminTournamentEditUi(id = existingId))
    val ui: StateFlow<AdminTournamentEditUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching {
                val categories = admin.categories()
                val modes = admin.gameModes()
                val types = admin.tournamentTypes()
                val existing = existingId?.let { id ->
                    admin.tournaments().firstOrNull { it.id == id }
                        ?: throw IllegalStateException("This tournament could not be found.")
                }
                Triple(categories, modes, types) to existing
            }
                .onSuccess { (lookups, existing) ->
                    val (categories, modes, types) = lookups
                    _ui.update {
                        it.copy(
                            loading = false,
                            categories = categories,
                            modes = modes,
                            types = types,
                            form = existing?.let(::formOf) ?: it.form,
                        )
                    }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    fun setForm(transform: (AdminTournamentForm) -> AdminTournamentForm) {
        _ui.update { base -> base.copy(form = transform(base.form), actionError = null) }
    }

    fun save(banner: PickedImage?, logo: PickedImage?) {
        val state = _ui.value
        if (state.saving) return
        val form = state.form
        val problem = validate(form)
        if (problem != null) {
            _ui.update { it.copy(actionError = problem) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(saving = true, actionError = null) }
            runCatching {
                var saved = admin.upsertTournament(existingId, payload(form))
                if (banner != null) {
                    val url = admin.uploadTournamentBanner(saved.id, banner)
                    saved = admin.upsertTournament(saved.id, payload(form.copy(bannerUrl = url)))
                }
                if (logo != null) {
                    val url = admin.uploadTournamentLogo(saved.id, logo)
                    saved = admin.upsertTournament(saved.id, payload(form.copy(logoUrl = url)))
                }
                saved
            }
                .onSuccess {
                    _ui.update { it.copy(saving = false, saved = true) }
                }
                .onFailure { e -> _ui.update { it.copy(saving = false, actionError = e.hmxMessage()) } }
        }
    }

    private fun validate(form: AdminTournamentForm): String? {
        if (form.title.trim().length < 3) return "Enter a title of at least 3 characters."
        if (form.title.trim().length > 80) return "Keep the title under 80 characters."
        val slots = form.maxSlots.toIntOrNull() ?: return "Enter the maximum number of slots."
        if (slots <= 0) return "Capacity must be at least one slot."
        if (slots < form.slotsFilled) {
            return "Capacity cannot be lower than the ${form.slotsFilled} registered players."
        }
        val opens = Format.parseInstant(form.registrationOpensAt)
        val closes = Format.parseInstant(form.registrationClosesAt)
        val starts = Format.parseInstant(form.startsAt)
        val ends = Format.parseInstant(form.endsAt)
        if (opens == null) return "Choose when registration opens."
        if (closes == null) return "Choose when registration closes."
        if (starts == null) return "Choose when the tournament starts."
        if (!closes.isAfter(opens)) return "Registration must close after it opens."
        if (starts.isBefore(closes)) return "The start time must be after registration closes."
        if (ends != null && ends.isBefore(starts)) return "The end time must be after the start time."
        return null
    }

    private fun payload(form: AdminTournamentForm): JsonObject = buildJsonObject {
        put("title", form.title.trim())
        put("slug", form.slug.trim())
        put("game", form.game)
        put("category_id", form.categoryId ?: "")
        put("game_mode_id", form.gameModeId ?: "")
        put("tournament_type_id", form.tournamentTypeId ?: "")
        put("map", form.map.trim())
        put("region", form.region.trim().ifBlank { "IN" })
        put("description", form.description.trim())
        put("format", form.format.trim())
        put("version", form.version.trim().ifBlank { "1.0" })
        put("entry_fee_paise", paise(form.entryFee).toInt())
        put("prize_pool_paise", paise(form.prizePool).toInt())
        put("per_kill_paise", paise(form.perKill).toInt())
        put("max_slots", form.maxSlots.toIntOrNull() ?: 1)
        put("min_team_size", form.minTeamSize.toIntOrNull() ?: 1)
        put("max_team_size", form.maxTeamSize.toIntOrNull() ?: 1)
        put("status", form.status)
        put("registration_opens_at", form.registrationOpensAt ?: "")
        put("registration_closes_at", form.registrationClosesAt ?: "")
        put("starts_at", form.startsAt ?: "")
        put("ends_at", form.endsAt ?: "")
        put("banner_url", form.bannerUrl ?: "")
        put("logo_url", form.logoUrl ?: "")
        put("is_featured", form.isFeatured)
    }

    private fun formOf(t: Tournament): AdminTournamentForm = AdminTournamentForm(
        title = t.title,
        slug = t.slug,
        game = t.game,
        categoryId = t.categoryId,
        gameModeId = t.gameModeId,
        tournamentTypeId = t.tournamentTypeId,
        map = t.map.orEmpty(),
        region = t.region,
        description = t.description,
        format = t.format.orEmpty(),
        version = t.version,
        entryFee = rupees(t.entryFeePaise),
        prizePool = rupees(t.prizePoolPaise),
        perKill = rupees(t.perKillPaise),
        maxSlots = t.maxSlots.toString(),
        minTeamSize = t.minTeamSize.toString(),
        maxTeamSize = t.maxTeamSize.toString(),
        status = t.status,
        registrationOpensAt = t.registrationOpensAt,
        registrationClosesAt = t.registrationClosesAt,
        startsAt = t.startsAt,
        endsAt = t.endsAt,
        bannerUrl = t.bannerUrl,
        logoUrl = t.logoUrl,
        isFeatured = t.isFeatured,
        slotsFilled = t.slotsFilled,
    )
}

@Composable
fun AdminTournamentEditScreen(
    nav: NavHostController,
    viewModel: AdminTournamentEditViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val form = ui.form
    var banner by remember { mutableStateOf<PickedImage?>(null) }
    var logo by remember { mutableStateOf<PickedImage?>(null) }
    val (bannerPicked, pickBanner) = rememberPickedImage()
    val (logoPicked, pickLogo) = rememberPickedImage()

    LaunchedEffect(bannerPicked) { bannerPicked?.let { banner = it } }
    LaunchedEffect(logoPicked) { logoPicked?.let { logo = it } }
    LaunchedEffect(ui.saved) { if (ui.saved) nav.popBackStack() }

    HmxPage(
        title = if (ui.id == null) "New tournament" else "Edit tournament",
        onBack = { nav.popBackStack() },
    ) {
        when {
            ui.loading -> LoadingBlock("Loading tournament")
            ui.error != null -> InlineError(ui.error!!, onRetry = viewModel::load)
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (ui.actionError != null) item { InlineError(ui.actionError!!) }

                item {
                    GlassSectionHeader("Identity")
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(HmxDimens.cardPadding),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            GlassTextField(
                                value = form.title,
                                onValueChange = { v -> viewModel.setForm { it.copy(title = v) } },
                                label = "Title",
                                placeholder = "HMX Weekend Clash",
                            )
                            GlassTextField(
                                value = form.slug,
                                onValueChange = { v ->
                                    viewModel.setForm { it.copy(slug = v.lowercase().filter { c -> c.isLetterOrDigit() || c == '-' }) }
                                },
                                label = "Identifier (optional)",
                                placeholder = "hmx-weekend-clash",
                            )
                            DropdownField("Game", form.game, GAMES) { v ->
                                viewModel.setForm { it.copy(game = v) }
                            }
                            LookupDropdown(
                                label = "Category",
                                selectedId = form.categoryId,
                                options = assignable(ui.categories, form.categoryId),
                                onSelect = { v -> viewModel.setForm { it.copy(categoryId = v) } },
                            )
                            LookupDropdown(
                                label = "Game mode",
                                selectedId = form.gameModeId,
                                options = assignable(ui.modes, form.gameModeId),
                                onSelect = { v -> viewModel.setForm { it.copy(gameModeId = v) } },
                            )
                            LookupDropdown(
                                label = "Tournament type",
                                selectedId = form.tournamentTypeId,
                                options = assignable(ui.types, form.tournamentTypeId),
                                onSelect = { v -> viewModel.setForm { it.copy(tournamentTypeId = v) } },
                            )
                            GlassTextField(
                                value = form.map,
                                onValueChange = { v -> viewModel.setForm { it.copy(map = v) } },
                                label = "Map",
                                placeholder = "Bermuda",
                            )
                            GlassTextField(
                                value = form.region,
                                onValueChange = { v -> viewModel.setForm { it.copy(region = v.uppercase()) } },
                                label = "Region",
                            )
                            GlassTextField(
                                value = form.description,
                                onValueChange = { v -> viewModel.setForm { it.copy(description = v) } },
                                label = "Description",
                                maxLines = 4,
                            )
                        }
                    }
                }

                item {
                    GlassSectionHeader("Format")
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(HmxDimens.cardPadding),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            GlassTextField(
                                value = form.format,
                                onValueChange = { v -> viewModel.setForm { it.copy(format = v) } },
                                label = "Format",
                                placeholder = "Battle royale, 3 rounds",
                            )
                            GlassTextField(
                                value = form.version,
                                onValueChange = { v -> viewModel.setForm { it.copy(version = v) } },
                                label = "Version",
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.weight(1f)) {
                                    GlassTextField(
                                        value = form.minTeamSize,
                                        onValueChange = { v ->
                                            viewModel.setForm { it.copy(minTeamSize = v.filter(Char::isDigit)) }
                                        },
                                        label = "Min team",
                                        keyboardType = KeyboardType.Number,
                                    )
                                }
                                Box(Modifier.weight(1f)) {
                                    GlassTextField(
                                        value = form.maxTeamSize,
                                        onValueChange = { v ->
                                            viewModel.setForm { it.copy(maxTeamSize = v.filter(Char::isDigit)) }
                                        },
                                        label = "Max team",
                                        keyboardType = KeyboardType.Number,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    GlassSectionHeader("Rewards and entry")
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(HmxDimens.cardPadding),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MoneyField("Prize pool (₹)", form.prizePool) { v ->
                                viewModel.setForm { it.copy(prizePool = v) }
                            }
                            MoneyField("Per kill (₹)", form.perKill) { v ->
                                viewModel.setForm { it.copy(perKill = v) }
                            }
                            MoneyField("Entry fee (₹)", form.entryFee) { v ->
                                viewModel.setForm { it.copy(entryFee = v) }
                            }
                            InlineNotice("An entry fee above zero is saved as a paid tournament. Zero is free entry.")
                        }
                    }
                }

                item {
                    GlassSectionHeader("Capacity")
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(HmxDimens.cardPadding),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            GlassTextField(
                                value = form.maxSlots,
                                onValueChange = { v ->
                                    viewModel.setForm { it.copy(maxSlots = v.filter(Char::isDigit)) }
                                },
                                label = "Maximum slots",
                                keyboardType = KeyboardType.Number,
                            )
                            if (ui.id != null) {
                                InlineNotice("${form.slotsFilled} slots are already confirmed. Capacity cannot drop below that.")
                            }
                        }
                    }
                }

                item {
                    GlassSectionHeader("Schedule")
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(HmxDimens.cardPadding),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            DateTimeField("Registration opens", form.registrationOpensAt) { v ->
                                viewModel.setForm { it.copy(registrationOpensAt = v) }
                            }
                            DateTimeField("Registration closes", form.registrationClosesAt) { v ->
                                viewModel.setForm { it.copy(registrationClosesAt = v) }
                            }
                            DateTimeField("Starts", form.startsAt) { v ->
                                viewModel.setForm { it.copy(startsAt = v) }
                            }
                            DateTimeField("Ends (optional)", form.endsAt, optional = true) { v ->
                                viewModel.setForm { it.copy(endsAt = v) }
                            }
                        }
                    }
                }

                item {
                    GlassSectionHeader("Status")
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(HmxDimens.cardPadding),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            DropdownField("Tournament status", form.status, TOURNAMENT_STATUSES) { v ->
                                viewModel.setForm { it.copy(status = v) }
                            }
                            SwitchRow("Feature on the home screen", form.isFeatured) { v ->
                                viewModel.setForm { it.copy(isFeatured = v) }
                            }
                        }
                    }
                }

                item {
                    GlassSectionHeader("Artwork")
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(HmxDimens.cardPadding),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            ArtworkRow(
                                label = "Banner (16:9)",
                                url = banner?.uri?.toString() ?: form.bannerUrl,
                                square = false,
                                onPick = pickBanner,
                                onRemove = {
                                    banner = null
                                    viewModel.setForm { it.copy(bannerUrl = null) }
                                },
                            )
                            ArtworkRow(
                                label = "Logo (1:1)",
                                url = logo?.uri?.toString() ?: form.logoUrl,
                                square = true,
                                onPick = pickLogo,
                                onRemove = {
                                    logo = null
                                    viewModel.setForm { it.copy(logoUrl = null) }
                                },
                            )
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton(
                            text = if (ui.saving) "Saving…" else "Save tournament",
                            onClick = { viewModel.save(banner, logo) },
                            enabled = !ui.saving,
                            variant = GlassButtonVariant.Ink,
                            modifier = Modifier.weight(1f),
                        )
                        GlassButton(
                            text = "Cancel",
                            onClick = { nav.popBackStack() },
                            variant = GlassButtonVariant.Secondary,
                            enabled = !ui.saving,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Match create / edit
// ---------------------------------------------------------------------------

data class AdminMatchForm(
    val tournamentId: String? = null,
    val name: String = "",
    val matchNumber: String = "",
    val round: String = "1",
    val game: String = "free_fire",
    val categoryId: String? = null,
    val gameModeId: String? = null,
    val tournamentTypeId: String? = null,
    val map: String = "",
    val format: String = "",
    val version: String = "1.0",
    val status: String = "scheduled",
    val scheduledAt: String? = null,
    val checkInOpensAt: String? = null,
    val registrationOpensAt: String? = null,
    val registrationClosesAt: String? = null,
    val maxSlots: String = "",
    val prizePool: String = "0",
    val perKill: String = "0",
    val entryFee: String = "0",
    val bannerUrl: String? = null,
    val logoUrl: String? = null,
    val isPublished: Boolean = false,
    val currentStatus: String = "scheduled",
)

data class AdminMatchEditUi(
    val loading: Boolean = true,
    val id: String? = null,
    val form: AdminMatchForm = AdminMatchForm(),
    val categories: List<LookupOption> = emptyList(),
    val modes: List<LookupOption> = emptyList(),
    val types: List<LookupOption> = emptyList(),
    val tournaments: List<Tournament> = emptyList(),
    val saving: Boolean = false,
    val error: String? = null,
    val actionError: String? = null,
    val saved: Boolean = false,
)

private data class MatchFormBundle(
    val categories: List<LookupOption>,
    val modes: List<LookupOption>,
    val types: List<LookupOption>,
    val tournaments: List<Tournament>,
    val existing: Match?,
)

@HiltViewModel
class AdminMatchEditViewModel @Inject constructor(
    private val admin: AdminRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val routeId: String = savedStateHandle.get<String>("id") ?: "new"
    private val existingId: String? = routeId.takeIf { it != "new" }

    private val _ui = MutableStateFlow(AdminMatchEditUi(id = existingId))
    val ui: StateFlow<AdminMatchEditUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching {
                val categories = admin.categories()
                val modes = admin.gameModes()
                val types = admin.tournamentTypes()
                val tournaments = admin.tournaments()
                val existing = existingId?.let { id ->
                    admin.matches().firstOrNull { it.id == id }
                        ?: throw IllegalStateException("This match could not be found.")
                }
                MatchFormBundle(categories, modes, types, tournaments, existing)
            }
                .onSuccess { bundle ->
                    _ui.update {
                        it.copy(
                            loading = false,
                            categories = bundle.categories,
                            modes = bundle.modes,
                            types = bundle.types,
                            tournaments = bundle.tournaments,
                            form = bundle.existing?.let(::formOf) ?: it.form,
                        )
                    }
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.hmxMessage()) } }
        }
    }

    fun setForm(transform: (AdminMatchForm) -> AdminMatchForm) {
        _ui.update { base -> base.copy(form = transform(base.form), actionError = null) }
    }

    fun save(confirmLocked: Boolean, banner: PickedImage?, logo: PickedImage?) {
        val state = _ui.value
        if (state.saving) return
        val form = state.form
        val problem = validate(form)
        if (problem != null) {
            _ui.update { it.copy(actionError = problem) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(saving = true, actionError = null) }
            runCatching {
                var saved = admin.upsertMatch(existingId, payload(form), confirmLocked)
                if (banner != null) {
                    val url = admin.uploadMatchBanner(saved.id, banner)
                    saved = admin.upsertMatch(saved.id, payload(form.copy(bannerUrl = url)), confirmLocked)
                }
                if (logo != null) {
                    val url = admin.uploadMatchLogo(saved.id, logo)
                    saved = admin.upsertMatch(saved.id, payload(form.copy(logoUrl = url)), confirmLocked)
                }
                saved
            }
                .onSuccess { _ui.update { it.copy(saving = false, saved = true) } }
                .onFailure { e -> _ui.update { it.copy(saving = false, actionError = e.hmxMessage()) } }
        }
    }

    private fun validate(form: AdminMatchForm): String? {
        if (form.name.trim().length < 3) return "Enter a match name of at least 3 characters."
        if (form.scheduledAt == null) return "Choose when the match starts."
        val slots = form.maxSlots.toIntOrNull()
        if (form.maxSlots.isNotBlank() && (slots == null || slots <= 0)) {
            return "Enter a positive slot count, or leave capacity empty for unlimited."
        }
        val opens = Format.parseInstant(form.registrationOpensAt)
        val closes = Format.parseInstant(form.registrationClosesAt)
        if (opens != null && closes != null && !closes.isAfter(opens)) {
            return "Registration must close after it opens."
        }
        return null
    }

    private fun payload(form: AdminMatchForm): JsonObject = buildJsonObject {
        put("tournament_id", form.tournamentId ?: "")
        put("name", form.name.trim())
        put("match_number", form.matchNumber.trim())
        put("round", form.round.toIntOrNull() ?: 1)
        put("game", form.game)
        put("category_id", form.categoryId ?: "")
        put("game_mode_id", form.gameModeId ?: "")
        put("tournament_type_id", form.tournamentTypeId ?: "")
        put("map", form.map.trim())
        put("format", form.format.trim())
        put("version", form.version.trim().ifBlank { "1.0" })
        put("status", form.status)
        put("scheduled_at", form.scheduledAt ?: "")
        put("check_in_opens_at", form.checkInOpensAt ?: "")
        put("registration_opens_at", form.registrationOpensAt ?: "")
        put("registration_closes_at", form.registrationClosesAt ?: "")
        put("max_slots", form.maxSlots.trim())
        put("prize_pool_paise", paise(form.prizePool).toInt())
        put("per_kill_paise", paise(form.perKill).toInt())
        put("entry_fee_paise", paise(form.entryFee).toInt())
        put("banner_url", form.bannerUrl ?: "")
        put("logo_url", form.logoUrl ?: "")
        put("is_published", form.isPublished)
    }

    private fun formOf(m: Match): AdminMatchForm = AdminMatchForm(
        tournamentId = m.tournamentId,
        name = m.name,
        matchNumber = m.matchNumber.orEmpty(),
        round = m.round.toString(),
        game = m.game ?: "free_fire",
        categoryId = m.categoryId,
        gameModeId = m.gameModeId,
        tournamentTypeId = m.tournamentTypeId,
        map = m.map.orEmpty(),
        format = m.format.orEmpty(),
        version = m.version,
        status = m.status,
        scheduledAt = m.scheduledAt,
        checkInOpensAt = m.checkInOpensAt,
        registrationOpensAt = m.registrationOpensAt,
        registrationClosesAt = m.registrationClosesAt,
        maxSlots = m.maxSlots?.toString().orEmpty(),
        prizePool = rupees(m.prizePoolPaise),
        perKill = rupees(m.perKillPaise),
        entryFee = rupees(m.entryFeePaise),
        bannerUrl = m.bannerUrl,
        logoUrl = m.logoUrl,
        isPublished = m.isPublished,
        currentStatus = m.status,
    )
}

@Composable
fun AdminMatchEditScreen(
    nav: NavHostController,
    viewModel: AdminMatchEditViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val form = ui.form
    var banner by remember { mutableStateOf<PickedImage?>(null) }
    var logo by remember { mutableStateOf<PickedImage?>(null) }
    var confirmLocked by remember { mutableStateOf(false) }
    val (bannerPicked, pickBanner) = rememberPickedImage()
    val (logoPicked, pickLogo) = rememberPickedImage()

    LaunchedEffect(bannerPicked) { bannerPicked?.let { banner = it } }
    LaunchedEffect(logoPicked) { logoPicked?.let { logo = it } }
    LaunchedEffect(ui.saved) { if (ui.saved) nav.popBackStack() }

    val locked = ui.id != null && form.currentStatus in setOf("live", "completed")

    HmxPage(
        title = if (ui.id == null) "New match" else "Edit match",
        onBack = { nav.popBackStack() },
    ) {
        when {
            ui.loading -> LoadingBlock("Loading match")
            ui.error != null -> InlineError(ui.error!!, onRetry = viewModel::load)
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (ui.actionError != null) item { InlineError(ui.actionError!!) }
                if (locked) {
                    item {
                        InlineNotice(
                            "This match is ${form.currentStatus}. Changing the schedule, rewards or capacity needs confirmation before saving.",
                        )
                    }
                }

                item {
                    GlassSectionHeader("Match")
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(HmxDimens.cardPadding),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            LookupDropdown(
                                label = "Tournament (optional)",
                                selectedId = form.tournamentId,
                                options = ui.tournaments.map {
                                    LookupOption(id = it.id, name = it.title, isActive = true)
                                },
                                noneLabel = "Independent match",
                                onSelect = { id ->
                                    val parent = ui.tournaments.firstOrNull { it.id == id }
                                    viewModel.setForm {
                                        it.copy(
                                            tournamentId = id,
                                            categoryId = it.categoryId ?: parent?.categoryId,
                                            gameModeId = it.gameModeId ?: parent?.gameModeId,
                                            tournamentTypeId = it.tournamentTypeId ?: parent?.tournamentTypeId,
                                            game = parent?.game ?: it.game,
                                        )
                                    }
                                },
                            )
                            GlassTextField(
                                value = form.name,
                                onValueChange = { v -> viewModel.setForm { it.copy(name = v) } },
                                label = "Name",
                                placeholder = "Match 1",
                            )
                            GlassTextField(
                                value = form.matchNumber,
                                onValueChange = { v -> viewModel.setForm { it.copy(matchNumber = v) } },
                                label = "Match number",
                                placeholder = "M1",
                            )
                            GlassTextField(
                                value = form.round,
                                onValueChange = { v ->
                                    viewModel.setForm { it.copy(round = v.filter(Char::isDigit)) }
                                },
                                label = "Round",
                                keyboardType = KeyboardType.Number,
                            )
                            DropdownField("Game", form.game, GAMES) { v ->
                                viewModel.setForm { it.copy(game = v) }
                            }
                            LookupDropdown(
                                label = "Category",
                                selectedId = form.categoryId,
                                options = assignable(ui.categories, form.categoryId),
                                onSelect = { v -> viewModel.setForm { it.copy(categoryId = v) } },
                            )
                            LookupDropdown(
                                label = "Game mode",
                                selectedId = form.gameModeId,
                                options = assignable(ui.modes, form.gameModeId),
                                onSelect = { v -> viewModel.setForm { it.copy(gameModeId = v) } },
                            )
                            LookupDropdown(
                                label = "Tournament type",
                                selectedId = form.tournamentTypeId,
                                options = assignable(ui.types, form.tournamentTypeId),
                                onSelect = { v -> viewModel.setForm { it.copy(tournamentTypeId = v) } },
                            )
                            GlassTextField(
                                value = form.map,
                                onValueChange = { v -> viewModel.setForm { it.copy(map = v) } },
                                label = "Map",
                                placeholder = "Bermuda",
                            )
                            GlassTextField(
                                value = form.format,
                                onValueChange = { v -> viewModel.setForm { it.copy(format = v) } },
                                label = "Format",
                                placeholder = "Squad, best of 3",
                            )
                            GlassTextField(
                                value = form.version,
                                onValueChange = { v -> viewModel.setForm { it.copy(version = v) } },
                                label = "Version",
                            )
                            DropdownField("Match status", form.status, MATCH_STATUSES) { v ->
                                viewModel.setForm { it.copy(status = v) }
                            }
                            SwitchRow("Published to players", form.isPublished) { v ->
                                viewModel.setForm { it.copy(isPublished = v) }
                            }
                        }
                    }
                }

                item {
                    GlassSectionHeader("Schedule and capacity")
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(HmxDimens.cardPadding),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            DateTimeField("Starts", form.scheduledAt) { v ->
                                viewModel.setForm { it.copy(scheduledAt = v) }
                            }
                            DateTimeField("Check-in opens (optional)", form.checkInOpensAt, optional = true) { v ->
                                viewModel.setForm { it.copy(checkInOpensAt = v) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.weight(1f)) {
                                    DateTimeField("Registration opens", form.registrationOpensAt, optional = true) { v ->
                                        viewModel.setForm { it.copy(registrationOpensAt = v) }
                                    }
                                }
                                Box(Modifier.weight(1f)) {
                                    DateTimeField("Registration closes", form.registrationClosesAt, optional = true) { v ->
                                        viewModel.setForm { it.copy(registrationClosesAt = v) }
                                    }
                                }
                            }
                            GlassTextField(
                                value = form.maxSlots,
                                onValueChange = { v ->
                                    viewModel.setForm { it.copy(maxSlots = v.filter(Char::isDigit)) }
                                },
                                label = "Maximum slots (blank = unlimited)",
                                keyboardType = KeyboardType.Number,
                            )
                            InlineNotice("Match capacity is separate from tournament capacity: a match slot is consumed by a match registration.")
                        }
                    }
                }

                item {
                    GlassSectionHeader("Rewards and entry")
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(HmxDimens.cardPadding),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MoneyField("Prize pool (₹)", form.prizePool) { v ->
                                viewModel.setForm { it.copy(prizePool = v) }
                            }
                            MoneyField("Per kill (₹)", form.perKill) { v ->
                                viewModel.setForm { it.copy(perKill = v) }
                            }
                            MoneyField("Entry fee (₹)", form.entryFee) { v ->
                                viewModel.setForm { it.copy(entryFee = v) }
                            }
                        }
                    }
                }

                item {
                    GlassSectionHeader("Artwork")
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(HmxDimens.cardPadding),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            ArtworkRow(
                                label = "Banner (16:9)",
                                url = banner?.uri?.toString() ?: form.bannerUrl,
                                square = false,
                                onPick = pickBanner,
                                onRemove = {
                                    banner = null
                                    viewModel.setForm { it.copy(bannerUrl = null) }
                                },
                            )
                            ArtworkRow(
                                label = "Logo (1:1)",
                                url = logo?.uri?.toString() ?: form.logoUrl,
                                square = true,
                                onPick = pickLogo,
                                onRemove = {
                                    logo = null
                                    viewModel.setForm { it.copy(logoUrl = null) }
                                },
                            )
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton(
                            text = if (ui.saving) "Saving…" else "Save match",
                            onClick = {
                                if (locked) {
                                    confirmLocked = true
                                } else {
                                    viewModel.save(false, banner, logo)
                                }
                            },
                            enabled = !ui.saving,
                            variant = GlassButtonVariant.Ink,
                            modifier = Modifier.weight(1f),
                        )
                        GlassButton(
                            text = "Cancel",
                            onClick = { nav.popBackStack() },
                            variant = GlassButtonVariant.Secondary,
                            enabled = !ui.saving,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    if (confirmLocked) {
        ConfirmDialog(
            title = "Save changes to a live match?",
            message = "The schedule, rewards or capacity may already be visible to players. Confirm to apply the change.",
            confirmLabel = "Save anyway",
            onConfirm = {
                confirmLocked = false
                viewModel.save(true, banner, logo)
            },
            onDismiss = { confirmLocked = false },
        )
    }
}
