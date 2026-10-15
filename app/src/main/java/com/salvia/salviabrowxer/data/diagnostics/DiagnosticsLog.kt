package com.salvia.salviabrowxer.data.diagnostics

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A bounded, local-only record of what the app did — the privacy-preserving stand-in for the crash
 * reporting SDK this app refuses to ship.
 *
 * The design follows the app's privacy posture rather than fighting it:
 *
 *  - **Nothing leaves the device.** No network call exists in this class. The file is read only
 *    when the user presses "Share diagnostics", and what is shared is what the user saw on screen.
 *  - **URLs are reduced to hosts before they are written.** A diagnostic that secretly carried
 *    session tokens in a query string would be worse than no diagnostic. [hostOf] is the single
 *    funnel every URL passes through.
 *  - **Bounded by size and by count.** The event log rotates at [maxEventBytes] (one `.old`
 *    generation), and only the last [maxCrashFiles] crash files survive. A diagnostics log that
 *    grows forever is a storage leak wearing a debugging hat.
 *
 * The clock is injected so tests are deterministic. All methods are safe from any thread: the
 * crash handler runs on whichever thread crashed, the service writes from IO.
 */
class DiagnosticsLog(
    private val directory: File,
    private val maxEventBytes: Int = 256 * 1024,
    private val maxCrashFiles: Int = 5,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val lock = Any()

    /** The rolling event file, and its single rotated predecessor. */
    private val eventFile: File get() = File(directory, EVENTS_NAME)
    private val rotatedEventFile: File get() = File(directory, EVENTS_ROTATED_NAME)

    /**
     * Records one event. [kind] is a stable, untranslated identifier (`transfer-failed`,
     * `renderer-gone`); [details] carries the facts, already reduced to hostnames and counts —
     * this class trusts its callers to have passed only what belongs in a shareable file.
     */
    fun event(kind: String, details: Map<String, String?> = emptyMap()) {
        val line = buildString {
            append(timestamp()).append(' ').append(kind)
            for ((key, value) in details) {
                if (value == null) continue
                append(' ').append(key).append('=').append(value.replace(LINE_BREAK, " "))
            }
        }.trim()
        if (line.isEmpty()) return
        synchronized(lock) {
            runCatching {
                directory.mkdirs()
                if (eventFile.length() >= maxEventBytes) rotate()
                eventFile.appendText(line + "\n")
            }
        }
    }

    /**
     * Writes a crash, separate from the event log so one bad night of crashes cannot bury the
     * steady history. Keeps only the newest [maxCrashFiles].
     */
    fun crash(thread: Thread, throwable: Throwable) {
        synchronized(lock) {
            runCatching {
                directory.mkdirs()
                val file = File(directory, "crash-${clock()}.txt")
                file.writeText(
                    buildString {
                        append(timestamp()).append(" thread=").append(thread.name).append('\n')
                        append(throwable.stackTraceToString())
                        append('\n')
                    }
                )
                directory.listFiles { f -> f.name.startsWith(CRASH_PREFIX) }
                    ?.sortedByDescending { it.name }
                    ?.drop(maxCrashFiles)
                    ?.forEach { it.delete() }
            }
        }
    }

    /**
     * Everything a bug report needs: the two event generations, the newest crash last, and a header
     * saying what this is — a file that starts with a version is a file that can be trusted to be
     * about that version.
     */
    fun snapshot(appVersion: String): String = synchronized(lock) {
        buildString {
            append("# SalviaBrowxer diagnostics\n")
            append("# version=").append(appVersion).append(" generated=").append(timestamp()).append('\n')
            append("# hosts only - urls, tokens and page content are never written here\n")
            for (file in listOf(rotatedEventFile, eventFile)) {
                runCatching { file.readLines() }.getOrNull().orEmpty().forEach { append(it).append('\n') }
            }
            directory.listFiles { f -> f.name.startsWith(CRASH_PREFIX) }
                ?.maxByOrNull { it.name }
                ?.let { newest -> runCatching { newest.readText() }.getOrNull() }
                ?.let { append("# last crash\n").append(it).append('\n') }
        }
    }

    /**
     * Writes [snapshot] into [target] for sharing, overwriting any previous report so the share
     * folder cannot accumulate copies. Returns the file, or null when the disk refused.
     */
    fun writeReport(target: File, appVersion: String): File? {
        val text = snapshot(appVersion)
        return runCatching {
            target.parentFile?.mkdirs()
            target.writeText(text)
            target
        }.getOrNull()
    }

    private fun rotate() {
        rotatedEventFile.delete()
        eventFile.renameTo(rotatedEventFile)
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(clock()))

    private companion object {
        const val EVENTS_NAME = "events.log"
        const val EVENTS_ROTATED_NAME = "events.old"
        const val CRASH_PREFIX = "crash-"
        private val LINE_BREAK = Regex("[\\r\\n]")
    }
}

/**
 * Reduces a URL to its host, the only part a diagnostic may carry. Returns null for anything the
 * reduction cannot make safe (no scheme separator, empty host) — a null detail is simply skipped.
 */
internal fun hostOf(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val schemeEnd = url.indexOf("://")
    if (schemeEnd <= 0) return null
    val rest = url.substring(schemeEnd + 3)
    val host = rest.substringBefore('/').substringBefore(':').substringBefore('?')
    return host.takeIf { it.isNotBlank() }
}
