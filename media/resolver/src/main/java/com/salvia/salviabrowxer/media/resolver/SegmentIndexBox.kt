package com.salvia.salviabrowxer.media.resolver

/**
 * One entry of a `sidx` box: a byte range of the media file and the duration it covers.
 */
data class SegmentIndexEntry(
    /** Absolute byte offset of the referenced range inside the file. */
    val start: Long,
    /** How many bytes the range holds. */
    val size: Long,
    val subsegmentDuration: Long,
    val startsWithSap: Boolean
) {
    val endExclusive: Long get() = start + size
}

/**
 * Reads the segment index (`sidx`, ISO/IEC 14496-12 §8.16.3) of a single-file DASH representation.
 *
 * Why this exists: in the `SegmentBase` shape the manifest points at **one** file and names only the
 * byte range of its index. Everything else — how many segments the file has, where each one starts
 * and how long it is — is inside that index. Without reading it the only honest download is "fetch
 * the whole file in one request"; with it the file becomes resumable and parallel like every other
 * transfer this app performs, because every entry is a `Range` request.
 *
 * The parser is deliberately minimal and defensive: it reads exactly the fields the box defines,
 * refuses anything that does not fit the bytes it was handed, ignores index-of-index references, and
 * stops at a sane bound so a hostile manifest cannot describe a million segments. It is pure — no
 * Android, no IO — and therefore tested directly on the JVM.
 */
object SegmentIndexBox {

    /** More references than this and the manifest is not describing a normal VOD file. */
    const val MAX_REFERENCES = 4096

    /**
     * @param data the bytes of the index range, starting at the `sidx` box itself
     * @param boxStart the absolute file offset those bytes were read from (usually
     *   `indexRange.first`), which turns the box's relative offsets into file offsets
     * @return the entries in file order, or null when the bytes are not a usable `sidx`
     */
    fun parse(data: ByteArray, boxStart: Long): List<SegmentIndexEntry>? {
        var position = 0

        fun u8(): Int? {
            if (position >= data.size) return null
            return data[position++].toInt() and 0xFF
        }

        fun u16(): Int? {
            val high = u8() ?: return null
            val low = u8() ?: return null
            return (high shl 8) or low
        }

        fun u32(): Long? {
            var value = 0L
            repeat(4) {
                val byte = u8() ?: return null
                value = (value shl 8) or byte.toLong()
            }
            return value
        }

        fun u64(): Long? {
            val high = u32() ?: return null
            val low = u32() ?: return null
            return (high shl 32) or low
        }

        fun type(): String? {
            if (position + 4 > data.size) return null
            val out = String(data, position, 4, Charsets.ISO_8859_1)
            position += 4
            return out
        }

        // Box header: size(32) + type(32). A size of 0 means "until the end of the data".
        val boxSize = u32() ?: return null
        if (type() != "sidx") return null
        val declaredEnd = if (boxSize == 0L) data.size.toLong() else boxSize
        val boxEnd = minOf(declaredEnd, data.size.toLong())
        if (boxEnd < HEADER_BYTES) return null

        val version = u8() ?: return null
        repeat(3) { if (u8() == null) return null } // flags
        if (u32() == null) return null // reference_ID
        if (u32() == null) return null // timescale
        val firstOffset = if (version == 0) {
            if (u32() == null) return null // earliest_presentation_time
            u32() ?: return null // first_offset
        } else {
            if (u64() == null) return null
            u64() ?: return null
        }
        if (u16() == null) return null // reserved
        val referenceCount = u16() ?: return null
        if (referenceCount <= 0 || referenceCount > MAX_REFERENCES) return null

        // The first referenced byte sits first_offset bytes past the end of this box.
        var cursor = boxStart + boxEnd + firstOffset
        val entries = ArrayList<SegmentIndexEntry>(referenceCount)
        for (index in 0 until referenceCount) {
            val word = u32() ?: return null
            val referenceType = (word shr 31) and 1L
            val referencedSize = word and 0x7FFF_FFFFL
            val subsegmentDuration = u32() ?: return null
            val sap = u32() ?: return null
            // Only media references (type 0) describe bytes of this file. A type-1 reference points at
            // another index; following it would mean fetching another range and recursing, which this
            // parser deliberately does not do — the caller falls back to the whole file instead.
            if (referenceType == 0L && referencedSize > 0L) {
                entries += SegmentIndexEntry(
                    start = cursor,
                    size = referencedSize,
                    subsegmentDuration = subsegmentDuration,
                    startsWithSap = (sap shr 31) and 1L == 1L
                )
                cursor += referencedSize
            }
        }
        return entries.takeIf { it.isNotEmpty() }
    }

    private const val HEADER_BYTES = 8L
}
