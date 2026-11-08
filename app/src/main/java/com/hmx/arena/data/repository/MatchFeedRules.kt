package com.hmx.arena.data.repository

import com.hmx.arena.core.model.Match

/**
 * Pure rules shared by the public match feed and its regression tests. Keeping
 * them outside the network layer makes it impossible for a screen to silently
 * reintroduce a draft, archived or cancelled match into a public list.
 */
internal fun isPublicMatch(match: Match): Boolean =
    match.isPublished && match.archivedAt == null && match.status != "cancelled"

enum class MatchFeedSection(val label: String) {
    ONGOING("Ongoing"),
    UPCOMING("Upcoming"),
    COMPLETED("Completed"),
}

internal fun matchSectionOf(match: Match): MatchFeedSection? = when (match.status) {
    "live", "check_in" -> MatchFeedSection.ONGOING
    "scheduled" -> MatchFeedSection.UPCOMING
    "completed" -> MatchFeedSection.COMPLETED
    else -> null
}

/** Counts every public match once, using the same rules as the feed groups. */
internal fun matchSectionCounts(items: List<MatchFeedItem>): Map<MatchFeedSection, Int> =
    MatchFeedSection.entries.associateWith { section ->
        items.count { isPublicMatch(it.match) && matchSectionOf(it.match) == section }
    }

data class MatchFeedGroup(
    val section: MatchFeedSection,
    val items: List<MatchFeedItem>,
)

internal fun groupMatchFeed(items: List<MatchFeedItem>): List<MatchFeedGroup> {
    val visible = items.filter { isPublicMatch(it.match) }
    val ongoing = visible
        .filter { matchSectionOf(it.match) == MatchFeedSection.ONGOING }
        .sortedBy { it.match.scheduledAt }
    val upcoming = visible
        .filter { matchSectionOf(it.match) == MatchFeedSection.UPCOMING }
        .sortedBy { it.match.scheduledAt }
    val completed = visible
        .filter { matchSectionOf(it.match) == MatchFeedSection.COMPLETED }
        .sortedByDescending { it.match.scheduledAt }
    return listOfNotNull(
        ongoing.takeIf { it.isNotEmpty() }?.let { MatchFeedGroup(MatchFeedSection.ONGOING, it) },
        upcoming.takeIf { it.isNotEmpty() }?.let { MatchFeedGroup(MatchFeedSection.UPCOMING, it) },
        completed.takeIf { it.isNotEmpty() }?.let { MatchFeedGroup(MatchFeedSection.COMPLETED, it) },
    )
}
