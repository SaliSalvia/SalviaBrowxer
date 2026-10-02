package com.salvia.salviabrowxer.media.detector

import com.salvia.salviabrowxer.core.model.MediaCandidate.MediaSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DomMediaDetectorTest {

    private lateinit var detector: DomMediaDetector

    @Before
    fun setup() {
        detector = DomMediaDetector()
    }

    @Test
    fun `DOM cannot reintroduce web manifests or media fragments rejected by sniffer`() = runTest {
        val html = """
            <video src="https://cdn.example.org/seg.ts"></video>
            <video><source src="https://cdn.example.org/part.m4s" type="video/mp4"></video>
            <meta property="og:video" content="https://cdn.example.org/site.webmanifest">
            <a href="https://cdn.example.org/part.mp4?bytestart=10">fragment</a>
        """.trimIndent()
        assertEquals(emptyList<Any>(), detector.detect("https://example.org/feed", html))
    }

    @Test
    fun `page metadata can reveal main file while the player shows an ad`() = runTest {
        val html = """
            <meta property="og:video" content="https://cdn.example.org/main.mp4">
            <video src="https://cdn.example.org/ad.mp4"></video>
        """.trimIndent()
        val urls = detector.detect("https://example.org/post", html).map { it.mediaUrl }.toSet()
        assertEquals(setOf("https://cdn.example.org/ad.mp4", "https://cdn.example.org/main.mp4"), urls)
    }

    @Test
    fun `detect finds video elements`() = runTest {
        val html = """
            <html>
                <body>
                    <video src="video.mp4" type="video/mp4"></video>
                </body>
            </html>
        """.trimIndent()

        val pageUrl = "https://example.com"
        val candidates = detector.detect(pageUrl, html)

        assertEquals(1, candidates.size)
        assertEquals("https://example.com/video.mp4", candidates[0].mediaUrl)
        assertEquals(MediaSource.DOM, candidates[0].source)
        assertEquals("video/mp4", candidates[0].mimeType)
    }

    @Test
    fun `detect finds audio elements`() = runTest {
        val html = """
            <html>
                <body>
                    <audio src="audio.mp3" type="audio/mpeg"></audio>
                </body>
            </html>
        """.trimIndent()

        val pageUrl = "https://example.com"
        val candidates = detector.detect(pageUrl, html)

        assertEquals(1, candidates.size)
        assertEquals("https://example.com/audio.mp3", candidates[0].mediaUrl)
        assertEquals(MediaSource.DOM, candidates[0].source)
        assertEquals("audio/mpeg", candidates[0].mimeType)
    }

    @Test
    fun `detect finds source elements in video`() = runTest {
        val html = """
            <html>
                <body>
                    <video>
                        <source src="video.mp4" type="video/mp4">
                        <source src="video.webm" type="video/webm">
                    </video>
                </body>
            </html>
        """.trimIndent()

        val pageUrl = "https://example.com"
        val candidates = detector.detect(pageUrl, html)

        assertEquals(2, candidates.size)
        assertEquals("https://example.com/video.mp4", candidates[0].mediaUrl)
        assertEquals("https://example.com/video.webm", candidates[1].mediaUrl)
    }

    @Test
    fun `detect finds media links`() = runTest {
        val html = """
            <html>
                <body>
                    <a href="video.mp4">Download Video</a>
                    <a href="audio.mp3">Download Audio</a>
                </body>
            </html>
        """.trimIndent()

        val pageUrl = "https://example.com"
        val candidates = detector.detect(pageUrl, html)

        assertEquals(2, candidates.size)
        assertEquals("https://example.com/video.mp4", candidates[0].mediaUrl)
        assertEquals("https://example.com/audio.mp3", candidates[1].mediaUrl)
    }

    @Test
    fun `detect handles relative URLs`() = runTest {
        val html = """
            <html>
                <body>
                    <video src="/videos/video.mp4"></video>
                </body>
            </html>
        """.trimIndent()

        val pageUrl = "https://example.com/page"
        val candidates = detector.detect(pageUrl, html)

        assertEquals(1, candidates.size)
        assertEquals("https://example.com/videos/video.mp4", candidates[0].mediaUrl)
    }

    @Test
    fun `detect handles absolute URLs`() = runTest {
        val html = """
            <html>
                <body>
                    <video src="https://cdn.example.com/video.mp4"></video>
                </body>
            </html>
        """.trimIndent()

        val pageUrl = "https://example.com"
        val candidates = detector.detect(pageUrl, html)

        assertEquals(1, candidates.size)
        assertEquals("https://cdn.example.com/video.mp4", candidates[0].mediaUrl)
    }

    @Test
    fun `detect handles protocol-relative URLs`() = runTest {
        val html = """
            <html>
                <body>
                    <video src="//cdn.example.com/video.mp4"></video>
                </body>
            </html>
        """.trimIndent()

        val pageUrl = "https://example.com"
        val candidates = detector.detect(pageUrl, html)

        assertEquals(1, candidates.size)
        assertEquals("https://cdn.example.com/video.mp4", candidates[0].mediaUrl)
    }

    @Test
    fun `structured data contentUrl is detected but embedUrl is not`() = runTest {
        val html = """
            <script type="application/ld+json">
            {"@type":"VideoObject","name":"Clip","contentUrl":"https://cdn.example.org/clip.mp4","embedUrl":"https://example.org/embed/1"}
            </script>
        """.trimIndent()
        val urls = detector.detect("https://example.org/post", html).map { it.mediaUrl }
        assertEquals(listOf("https://cdn.example.org/clip.mp4"), urls)
    }

    @Test
    fun `lazy data attributes are detected only when the value is media`() = runTest {
        val html = """
            <div data-video-src="https://cdn.example.org/trailer.mp4"></div>
            <video data-src="https://cdn.example.org/lazy.webm"></video>
            <div data-src="https://example.org/api/feed"></div>
            <div data-url="https://example.org/thumb.jpg"></div>
        """.trimIndent()
        val urls = detector.detect("https://example.org/page", html).map { it.mediaUrl }.toSet()
        assertEquals(setOf("https://cdn.example.org/trailer.mp4", "https://cdn.example.org/lazy.webm"), urls)
    }

    @Test
    fun `detect returns empty list for no media`() = runTest {
        val html = """
            <html>
                <body>
                    <h1>No media here</h1>
                </body>
            </html>
        """.trimIndent()

        val pageUrl = "https://example.com"
        val candidates = detector.detect(pageUrl, html)

        assertEquals(0, candidates.size)
    }
}
