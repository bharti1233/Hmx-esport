package com.hmx.arena.core.designsystem

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * Backdrop supplied by [HmxGlassHost]. Null when the host is not mounted, in
 * which case every glass component falls back to the cheap translucent fill.
 */
val LocalHmxBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * Whether real blur/refraction should run on this device. Backdrop's blur is a
 * no-op below API 31 and its lens shader below API 33, and low-RAM devices skip
 * the effect entirely. Functionality never depends on this flag.
 */
val LocalHmxGlassEffects = staticCompositionLocalOf { true }

/**
 * Records a single decorative background layer and exposes it to descendant
 * glass surfaces. Only chrome (top bar, bottom bar, sheets, dialogs and hero
 * cards) opts into refraction; list items stay on the lightweight fill so a
 * long leaderboard or tournament list never runs per-row blur.
 */
@Composable
fun HmxGlassHost(
    modifier: Modifier = Modifier,
    background: @Composable () -> Unit = { GlassScreenBackground() },
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val effectsEnabled = remember(context) { liquidGlassSupported(context) }
    val backdrop = rememberLayerBackdrop()

    Box(modifier.fillMaxSize().background(HmxColor.Canvas)) {
        // This layer is what the glass samples. Keep it free of UI content:
        // recording the app itself would create a feedback loop and cost a
        // full-screen offscreen pass.
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
