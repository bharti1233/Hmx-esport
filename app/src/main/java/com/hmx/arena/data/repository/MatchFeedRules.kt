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

data class MatchFeedGroup(
    val section: MatchFeedSection,
    val items: List<MatchFeedItem>,
)

internal fun groupMatchFeed(items: List<MatchFeedItem>): List<MatchFeedGroup> {
    val visible = items.filter { isPublicMatch(it.match) }
    val ongoing = visible
        .filter { it.match.status == "live" || it.match.status == "check_in" }
        .sortedBy { it.match.scheduledAt }
    val upcoming = visible
        .filter { it.match.status == "scheduled" }
        .sortedBy { it.match.scheduledAt }
    val completed = visible
        .filter { it.match.status == "completed" }
        .sortedByDescending { it.match.scheduledAt }
    return listOfNotNull(
        ongoing.takeIf { it.isNotEmpty() }?.let { MatchFeedGroup(MatchFeedSection.ONGOING, it) },
        upcoming.takeIf { it.isNotEmpty() }?.let { MatchFeedGroup(MatchFeedSection.UPCOMING, it) },
        completed.takeIf { it.isNotEmpty() }?.let { MatchFeedGroup(MatchFeedSection.COMPLETED, it) },
    )
}
