package com.salvia.salviabrowxer.data.diagnostics

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticsLogTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var now = 1_000L

    private fun newLog(maxEventBytes: Int = 200, maxCrashFiles: Int = 2): Pair<DiagnosticsLog, File> {
        val directory = folder.newFolder()
        return DiagnosticsLog(directory, maxEventBytes = maxEventBytes, maxCrashFiles = maxCrashFiles, clock = { now }) to directory
    }

    @Test
    fun `events are appended in order with their details`() {
        val (diagnostics, _) = newLog()
        now = 5_000L
        diagnostics.event("transfer-failed", mapOf("host" to "example.com", "reason" to "HTTP 403"))
        now = 6_000L
        diagnostics.event("renderer-gone", mapOf("host" to "news.example.org"))
        val lines = diagnostics.snapshot("1.0.0").lines()
        assertTrue(lines.any { it.contains("transfer-failed") && it.contains("host=example.com") && it.contains("reason=HTTP 403") })
        assertTrue(lines.indexOfFirst { it.contains("transfer-failed") } < lines.indexOfFirst { it.contains("renderer-gone") })
        assertTrue(lines.any { it.contains("version=1.0.0") })
    }

    @Test
    fun `null details are skipped and line breaks are flattened`() {
        val (diagnostics, _) = newLog()
        diagnostics.event("weird", mapOf("note" to "line1\nline2", "absent" to null))
        val line = diagnostics.snapshot("1.0.0").lines().first { it.contains("weird") }
        assertFalse(line.contains('\n'))
        assertFalse(line.contains("absent"))
    }

    @Test
    fun `the event log rotates instead of growing forever`() {
        val (diagnostics, directory) = newLog()
        repeat(50) { index -> diagnostics.event("filler-$index") }
        val current = File(directory, "events.log")
        val rotated = File(directory, "events.old")
        // A generation rotates when it is already at the cap, so the live file can exceed it by at
        // most the one line that triggered the check — a line is ~34 bytes here.
        assertTrue(current.exists() && current.length() <= 200 + 64)
        assertTrue(rotated.exists())
        // The pair — not one file — is the bound: two generations of at most the cap each.
        assertTrue(current.length() + rotated.length() <= 2 * 200 + 2 * 64)
        val snapshot = diagnostics.snapshot("1.0.0")
        assertTrue(snapshot.contains("filler-49"))
        // The very first events fell off the back of the second generation ("filler-0" is not a
        // prefix of any later index, so the absence is unambiguous).
        assertFalse(snapshot.contains("filler-0"))
    }

    @Test
    fun `a crash is written and the newest one is included in the snapshot`() {
        val (diagnostics, _) = newLog()
        now = 10_000L
        diagnostics.crash(Thread.currentThread(), IllegalStateException("boom"))
        now = 20_000L
        diagnostics.crash(Thread.currentThread(), RuntimeException("later"))
        val snapshot = diagnostics.snapshot("1.0.0")
        assertTrue(snapshot.contains("last crash"))
        assertTrue(snapshot.contains("RuntimeException"))
        assertTrue(snapshot.contains("later"))
    }

    @Test
    fun `only the newest crashes survive`() {
        val (diagnostics, directory) = newLog(maxCrashFiles = 2)
        repeat(5) { index ->
            now = (index + 1) * 1_000L
            diagnostics.crash(Thread.currentThread(), RuntimeException("crash-$index"))
        }
        val remaining = directory.listFiles { f -> f.name.startsWith("crash-") }?.map { it.name }.orEmpty()
        assertEquals(listOf("crash-4000.txt", "crash-5000.txt"), remaining.sorted())
    }

    @Test
    fun `writeReport produces a shareable file`() {
        val (diagnostics, _) = newLog()
        diagnostics.event("transfer-completed", mapOf("bytes" to "42"))
        // Two File(parent, child) hops: java.io.File has no three-argument constructor, and a
        // not-yet-existing parent doubles as the check that writeReport mkdirs before writing.
        val target = File(File(folder.newFolder(), "share"), "diagnostics.txt")
        assertEquals(target, diagnostics.writeReport(target, "1.0.0"))
        assertTrue(target.readText().contains("transfer-completed"))
    }

    @Test
    fun `hostOf reduces urls to their host and refuses the rest`() {
        assertEquals("cdn.example.org", hostOf("https://cdn.example.org/path/file.mp4?token=secret"))
        assertEquals("example.com", hostOf("http://example.com:8080/a"))
        assertEquals("player.example.tv", hostOf("blob:https://player.example.tv/uuid"))
        assertNull(hostOf("notaurl"))
        assertNull(hostOf(null))
        assertNull(hostOf(""))
        assertNull(hostOf("://missing-scheme"))
    }
}
