package com.hmx.arena.core.designsystem

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * Backdrop supplied by [HmxGlassHost]. It records the decorative background
 * only, never the UI itself, so cards and chrome can sample it safely without
 * a feedback loop. Null when the host is not mounted.
 */
val LocalHmxBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * Whether real RenderEffect blur/lens should run. Below API 31 and on devices
 * reporting [ActivityManager.isLowRamDevice] every glass surface takes the
 * cheap translucent fallback. Behaviour never depends on this flag.
 */
val LocalHmxGlassEffects = staticCompositionLocalOf { false }

/**
 * Root glass host.
 *
 * It records one full-screen decorative layer. Chrome, cards, sheets and
 * dialogs sample that layer through [LocalHmxBackdrop]. Screens that need the
 * bottom bar to refract their own scrolling content create a second, short
 * lived content backdrop in `GlassScaffold`; the two never nest.
 */
@Composable
fun HmxGlassHost(
    modifier: Modifier = Modifier,
    background: @Composable () -> Unit = { HmxLiquidBackdrop() },
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val effectsEnabled = remember(context) { liquidGlassSupported(context) }
    val backdrop = rememberLayerBackdrop {
        drawRect(HmxColor.Canvas)
        drawContent()
    }

    Box(modifier.fillMaxSize().background(HmxColor.Canvas)) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            background()
        }
        CompositionLocalProvider(
            LocalHmxBackdrop provides backdrop,
            LocalHmxGlassEffects provides effectsEnabled,
        ) {
            content()
        }
    }
}

fun liquidGlassSupported(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    return activityManager?.isLowRamDevice != true
}

/**
 * The visual substrate that makes glass read as glass.
 *
 * A plain white page cannot show refraction. This layer paints large soft grey
 * masses, two sweeping arcs and a ghost wordmark so blur and lens effects have
 * real contrast to distort. It stays strictly monochrome. The slow drift is
 * disabled when effects are unavailable, so low-RAM devices draw one static
 * frame.
 */
@Composable
fun HmxLiquidBackdrop(modifier: Modifier = Modifier) {
    val animate = LocalHmxGlassEffects.current
    // Only start the perpetual transition when effects are actually rendered.
    // On low-RAM/API < 31 devices the backdrop is a single static frame, so the
    // composable never invalidates and never burns battery redrawing.
    val motion = if (animate) {
        val transition = rememberInfiniteTransition(label = "liquidBackdrop")
        val drift by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 24_000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "backdropDrift",
        )
        drift
    } else {
        0.5f
    }

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(HmxColor.Canvas)

            val w = size.width
            val h = size.height

            // Large bright bloom top-right.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color(0x00FFFFFF)),
                    center = Offset(w * 0.86f, h * 0.06f),
                    radius = w * 0.92f,
                ),
                radius = w * 0.92f,
                center = Offset(w * 0.86f, h * 0.06f),
            )

            // Soft graphite cloud bottom-left.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x33BFC0C6), Color(0x00BFC0C6)),
                    center = Offset(w * 0.02f, h * 0.78f),
                    radius = w * 0.86f,
                ),
                radius = w * 0.86f,
                center = Offset(w * 0.02f, h * 0.78f),
            )

            // Mid grey mass that slides slowly behind the list.
            val cloudX = w * (0.28f + 0.10f * motion)
            val cloudY = h * (0.30f + 0.03f * motion)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x2ED2D2D8), Color(0x00D2D2D8)),
                    center = Offset(cloudX, cloudY),
                    radius = w * 0.68f,
                ),
                radius = w * 0.68f,
                center = Offset(cloudX, cloudY),
            )

            // Two wide arcs give the lens shader a strong edge to bend.
            drawArc(
                color = Color(0x17101012),
                startAngle = 196f + motion * 8f,
                sweepAngle = 118f,
                useCenter = false,
                topLeft = Offset(-w * 0.55f, h * 0.34f),
                size = androidx.compose.ui.geometry.Size(w * 1.45f, w * 1.45f),
                style = Stroke(width = 42.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = Color(0x0F101012),
                startAngle = 20f - motion * 6f,
                sweepAngle = 96f,
                useCenter = false,
                topLeft = Offset(w * 0.18f, -h * 0.10f),
                size = androidx.compose.ui.geometry.Size(w * 1.05f, w * 1.05f),
                style = Stroke(width = 26.dp.toPx(), cap = StrokeCap.Round),
            )

            // A fine ring adds readable distortion without colour.
            drawCircle(
                color = Color(0x1A101012),
                radius = w * 0.34f,
                center = Offset(w * 0.84f, h * 0.58f),
                style = Stroke(width = 1.dp.toPx()),
            )
            drawCircle(
                color = Color(0x14101012),
                radius = w * 0.46f,
                center = Offset(w * 0.84f, h * 0.58f),
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        Text(
            text = "HMX",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 72.dp, end = 12.dp)
                .rotate(-9f),
            color = HmxColor.Ink.copy(alpha = 0.045f),
            fontSize = 118.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-6).sp,
        )
    }
}
