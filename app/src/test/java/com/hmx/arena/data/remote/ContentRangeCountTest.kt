package com.hmx.arena.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * PostgREST reports exact totals in `Content-Range`. A missing or malformed
 * header must return null so the UI can withhold the number instead of
 * claiming "0 matches" before the query completes.
 */
class ContentRangeCountTest {

    @Test
    fun readsExactTotal() {
        assertEquals(42, parseContentRangeCount("0-19/42"))
        assertEquals(1, parseContentRangeCount("0-0/1"))
    }

    @Test
    fun emptyResultStillReportsARealZero() {
        assertEquals(0, parseContentRangeCount("*/0"))
        assertEquals(0, parseContentRangeCount("0-0/0"))
    }

    @Test
    fun malformedOrMissingHeaderIsUnknownNotZero() {
        assertNull(parseContentRangeCount(null))
        assertNull(parseContentRangeCount(""))
        assertNull(parseContentRangeCount("garbage"))
        assertNull(parseContentRangeCount("0-19/not-a-number"))
        assertNull(parseContentRangeCount("0-19/"))
        assertNull(parseContentRangeCount("-1"))
    }
}
