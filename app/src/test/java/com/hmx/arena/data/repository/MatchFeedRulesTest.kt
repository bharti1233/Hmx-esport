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
}
