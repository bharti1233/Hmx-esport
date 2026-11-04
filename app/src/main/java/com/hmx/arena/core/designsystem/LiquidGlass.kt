package com.hmx.arena.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/**
 * How much GPU work a glass surface may spend.
 *
 * [Card] samples the decorative backdrop without a blur pass, which is what
 * every list row uses. [Control] adds a small blur/lens for buttons, chips and
 * fields. [Chrome] is reserved for the top bar, bottom bar, sheets, dialogs and
 * hero cards: at most a handful on screen at once.
 */
enum class HmxGlassLevel { Card, Control, Chrome }

/**
 * The single real glass implementation used by the whole app.
 *
 * On capable devices this attaches the Kyant0 Backdrop rendering pipeline:
 * a recorded backdrop layer, optional vibrancy + blur + lens RenderEffects,
 * a white specular highlight, an outer shadow and an optional inner shadow.
 * On API < 31, low-RAM devices, or when no host backdrop is mounted it returns
 * a plain translucent fallback with identical layout and interaction.
 */
@Composable
fun Modifier.hmxLiquidGlass(
    shape: Shape,
    level: HmxGlassLevel = HmxGlassLevel.Card,
    fill: Color = HmxColor.Glass,
    elevation: Dp = 10.dp,
): Modifier {
    val backdrop = LocalHmxBackdrop.current
    val effects = LocalHmxGlassEffects.current

    if (backdrop == null || !effects) {
        return hmxGlassFallback(shape = shape, fill = fill, elevation = elevation)
    }

    val chrome = level == HmxGlassLevel.Chrome
    val control = level == HmxGlassLevel.Control

    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                when {
                    chrome -> {
                        vibrancy()
                        blur(radius = 18.dp.toPx())
                        lens(
                            refractionHeight = 18.dp.toPx(),
                            refractionAmount = 24.dp.toPx(),
                            depthEffect = true,
                        )
                    }
                    control -> {
                        vibrancy()
                        blur(radius = 6.dp.toPx())
                        lens(
                            refractionHeight = 8.dp.toPx(),
                            refractionAmount = 12.dp.toPx(),
                            depthEffect = true,
                        )
                    }
                }
            },
            highlight = {
                Highlight.Default.copy(
                    width = if (chrome) 1.dp else 0.75.dp,
                    blurRadius = if (chrome) 0.75.dp else 0.5.dp,
                    alpha = if (chrome) 0.92f else 0.55f,
                )
            },
            shadow = {
                Shadow(
                    radius = if (chrome) 22.dp else 14.dp,
                    offset = DpOffset(0.dp, if (chrome) 10.dp else 6.dp),
                    color = Color.Black.copy(alpha = if (chrome) 0.12f else 0.07f),
                    alpha = 1f,
                )
            },
            innerShadow = if (chrome) {
                {
                    InnerShadow(
                        radius = 14.dp,
                        offset = DpOffset(0.dp, 7.dp),
                        color = Color.Black.copy(alpha = 0.07f),
                        alpha = 1f,
                    )
                }
            } else {
                null
            },
            onDrawSurface = {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(
                            alpha = (fill.alpha + 0.14f).coerceAtMost(0.94f),
                        ),
                        0.28f to fill,
                        1f to fill.copy(alpha = (fill.alpha * 0.78f).coerceAtLeast(0.08f)),
                    ),
                )
                // A narrow specular sheen along the top lip.
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = if (chrome) 0.28f else 0.16f),
                        0.14f to Color.Transparent,
                    ),
                )
            },
        )
}

@Composable
private fun Modifier.hmxGlassFallback(
    shape: Shape,
    fill: Color,
    elevation: Dp,
): Modifier {
    return this
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
                0f to Color.White.copy(alpha = (fill.alpha + 0.22f).coerceAtMost(0.96f)),
                0.35f to fill,
                1f to fill.copy(alpha = (fill.alpha * 0.86f).coerceAtLeast(0.18f)),
            ),
            shape = shape,
        )
        .border(0.75.dp, Color.White.copy(alpha = 0.55f), shape)
}

/**
 * Shared glass container used by every HMX component.
 */
@Composable
fun HmxLiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = HmxShapes.card,
    level: HmxGlassLevel = HmxGlassLevel.Card,
    fill: Color = HmxColor.Glass,
    elevation: Dp = 10.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .hmxLiquidGlass(shape = shape, level = level, fill = fill, elevation = elevation)
            .padding(contentPadding),
    ) {
        content()
    }
}
