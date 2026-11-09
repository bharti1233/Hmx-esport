package com.hmx.arena.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the simplified Home contract: the order of retained sections and the
 * blocks that the redesign deliberately removed from Home only.
 */
class HomeLayoutTest {

    @Test
    fun sectionsAppearInTheRequiredOrder() {
        assertEquals(
            listOf(
                HomeSection.HEADER,
                HomeSection.PROFILE_WALLET,
                HomeSection.ANNOUNCEMENTS,
                HomeSection.PROMOTIONS,
                HomeSection.MY_MATCHES,
                HomeSection.ESPORTS_GAME,
                HomeSection.CATEGORIES,
            ),
            homeSectionOrder,
        )
        assertEquals(HomeSection.entries, homeSectionOrder)
    }

    @Test
    fun categoriesAreTheFinalSection() {
        assertEquals(HomeSection.CATEGORIES, homeSectionOrder.last())
    }

    @Test
    fun removedHomeSectionsAreNoLongerPartOfTheLayout() {
        assertTrue(removedHomeSections.contains("Refer and Earn"))
        assertTrue(removedHomeSections.contains("Featured arenas"))
        assertTrue(removedHomeSections.contains("Your schedule"))
        assertTrue(removedHomeSections.contains("Arena news"))
        removedHomeSections.forEach { label ->
            assertFalse(homeSectionOrder.any { it.label == label })
        }
    }

    @Test
    fun esportsHeadingAndDescriptionAreExact() {
        assertEquals("Esports game", HOME_ESPORTS_HEADING)
        assertEquals("Pick a category to find its matches.", HOME_ESPORTS_DESCRIPTION)
    }
}
