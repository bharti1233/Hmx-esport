package com.hmx.arena.ui.common

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.GlassScaffold
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.designsystem.HmxGlassLevel
import com.hmx.arena.core.designsystem.HmxLiquidGlassSurface
import com.hmx.arena.core.designsystem.HmxShapes
import com.hmx.arena.core.model.Match
import com.hmx.arena.core.model.Tournament
import com.hmx.arena.core.util.Format
import com.hmx.arena.data.remote.ApiException
import kotlinx.coroutines.delay
import java.io.IOException
import java.time.Instant

data class UiState<T>(
    val loading: Boolean = true,
    val data: T? = null,
    val error: String? = null,
    val refreshing: Boolean = false,
) {
    val hasData: Boolean get() = data != null
}

fun Throwable.hmxMessage(): String = when (this) {
    is ApiException -> userMessage
    is IOException -> "You appear to be offline. Check your connection and try again."
    else -> "Something went wrong. Please try again."
}

fun ApiException.isNetworkError(): Boolean = cause is IOException

@Composable
fun HmxPage(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    GlassScaffold(
        topBar = { HmxLiquidGlassTopBar(title = title, onBack = onBack, actions = actions) },
        bottomBar = bottomBar,
    ) {
        content()
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    enabled: Boolean = true,
    error: String? = null,
    maxLines: Int = 1,
    leadingIcon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    onImeAction: (() -> Unit)? = null,
) {
    HmxLiquidGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = HmxShapes.field,
        level = HmxGlassLevel.Control,
        fill = HmxColor.GlassControl,
        elevation = 6.dp,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label, style = MaterialTheme.typography.bodySmall) },
            placeholder = placeholder?.let { text ->
                { Text(text, style = MaterialTheme.typography.bodyMedium, color = HmxColor.InkTertiary) }
            },
            enabled = enabled,
            isError = error != null,
            supportingText = error?.let {
                { Text(it, style = MaterialTheme.typography.bodySmall, color = HmxColor.Danger) }
            },
            maxLines = maxLines,
            singleLine = maxLines == 1,
            shape = HmxShapes.field,
            textStyle = MaterialTheme.typography.bodyMedium,
            leadingIcon = leadingIcon?.let {
                { Icon(it, contentDescription = null, tint = HmxColor.InkSecondary, modifier = Modifier.size(20.dp)) }
            },
            trailingIcon = trailing,
            visualTransformation = VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
                capitalization = capitalization,
            ),
            keyboardActions = KeyboardActions(onDone = { onImeAction?.invoke() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = HmxColor.Ink,
                unfocusedTextColor = HmxColor.Ink,
                disabledTextColor = HmxColor.InkTertiary,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                errorBorderColor = HmxColor.Danger,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                cursorColor = HmxColor.Ink,
                focusedLabelColor = HmxColor.InkSecondary,
                unfocusedLabelColor = HmxColor.InkTertiary,
            ),
        )
    }
}

@Composable
fun GlassPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    HmxLiquidGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = HmxShapes.field,
        level = HmxGlassLevel.Control,
        fill = HmxColor.GlassControl,
        elevation = 6.dp,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label, style = MaterialTheme.typography.bodySmall) },
            isError = error != null,
            supportingText = error?.let {
                { Text(it, style = MaterialTheme.typography.bodySmall, color = HmxColor.Danger) }
            },
            singleLine = true,
            shape = HmxShapes.field,
            textStyle = MaterialTheme.typography.bodyMedium,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onImeAction?.invoke() }),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (visible) "Hide password" else "Show password",
                        tint = HmxColor.InkSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = HmxColor.Ink,
                unfocusedTextColor = HmxColor.Ink,
                disabledTextColor = HmxColor.InkTertiary,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                errorBorderColor = HmxColor.Danger,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                cursorColor = HmxColor.Ink,
                focusedLabelColor = HmxColor.InkSecondary,
                unfocusedLabelColor = HmxColor.InkTertiary,
            ),
        )
    }
}

@Composable
fun GlassAvatar(url: String?, name: String, size: Dp = 48.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(HmxColor.CanvasTint)
            .border(HmxDimens.hairline, HmxColor.Hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = Format.initials(name),
                style = MaterialTheme.typography.labelLarge,
                color = HmxColor.InkSecondary,
            )
        }
    }
}

@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier
            .clip(HmxShapes.pill)
            .background(if (filled) HmxColor.Ink else HmxColor.Glass)
            .border(HmxDimens.hairline, if (filled) Color.Transparent else HmxColor.Hairline, HmxShapes.pill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (filled) HmxColor.OnInk else HmxColor.InkSecondary,
            maxLines = 1,
        )
    }
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = HmxColor.InkSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = HmxColor.Ink)
    }
}

@Composable
fun GlassActionRow(
    title: String,
    icon: ImageVector? = null,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailingText: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = HmxColor.InkSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = HmxColor.Ink)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = HmxColor.InkTertiary)
            }
        }
        if (trailingText != null) {
            Text(trailingText, style = MaterialTheme.typography.bodySmall, color = HmxColor.InkTertiary)
        }
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = HmxColor.InkTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = HmxDimens.cardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun InlineError(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    GlassSurface(modifier = modifier.fillMaxWidth(), fill = HmxColor.GlassStrong) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = HmxColor.Danger, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = HmxColor.Ink,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

@Composable
fun InlineNotice(message: String, modifier: Modifier = Modifier) {
    GlassSurface(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = HmxColor.InkSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = HmxColor.InkSecondary)
        }
    }
}

