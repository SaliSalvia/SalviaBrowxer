package com.salvia.salviabrowxer.media.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The `sidx` reader, against bytes built by hand.
 *
 * A wrong offset here does not fail loudly — it produces a file with a hole in it that every player
 * reports as "cannot play" — so the offsets are asserted exactly, including the version-1 layout
 * where every timing field is twice as wide.
 */
class SegmentIndexBoxTest {

    /**
     * Builds `sidx` bytes. Each entry is a (size, duration) pair; [referenceType] 1 turns the entry
     * into a reference to another index, which is the one shape a `sidx` can carry that this parser
     * deliberately ignores.
     */
    private fun sidx(
        entries: List<Pair<Long, Long>>,
        firstOffset: Long = 0L,
        version: Int = 0,
        referenceType: Int = 0
    ): ByteArray {
        val payload = mutableListOf<Byte>()
        fun u8(value: Int) = payload.add((value and 0xFF).toByte())
        fun u16(value: Int) { u8(value shr 8); u8(value) }
        fun u32(value: Long) { u8((value shr 24).toInt()); u8((value shr 16).toInt()); u8((value shr 8).toInt()); u8(value.toInt()) }
        fun u64(value: Long) { u32(value shr 32); u32(value) }

        u8(version)
        u8(0); u8(0); u8(0) // flags
        u32(1) // reference_ID
        u32(1000) // timescale
        if (version == 0) {
            u32(0) // earliest_presentation_time
            u32(firstOffset)
        } else {
            u64(0)
            u64(firstOffset)
        }
        u16(0) // reserved
        u16(entries.size)
        entries.forEach { (size, duration) ->
            u32((referenceType.toLong() shl 31) or size)
            u32(duration)
            u32(0x9000_0000L) // starts_with_SAP + SAP type 1
        }
        val body = payload.toByteArray()
        val size = body.size + 8
        val out = ByteArray(size)
        // big-endian size then the box type
        out[0] = (size ushr 24).toByte(); out[1] = (size ushr 16).toByte(); out[2] = (size ushr 8).toByte(); out[3] = size.toByte()
        "sidx".toByteArray(Charsets.ISO_8859_1).copyInto(out, 4)
        body.copyInto(out, 8)
        return out
    }

    @Test
    fun `version 0 index turns referenced sizes into absolute file offsets`() {
        val bytes = sidx(entries = listOf(100L to 1000L, 200L to 1000L))
        val boxStart = 32L

        val entries = SegmentIndexBox.parse(bytes, boxStart)!!

        val first = 32L + bytes.size
        assertEquals(2, entries.size)
        // The first referenced byte sits right after the box when first_offset is 0.
        assertEquals(first, entries[0].start)
        assertEquals(100L, entries[0].size)
        assertEquals(first + 100L, entries[1].start)
        assertEquals(200L, entries[1].size)
        assertEquals(1000L, entries[0].subsegmentDuration)
        // The ranges have to tile the file end to end; this is the number the caller verifies.
        assertEquals(first + 300L, entries[1].endExclusive)
    }

    @Test
    fun `a non-zero first_offset shifts the first segment`() {
        val bytes = sidx(entries = listOf(50L to 500L), firstOffset = 12L)

        val entries = SegmentIndexBox.parse(bytes, 0L)!!

        assertEquals(bytes.size.toLong() + 12L, entries[0].start)
    }

    @Test
    fun `version 1 uses 64-bit timing fields`() {
        val bytes = sidx(entries = listOf(64L to 900L), version = 1)

        val entries = SegmentIndexBox.parse(bytes, 8L)!!

        assertEquals(1, entries.size)
        assertEquals(8L + bytes.size, entries[0].start)
        assertEquals(64L, entries[0].size)
    }

    @Test
    fun `a box that is not an index is refused`() {
        val moov = byteArrayOf(0, 0, 0, 8, 'm'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'v'.code.toByte())

        assertNull(SegmentIndexBox.parse(moov, 0L))
    }

    @Test
    fun `a truncated index is refused instead of guessed`() {
        val bytes = sidx(entries = listOf(100L to 1000L, 100L to 1000L))

        assertNull(
            "a missing entry must not be inferred from a short body",
            SegmentIndexBox.parse(bytes.copyOf(bytes.size - 8), 0L)
        )
    }

    @Test
    fun `an index claiming more entries than its body holds is refused`() {
        val bytes = sidx(entries = listOf(10L to 100L))
        // Bump reference_count, the two bytes before the first entry, from 1 to 2000. The body holds
        // one entry, so the reader runs out of bytes and refuses rather than inventing a range.
        val countOffset = bytes.size - 12 - 2
        bytes[countOffset] = (2000 shr 8).toByte()
        bytes[countOffset + 1] = 2000.toByte()

        assertNull(SegmentIndexBox.parse(bytes, 0L))
    }

    @Test
    fun `an index past the reference bound is refused before it is read`() {
        // 4097 real entries: the bound is what refuses this, not a truncated body.
        val bytes = sidx(entries = List(SegmentIndexBox.MAX_REFERENCES + 1) { 10L to 100L })

        assertNull(SegmentIndexBox.parse(bytes, 0L))
    }

    @Test
    fun `an index of index references yields nothing rather than a wrong range`() {
        val bytes = sidx(entries = listOf(40L to 100L), referenceType = 1)

        assertNull("an index pointing at another index is not followed", SegmentIndexBox.parse(bytes, 0L))
    }
}
