package com.hmx.arena.ui.home

/**
 * Canonical Home content contract. The Home screen renders these sections in
 * this order; regression tests pin both the order and the deliberate removals
 * so a future redesign cannot quietly reintroduce the old dashboard blocks.
 */
internal enum class HomeSection(val label: String) {
    HEADER("HMX Arena header"),
    PROFILE_WALLET("Profile and wallet card"),
    ANNOUNCEMENTS("Announcements and notifications"),
    PROMOTIONS("Promotional banners"),
    MY_MATCHES("My matches"),
    ESPORTS_GAME("Esports game"),
    CATEGORIES("Admin-created categories"),
}

internal val homeSectionOrder: List<HomeSection> = HomeSection.entries

/** Sections removed from Home only; their dedicated screens and backend stay intact. */
internal val removedHomeSections: Set<String> = setOf(
    "Refer and Earn",
    "Featured arenas",
    "Your schedule",
    "Arena news",
)

internal const val HOME_ESPORTS_HEADING = "Esports game"
internal const val HOME_ESPORTS_DESCRIPTION = "Pick a category to find its matches."
