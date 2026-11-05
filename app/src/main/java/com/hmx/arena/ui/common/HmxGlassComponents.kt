package com.hmx.arena.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.hmx.arena.core.designsystem.GlassButton
import com.hmx.arena.core.designsystem.GlassButtonVariant
import com.hmx.arena.core.designsystem.GlassChip
import com.hmx.arena.core.designsystem.GlassEmptyState
import com.hmx.arena.core.designsystem.GlassErrorState
import com.hmx.arena.core.designsystem.GlassIconButton
import com.hmx.arena.core.designsystem.GlassLoadingIndicator
import com.hmx.arena.core.designsystem.GlassTopBar
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.designsystem.HmxGlassLevel
import com.hmx.arena.core.designsystem.HmxLiquidGlassSurface
import com.hmx.arena.core.designsystem.HmxShapes
import com.hmx.arena.core.designsystem.HmxThemeMode
import com.hmx.arena.core.designsystem.hmxColorScheme
import com.hmx.arena.core.model.LeaderboardRow
import com.hmx.arena.core.model.Match
import com.hmx.arena.core.model.NotificationItem
import com.hmx.arena.core.model.Profile
import com.hmx.arena.core.model.Team
import com.hmx.arena.core.model.Tournament
import com.hmx.arena.core.util.Format

/**
 * HMX Liquid Glass component library.
 *
 * Every component below delegates to `Modifier.hmxLiquidGlass`, so the surface
 * is a real recorded backdrop with optional blur, lens, highlight, outer and
 * inner shadow. List rows use the zero-blur Card level; chrome (bars, sheets,
 * dialogs, hero cards) uses the Chrome level. Low-RAM and API < 31 devices take
 * the identical-layout translucent fallback.
 */

@Composable
fun HmxLiquidGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    refractive: Boolean = false,
    contentPadding: Dp = HmxDimens.cardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    HmxLiquidGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        shape = HmxShapes.card,
        level = if (refractive) HmxGlassLevel.Chrome else HmxGlassLevel.Card,
        fill = HmxColor.Glass,
        elevation = if (refractive) 16.dp else 10.dp,
        contentPadding = PaddingValues(contentPadding),
    ) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
fun HmxLiquidGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    variant: GlassButtonVariant = GlassButtonVariant.Ink,
    leadingIcon: ImageVector? = null,
) {
    GlassButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        variant = variant,
        leadingIcon = leadingIcon,
    )
}

@Composable
fun HmxLiquidGlassOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    GlassButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        variant = GlassButtonVariant.Secondary,
        leadingIcon = leadingIcon,
    )
}

@Composable
fun HmxLiquidGlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    GlassIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun HmxLiquidGlassBottomBar(selectedRoute: String, onSelect: (String) -> Unit) {
    com.hmx.arena.ui.shell.HmxLiquidGlassBottomBar(selectedRoute = selectedRoute, onSelect = onSelect)
}

@Composable
fun HmxLiquidGlassTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    GlassTopBar(title = title, onBack = onBack, modifier = modifier, actions = actions)
}

/**
 * In-window bottom sheet. Rendering inside the same composition (instead of a
 * separate Dialog window) keeps the recorded backdrop coordinates valid, so
 * the sheet really refracts the page underneath it.
 */
@Composable
fun HmxLiquidGlassBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(Modifier.fillMaxSize().zIndex(40f)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(HmxColor.Scrim)
                    .clickable(onClick = onDismiss),
            )
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                HmxLiquidGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
                    level = HmxGlassLevel.Chrome,
                    fill = HmxColor.GlassChrome,
                    elevation = 24.dp,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 22.dp, vertical = 18.dp),
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(width = 42.dp, height = 4.dp)
                                .clip(HmxShapes.pill)
                                .background(HmxColor.HairlineStrong),
                        )
                        if (title != null) {
                            Spacer(Modifier.height(18.dp))
                            Text(title, style = MaterialTheme.typography.titleLarge, color = HmxColor.Ink)
                        }
                        Spacer(Modifier.height(16.dp))
                        content()
                    }
                }
            }
        }
    }
}

