package com.hmx.arena.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Category artwork fallbacks must be deterministic and distinct. The previous
 * shared trophy icon made every un-branded category look identical.
 */
class CategoryFallbackTest {

    @Test
    fun knownCategoriesMapToStableGlyphs() {
        assertEquals(CategoryFallbackGlyph.BattleRoyale, categoryFallbackGlyph("battle-royale"))
        assertEquals(CategoryFallbackGlyph.ClashSquad, categoryFallbackGlyph("clash-squad"))
        assertEquals(CategoryFallbackGlyph.FullMap, categoryFallbackGlyph("full-map"))
        assertEquals(CategoryFallbackGlyph.SpecialTournament, categoryFallbackGlyph("special-tournament"))
        assertEquals(CategoryFallbackGlyph.CustomTournament, categoryFallbackGlyph("custom-tournament"))
    }

    @Test
    fun unknownCategoryFallsBackToInitialsNotATrophy() {
        assertEquals(CategoryFallbackGlyph.Letter, categoryFallbackGlyph("lone-wolf"))
        assertEquals(CategoryFallbackGlyph.Letter, categoryFallbackGlyph(""))
    }

    @Test
    fun knownCategoriesDoNotShareTheSameFallback() {
        val glyphs = listOf("battle-royale", "clash-squad", "full-map", "special-tournament", "custom-tournament")
            .map(::categoryFallbackGlyph)
        assertEquals(glyphs.size, glyphs.toSet().size)
        assertNotEquals(CategoryFallbackGlyph.Letter, categoryFallbackGlyph("battle-royale"))
    }

    @Test
    fun initialsAreTwoLettersAtMost() {
        assertEquals("BR", categoryInitials("Battle Royale"))
        assertEquals("BA", categoryInitials("Battle"))
        assertEquals("?", categoryInitials("   "))
        assertEquals("CW", categoryInitials("  clash   wars  "))
    }

    @Test
    fun configuredArtworkWinsAndBlankAdminFieldsAreIgnored() {
        val configured = categoryVisuals("https://cdn/art.jpg", "https://cdn/logo.png")
        assertEquals("https://cdn/art.jpg", configured.artwork)
        assertEquals("https://cdn/logo.png", configured.logo)

        val logoOnly = categoryVisuals(null, "https://cdn/logo.png")
        assertEquals(null, logoOnly.artwork)
        assertEquals("https://cdn/logo.png", logoOnly.logo)

        val blank = categoryVisuals("   ", "")
        assertEquals(null, blank.artwork)
        assertEquals(null, blank.logo)
    }
}
