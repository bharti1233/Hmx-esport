package com.hmx.arena.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hmx.arena.core.designsystem.GlassButton
import com.hmx.arena.core.designsystem.GlassButtonVariant
import com.hmx.arena.core.designsystem.GlassChip
import com.hmx.arena.core.designsystem.GlassEmptyState
import com.hmx.arena.core.designsystem.GlassErrorState
import com.hmx.arena.core.designsystem.GlassIconButton
import com.hmx.arena.core.designsystem.GlassLoadingIndicator
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.GlassTextField
import com.hmx.arena.core.designsystem.GlassTopBar
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.designsystem.HmxShapes
import com.hmx.arena.core.model.LeaderboardRow
import com.hmx.arena.core.model.Match
import com.hmx.arena.core.model.NotificationItem
import com.hmx.arena.core.model.Profile
import com.hmx.arena.core.model.Team
import com.hmx.arena.core.model.Tournament
import com.hmx.arena.core.util.Format
import com.hmx.arena.ui.shell.GlassBottomBar

/**
 * HMX Arena component library.
 *
 * Every component is monochrome by construction: pure white / translucent white
 * fills, ink text, neutral hairlines. Only chrome opts into Backdrop
 * refraction; cards and list rows stay on the cheap fill so long lists remain
 * smooth on low-RAM hardware.
 */

@Composable
fun HmxGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    refractive: Boolean = false,
    contentPadding: Dp = HmxDimens.cardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        refractive = refractive,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun HmxGlassButton(
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
fun HmxGlassOutlinedButton(
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
fun HmxGlassIconButton(
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
fun HmxGlassBottomBar(selectedRoute: String, onSelect: (String) -> Unit) {
    GlassBottomBar(selectedRoute = selectedRoute, onSelect = onSelect)
}

@Composable
fun HmxGlassTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    GlassTopBar(title = title, onBack = onBack, modifier = modifier, actions = actions)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HmxGlassBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = HmxColor.Scrim,
        dragHandle = null,
        tonalElevation = 0.dp,
    ) {
        GlassSurface(
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            fill = Color.White.copy(alpha = 0.72f),
            elevation = 0.dp,
            refractive = true,
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

@Composable
fun HmxGlassDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    onConfirm: () -> Unit,
    dismissLabel: String? = "Cancel",
) {
    if (!visible) return
    val dismissButton: (@Composable () -> Unit)? = if (dismissLabel != null) {
        {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, color = HmxColor.InkSecondary, style = MaterialTheme.typography.labelMedium)
            }
        }
    } else {
        null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White.copy(alpha = 0.94f),
        shape = HmxShapes.dialog,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = HmxColor.Ink) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = HmxColor.InkSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = HmxColor.Ink, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = dismissButton,
    )
}

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
fun HmxGlassSearchField(
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
fun HmxGlassChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    GlassChip(text = text, selected = selected, modifier = modifier, enabled = enabled, onClick = onClick)
}

@Composable
fun HmxGlassTournamentCard(
    tournament: Tournament,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TournamentCard(tournament = tournament, modifier = modifier, onClick = onClick)
}

@Composable
fun HmxGlassTeamCard(
    team: Team,
    modifier: Modifier = Modifier,
    memberCount: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    HmxGlassCard(modifier = modifier, onClick = onClick) {
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
                    color = HmxColor.White,
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
fun HmxGlassPlayerCard(
    profile: Profile,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
) {
    HmxGlassCard(modifier = modifier, onClick = onClick) {
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
fun HmxGlassLeaderboardItem(
    row: LeaderboardRow,
    modifier: Modifier = Modifier,
    clickable: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val isTop = row.rankNo <= 3
    HmxGlassCard(
        modifier = modifier,
        onClick = if (clickable) onClick else null,
        contentPadding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (isTop) HmxColor.Ink else HmxColor.CanvasTint),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    row.rankNo.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isTop) HmxColor.White else HmxColor.InkSecondary,
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
fun HmxGlassMatchCard(
    match: Match,
    tournamentTitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MatchCard(match = match, tournamentTitle = tournamentTitle, onClick = onClick, modifier = modifier)
}

@Composable
fun HmxGlassStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    icon: ImageVector? = null,
) {
    HmxGlassCard(modifier = modifier, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(HmxShapes.small)
                        .background(HmxColor.CanvasTint),
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
fun HmxGlassNotificationCard(
    item: NotificationItem,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    HmxGlassCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (item.unread) HmxColor.Ink else HmxColor.CanvasTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Notifications,
                    contentDescription = null,
                    tint = if (item.unread) HmxColor.White else HmxColor.InkSecondary,
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
fun HmxGlassLoading(label: String = "Loading", modifier: Modifier = Modifier) {
    GlassLoadingIndicator(modifier = modifier, label = label)
}

@Composable
fun HmxGlassEmptyState(
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
fun HmxGlassErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    GlassErrorState(message = message, modifier = modifier, onRetry = onRetry)
}
