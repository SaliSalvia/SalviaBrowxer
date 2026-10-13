package com.salvia.salviabrowxer.media.downloader

/**
 * How a known-size file is split across several `Range` connections.
 *
 * @param totalBytes the length the server stated, exactly once, and never inferred.
 * @param ranges the byte ranges to fetch, in file order. They always cover the whole file with no
 *   gap and no overlap, so joining the parts in order reconstructs the file byte for byte.
 */
data class RangePlan(val totalBytes: Long, val ranges: List<LongRange>) {
    val connections: Int get() = ranges.size
}

/**
 * The arithmetic behind parallel downloading, separated from the downloading itself.
 *
 * This is the part worth testing on its own: a planner that leaves a gap, overlaps two parts or
 * drops the remainder byte produces a corrupt file that no amount of retrying fixes. Pure — no IO,
 * no state, no Android.
 */
object RangePlanner {

    /** Fewer than this is not parallelism, it is a sequential download with extra bookkeeping. */
    const val MIN_CONNECTIONS: Int = 2

    /**
     * Below this a connection is not worth opening: the handshake, the request and the extra part
     * file cost more than the parallelism saves, and a small file is usually finished before the
     * second connection has answered.
     */
    const val MIN_PART_BYTES: Long = 1024L * 1024L

    /**
     * The plan for [totalBytes] over at most [connections] connections, or null when the file should
     * be fetched on one connection.
     *
     * Null is the normal answer for an unknown or small size, and callers must treat it as "stay
     * sequential" rather than as a failure — a file whose length nobody stated is exactly the file
     * that must not be sliced on a guess.
     */
    fun plan(totalBytes: Long?, connections: Int): RangePlan? {
        val total = totalBytes ?: return null
        if (total <= 0L) return null
        if (connections < MIN_CONNECTIONS) return null
        if (total < MIN_PART_BYTES * MIN_CONNECTIONS) return null
        val parts = minOf(connections.toLong(), total / MIN_PART_BYTES).toInt()
        if (parts < MIN_CONNECTIONS) return null

        // An uneven split on purpose: the last part takes the remainder, so the ranges always add up
        // to the whole file instead of to a rounded-down approximation of it.
        val partSize = total / parts
        val ranges = ArrayList<LongRange>(parts)
        for (index in 0 until parts) {
            val start = index.toLong() * partSize
            val end = if (index == parts - 1) total - 1 else start + partSize - 1
            ranges += start..end
        }
        return RangePlan(total, ranges)
    }
}