@Composable
fun HmxLiquidGlassDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    onConfirm: () -> Unit,
    dismissLabel: String? = "Cancel",
    confirmColor: Color = HmxColor.Ink,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(Modifier.fillMaxSize().zIndex(50f), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(HmxColor.Scrim)
                    .clickable(onClick = onDismiss),
            )
            HmxLiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = HmxShapes.dialog,
                level = HmxGlassLevel.Chrome,
                fill = HmxColor.GlassChrome,
                elevation = 28.dp,
                contentPadding = PaddingValues(24.dp),
            ) {
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = HmxColor.Ink)
                    Spacer(Modifier.height(10.dp))
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = HmxColor.InkSecondary)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        if (dismissLabel != null) {
                            TextButton(onClick = onDismiss) {
                                Text(
                                    dismissLabel,
                                    color = HmxColor.InkSecondary,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        TextButton(onClick = onConfirm) {
                            Text(
                                confirmLabel,
                                color = confirmColor,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HmxLiquidGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    enabled: Boolean = true,
    error: String? = null,
    maxLines: Int = 1,
    leadingIcon: ImageVector? = null,
    onImeAction: (() -> Unit)? = null,
) {
    GlassTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        placeholder = placeholder,
        keyboardType = keyboardType,
        imeAction = imeAction,
        enabled = enabled,
        error = error,
        maxLines = maxLines,
        leadingIcon = leadingIcon,
        onImeAction = onImeAction,
    )
}

@Composable
fun HmxLiquidGlassSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search players",
    onSearch: (() -> Unit)? = null,
) {
    GlassTextField(
        value = value,
        onValueChange = onValueChange,
        label = "Search",
        modifier = modifier,
        placeholder = placeholder,
        imeAction = ImeAction.Search,
        leadingIcon = Icons.Rounded.Search,
        trailing = {
            if (value.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Clear search",
                    tint = HmxColor.InkTertiary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { onValueChange("") }
                        .padding(6.dp)
                        .size(18.dp),
                )
            }
        },
        onImeAction = onSearch,
    )
}

@Composable
fun HmxLiquidGlassChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    GlassChip(text = text, selected = selected, modifier = modifier, enabled = enabled, onClick = onClick)
}

@Composable
fun HmxLiquidGlassTournamentCard(
    tournament: Tournament,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TournamentCard(tournament = tournament, modifier = modifier, onClick = onClick)
}

@Composable
fun HmxLiquidGlassMatchCard(
    match: Match,
    tournamentTitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MatchCard(match = match, tournamentTitle = tournamentTitle, onClick = onClick, modifier = modifier)
}

@Composable
fun HmxLiquidGlassTeamCard(
    team: Team,
    modifier: Modifier = Modifier,
    memberCount: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    HmxLiquidGlassCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(HmxShapes.small)
                    .background(HmxColor.Ink),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    team.tag.take(2).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = HmxColor.OnInk,
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    team.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = HmxColor.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        team.tag,
                        memberCount?.let { "$it members" },
                        team.region,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = HmxColor.InkTertiary,
                )
            }
            if (onClick != null) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = HmxColor.InkTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun HmxLiquidGlassPlayerCard(
    profile: Profile,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
) {
    HmxLiquidGlassCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassAvatar(profile.avatarUrl, profile.display, 46.dp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile.display,
                    style = MaterialTheme.typography.titleMedium,
                    color = HmxColor.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "@${profile.username} · ${profile.region}",
                    style = MaterialTheme.typography.bodySmall,
                    color = HmxColor.InkTertiary,
                )
            }
            Text(
                trailing ?: Format.points(profile.points.toLong()),
                style = MaterialTheme.typography.labelLarge,
                color = HmxColor.Ink,
            )
        }
    }
}

