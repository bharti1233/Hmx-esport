package com.hmx.arena.core.designsystem

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window size classes used by the adaptive shell.
 *
 * The values follow the Material 3 breakpoints (600dp / 840dp) but are computed
 * from the *available window width*, not from a device model. That means a
 * phone in split-screen, a foldable mid-fold or a resized desktop window all
 * re-flow automatically.
 */
enum class HmxWindowClass { Compact, Medium, Expanded }

fun hmxWindowClassForWidth(width: Dp): HmxWindowClass = when {
    width < 600.dp -> HmxWindowClass.Compact
    width < 840.dp -> HmxWindowClass.Medium
    else -> HmxWindowClass.Expanded
}

/**
 * Maximum readable content width. Unbounded on compact phones so lists keep
 * their natural full-width rhythm; capped on larger windows so cards do not
 * stretch into unusable lines.
 */
fun hmxContentMaxWidth(window: HmxWindowClass): Dp = when (window) {
    HmxWindowClass.Compact -> Dp.Unspecified
    HmxWindowClass.Medium -> 760.dp
    HmxWindowClass.Expanded -> 980.dp
}

/** Columns for card grids. */
fun hmxGridColumns(window: HmxWindowClass): Int = when (window) {
    HmxWindowClass.Compact -> 1
    HmxWindowClass.Medium -> 2
    HmxWindowClass.Expanded -> 3
}

/**
 * Columns for the artwork-first category grid. Artwork cards are square-ish and
 * carry only a name, so they can be denser than tournament cards. Compact keeps
 * the reference 2-column rhythm; tablets get a real gallery rather than two
 * stretched tiles.
 */
fun hmxCategoryColumns(window: HmxWindowClass): Int = when (window) {
    HmxWindowClass.Compact -> 2
    HmxWindowClass.Medium -> 3
    HmxWindowClass.Expanded -> 5
}

/** Width reserved for the navigation rail on medium/expanded windows. */
val HmxRailWidth: Dp = 96.dp

/**
 * Provided by [HmxGlassHost] from the current window constraints. Screens that
 * are not hosted (previews, tests) default to the phone layout.
 */
val LocalHmxWindowClass = compositionLocalOf { HmxWindowClass.Compact }
