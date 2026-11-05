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
 * [Card] samples the decorative backdrop with a small blur, which is what every
 * list row uses. [Control] adds a little more blur and a lens for buttons,
 * chips and fields. [Chrome] is reserved for the top bar, bottom bar, sheets,
 * dialogs and hero cards: at most a handful on screen at once.
 */
enum class HmxGlassLevel { Card, Control, Chrome }

/**
 * The single real glass implementation used by the whole app.
 *
 * On capable devices this attaches the Kyant0 Backdrop rendering pipeline:
 * a recorded backdrop layer, optional vibrancy + blur + lens RenderEffects,
 * a specular highlight, an outer shadow and an optional inner shadow. All
 * colours and radii come from [HmxColorScheme], so Light and Dark glass are
 * materially different rather than a colour inversion. On API < 31, low-RAM
 * devices, or when no host backdrop is mounted it returns a translucent
 * fallback with identical layout and interaction.
 */
@Composable
fun Modifier.hmxLiquidGlass(
    shape: Shape,
    level: HmxGlassLevel = HmxGlassLevel.Card,
    fill: Color = HmxColor.Glass,
    elevation: Dp = 10.dp,
): Modifier {
    val scheme = hmxColorScheme()
    val tokens = scheme.glass
    val effects = scheme.effects
    val backdrop = LocalHmxBackdrop.current
    val effectsEnabled = LocalHmxGlassEffects.current

    if (backdrop == null || !effectsEnabled) {
        return hmxGlassFallback(
            shape = shape,
            fill = fill,
            elevation = elevation,
            tokens = tokens,
            scheme = scheme,
        )
    }

    val chrome = level == HmxGlassLevel.Chrome
    val control = level == HmxGlassLevel.Control

    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                when {
                    chrome -> {
                        blur(radius = effects.chromeBlurRadius.toPx())
                        lens(
                            refractionHeight = effects.chromeLensHeight.toPx(),
                            refractionAmount = effects.chromeLensAmount.toPx(),
                            depthEffect = true,
                        )
                    }
                    control -> {
                        blur(radius = effects.controlBlurRadius.toPx())
                        lens(
                            refractionHeight = effects.controlLensHeight.toPx(),
                            refractionAmount = effects.controlLensAmount.toPx(),
                            depthEffect = true,
                        )
                    }
                    else -> {
                        // Card level is intentionally vibrancy-only. Cards are
                        // the most common surface on scrolling screens; a blur
                        // RenderEffect per row is the main jank source on
                        // low-end devices, and the sheen/edge still read as
                        // glass above the recorded backdrop.
                    }
                }
            },
            highlight = {
                Highlight.Default.copy(
                    width = if (chrome) 1.dp else 0.75.dp,
                    blurRadius = if (chrome) 0.75.dp else 0.5.dp,
                    alpha = if (chrome) tokens.chromeHighlightAlpha else tokens.highlightAlpha,
                )
            },
            shadow = {
                Shadow(
                    radius = if (chrome) 22.dp else 14.dp,
                    offset = DpOffset(0.dp, if (chrome) 10.dp else 6.dp),
                    color = tokens.outerShadowColor.copy(
                        alpha = if (chrome) tokens.chromeOuterShadowAlpha else tokens.outerShadowAlpha,
                    ),
                    alpha = 1f,
                )
            },
            innerShadow = if (chrome) {
                {
                    InnerShadow(
                        radius = 14.dp,
                        offset = DpOffset(0.dp, 7.dp),
                        color = tokens.innerShadowColor.copy(alpha = tokens.innerShadowAlpha),
                        alpha = 1f,
                    )
                }
            } else {
                null
            },
            onDrawSurface = {
                // Base tint is derived from the surface fill itself. Using the
                // sheen colour here would paint a white wash over dark glass.
                val topAlpha = (fill.alpha + tokens.surfaceTopBoost).coerceAtMost(0.94f)
                val bottomAlpha = (fill.alpha * tokens.surfaceBottomFactor).coerceAtLeast(0.08f)
                drawRect(
                    Brush.verticalGradient(
                        0f to fill.copy(alpha = topAlpha),
                        0.28f to fill,
                        1f to fill.copy(alpha = bottomAlpha),
                    ),
                )
                // A narrow specular sheen along the top lip. In Dark this is a
                // soft neutral reflection, not a white plastic gloss.
                drawRect(
                    Brush.verticalGradient(
                        0f to tokens.sheenColor.copy(
                            alpha = if (chrome) tokens.chromeSheenAlpha else tokens.sheenAlpha,
                        ),
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
    tokens: HmxGlassTokens,
    scheme: HmxColorScheme,
): Modifier {
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = scheme.shadow,
            spotColor = scheme.shadow,
            clip = false,
        )
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                0f to fill.copy(
                    alpha = (fill.alpha + tokens.surfaceTopBoost).coerceAtMost(0.9f),
                ),
                0.35f to fill,
                1f to fill.copy(alpha = (fill.alpha * tokens.surfaceBottomFactor).coerceAtLeast(0.18f)),
            ),
            shape = shape,
        )
        .border(0.75.dp, tokens.edge.copy(alpha = tokens.edge.alpha * 0.8f), shape)
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
