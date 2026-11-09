package com.hmx.arena.ui.category

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryEmptyStateTest {

    @Test
    fun emptyStateMessageIsTheApprovedCopy() {
        assertEquals(
            "No matches available in this category yet.",
            CATEGORY_EMPTY_MESSAGE,
        )
    }
}
