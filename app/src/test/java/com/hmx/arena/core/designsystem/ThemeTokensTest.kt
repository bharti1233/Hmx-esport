package com.hmx.arena.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the design-system invariants that caused the original dark-mode leaks.
 *
 * These are cheap JVM assertions on the palette rather than screenshot diffs:
 * they run on every CI build and fail as soon as a bright white sheen, white
 * bloom or opaque edge is reintroduced into the dark scheme.
 */
class ThemeTokensTest {

    private fun luminance(color: androidx.compose.ui.graphics.Color): Float =
        0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

    @Test
    fun themeModeParsingDefaultsToSystem() {
        assertEquals(HmxThemeMode.SYSTEM, HmxThemeMode.from(null))
        assertEquals(HmxThemeMode.SYSTEM, HmxThemeMode.from(""))
        assertEquals(HmxThemeMode.SYSTEM, HmxThemeMode.from("sepia"))
        assertEquals(HmxThemeMode.LIGHT, HmxThemeMode.from("LIGHT"))
        assertEquals(HmxThemeMode.DARK, HmxThemeMode.from("DARK"))
        assertEquals(HmxThemeMode.SYSTEM, HmxThemeMode.from("SYSTEM"))
    }

    @Test
    fun darkCanvasIsNearBlack() {
        val dark = HmxDarkColorScheme
        assertTrue("dark canvas must stay near-black", luminance(dark.canvas) < 0.05f)
        assertTrue("dark canvas tint must stay near-black", luminance(dark.canvasTint) < 0.10f)
        assertTrue("dark primary text must be light", luminance(dark.ink) > 0.85f)
        assertTrue("dark secondary text must be muted but readable", luminance(dark.inkSecondary) in 0.35f..0.85f)
    }

    @Test
    fun darkGlassHasNoWhiteWash() {
        val glass = HmxDarkColorScheme.glass
        // The base surface tint is charcoal, not white.
        assertTrue("dark glass surface must be dark", luminance(glass.surface) < 0.25f)
        // The specular sheen is a neutral grey and very restrained.
        assertTrue("dark sheen must not be pure white", luminance(glass.sheenColor) < 0.90f)
        assertTrue("dark sheen alpha too high", glass.sheenAlpha <= 0.08f)
        assertTrue("dark chrome sheen alpha too high", glass.chromeSheenAlpha <= 0.12f)
        assertTrue("dark highlight alpha too high", glass.highlightAlpha <= 0.30f)
        assertTrue("dark chrome highlight alpha too high", glass.chromeHighlightAlpha <= 0.50f)
        assertTrue("dark top gradient boost too high", glass.surfaceTopBoost <= 0.05f)
        assertTrue("dark border too bright", glass.edge.alpha <= 0.25f)
    }

    @Test
    fun darkBackdropHasNoWhitePatch() {
        val dark = HmxDarkColorScheme
        assertTrue("dark bloom must not be white", luminance(dark.backdropBloom) < 0.85f)
        assertTrue("dark watermark must stay subtle", dark.watermark.alpha <= 0.08f)
        assertTrue("dark arcs must stay subtle", dark.backdropArc.alpha <= 0.03f)
        assertTrue("dark soft arcs must stay subtle", dark.backdropArcSoft.alpha <= 0.025f)
        assertTrue("dark rings must stay subtle", dark.backdropRing.alpha <= 0.03f)
        assertTrue("dark diffuse cloud must stay subtle", dark.backdropCloud.alpha <= 0.05f)
        assertTrue("dark mid glow must stay subtle", dark.backdropMid.alpha <= 0.04f)
    }

    @Test
    fun lightThemeKeepsWhiteGlassIdentity() {
        val light = HmxLightColorScheme
        assertTrue("light canvas must be off-white", luminance(light.canvas) > 0.90f)
        assertTrue("light primary text must be near-black", luminance(light.ink) < 0.10f)
        assertFalse(light.isDark)
        assertTrue(HmxDarkColorScheme.isDark)
    }

    @Test
    fun pressFeedbackExistsInBothThemes() {
        assertTrue(HmxLightColorScheme.pressedOverlay.alpha > 0.05f)
        assertTrue(HmxDarkColorScheme.pressedOverlay.alpha > 0.05f)
    }

    @Test
    fun cardGlassAvoidsPerRowBlur() {
        // Card-level blur is the expensive effect that made long lists janky.
        assertEquals(0f, HmxDarkColorScheme.effects.cardBlurRadius.value, 0.001f)
        assertEquals(0f, HmxLightColorScheme.effects.cardBlurRadius.value, 0.001f)
    }
}
