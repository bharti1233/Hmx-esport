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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.draw.scale
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.hmx.arena.R

/**
 * A frosted translucent surface.
 *
 * By default it uses a cheap translucent fill, which is what list items use so
 * scrolling never pays for per-row blur. Chrome surfaces (top/bottom bars,
 * sheets, dialogs, hero cards) pass [refractive] = true to sample the Backdrop
 * layer recorded by [HmxGlassHost]. On devices where effects are unavailable
 * the same call silently falls back to the fill, so layout and behaviour are
 * identical.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = HmxShapes.card,
    fill: Color = HmxColor.Glass,
    borderColor: Color = HmxColor.Hairline,
    elevation: Dp = HmxElevation.card,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    refractive: Boolean = false,
    level: HmxGlassLevel = HmxGlassLevel.Card,
    content: @Composable () -> Unit,
) {
    val glassLevel = if (refractive) HmxGlassLevel.Chrome else level
    HmxLiquidGlassSurface(
        modifier = modifier,
        shape = shape,
        level = glassLevel,
        fill = fill,
        elevation = elevation,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun GlassScreenBackground(
    modifier: Modifier = Modifier,
    force: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    // Under HmxGlassHost the root already paints this exact backdrop, and all
    // glass surfaces sample that root layer. Painting a second copy would put
    // the visible background out of sync with what the glass refracts. The
    // content layer used by GlassScaffold sets force=true because it must be
    // opaque before it is recorded for the floating bottom bar.
    if (force || LocalHmxBackdrop.current == null) {
        HmxLiquidBackdrop(modifier)
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
    val effectsEnabled = LocalHmxGlassEffects.current
    val rootBackdrop = LocalHmxBackdrop.current
    val canvasColor = hmxColorScheme().canvas

    // The content layer is only needed when a bottom bar must refract real
    // scrolling content. Recording it for every screen doubles the graphics
    // work (and battery cost) with no visual benefit.
    val contentBackdrop = if (effectsEnabled && bottomBar != null) {
        rememberLayerBackdrop {
            drawRect(canvasColor)
            drawContent()
        }
    } else {
        null
    }

    Box(modifier.fillMaxSize()) {
        // HmxGlassHost already paints this background. Only paint a local copy
        // when GlassScaffold is used without the host (e.g. previews), so a
        // normal screen never renders the decorative backdrop twice.
        if (rootBackdrop == null) {
            GlassScreenBackground()
        }
        Column(Modifier.fillMaxSize()) {
            if (topBar != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = HmxDimens.screenPadding, vertical = HmxDimens.xs),
                ) { topBar() }
            }
            Box(Modifier.weight(1f)) {
                if (contentBackdrop != null) {
                    Box(Modifier.fillMaxSize().layerBackdrop(contentBackdrop)) {
                        GlassScreenBackground(force = true)
                        content(PaddingValues(0.dp))
                    }
                } else {
                    content(PaddingValues(0.dp))
                }

                if (bottomBar != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = HmxDimens.md, vertical = HmxDimens.xs),
                    ) {
                        CompositionLocalProvider(
                            LocalHmxBackdrop provides (contentBackdrop ?: rootBackdrop),
                        ) {
                            bottomBar()
                        }
                    }
                }
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
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    HmxLiquidGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = HmxShapes.pill,
        level = HmxGlassLevel.Chrome,
        fill = HmxColor.GlassChrome,
        elevation = 0.dp,
        // A floating bar must not close its outline: the bottom specular edge
        // and the chrome drop shadow are what produced the grey line under the
        // header on every screen.
        edge = HmxGlassEdge.Top,
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(10.dp))
            }
            if (onBack != null) {
                GlassIconButton(
                    icon = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                )
                Spacer(Modifier.width(10.dp))
            } else if (leading == null) {
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = HmxColor.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions()
            Spacer(Modifier.width(4.dp))
        }
    }
}

/**
 * Circular HMX Arena brand mark used in the shared top bar.
 *
 * It reuses the launcher/splash vector (`ic_hmx_mark`, with a night variant) so
 * the header, the splash and the app icon all show the same logo. The mark is
 * tinted with the active theme ink and sits on a small glass disc; the disc is
 * intentionally shadowless so it does not add another edge inside the header.
 */
@Composable
fun HmxBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    HmxLiquidGlassSurface(
        modifier = modifier.size(size),
        shape = CircleShape,
        level = HmxGlassLevel.Control,
        fill = HmxColor.GlassControl,
        elevation = 0.dp,
        edge = HmxGlassEdge.Top,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_hmx_mark),
                contentDescription = null,
                tint = HmxColor.Ink,
                modifier = Modifier.size(size * 0.6f),
            )
            // A full hairline keeps the circular logo legible on the very
            // light header, where a white-on-white glass disc would otherwise
            // disappear. Drawn inside the clipped surface, so it is a ring.
            Box(
                Modifier
                    .fillMaxSize()
                    .border(1.dp, HmxColor.HairlineStrong, CircleShape),
            )
        }
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.94f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "iconPressScale",
    )
    HmxLiquidGlassSurface(
        modifier = modifier
            .size(HmxDimens.iconButton)
            .scale(scale)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        level = HmxGlassLevel.Control,
        fill = HmxColor.GlassControl,
        elevation = 6.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) tint else HmxColor.Disabled,
                modifier = Modifier.size(20.dp),
            )
        }
        if (pressed) {
            // Immediate local feedback, drawn above the glass surface so it
            // cannot be hidden by the backdrop effect.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(HmxColor.PressedOverlay, CircleShape),
            )
        }
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
        GlassButtonVariant.Primary -> HmxColor.GlassControl
        GlassButtonVariant.Secondary -> HmxColor.GlassMuted
        GlassButtonVariant.Ink -> if (enabled) HmxColor.Ink else HmxColor.Disabled
        GlassButtonVariant.Danger -> HmxColor.GlassMuted
    }
    val contentColor = when (variant) {
        GlassButtonVariant.Primary -> HmxColor.Ink
        GlassButtonVariant.Secondary -> HmxColor.Ink
        GlassButtonVariant.Ink -> HmxColor.OnInk
        GlassButtonVariant.Danger -> HmxColor.Danger
    }
    HmxLiquidGlassSurface(
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
        level = if (variant == GlassButtonVariant.Ink) HmxGlassLevel.Card else HmxGlassLevel.Control,
        fill = fill,
        elevation = if (variant == GlassButtonVariant.Ink) 8.dp else HmxElevation.card,
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
    val textColor = if (selected) HmxColor.OnInk else HmxColor.InkSecondary
    HmxLiquidGlassSurface(
        modifier = modifier
            .height(38.dp)
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick),
        shape = HmxShapes.pill,
        level = if (selected) HmxGlassLevel.Card else HmxGlassLevel.Control,
        fill = if (selected) HmxColor.Ink else HmxColor.GlassControl,
        elevation = 6.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) textColor else HmxColor.Disabled,
            )
        }
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
