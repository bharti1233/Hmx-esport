package com.hmx.arena.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.draw.scale

/**
 * A frosted translucent surface. Real-time blur is intentionally avoided:
 * translucent fills, a white top highlight and a hairline border read as glass
 * on entry-level hardware without the GPU cost of `Modifier.blur`.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = HmxShapes.card,
    fill: Color = HmxColor.Glass,
    borderColor: Color = HmxColor.Hairline,
    elevation: Dp = HmxElevation.card,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = HmxColor.Shadow,
                spotColor = HmxColor.Shadow,
                clip = false,
            )
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = fill.alpha), fill),
                ),
            )
            .border(HmxDimens.hairline, borderColor, shape)
            .padding(contentPadding),
    ) {
        content()
    }
}

@Composable
fun GlassScreenBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = HmxColor.Canvas)
        val w = size.width
        val h = size.height
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0x00FFFFFF)),
                center = Offset(w * 0.82f, h * 0.04f),
                radius = w * 0.75f,
            ),
            radius = w * 0.75f,
            center = Offset(w * 0.82f, h * 0.04f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFF0F0F4), Color(0x00F0F0F4)),
                center = Offset(w * 0.06f, h * 0.72f),
                radius = w * 0.9f,
            ),
            radius = w * 0.9f,
            center = Offset(w * 0.06f, h * 0.72f),
        )
    }
    content()
}

@Composable
fun GlassScaffold(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        GlassScreenBackground()
        Column(Modifier.fillMaxSize()) {
            if (topBar != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = HmxDimens.screenPadding),
                ) { topBar() }
            }
            Box(Modifier.weight(1f)) {
                content(PaddingValues(top = 0.dp))
            }
            if (bottomBar != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = HmxDimens.md, vertical = HmxDimens.xs),
                ) { bottomBar() }
            }
        }
        if (floatingActionButton != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(20.dp),
            ) { floatingActionButton() }
        }
    }
}

@Composable
fun GlassTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            GlassIconButton(
                icon = Icons.Rounded.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = HmxColor.Ink,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = HmxColor.Ink,
) {
    Box(
        modifier = modifier
            .size(HmxDimens.iconButton)
            .clip(CircleShape)
            .background(HmxColor.Glass)
            .border(HmxDimens.hairline, HmxColor.Hairline, CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else HmxColor.Disabled,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    variant: GlassButtonVariant = GlassButtonVariant.Primary,
    leadingIcon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.98f else 1f)
    val fill = when (variant) {
        GlassButtonVariant.Primary -> HmxColor.GlassStrong
        GlassButtonVariant.Secondary -> Color.Transparent
        GlassButtonVariant.Ink -> if (enabled) HmxColor.Ink else HmxColor.Disabled
        GlassButtonVariant.Danger -> Color.Transparent
    }
    val contentColor = when (variant) {
        GlassButtonVariant.Primary -> HmxColor.Ink
        GlassButtonVariant.Secondary -> HmxColor.Ink
        GlassButtonVariant.Ink -> HmxColor.White
        GlassButtonVariant.Danger -> HmxColor.Danger
    }
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(HmxDimens.buttonHeight)
            .scale(scale)
            .clickable(
                enabled = enabled && !loading,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = HmxShapes.pill,
        fill = if (fill == Color.Transparent) Color.Transparent else fill,
        borderColor = when (variant) {
            GlassButtonVariant.Secondary, GlassButtonVariant.Danger -> HmxColor.HairlineStrong
            GlassButtonVariant.Ink -> Color.Transparent
            else -> HmxColor.Hairline
        },
        elevation = if (variant == GlassButtonVariant.Ink) 4.dp else HmxElevation.card,
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = contentColor,
                    strokeWidth = 2.dp,
                )
            } else {
                if (leadingIcon != null) {
                    Icon(
                        leadingIcon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) contentColor else HmxColor.Disabled,
                )
            }
        }
    }
}

enum class GlassButtonVariant { Primary, Secondary, Ink, Danger }

@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val background = if (selected) HmxColor.Ink else HmxColor.Glass
    val textColor = if (selected) HmxColor.White else HmxColor.InkSecondary
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(HmxShapes.pill)
            .background(background)
            .border(HmxDimens.hairline, if (selected) Color.Transparent else HmxColor.Hairline, HmxShapes.pill)
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) textColor else HmxColor.Disabled,
        )
    }
}

@Composable
fun GlassSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = HmxColor.Ink,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelMedium,
                color = HmxColor.InkSecondary,
                modifier = Modifier
                    .clip(HmxShapes.pill)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun GlassStatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = HmxColor.Ink)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = HmxColor.InkTertiary)
    }
}

@Composable
fun GlassEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(HmxColor.Glass)
                    .border(HmxDimens.hairline, HmxColor.Hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = HmxColor.InkSecondary, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(18.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = HmxColor.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = HmxColor.InkSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            GlassButton(
                text = actionLabel,
                onClick = onAction,
                variant = GlassButtonVariant.Secondary,
                modifier = Modifier.width(200.dp),
            )
        }
    }
}

@Composable
fun GlassErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    GlassEmptyState(
        title = "Something went wrong",
        message = message,
        modifier = modifier,
        icon = Icons.Rounded.Refresh,
        actionLabel = if (onRetry != null) "Try again" else null,
        onAction = onRetry,
    )
}

@Composable
fun GlassLoadingIndicator(modifier: Modifier = Modifier, label: String? = null) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            color = HmxColor.Ink,
            strokeWidth = 2.dp,
        )
        if (label != null) {
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = HmxColor.InkSecondary)
        }
    }
}

@Composable
fun GlassShimmer(modifier: Modifier = Modifier, shape: Shape = HmxShapes.small) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "shimmerAlpha",
    )
    Box(
        modifier
            .clip(shape)
            .alpha(alpha)
            .background(HmxColor.CanvasTint),
    )
}

@Composable
fun GlassScreenPadding(): PaddingValues {
    val insets = WindowInsets.systemBars.asPaddingValues()
    return PaddingValues(
        start = HmxDimens.screenPadding,
        end = HmxDimens.screenPadding,
        top = insets.calculateTopPadding() + 8.dp,
        bottom = insets.calculateBottomPadding() + 24.dp,
    )
}
