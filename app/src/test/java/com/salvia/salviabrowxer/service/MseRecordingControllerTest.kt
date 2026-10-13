package com.salvia.salviabrowxer.service

import com.salvia.salviabrowxer.media.downloader.MseRecorder
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The only place that decides whether a byte a page posted becomes part of a recording.
 *
 * A page can post to the recording port whenever it likes and from any frame, so each rule is tested
 * on its own: nothing is written without the user's arming, nothing is written for a frame that is not
 * the page's own, and a recording that wrote nothing hands back nothing.
 */
class MseRecordingControllerTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("salvia-mse-control").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun controller() = MseRecordingController(root)

    private fun payload(): ByteArray = byteArrayOf(0, 0, 0, 16) + "ftypisom".toByteArray()

    private fun filesUnderRoot(): List<File> = root.walkTopDown().filter { it.isFile }.toList()

    @Test
    fun `a page that was never armed is refused outright`() {
        val controller = controller()

        assertEquals(MseRecordingController.Open.NOT_ARMED, controller.onOpen("tab-1", "video/mp4", "https://site.example"))
        assertEquals(MseRecorder.Append.IGNORED, controller.onSegment("tab-1", payload(), "https://site.example"))
        assertFalse(controller.isRecording)
        assertNull(controller.finish("tab-1"))
        assertTrue(filesUnderRoot().isEmpty())
    }

    @Test
    fun `only the tab the user armed may record`() {
        val controller = controller()
        arm(controller, "tab-1")

        assertEquals(MseRecordingController.Open.NOT_ARMED, controller.onOpen("tab-2", "video/mp4", "https://site.example"))
        assertFalse(controller.isRecording)
    }

    @Test
    fun `a frame that is not the page's own cannot feed the recording`() {
        val controller = controller()
        arm(controller, "tab-1")

        assertEquals(
            MseRecordingController.Open.DENIED_FRAME,
            controller.onOpen("tab-1", "video/mp4", "https://ads.example")
        )
        assertFalse(controller.isRecording)
        assertTrue(filesUnderRoot().isEmpty())
    }

    @Test
    fun `the page's own frame opens the recording, and its segments are kept byte for byte`() {
        val controller = controller()
        arm(controller, "tab-1")

        assertEquals(
            MseRecordingController.Open.RECORDING,
            controller.onOpen("tab-1", "video/mp4", "https://site.example")
        )
        assertEquals(
            MseRecorder.Append.CONTINUED,
            controller.onSegment("tab-1", payload(), "https://site.example")
        )
        assertEquals(payload().size.toLong(), controller.bytesOf("tab-1"))

        val finished = controller.finish("tab-1")

        assertNotNull(finished)
        assertArrayEquals(payload(), finished!!.recording.file.readBytes())
        assertEquals("video/mp4", finished.recording.mimeType)
        assertEquals("clip", finished.session.title)
        assertEquals("tab-1", finished.session.tabId)
        assertFalse(controller.isRecording)
        assertNull(controller.active.value)
    }

    @Test
    fun `a segment from a refused frame is ignored, not written`() {
        val controller = controller()
        arm(controller, "tab-1")
        controller.onOpen("tab-1", "video/mp4", "https://site.example")

        assertEquals(
            MseRecorder.Append.IGNORED,
            controller.onSegment("tab-1", payload(), "https://ads.example")
        )

        assertEquals(0L, controller.bytesOf("tab-1"))
        // The recorder deletes a file with no bytes in it rather than handing over an empty download.
        assertNull(controller.finish("tab-1"))
        assertTrue(filesUnderRoot().isEmpty())
    }

    @Test
    fun `a recording that never received a stream keeps nothing`() {
        val controller = controller()
        arm(controller, "tab-1")

        assertNull(controller.finish("tab-1"))
        assertFalse(controller.isRecording)
    }

    @Test
    fun `abandoning a recording deletes what was written`() {
        val controller = controller()
        arm(controller, "tab-1")
        controller.onOpen("tab-1", "video/mp4", "https://site.example")
        controller.onSegment("tab-1", payload(), "https://site.example")

        controller.abandon("tab-1")

        assertFalse(controller.isRecording)
        assertNull(controller.active.value)
        assertTrue("nothing may be left on disk", filesUnderRoot().isEmpty())
    }

    @Test
    fun `abandonAll drops every tab at once`() {
        val controller = controller()
        arm(controller, "tab-1")
        arm(controller, "tab-2")
        controller.onOpen("tab-1", "video/mp4", "https://site.example")
        controller.onOpen("tab-2", "audio/mp4", "https://site.example")
        controller.onSegment("tab-1", payload(), "https://site.example")
        controller.onSegment("tab-2", payload(), "https://site.example")

        controller.abandonAll()

        assertFalse(controller.isRecording)
        assertNull(controller.active.value)
        assertTrue(filesUnderRoot().isEmpty())
    }

    @Test
    fun `a tab id cannot escape the recording directory`() {
        val controller = controller()
        arm(controller, "../../escape")

        assertEquals(
            MseRecordingController.Open.RECORDING,
            controller.onOpen("../../escape", "video/mp4", null)
        )
        assertEquals(
            MseRecorder.Append.CONTINUED,
            controller.onSegment("../../escape", payload(), null)
        )

        val rootPath = root.canonicalPath + File.separator
        assertTrue("every file must stay under the recording root", filesUnderRoot().all { it.canonicalPath.startsWith(rootPath) })
    }

    private fun arm(controller: MseRecordingController, tabId: String) {
        controller.arm(
            tabId = tabId,
            kind = "video",
            pageUrl = "https://site.example/watch",
            frameOrigin = "https://site.example/watch",
            title = "clip"
        )
    }
}
