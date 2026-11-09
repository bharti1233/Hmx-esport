package com.hmx.arena.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The five floating destinations are a product contract: Earn, Ranking, Play,
 * Wallet and Account. These assertions keep the mapping from drifting back to
 * the old Home/Arena/Matches/Ranks/Profile set.
 */
class NavigationRoutesTest {

    @Test
    fun bottomBarMapsToTheFiveRealDestinations() {
        assertEquals(
            setOf(Routes.REFERRAL, Routes.LEADERBOARD, Routes.HOME, Routes.WALLET, Routes.PROFILE),
            Routes.tabs,
        )
        assertEquals(5, Routes.tabs.size)
        assertFalse(Routes.TOURNAMENTS in Routes.tabs)
        assertFalse(Routes.MATCHES in Routes.tabs)
    }

    @Test
    fun playTabStaysTheHomeDestination() {
        assertTrue(Routes.HOME in Routes.tabs)
        assertEquals("home", Routes.HOME)
    }

    @Test
    fun filteredTournamentRouteOmitsTheArgumentWhenUnfiltered() {
        assertEquals(Routes.TOURNAMENTS, Routes.tournaments())
        assertEquals(Routes.TOURNAMENTS, Routes.tournaments("  "))
        assertEquals("tournaments?category=abc", Routes.tournaments("abc"))
        assertEquals(Routes.TOURNAMENTS_FILTERED, "tournaments?category={category}")
    }

    @Test
    fun myMatchesSectionRouteIsOptional() {
        assertEquals(Routes.MY_MATCHES, Routes.myMatches())
        assertEquals(Routes.MY_MATCHES, Routes.myMatches("all"))
        assertEquals("my-matches?section=ongoing", Routes.myMatches("ongoing"))
        assertEquals(Routes.MY_MATCHES_FILTERED, "my-matches?section={section}")
    }

    @Test
    fun adminContentRoutesAreStable() {
        assertEquals("admin", Routes.ADMIN)
        assertEquals("admin/banners", Routes.ADMIN_BANNERS)
        assertEquals("admin/announcements", Routes.ADMIN_ANNOUNCEMENTS)
        assertEquals("admin/audit", Routes.ADMIN_AUDIT)
        assertEquals("admin/lookups/{kind}", Routes.ADMIN_LOOKUP)
        assertEquals("admin/lookups/categories", Routes.adminLookup("categories"))
    }

    @Test
    fun categoryMatchRouteUsesAStableCategoryId() {
        assertEquals("category/{categoryId}", Routes.CATEGORY_MATCHES)
        assertEquals(
            "category/2efb765a-3f3a-444b-8207-8fdb9141e93b",
            Routes.categoryMatches("2efb765a-3f3a-444b-8207-8fdb9141e93b"),
        )
        // The category screen must not reuse the generic tournament route.
        assertFalse(Routes.categoryMatches("abc") == Routes.tournaments("abc"))
    }
}