@Composable
fun HmxLiquidGlassLeaderboardItem(
    row: LeaderboardRow,
    modifier: Modifier = Modifier,
    clickable: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val isTop = row.rankNo <= 3
    HmxLiquidGlassCard(
        modifier = modifier,
        onClick = if (clickable) onClick else null,
        contentPadding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (isTop) HmxColor.Ink else HmxColor.GlassControl),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    row.rankNo.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isTop) HmxColor.OnInk else HmxColor.InkSecondary,
                )
            }
            Spacer(Modifier.width(12.dp))
            GlassAvatar(row.avatarUrl, row.name, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    row.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = HmxColor.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    row.subtitle ?: "${row.matches} matches · ${row.wins} wins",
                    style = MaterialTheme.typography.bodySmall,
                    color = HmxColor.InkTertiary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.points(row.points), style = MaterialTheme.typography.titleMedium, color = HmxColor.Ink)
                Text("points", style = MaterialTheme.typography.labelSmall, color = HmxColor.InkTertiary)
            }
        }
    }
}

@Composable
fun HmxLiquidGlassStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    icon: ImageVector? = null,
) {
    HmxLiquidGlassCard(modifier = modifier, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(HmxShapes.small)
                        .background(HmxColor.GlassControl),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = HmxColor.Ink, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, color = HmxColor.Ink)
                Text(label, style = MaterialTheme.typography.labelSmall, color = HmxColor.InkTertiary)
                if (caption != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(caption, style = MaterialTheme.typography.bodySmall, color = HmxColor.InkSecondary)
                }
            }
        }
    }
}

@Composable
fun HmxLiquidGlassNotificationCard(
    item: NotificationItem,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    HmxLiquidGlassCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (item.unread) HmxColor.Ink else HmxColor.GlassControl),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Notifications,
                    contentDescription = null,
                    tint = if (item.unread) HmxColor.OnInk else HmxColor.InkSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = HmxColor.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.unread) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(HmxColor.Ink),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(item.body, style = MaterialTheme.typography.bodySmall, color = HmxColor.InkSecondary)
                Spacer(Modifier.height(5.dp))
                Text(
                    Format.relative(item.createdAt.orEmpty()),
                    style = MaterialTheme.typography.labelSmall,
                    color = HmxColor.InkTertiary,
                )
            }
        }
    }
}

@Composable
fun HmxLiquidGlassLoading(label: String = "Loading", modifier: Modifier = Modifier) {
    GlassLoadingIndicator(modifier = modifier, label = label)
}

@Composable
fun HmxLiquidGlassEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.EmojiEvents,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    GlassEmptyState(
        title = title,
        message = message,
        modifier = modifier,
        icon = icon,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}

@Composable
fun HmxLiquidGlassErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    GlassErrorState(message = message, modifier = modifier, onRetry = onRetry)
}

// ---------------------------------------------------------------------------
// Appearance controls
// ---------------------------------------------------------------------------

/**
 * A glass segmented control with a single animated selection pill. Used by the
 * Appearance section, and available anywhere a theme-aware choice is needed.
 * Every segment is at least 48dp tall and exposes its selected state.
 */
