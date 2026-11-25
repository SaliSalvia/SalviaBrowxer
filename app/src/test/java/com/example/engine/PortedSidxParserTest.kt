package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer

class SidxParserTest {

    /** Builds a minimal v0 sidx box with the given reference entries (ISO 14496-12 layout). */
    private fun sidxBox(timescale: Int, firstOffset: Int, refs: List<Triple<Int, Int, Int>>): ByteArray {
        // box header(8) + version/flags(4) + reference_ID(4) + timescale(4) +
        // earliest_presentation_time(4) + first_offset(4) + reserved(2) + reference_count(2)
        val size = 32 + refs.size * 12
        val buf = ByteBuffer.allocate(size)
        buf.putInt(size)                       // box size
        buf.put("sidx".toByteArray(Charsets.US_ASCII))
        buf.put(0)                             // version
        buf.put(0); buf.put(0); buf.put(0)     // flags
        buf.putInt(1)                          // reference_ID
        buf.putInt(timescale)                  // timescale
        buf.putInt(0)                          // earliest_presentation_time
        buf.putInt(firstOffset)                // first_offset
        buf.putShort(0)                        // reserved
        buf.putShort(refs.size.toShort())      // reference_count
        for ((typeAndSize, duration, sap) in refs) {
            buf.putInt(typeAndSize)            // reference_type(1bit) + referenced_size(31bit)
            buf.putInt(duration)               // subsegment_duration
            buf.putInt(sap)                    // starts_with_SAP etc.
        }
        return buf.array()
    }

    @Test
    fun parsesVersion0WithConsecutiveEntries() {
        val box = sidxBox(1000, 0, listOf(Triple(0x0000_2000, 4000, 0x8000_0000.toInt()), Triple(0x0000_1800, 4000, 0x8000_0000.toInt())))
        val info = SidxParser.parse(box)
        assertNotNull(info)
        assertEquals(2, info!!.entries.size)
        assertEquals(0x2000L, info.entries[0].size)
        assertEquals(0x1800L, info.entries[1].size)
        assertEquals(0L, info.entries[0].offset)
        assertEquals(0x2000L, info.entries[1].offset)
        assertEquals(1000L, info.timescale)
        assertEquals(0L, info.firstOffset)
    }

    @Test
    fun parsesFirstOffset() {
        val box = sidxBox(1000, 128, listOf(Triple(100, 1000, 0)))
        val info = SidxParser.parse(box)
        assertNotNull(info)
        assertEquals(128L, info!!.firstOffset)
        assertEquals(0L, info.entries[0].offset)
    }

    @Test
    fun rejectsNonSidxBoxes() {
        val notSidx = ByteBuffer.allocate(64).apply {
            putInt(64)
            put("ftyp".toByteArray(Charsets.US_ASCII))
        }.array()
        assertNull(SidxParser.parse(notSidx))
        assertNull(SidxParser.parse(ByteArray(10)))
    }
}
