package com.hmx.arena.data.repository

import com.hmx.arena.core.model.Match
import com.hmx.arena.core.model.MatchRegistration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchFeedRulesTest {

    private fun match(
        id: String,
        status: String = "scheduled",
        published: Boolean = true,
        archivedAt: String? = null,
        scheduledAt: String = "2026-11-10T10:00:00+00:00",
    ) = Match(
        id = id,
        name = "Match $id",
        status = status,
        scheduledAt = scheduledAt,
        isPublished = published,
        archivedAt = archivedAt,
    )

    @Test
    fun publicMatchRequiresPublishedAndNotArchivedAndNotCancelled() {
        assertTrue(isPublicMatch(match("ok")))
        assertFalse(isPublicMatch(match("draft", published = false)))
        assertFalse(isPublicMatch(match("archived", archivedAt = "2026-11-01T00:00:00+00:00")))
        assertFalse(isPublicMatch(match("cancelled", status = "cancelled")))
    }

    @Test
    fun feedGroupsUpcomingOngoingAndCompletedWithoutLeakingHiddenRows() {
        val items = listOf(
            MatchFeedItem(match("upcoming-late", scheduledAt = "2026-11-12T10:00:00+00:00")),
            MatchFeedItem(match("upcoming-soon", "check_in", scheduledAt = "2026-11-09T10:00:00+00:00")),
            MatchFeedItem(match("live", "live", scheduledAt = "2026-11-08T10:00:00+00:00")),
            MatchFeedItem(match("completed-old", "completed", scheduledAt = "2026-11-01T10:00:00+00:00")),
            MatchFeedItem(match("completed-new", "completed", scheduledAt = "2026-11-05T10:00:00+00:00")),
            MatchFeedItem(match("draft", published = false)),
            MatchFeedItem(match("archived", archivedAt = "2026-11-01T00:00:00+00:00")),
            MatchFeedItem(match("cancelled", "cancelled")),
        )

        val groups = groupMatchFeed(items)

        assertEquals(
            listOf(MatchFeedSection.ONGOING, MatchFeedSection.UPCOMING, MatchFeedSection.COMPLETED),
            groups.map { it.section },
        )
        assertEquals(
            listOf("live", "upcoming-soon"),
            groups.first { it.section == MatchFeedSection.ONGOING }.items.map { it.match.id },
        )
        assertEquals(
            listOf("upcoming-late"),
            groups.first { it.section == MatchFeedSection.UPCOMING }.items.map { it.match.id },
        )
        assertEquals(
            listOf("completed-new", "completed-old"),
            groups.first { it.section == MatchFeedSection.COMPLETED }.items.map { it.match.id },
        )
        val visibleIds = groups.flatMap { group -> group.items.map { it.match.id } }
        assertFalse(visibleIds.contains("draft"))
        assertFalse(visibleIds.contains("archived"))
        assertFalse(visibleIds.contains("cancelled"))
    }

    @Test
    fun sectionCountsOnlyCountPublicMatches() {
        val items = listOf(
            MatchFeedItem(match("live", "live")),
            MatchFeedItem(match("check-in", "check_in")),
            MatchFeedItem(match("upcoming")),
            MatchFeedItem(match("done", "completed")),
            MatchFeedItem(match("draft", published = false)),
            MatchFeedItem(match("cancelled", "cancelled")),
        )

        val counts = matchSectionCounts(items)

        assertEquals(2, counts[MatchFeedSection.ONGOING])
        assertEquals(1, counts[MatchFeedSection.UPCOMING])
        assertEquals(1, counts[MatchFeedSection.COMPLETED])
    }

    @Test
    fun sectionLookupMatchesTheFeedGroups() {
        assertEquals(MatchFeedSection.ONGOING, matchSectionOf(match("live", "live")))
        assertEquals(MatchFeedSection.ONGOING, matchSectionOf(match("check-in", "check_in")))
        assertEquals(MatchFeedSection.UPCOMING, matchSectionOf(match("upcoming")))
        assertEquals(MatchFeedSection.COMPLETED, matchSectionOf(match("done", "completed")))
        assertEquals(null, matchSectionOf(match("draft", "draft")))
    }

    @Test
    fun registeredBadgeOnlyDependsOnMatchRegistration() {
        val publicMatch = match("m1")
        assertFalse(MatchFeedItem(publicMatch).registered)
        assertTrue(
            MatchFeedItem(
                match = publicMatch,
                matchRegistration = MatchRegistration(
                    id = "mr1",
                    matchId = publicMatch.id,
                    userId = "u1",
                ),
            ).registered,
        )
    }

    @Test
    fun arenaQueryUsesDynamicLookupForeignKeysAndHidesIneligibleRows() {
        val params = tournamentQueryParams()
        assertEquals("not.in.(cancelled)", params["status"])
        assertEquals("is.null", params["archived_at"])
        assertFalse(params.containsKey("mode"))
        assertFalse(params.containsKey("category_id"))
        assertFalse(params.containsKey("game_mode_id"))
        assertTrue(params.getValue("select").contains("category:categories"))
        assertTrue(params.getValue("select").contains("gameMode:game_modes"))

        val filtered = tournamentQueryParams(
            TournamentFilter(
                categoryId = "category-1",
                gameModeId = "mode-1",
                freeOnly = true,
            ),
        )
        assertEquals("eq.category-1", filtered["category_id"])
        assertEquals("eq.mode-1", filtered["game_mode_id"])
        assertEquals("eq.free", filtered["entry_type"])
        assertFalse(filtered.containsKey("mode"))
    }

    @Test
    fun publicMatchQueryNeverRequestsDraftsOrArchives() {
        val params = publishedMatchQueryParams(limit = 25)
        assertEquals("eq.true", params["is_published"])
        assertEquals("is.null", params["archived_at"])
        assertEquals("neq.cancelled", params["status"])
        assertEquals("25", params["limit"])
        assertTrue(params.getValue("select").contains("tournamentType:tournament_types"))
    }

    @Test
    fun tabStatusFiltersUseTheCanonicalBackendStatuses() {
        assertEquals("in.(live,check_in)", matchSectionStatusFilter(MatchFeedSection.ONGOING))
        assertEquals("eq.scheduled", matchSectionStatusFilter(MatchFeedSection.UPCOMING))
        assertEquals("eq.completed", matchSectionStatusFilter(MatchFeedSection.COMPLETED))
        assertEquals("ongoing", matchSectionKey(MatchFeedSection.ONGOING))
        assertEquals("upcoming", matchSectionKey(MatchFeedSection.UPCOMING))
        assertEquals("completed", matchSectionKey(MatchFeedSection.COMPLETED))
    }

    @Test
    fun preferredSectionSkipsEmptyTabsButNeverInventsACount() {
        assertEquals(
            MatchFeedSection.UPCOMING,
            preferredMatchSection(mapOf(MatchFeedSection.ONGOING to 0, MatchFeedSection.UPCOMING to 2)),
        )
        assertEquals(
            MatchFeedSection.ONGOING,
            preferredMatchSection(mapOf(MatchFeedSection.ONGOING to 1)),
        )
        assertEquals(null, preferredMatchSection(null))
        assertEquals(
            null,
            preferredMatchSection(MatchFeedSection.entries.associateWith { 0 }),
        )
    }

    @Test
    fun categoryQueryIsServerSideFilteredAndPaged() {
        val params = categoryMatchQueryParams(
            categoryId = "cat-1",
            section = MatchFeedSection.UPCOMING,
            limit = 20,
            offset = 40,
        )
        assertEquals("eq.cat-1", params["category_id"])
        assertEquals("eq.true", params["is_published"])
        assertEquals("is.null", params["archived_at"])
        assertEquals("eq.scheduled", params["status"])
        assertEquals("20", params["limit"])
        assertEquals("40", params["offset"])
        assertEquals("scheduled_at.asc", params["order"])
        assertTrue(params.getValue("select").contains("category:categories"))
    }

    @Test
    fun completedCategoryTabOrdersNewestFirst() {
        val params = categoryMatchQueryParams("cat-1", MatchFeedSection.COMPLETED)
        assertEquals("eq.completed", params["status"])
        assertEquals("scheduled_at.desc", params["order"])
    }

    @Test
    fun categoryCountQueryMirrorsTheListFilters() {
        val list = categoryMatchQueryParams("cat-1", MatchFeedSection.ONGOING)
        val count = categoryMatchCountQueryParams("cat-1", MatchFeedSection.ONGOING)
        list.filterKeys { it != "limit" && it != "offset" && it != "order" && it != "select" }
            .forEach { (key, value) -> assertEquals(value, count[key]) }
        assertEquals("1", count["limit"])
        assertEquals("id", count["select"])
    }

    @Test
    fun tournamentCountNeverClaimsZeroBeforeTheQueryFinishes() {
        assertEquals("Loading tournaments…", tournamentCountLabel(0, loading = true, hasError = false))
        assertEquals("Tournaments unavailable", tournamentCountLabel(0, loading = false, hasError = true))
        assertEquals("0 tournaments", tournamentCountLabel(0, loading = false, hasError = false))
        assertEquals("3 tournaments", tournamentCountLabel(3, loading = false, hasError = false))
        // A refresh keeps the last known total visible instead of flashing zero.
        assertEquals("3 tournaments", tournamentCountLabel(3, loading = true, hasError = false))
    }
}
