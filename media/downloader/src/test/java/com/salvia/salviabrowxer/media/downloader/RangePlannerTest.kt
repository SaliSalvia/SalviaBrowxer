package com.salvia.salviabrowxer.media.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic of splitting one file across several connections.
 *
 * A planner that leaves a gap, overlaps two parts or drops the last byte produces a corrupt file
 * that no retry repairs, so this is tested on its own rather than only through a download.
 */
class RangePlannerTest {

    @Test
    fun `a size nobody stated is never sliced`() {
        assertNull(RangePlanner.plan(null, 4))
        assertNull(RangePlanner.plan(0L, 4))
        assertNull(RangePlanner.plan(-1L, 4))
    }

    @Test
    fun `a file too small to share stays on one connection`() {
        assertNull(RangePlanner.plan(RangePlanner.MIN_PART_BYTES * 2 - 1, 4))
        assertNull(RangePlanner.plan(500_000L, 4))
    }

    @Test
    fun `one connection is not a plan`() {
        assertNull(RangePlanner.plan(10L * 1024 * 1024, 1))
        assertNull(RangePlanner.plan(10L * 1024 * 1024, 0))
    }

    @Test
    fun `the parts cover the file exactly once, in order`() {
        val total = 10L * 1024 * 1024

        val plan = RangePlanner.plan(total, 4)!!

        assertEquals(4, plan.connections)
        assertEquals(0L, plan.ranges.first().first)
        assertEquals(total - 1, plan.ranges.last().last)
        plan.ranges.zipWithNext().forEach { (first, second) ->
            assertEquals("a gap or an overlap", first.last + 1, second.first)
        }
        assertEquals(total, plan.ranges.sumOf { it.last - it.first + 1 })
    }

    @Test
    fun `a size that does not divide evenly leaves the remainder to the last part`() {
        val total = 4L * 1024 * 1024 + 12_345

        val plan = RangePlanner.plan(total, 4)!!

        assertEquals(total, plan.ranges.sumOf { it.last - it.first + 1 })
        assertEquals(total - 1, plan.ranges.last().last)
        assertEquals(total, plan.totalBytes)
    }

    @Test
    fun `no part is smaller than the minimum worth a connection`() {
        val plan = RangePlanner.plan(3L * 1024 * 1024, 8)!!

        assertTrue("asked for 8, the file only supports ${plan.connections}", plan.connections <= 3)
        plan.ranges.forEach { range ->
            assertTrue(
                "part of ${range.last - range.first + 1} bytes is not worth a connection",
                range.last - range.first + 1 >= RangePlanner.MIN_PART_BYTES
            )
        }
    }
}
