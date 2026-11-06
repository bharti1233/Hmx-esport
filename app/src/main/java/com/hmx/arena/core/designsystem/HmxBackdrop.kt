package com.hmx.arena.core.designsystem

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
 * How much of the real glass pipeline may run.
 *
 * [Full] enables backdrop, vibrancy, blur and lens on every glass level.
 * [Reduced] keeps the Liquid Glass identity (tint, sheen, edge, chrome blur)
 * but drops per-card/per-control RenderEffects and the second recorded layer,
 * which is the dominant GPU cost on lower-memory devices.
 * [Disabled] is the API < 31 path: translucent gradients only.
 */
enum class HmxGlassTier { Full, Reduced, Disabled }

val LocalHmxGlassTier = staticCompositionLocalOf { HmxGlassTier.Disabled }

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
    val scheme = hmxColorScheme()
    val tier = remember(context) { liquidGlassTier(context) }
    val effectsEnabled = tier != HmxGlassTier.Disabled
    val canvasColor = scheme.canvas
    val backdrop = rememberLayerBackdrop {
        drawRect(canvasColor)
        drawContent()
    }

    BoxWithConstraints(modifier.fillMaxSize().background(canvasColor)) {
        val windowClass = hmxWindowClassForWidth(maxWidth)
        CompositionLocalProvider(
            LocalHmxGlassEffects provides effectsEnabled,
            LocalHmxGlassTier provides tier,
            LocalHmxWindowClass provides windowClass,
        ) {
            // The decorative background must see the same tier as the content;
            // otherwise the low-end branch is always used because the default
            // CompositionLocal value is Disabled.
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                background()
            }
            CompositionLocalProvider(LocalHmxBackdrop provides backdrop) {
                content()
            }
        }
    }
}

fun liquidGlassSupported(context: Context): Boolean =
    liquidGlassTier(context) != HmxGlassTier.Disabled

/**
 * The OS/device capability tier. `isLowRamDevice` is the authoritative low-end
 * signal; the memory-class check catches budget devices that do not set it.
 * No device model names are used.
 */
fun liquidGlassTier(context: Context): HmxGlassTier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return HmxGlassTier.Disabled
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        ?: return HmxGlassTier.Disabled
    if (activityManager.isLowRamDevice) return HmxGlassTier.Reduced
    return if (activityManager.memoryClass <= 256) HmxGlassTier.Reduced else HmxGlassTier.Full
}

/**
 * The visual substrate that makes glass read as glass.
 *
 * A plain page cannot show refraction. This layer paints one soft bloom, one
 * diffuse neutral mass and two very low-contrast arcs so blur and lens have
 * real detail to distort without competing with foreground text. Everything is
 * derived from [HmxColorScheme] so Light and Dark are designed separately.
 */
@Composable
fun HmxLiquidBackdrop(modifier: Modifier = Modifier) {
    val scheme = hmxColorScheme()
    val reduced = LocalHmxGlassTier.current != HmxGlassTier.Full
    // Dark bloom is deliberately weak: a large white radial on near-black
    // reads as a grey patch behind cards.
    val bloomAlpha = if (scheme.isDark) 0.035f else 0.45f
    val watermark = scheme.watermark

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(scheme.canvas)

            val w = size.width
            val h = size.height

            // A single soft bloom in the top-right. In Dark it is a faint cool
            // light source rather than a white glow.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        scheme.backdropBloom.copy(alpha = bloomAlpha),
                        scheme.backdropBloom.copy(alpha = 0f),
                    ),
                    center = Offset(w * 0.88f, h * 0.05f),
                    radius = w * 0.78f,
                ),
                radius = w * 0.78f,
                center = Offset(w * 0.88f, h * 0.05f),
            )

            if (!reduced) {
                // Diffuse neutral mass, deliberately weak so it never turns
                // into a grey slab crossing headings or cards.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(scheme.backdropCloud, scheme.backdropCloudSoft),
                        center = Offset(w * 0.02f, h * 0.80f),
                        radius = w * 0.72f,
                    ),
                    radius = w * 0.72f,
                    center = Offset(w * 0.02f, h * 0.80f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(scheme.backdropMid, scheme.backdropMid.copy(alpha = 0f)),
                        center = Offset(w * 0.34f, h * 0.34f),
                        radius = w * 0.56f,
                    ),
                    radius = w * 0.56f,
                    center = Offset(w * 0.34f, h * 0.34f),
                )
            }

            // One wide, low-contrast arc gives the lens shader a long edge to
            // bend without competing with text. The reduced tier still keeps
            // one arc so chrome blur has something to refract.
            drawArc(
                color = scheme.backdropArc,
                startAngle = 198f,
                sweepAngle = 112f,
                useCenter = false,
                topLeft = Offset(-w * 0.55f, h * 0.40f),
                size = androidx.compose.ui.geometry.Size(w * 1.45f, w * 1.45f),
                style = Stroke(width = 34.dp.toPx(), cap = StrokeCap.Round),
            )
            if (!reduced) {
                drawArc(
                    color = scheme.backdropArcSoft,
                    startAngle = 18f,
                    sweepAngle = 92f,
                    useCenter = false,
                    topLeft = Offset(w * 0.22f, -h * 0.12f),
                    size = androidx.compose.ui.geometry.Size(w * 1.02f, w * 1.02f),
                    style = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        if (!reduced) {
            // A small, low-contrast wordmark. It sits in the top corner at 4%
            // opacity so it reads as texture, never as a heading.
            Text(
                text = "HMX",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 86.dp, end = 16.dp)
                    .rotate(-9f),
                color = watermark,
                fontSize = 84.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-5).sp,
            )
        }
    }
}