@Composable
fun HmxLiquidGlassSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?> = emptyList(),
    contentDescription: String = "Selection",
) {
    if (options.isEmpty()) return
    val index = selectedIndex.coerceIn(0, options.lastIndex)
    HmxLiquidGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = HmxShapes.pill,
        level = HmxGlassLevel.Control,
        fill = HmxColor.GlassControl,
        elevation = 6.dp,
        contentPadding = PaddingValues(4.dp),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .semantics { this.contentDescription = contentDescription },
        ) {
            val segmentWidth = maxWidth / options.size
            val offset by animateDpAsState(
                targetValue = segmentWidth * index,
                animationSpec = tween(durationMillis = 260),
                label = "segmentOffset",
            )
            Box(
                Modifier
                    .offset(x = offset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .padding(2.dp)
                    .clip(HmxShapes.pill)
                    .background(HmxColor.Ink),
            )
            Row(Modifier.fillMaxSize()) {
                options.forEachIndexed { i, label ->
                    val selected = i == index
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics {
                                this.selected = selected
                                this.contentDescription = label
                            }
                            .clickable(role = Role.RadioButton) { onSelect(i) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val icon = icons.getOrNull(i)
                        if (icon != null) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = if (selected) HmxColor.OnInk else HmxColor.InkSecondary,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) HmxColor.OnInk else HmxColor.InkSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Settings -> Appearance. The selector and its preview use the same live glass
 * material as the rest of the app, so the preview changes in the same frame as
 * the theme.
 */
@Composable
fun HmxLiquidGlassThemeSelector(
    mode: HmxThemeMode,
    onModeChange: (HmxThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = hmxColorScheme()
    val modes = listOf(HmxThemeMode.LIGHT, HmxThemeMode.DARK, HmxThemeMode.SYSTEM)
    val labels = listOf("Light", "Dark", "System")
    val icons = listOf(Icons.Rounded.LightMode, Icons.Rounded.DarkMode, Icons.Rounded.SettingsBrightness)
    val index = modes.indexOf(mode)
    val activeLabel = if (scheme.isDark) "Dark glass" else "Light glass"

    HmxLiquidGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = HmxShapes.card,
        level = HmxGlassLevel.Card,
        fill = HmxColor.Glass,
        elevation = 10.dp,
        contentPadding = PaddingValues(HmxDimens.cardPadding),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                "Appearance",
                style = MaterialTheme.typography.titleLarge,
                color = HmxColor.Ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Choose how HMX Arena looks.",
                style = MaterialTheme.typography.bodySmall,
                color = HmxColor.InkSecondary,
            )
            Spacer(Modifier.height(14.dp))
            HmxLiquidGlassSegmentedControl(
                options = labels,
                selectedIndex = index,
                onSelect = { onModeChange(modes[it]) },
                icons = icons,
                contentDescription = "Appearance theme",
            )
            Spacer(Modifier.height(14.dp))
            HmxLiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = HmxShapes.field,
                level = HmxGlassLevel.Control,
                fill = HmxColor.GlassControl,
                elevation = 4.dp,
                contentPadding = PaddingValues(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(HmxShapes.small)
                            .background(HmxColor.Ink),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "H",
                            style = MaterialTheme.typography.titleMedium,
                            color = HmxColor.OnInk,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "HMX Arena",
                            style = MaterialTheme.typography.titleMedium,
                            color = HmxColor.Ink,
                        )
                        Text(
                            activeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = HmxColor.InkTertiary,
                        )
                    }
                    HmxLiquidGlassChip(
                        text = if (mode == HmxThemeMode.SYSTEM) "System" else labels[index],
                        selected = true,
                        onClick = {},
                    )
                }
            }
        }
    }
}