@Composable
fun InlineSuccess(message: String, modifier: Modifier = Modifier) {
    GlassSurface(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = HmxColor.Ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = HmxColor.Ink)
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    HmxLiquidGlassDialog(
        visible = true,
        onDismiss = onDismiss,
        title = title,
        message = message,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        dismissLabel = "Cancel",
        confirmColor = HmxColor.Danger,
    )
}

@Composable
fun TickingCountdown(iso: String?, modifier: Modifier = Modifier) {
    var tick by remember(iso) { mutableStateOf(Instant.now()) }
    LaunchedEffect(iso) {
        while (true) {
            tick = Instant.now()
            delay(1000)
        }
    }
    val label = remember(tick, iso) { Format.countdown(iso) }
    Text(label, style = MaterialTheme.typography.labelLarge, color = HmxColor.Ink, modifier = modifier)
}

@Composable
fun LoadingBlock(label: String = "Loading", modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = HmxColor.Ink, strokeWidth = 2.dp)
        Spacer(Modifier.height(12.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = HmxColor.InkSecondary)
    }
}

@Composable
fun EmptyBlock(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(HmxColor.Glass)
                .border(HmxDimens.hairline, HmxColor.Hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = HmxColor.InkSecondary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = HmxColor.Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = HmxColor.InkSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Text(
                actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = HmxColor.Ink,
                modifier = Modifier
                    .clip(HmxShapes.pill)
                    .border(HmxDimens.hairline, HmxColor.HairlineStrong, HmxShapes.pill)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
fun TournamentCard(
    tournament: Tournament,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
    ) {
        Column {
            if (!tournament.bannerUrl.isNullOrBlank()) {
                AsyncImage(
                    model = tournament.bannerUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(124.dp),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(Modifier.padding(HmxDimens.cardPadding)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(Format.tournamentStatusLabel(tournament.status))
                    Spacer(Modifier.width(6.dp))
                    StatusPill(tournament.modeLabel)
                    Spacer(Modifier.weight(1f))
                    if (tournament.isFree) {
                        StatusPill("Free entry", filled = true)
                    } else {
                        StatusPill(Format.money(tournament.entryFeePaise))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    tournament.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = HmxColor.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(
                        tournament.map?.takeIf { it.isNotBlank() },
                        tournament.game.replace("_", " ").replaceFirstChar { it.uppercase() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = HmxColor.InkSecondary,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (tournament.prizePoolPaise > 0) Format.money(tournament.prizePoolPaise) else "No cash prize",
                            style = MaterialTheme.typography.titleMedium,
                            color = HmxColor.Ink,
                        )
                        Text(
                            "Prize pool",
                            style = MaterialTheme.typography.labelSmall,
                            color = HmxColor.InkTertiary,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${tournament.slotsFilled}/${tournament.maxSlots}",
                            style = MaterialTheme.typography.labelLarge,
                            color = HmxColor.Ink,
                        )
                        Text("slots", style = MaterialTheme.typography.labelSmall, color = HmxColor.InkTertiary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                val fraction = if (tournament.maxSlots > 0) {
                    (tournament.slotsFilled.toFloat() / tournament.maxSlots).coerceIn(0f, 1f)
                } else 0f
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = HmxColor.Ink,
                    trackColor = HmxColor.CanvasTint,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Format.dateTime(tournament.startsAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = HmxColor.InkSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        Format.countdown(tournament.startsAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = HmxColor.Ink,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = HmxColor.Ink, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun MatchCard(
    match: Match,
    tournamentTitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(Modifier.padding(HmxDimens.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tournamentTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = HmxColor.InkTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(match.status.replaceFirstChar { it.uppercase() })
            }
            Spacer(Modifier.height(8.dp))
            Text(match.name, style = MaterialTheme.typography.titleLarge, color = HmxColor.Ink)
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(
                    Format.dateTime(match.scheduledAt),
                    match.map?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = HmxColor.InkSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Room", style = MaterialTheme.typography.labelSmall, color = HmxColor.InkTertiary)
                Spacer(Modifier.weight(1f))
                TickingCountdown(match.scheduledAt)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = HmxColor.Ink, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun CopyRow(label: String, value: String, onCopy: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = HmxColor.InkTertiary)
            Text(value, style = MaterialTheme.typography.titleMedium, color = HmxColor.Ink)
        }
        Box(
            Modifier
                .size(HmxDimens.iconButton)
                .clip(CircleShape)
                .background(HmxColor.Glass)
                .border(HmxDimens.hairline, HmxColor.Hairline, CircleShape)
                .clickable(role = Role.Button, onClick = onCopy)
                .semantics { contentDescription = "Copy $label" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = HmxColor.InkSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = HmxColor.Ink, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
    }
}

data class PickedImage(val bytes: ByteArray, val mime: String, val uri: Uri)

@Composable
fun rememberPickedImage(): Pair<PickedImage?, () -> Unit> {
    val context = LocalContext.current
    var picked by remember { mutableStateOf<PickedImage?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes != null) picked = PickedImage(bytes, mime, uri)
        }
    }
    return picked to { launcher.launch("image/*") }
}

fun android.content.ClipboardManager.copy(text: String) {
    setPrimaryClip(android.content.ClipData.newPlainText("HMX Arena", text))
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.copy(text)
}
