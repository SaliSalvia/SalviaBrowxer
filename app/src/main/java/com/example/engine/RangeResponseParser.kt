package com.example.engine

/**
 * Parses satisfied HTTP `Content-Range` response headers used by the
 * resumable download paths of [MediaDownloadManager] (range probe + multi-part).
 *
 * Only satisfied byte ranges of the form `bytes <start>-<end>/<total>`
 * (case-insensitive) are accepted. Unsatisfied ranges (a wildcard start) and
 * open-ended totals (a wildcard total) are rejected so callers fall back to
 * the non-resumable single-stream path instead of mislabeling partial data.
 *
 * Extracted as a pure object so the regex behavior is covered by unit tests
 * (Regression First contract of the architecture baseline).
 */
object RangeResponseParser {

    private val satisfiedRangeRegex = Regex(
        "^bytes\\s+(\\d+)-(\\d+)/(\\d+)$",
        RegexOption.IGNORE_CASE,
    )

    data class SatisfiedByteRange(
        val start: Long,
        val end: Long,
        val totalBytes: Long,
    )

    /**
     * Returns the parsed (start, end, totalBytes) for a satisfied byte range
     * header, or null when the header is missing, malformed, unsatisfied, or
     * open-ended. Range-completeness validation stays with the caller.
     */
    fun parse(header: String?): SatisfiedByteRange? {
        if (header.isNullOrBlank()) return null
        val match = satisfiedRangeRegex.matchEntire(header.trim()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].toLongOrNull() ?: return null
        return SatisfiedByteRange(start, end, total)
    }
}