/** Compatibility alias matching the shared component names in the brief. */
@Composable
fun HmxGlassThemeSelector(
    mode: HmxThemeMode,
    onModeChange: (HmxThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) = HmxLiquidGlassThemeSelector(mode, onModeChange, modifier)

/**
 * In-window glass form dialog. Unlike a Material [androidx.compose.material3.AlertDialog]
 * this renders inside the same composition, so the backdrop recorded by the
 * host is at the correct coordinates and the sheet really refracts the page.
 */
@Composable
fun HmxLiquidGlassFormDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    dismissLabel: String? = "Cancel",
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(Modifier.fillMaxSize().zIndex(60f), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(HmxColor.Scrim)
                    .clickable(onClick = onDismiss),
            )
            HmxLiquidGlassSurface(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = HmxShapes.dialog,
                level = HmxGlassLevel.Chrome,
                fill = HmxColor.GlassChrome,
                elevation = 28.dp,
                contentPadding = PaddingValues(24.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = HmxColor.Ink)
                    Spacer(Modifier.height(14.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        content = content,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (dismissLabel != null) {
                            TextButton(onClick = onDismiss) {
                                Text(
                                    dismissLabel,
                                    color = HmxColor.InkSecondary,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                            Text(
                                confirmLabel,
                                color = if (confirmEnabled) HmxColor.Ink else HmxColor.Disabled,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Compatibility aliases. Existing screens can migrate incrementally; these
// call the same real-glass implementation, never a colour-only imitation.
// ---------------------------------------------------------------------------

@Composable
fun HmxGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    refractive: Boolean = false,
    contentPadding: Dp = HmxDimens.cardPadding,
    content: @Composable ColumnScope.() -> Unit,
) = HmxLiquidGlassCard(modifier, onClick, refractive, contentPadding, content)

@Composable
fun HmxGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    variant: GlassButtonVariant = GlassButtonVariant.Ink,
    leadingIcon: ImageVector? = null,
) = HmxLiquidGlassButton(text, onClick, modifier, enabled, loading, variant, leadingIcon)

@Composable
fun HmxGlassOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) = HmxLiquidGlassOutlinedButton(text, onClick, modifier, enabled, loading, leadingIcon)

@Composable
fun HmxGlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = HmxLiquidGlassIconButton(icon, contentDescription, onClick, modifier, enabled)

@Composable
fun HmxGlassBottomBar(selectedRoute: String, onSelect: (String) -> Unit) =
    HmxLiquidGlassBottomBar(selectedRoute, onSelect)

@Composable
fun HmxGlassTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) = HmxLiquidGlassTopBar(title, onBack, modifier, actions)

@Composable
fun HmxGlassBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) = HmxLiquidGlassBottomSheet(visible, onDismiss, title, content)

@Composable
fun HmxGlassDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    onConfirm: () -> Unit,
    dismissLabel: String? = "Cancel",
) = HmxLiquidGlassDialog(visible, onDismiss, title, message, confirmLabel, onConfirm, dismissLabel)

@Composable
fun HmxGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    enabled: Boolean = true,
    error: String? = null,
    maxLines: Int = 1,
    leadingIcon: ImageVector? = null,
    onImeAction: (() -> Unit)? = null,
) = HmxLiquidGlassTextField(
    value, onValueChange, label, modifier, placeholder, keyboardType, imeAction,
    enabled, error, maxLines, leadingIcon, onImeAction,
)

@Composable
fun HmxGlassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search players",
    onSearch: (() -> Unit)? = null,
) = HmxLiquidGlassSearchBar(value, onValueChange, modifier, placeholder, onSearch)

@Composable
fun HmxGlassChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = HmxLiquidGlassChip(text, selected, modifier, enabled, onClick)

@Composable
fun HmxGlassTournamentCard(tournament: Tournament, onClick: () -> Unit, modifier: Modifier = Modifier) =
    HmxLiquidGlassTournamentCard(tournament, onClick, modifier)

@Composable
fun HmxGlassTeamCard(
    team: Team,
    modifier: Modifier = Modifier,
    memberCount: Int? = null,
    onClick: (() -> Unit)? = null,
) = HmxLiquidGlassTeamCard(team, modifier, memberCount, onClick)

@Composable
fun HmxGlassPlayerCard(
    profile: Profile,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
) = HmxLiquidGlassPlayerCard(profile, modifier, trailing, onClick)

@Composable
fun HmxGlassLeaderboardItem(
    row: LeaderboardRow,
    modifier: Modifier = Modifier,
    clickable: Boolean = true,
    onClick: (() -> Unit)? = null,
) = HmxLiquidGlassLeaderboardItem(row, modifier, clickable, onClick)

@Composable
fun HmxGlassMatchCard(
    match: Match,
    tournamentTitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = HmxLiquidGlassMatchCard(match, tournamentTitle, onClick, modifier)

@Composable
fun HmxGlassStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    icon: ImageVector? = null,
) = HmxLiquidGlassStatCard(label, value, modifier, caption, icon)

@Composable
fun HmxGlassNotificationCard(
    item: NotificationItem,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) = HmxLiquidGlassNotificationCard(item, modifier, onClick)

@Composable
fun HmxGlassLoading(label: String = "Loading", modifier: Modifier = Modifier) =
    HmxLiquidGlassLoading(label, modifier)

@Composable
fun HmxGlassEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.EmojiEvents,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) = HmxLiquidGlassEmptyState(title, message, modifier, icon, actionLabel, onAction)

@Composable
fun HmxGlassErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) = HmxLiquidGlassErrorState(message, modifier, onRetry)
