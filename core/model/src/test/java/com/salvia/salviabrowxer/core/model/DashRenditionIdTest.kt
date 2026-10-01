package com.salvia.salviabrowxer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashRenditionIdTest {

    @Test
    fun `a video rendition round-trips`() {
        assertEquals(DashSelection.Video("v1"), DashRenditionId.parse(DashRenditionId.video("v1")))
    }

    @Test
    fun `an audio rendition round-trips`() {
        assertEquals(DashSelection.Audio("a1"), DashRenditionId.parse(DashRenditionId.audio("a1")))
    }

    @Test
    fun `a merged rendition keeps both ids`() {
        val encoded = DashRenditionId.merged("v1", "a2")
        assertEquals(DashSelection.Merged("v1", "a2"), DashRenditionId.parse(encoded))
    }

    @Test
    fun `a direct download has no dash selection`() {
        assertNull(DashRenditionId.parse(null))
        assertNull(DashRenditionId.parse(""))
        assertNull(DashRenditionId.parse("1080p"))
        assertNull(DashRenditionId.parse("m|v1"))
    }
}
