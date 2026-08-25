package com.jayr91.vdr.engine

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class EngineTest {
    @Test
    fun streamingWatchPagesAreRejected() {
        assertEquals(
            DirectUrl.PAGE_ERROR,
            DirectUrl.rejectionMessage("https://youtu.be/jqpFjsMtCb0"),
        )
        assertEquals(
            DirectUrl.PAGE_ERROR,
            DirectUrl.rejectionMessage("https://www.youtube.com/watch?v=jqpFjsMtCb0"),
        )
        assertEquals(
            DirectUrl.PAGE_ERROR,
            DirectUrl.rejectionMessage("https://m.youtube.com/shorts/jqpFjsMtCb0"),
        )
        assertEquals(
            DirectUrl.PAGE_ERROR,
            DirectUrl.rejectionMessage("https://www.instagram.com/reel/abc123/"),
        )
        assertEquals(
            DirectUrl.PAGE_ERROR,
            DirectUrl.rejectionMessage(
                "https://odysee.com/@SpaceCay:3/neil-degrasse-tyson%27s-sharpest-argument:f",
            ),
        )
        assertEquals(
            DirectUrl.PAGE_ERROR,
            DirectUrl.rejectionMessage("https://lbry.tv/@channel/video"),
        )
        assertEquals(null, DirectUrl.rejectionMessage("https://cdn.example.com/clip.mp4"))
        assertEquals(null, DirectUrl.rejectionMessage("https://rr1---sn-abc.googlevideo.com/videoplayback"))
        assertEquals(
            null,
            DirectUrl.rejectionMessage("https://player.odycdn.com/v6/streams/abc/clip.mp4"),
        )
        assertEquals(null, DirectUrl.rejectionMessage("https://odysee.com/clip.mp4"))
        assertTrue(DirectUrl.isHtmlContentType("text/html; charset=utf-8"))
        assertFalse(DirectUrl.isHtmlContentType("video/mp4"))
        assertEquals(
            listOf("https://cdn.example.com/a.mp4", "https://files.example.org/b.pdf"),
            DirectUrl.extractHttpUrls(
                "https://cdn.example.com/a.mp4\nhttps://files.example.org/b.pdf",
            ),
        )
        assertEquals(
            listOf("https://cdn.example.com/a.mp4", "https://files.example.org/b.pdf"),
            DirectUrl.extractHttpUrls("https://cdn.example.com/a.mp4 https://files.example.org/b.pdf"),
        )
        assertEquals(
            listOf("https://youtu.be/jqpFjsMtCb0"),
            DirectUrl.extractHttpUrls("watch this https://youtu.be/jqpFjsMtCb0 please"),
        )
    }

    @Test
    fun htmlProbeFailsInsteadOfSavingThePage() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html; charset=utf-8")
                    .setHeader("Content-Length", 1200),
            )
            server.start()
            val dest = File.createTempFile("vdr", ".bin")
            dest.deleteOnExit()
            val done = CountDownLatch(1)
            val task = DownloadTask(
                id = "html",
                url = server.url("/watch?v=abc").toString(),
                destFile = dest,
                displayName = "watch",
                category = "Other",
                bucket = TokenBucket(null),
            ) { t ->
                if (t.status == DownloadStatus.COMPLETED || t.status == DownloadStatus.ERROR) done.countDown()
            }
            task.start()
            assertTrue("timed out status=${task.status}", done.await(10, TimeUnit.SECONDS))
            assertEquals(DownloadStatus.ERROR, task.status)
            assertEquals(DirectUrl.HTML_ERROR, task.errorMessage)
        }
    }

    @Test
    fun organizerCategorizesAndSanitizes() {
        assertEquals("clip.mp4", Organizer.filenameFromUrl("https://cdn.example/a/clip.mp4?token=1"))
        assertEquals("Videos", Organizer.categoryFor("clip.mp4"))
        assertEquals("Documents", Organizer.categoryFor("notes.pdf"))
        assertEquals("download", Organizer.safeFilename("../.."))
        assertEquals("Other", Organizer.categoryFor("weird"))
        assertEquals("video/mp4", Organizer.mimeType("clip.mp4"))
        assertEquals("image/jpeg", Organizer.mimeType("photo.jpg"))
        assertEquals("Download/VDR/Videos/", Organizer.mediaStoreRelativePath("Videos"))
        assertEquals("Downloads/VDR/Videos/clip.mp4", Organizer.publicDisplayPath("Videos", "clip.mp4"))
    }

    @Test
    fun wifiOnlyHoldsUntilUnmetered() {
        val dest = File.createTempFile("vdr-wifi", ".bin")
        dest.deleteOnExit()
        val queue = QueueManager()
        queue.setWifiOnly(true)
        queue.setUnmetered(false)
        queue.add(
            url = "https://cdn.example.com/clip.mp4",
            destFile = dest,
            displayName = "clip.mp4",
            category = "Videos",
            autoStart = true,
        )
        assertEquals(DownloadStatus.WIFI_HOLD, queue.snapshot().single().status)
        queue.remove(queue.snapshot().single().id)
    }

    @Test
    fun focusGuardPausesOnBattery() {
        assertEquals(FocusGuard.POLICY_HOLD, FocusGuard.decidePolicy(true, true, false, true))
        assertEquals(FocusGuard.POLICY_CRAWL, FocusGuard.decidePolicy(true, false, false, true))
        assertEquals(FocusGuard.POLICY_FULL, FocusGuard.decidePolicy(true, false, false, false))
        assertEquals(FocusGuard.POLICY_OFF, FocusGuard.decidePolicy(false, true, true, true))
    }

    @Test
    fun segmentedDownloadMatchesSourceBytes() {
        val payload = ByteArray(256 * 1024) { i -> (i % 251).toByte() }
        MockWebServer().use { server ->
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                    if (request.method == "HEAD") {
                        return MockResponse()
                            .setHeader("Accept-Ranges", "bytes")
                            .setHeader("Content-Length", payload.size)
                    }
                    val range = request.getHeader("Range")
                    if (range == null) {
                        return MockResponse()
                            .setHeader("Accept-Ranges", "bytes")
                            .setHeader("Content-Length", payload.size)
                            .setBody(Buffer().write(payload))
                    }
                    val spec = range.removePrefix("bytes=")
                    val start = spec.substringBefore('-').toInt()
                    val end = spec.substringAfter('-').ifBlank { (payload.size - 1).toString() }.toInt()
                    val slice = payload.copyOfRange(start, (end + 1).coerceAtMost(payload.size))
                    return MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Range", "bytes $start-$end/${payload.size}")
                        .setHeader("Accept-Ranges", "bytes")
                        .setBody(Buffer().write(slice))
                }
            }
            server.start()
            val dest = File.createTempFile("vdr", ".bin")
            dest.deleteOnExit()
            val done = CountDownLatch(1)
            var last = DownloadStatus.QUEUED
            val task = DownloadTask(
                id = "t1",
                url = server.url("/file.bin").toString(),
                destFile = dest,
                displayName = "file.bin",
                category = "Other",
                numSegmentsRequested = 8,
                bucket = TokenBucket(null),
            ) { t ->
                last = t.status
                if (t.status == DownloadStatus.COMPLETED || t.status == DownloadStatus.ERROR) {
                    done.countDown()
                }
            }
            task.start()
            assertTrue("download timed out, status=$last err=${task.errorMessage}", done.await(20, TimeUnit.SECONDS))
            assertEquals("err=${task.errorMessage}", DownloadStatus.COMPLETED, last)
            assertEquals(8, task.numSegments)
            assertArrayEquals(payload, dest.readBytes())
        }
    }

    @Test
    fun noRangeServerFallsBackToSingleStream() {
        val payload = "hello-vdr-single-stream".toByteArray()
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", payload.size)
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", payload.size)
                    .setBody(Buffer().write(payload))
            )
            server.start()
            val dest = File.createTempFile("vdr", ".txt")
            dest.deleteOnExit()
            val done = CountDownLatch(1)
            val task = DownloadTask(
                id = "t2",
                url = server.url("/plain.txt").toString(),
                destFile = dest,
                displayName = "plain.txt",
                category = "Documents",
                numSegmentsRequested = 8,
                bucket = TokenBucket(null),
            ) { t ->
                if (t.status == DownloadStatus.COMPLETED || t.status == DownloadStatus.ERROR) done.countDown()
            }
            task.start()
            assertTrue("timed out status=${task.status} err=${task.errorMessage}", done.await(20, TimeUnit.SECONDS))
            assertEquals("err=${task.errorMessage}", DownloadStatus.COMPLETED, task.status)
            assertEquals(1, task.numSegments)
            assertFalse(task.acceptRanges)
            assertEquals("hello-vdr-single-stream", dest.readText())
        }
    }
}
