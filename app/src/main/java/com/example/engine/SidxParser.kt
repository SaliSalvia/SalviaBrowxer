package com.example.engine

import java.nio.ByteBuffer

/**
 * Parses an ISO-BMFF `sidx` (Segment Index) box, the index format used by DASH
 * `SegmentBase`/`indexRange` representations (YouTube and many CDNs).
 *
 * Segment offsets returned in [SidxInfo.entries] are relative to the anchor point, which per the
 * ISO spec is `firstOffset` bytes after the end of the sidx box itself.
 */
object SidxParser {

    data class SidxEntry(val offset: Long, val size: Long, val durationTicks: Long, val isSubsegment: Boolean)

    data class SidxInfo(
        /** Size of the sidx box in bytes (the box must start at offset 0 of the parsed buffer). */
        val boxSize: Long,
        val timescale: Long,
        val firstOffset: Long,
        val entries: List<SidxEntry>
    )

    /**
     * Parses the sidx box out of [bytes], which must start exactly at the first byte of the box
     * (i.e. the bytes requested via indexRange, or a buffer positioned at the box start).
     */
    fun parse(bytes: ByteArray): SidxInfo? {
        if (bytes.size < 8 + 24) return null
        val buf = ByteBuffer.wrap(bytes)
        val boxSize = buf.int.toLong() and 0xFFFFFFFFL
        val type = ByteArray(4).also { buf.get(it) }
        if (String(type, Charsets.US_ASCII) != "sidx") return null
        if (boxSize < 8 || boxSize > bytes.size) return null
        val version = buf.get().toInt()
        buf.get() // flags
        buf.get()
        buf.get()

        buf.int // reference_ID
        val timescale = buf.int.toLong() and 0xFFFFFFFFL
        var firstOffset = 0L
        if (version == 0) {
            buf.int // earliest_presentation_time
            firstOffset = buf.int.toLong() and 0xFFFFFFFFL
        } else {
            buf.long // earliest_presentation_time
            firstOffset = buf.long
        }
        // ISO 14496-12 SegmentIndexBox: unsigned int(16) reserved comes BEFORE reference_count.
        // Skipping it was reading reserved (0) as the count, so every real-world sidx (YouTube
        // and most CDNs) parsed as invalid.
        buf.short // reserved
        val referenceCount = buf.short.toInt() and 0xFFFF
        if (referenceCount <= 0 || referenceCount > 100_000) return null

        val entries = ArrayList<SidxEntry>(referenceCount)
        var cursor = 0L
        for (i in 0 until referenceCount) {
            val referenceTypeAndSize = buf.int.toLong() and 0xFFFFFFFFL
            val isSubsegment = (referenceTypeAndSize ushr 31) == 1L
            val size = referenceTypeAndSize and 0x7FFFFFFFL
            val duration = buf.int.toLong() and 0xFFFFFFFFL
            buf.int // starts_with_SAP / SAP type / SAP delta time
            if (size <= 0L) return null
            entries.add(SidxEntry(offset = cursor, size = size, durationTicks = duration, isSubsegment = isSubsegment))
            cursor += size
        }

        return SidxInfo(boxSize = boxSize, timescale = timescale, firstOffset = firstOffset, entries = entries)
    }
}
