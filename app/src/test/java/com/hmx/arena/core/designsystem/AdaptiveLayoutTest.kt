package com.hmx.arena.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The adaptive shell must react to the available window width, not to a device
 * model. These tests pin the two Material breakpoints and the derived layout
 * values so a future "small tablet" exception cannot creep in.
 */
class AdaptiveLayoutTest {

    @Test
    fun widthBreakpoints() {
        assertEquals(HmxWindowClass.Compact, hmxWindowClassForWidth(0.dp))
        assertEquals(HmxWindowClass.Compact, hmxWindowClassForWidth(320.dp))
        assertEquals(HmxWindowClass.Compact, hmxWindowClassForWidth(599.dp))
        assertEquals(HmxWindowClass.Medium, hmxWindowClassForWidth(600.dp))
        assertEquals(HmxWindowClass.Medium, hmxWindowClassForWidth(720.dp))
        assertEquals(HmxWindowClass.Medium, hmxWindowClassForWidth(839.dp))
        assertEquals(HmxWindowClass.Expanded, hmxWindowClassForWidth(840.dp))
        assertEquals(HmxWindowClass.Expanded, hmxWindowClassForWidth(1280.dp))
    }

    @Test
    fun compactPhonesKeepFullWidthContent() {
        assertEquals(Dp.Unspecified, hmxContentMaxWidth(HmxWindowClass.Compact))
    }

    @Test
    fun largerWindowsCapReadableWidth() {
        assertEquals(760.dp, hmxContentMaxWidth(HmxWindowClass.Medium))
        assertEquals(980.dp, hmxContentMaxWidth(HmxWindowClass.Expanded))
    }

    @Test
    fun gridColumnsFollowWindowClass() {
        assertEquals(1, hmxGridColumns(HmxWindowClass.Compact))
        assertEquals(2, hmxGridColumns(HmxWindowClass.Medium))
        assertEquals(3, hmxGridColumns(HmxWindowClass.Expanded))
    }

    @Test
    fun railIsWideEnoughForTheVerticalBottomBar() {
        // HmxLiquidGlassBottomBar switches to its rail layout below 200.dp.
        assertEquals(96.dp, HmxRailWidth)
    }
}
