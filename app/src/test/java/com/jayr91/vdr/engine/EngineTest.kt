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
        assertEquals(
            DirectUrl.PAGE_ERROR,
            DirectUrl.rejectionMessage("https://www.netflix.com/watch/123"),
        )
        assertTrue(DirectUrl.isBlockedWatchPage("https://www.youtube.com/watch?v=abc"))
        assertFalse(DirectUrl.isBlockedWatchPage("https://cdn.example.com/clip.mp4"))
        assertEquals(null, DirectUrl.rejectionMessage("https://cdn.example.com/clip.mp4"))
        assertEquals(null, DirectUrl.rejectionMessage("https://cdn.example.com/stream.m3u8"))
        assertEquals(null, DirectUrl.rejectionMessage("https://cdn.example.com/manifest.mpd"))
        assertEquals(null, DirectUrl.rejectionMessage("https://cdn.example.com/subs.vtt"))
        assertEquals(null, DirectUrl.rejectionMessage("https://cdn.example.com/subs.srt"))
        assertEquals(null, DirectUrl.rejectionMessage("https://rr1---sn-abc.googlevideo.com/videoplayback"))
        assertEquals(
            null,
            DirectUrl.rejectionMessage("https://player.odycdn.com/v6/streams/abc/clip.mp4"),
        )
        assertEquals(null, DirectUrl.rejectionMessage("https://odysee.com/clip.mp4"))
        assertTrue(DirectUrl.isHtmlContentType("text/html; charset=utf-8"))
        assertFalse(DirectUrl.isHtmlContentType("video/mp4"))
        assertTrue(DirectUrl.isHlsContentType("application/vnd.apple.mpegurl"))
        assertTrue(DirectUrl.isDashContentType("application/dash+xml"))
        assertTrue(DirectUrl.looksLikeHlsUrl("https://cdn.example.com/a/master.m3u8?tok=1"))
        assertTrue(DirectUrl.looksLikeDashUrl("https://cdn.example.com/a/manifest.mpd"))
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
    fun vdrHttpBuildsParentReferer() {
        assertEquals(
            "https://www.w3schools.com/html/",
            VdrHttp.refererFor("https://www.w3schools.com/html/mov_bbb.mp4"),
        )
        assertEquals(
            "https://cdn.example.com/",
            VdrHttp.refererFor("https://cdn.example.com/clip.mp4"),
        )
    }

    @Test
    fun looksLikeDirectFileRecognizesArchiveAndDocuments() {
        assertTrue(
            DirectUrl.looksLikeDirectFile(
                "https://archive.org/download/ElephantsDream/ed_1024.mp4",
            ),
        )
        assertTrue(DirectUrl.looksLikeDirectFile("https://cdn.example.com/a.pdf"))
        assertTrue(DirectUrl.looksLikeDirectFile("https://cdn.example.com/a.zip"))
        assertTrue(DirectUrl.looksLikeDirectFile("https://cdn.example.com/a.m3u8?tok=1"))
        assertFalse(DirectUrl.looksLikeDirectFile("https://archive.org/details/ElephantsDream"))
        // Description pages that embed a filename must NOT bypass HTML extract.
        assertFalse(
            DirectUrl.looksLikeDirectFile(
                "https://archive.org/details/BBC_Sherlock2010/3x00+Many+Happy+Returns.mp4",
            ),
        )
        assertFalse(
            DirectUrl.looksLikeDirectFile(
                "https://commons.wikimedia.org/wiki/File:Example.ogg",
            ),
        )
        assertTrue(
            MediaGrabber.looksLikeMediaUrl(
                "https://archive.org/download/ElephantsDream/ed_1024.mp4",
            ),
        )
    }

    @Test
    fun htmlExtractFindsOgVideoJsonLdAndAbsoluteUrls() {
        val html = """
            <html><head>
            <meta property="og:video" content="https://cdn.example.com/og/clip.mp4">
            <meta name="twitter:player:stream" content="https://cdn.example.com/tw/stream.m3u8">
            <script type="application/ld+json">
            {"@type":"VideoObject","contentUrl":"https://cdn.example.com/ld/video.webm"}
            </script>
            </head><body>
            <p>Also see https://cdn.example.com/abs/track.mp3?x=1 in text</p>
            <a href="/rel/sample.mp4">rel</a>
            <embed src="/embed/demo.ogg">
            </body></html>
        """.trimIndent()
        val base = "https://pages.example.com/lesson/"
        val found = MediaGrabber.extractMediaUrlsFromHtml(html, base)
        assertTrue(found.contains("https://cdn.example.com/og/clip.mp4"))
        assertTrue(found.contains("https://cdn.example.com/tw/stream.m3u8"))
        assertTrue(found.contains("https://cdn.example.com/ld/video.webm"))
        assertTrue(found.contains("https://cdn.example.com/abs/track.mp3?x=1"))
        assertTrue(found.contains("https://pages.example.com/rel/sample.mp4"))
        assertTrue(found.contains("https://pages.example.com/embed/demo.ogg"))
        // Prefer clear mp4; longer absolute paths rank above short relative ones.
        assertTrue(found.first().endsWith(".mp4"))
        assertTrue(found.any { it.contains("/og/clip.mp4") })
        // .webmanifest must not be truncated to .webm
        val junk = MediaGrabber.extractMediaUrlsFromHtml(
            """<link rel="manifest" href="/site.webmanifest">""",
            "https://samplelib.com/",
        )
        assertTrue(junk.none { it.endsWith(".webm") || it.contains("webmanifest") })
    }

    @Test
    fun probePageUrlFindsOgVideoOnHtmlPage() {
        MockWebServer().use { server ->
            server.start()
            val media = server.url("/files/small.mp3").toString()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html; charset=utf-8")
                    .setBody(
                        """
                        <html><head>
                        <meta property="og:video" content="$media">
                        </head><body><p>details</p></body></html>
                        """.trimIndent(),
                    ),
            )
            val page = server.url("/details/item/file.mp3").toString()
            assertFalse(DirectUrl.looksLikeDirectFile(page))
            when (val result = MediaGrabber.probePageUrl(page)) {
                is PageProbeResult.Media -> {
                    assertEquals(listOf(media), result.urls)
                }
                else -> error("expected Media, got $result")
            }
        }
    }

    @Test
    fun downloadTaskRetargetsWhenHtmlBodyContainsMedia() {
        MockWebServer().use { server ->
            server.start()
            val mediaBody = "fake-mp3-bytes"
            val mediaUrl = server.url("/real/clip.mp3").toString()
            // HEAD → HTML (disguise); empty body (real HEAD); Connection drained in probe.
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html")
                    .setHeader("Content-Length", "0"),
            )
            // Range GET → HTML page that embeds the real file
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html; charset=utf-8")
                    .setBody(
                        """
                        <html><head>
                        <meta property="og:video" content="$mediaUrl">
                        </head></html>
                        """.trimIndent(),
                    ),
            )
            // reprobe Range on extracted URL
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Type", "audio/mpeg")
                    .setHeader("Content-Range", "bytes 0-0/${mediaBody.length}")
                    .setHeader("Content-Length", "1")
                    .setBody("f"),
            )
            // segment download
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Type", "audio/mpeg")
                    .setHeader("Content-Range", "bytes 0-${mediaBody.length - 1}/${mediaBody.length}")
                    .setBody(mediaBody),
            )
            val dest = File.createTempFile("vdr-retarget", ".bin")
            dest.deleteOnExit()
            val done = CountDownLatch(1)
            val task = DownloadTask(
                id = "retarget",
                url = server.url("/details/item/clip.mp3").toString(),
                destFile = dest,
                displayName = "clip.mp3",
                category = "Audio",
                bucket = TokenBucket(null),
                numSegmentsRequested = 1,
            ) { t ->
                if (t.status == DownloadStatus.COMPLETED || t.status == DownloadStatus.ERROR) done.countDown()
            }
            task.start()
            assertTrue(
                "timed out status=${task.status} err=${task.errorMessage}",
                done.await(15, TimeUnit.SECONDS),
            )
            assertEquals(
                "err=${task.errorMessage}",
                DownloadStatus.COMPLETED,
                task.status,
            )
            assertEquals(mediaBody, dest.readText())
        }
    }

    @Test
    fun probeHtmlOnlyPageReportsNoMedia() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html; charset=utf-8")
                    .setBody("<html><body><p>Hello with no video</p></body></html>"),
            )
            server.start()
            assertEquals(
                PageProbeResult.HtmlNoMedia,
                MediaGrabber.probePageUrl(server.url("/empty.html").toString()),
            )
        }
    }

    @Test
    fun probeDirectFileByPathSkipsHtmlExtract() {
        // Even without hitting the network, path classification must win.
        assertEquals(
            PageProbeResult.DirectFile,
            MediaGrabber.probePageUrl(
                "https://archive.org/download/ElephantsDream/ed_1024.mp4",
            ),
        )
    }

    @Test
    fun mediaGrabberDetectsDirectMediaAndDedupes() {
        assertTrue(MediaGrabber.looksLikeMediaUrl("https://www.w3schools.com/html/mov_bbb.mp4"))
        assertTrue(MediaGrabber.isClearDownloadable("https://cdn.example.com/a.m3u8"))
        assertTrue(MediaGrabber.isClearDownloadable("https://cdn.example.com/a.mpd"))
        assertTrue(MediaGrabber.isMediaMime("video/mp4"))
        assertTrue(MediaGrabber.isClearDownloadable("https://cdn.example.com/stream", "video/mp4"))
        assertFalse(MediaGrabber.isClearDownloadable("https://www.youtube.com/watch?v=abc"))
        assertFalse(MediaGrabber.looksLikeMediaUrl("https://cdn.example.com/hls/seg-12.ts"))
        assertTrue(MediaGrabber.looksLikeMediaUrl("https://cdn.example.com/clip.ts"))

        val session = GrabPromptSession(cooldownMs = 60_000L, promptGapMs = 2 * 60_000L)
        val url = "https://cdn.example.com/mov_bbb.mp4?token=1"
        assertTrue(session.shouldOffer(url, now = 1_000L))
        assertTrue(session.tryBeginOffer(url, now = 1_000L))
        assertFalse(session.shouldOffer(url, now = 2_000L))
        assertFalse(session.tryBeginOffer(url, now = 2_000L))
        session.markDismissed(url, now = 3_000L)
        assertFalse(session.shouldOffer(url, now = 4_000L))
        assertTrue(session.shouldOffer(url, now = 3_000L + 60_000L))
        // Global 2-min gap still blocks after dismiss even when URL cooldown elapsed.
        assertFalse(session.tryBeginOffer(url, now = 3_000L + 60_000L))
        assertEquals(2 * 60_000L - 60_000L, session.millisUntilNextPrompt(now = 3_000L + 60_000L))
        assertTrue(session.tryBeginOffer(url, now = 3_000L + 2 * 60_000L))
        session.markQueued(url, now = 200_000L)
        assertFalse(session.shouldOffer(url, now = 300_000L))

        // Different URL on same session must wait for the global prompt gap.
        val other = "https://cdn.example.com/other.mp4"
        val gapSession = GrabPromptSession(cooldownMs = 60_000L, promptGapMs = 120_000L)
        assertTrue(gapSession.tryBeginOffer(url, now = 0L))
        gapSession.markDismissed(url, now = 1_000L)
        assertTrue(gapSession.shouldOffer(other, now = 2_000L))
        assertFalse(gapSession.tryBeginOffer(other, now = 2_000L))
        assertTrue(gapSession.tryBeginOffer(other, now = 1_000L + 120_000L))

        // Future lastPromptAt must not block Browse prompts forever.
        val skew = GrabPromptSession(cooldownMs = 60_000L, promptGapMs = 120_000L)
        assertTrue(skew.tryBeginOffer(url, now = 5_000_000L))
        assertEquals(0L, skew.millisUntilNextPrompt(now = 1_000L))
        assertTrue(skew.tryBeginOffer(other, now = 1_000L))
    }

    @Test
    fun filterVideoMediaKeepsVideoAndStreamsOnly() {
        val mixed = listOf(
            "https://cdn.example.com/clip.mp4",
            "https://cdn.example.com/track.mp3",
            "https://cdn.example.com/live.m3u8",
            "https://cdn.example.com/manifest.mpd",
        )
        assertEquals(
            listOf(
                "https://cdn.example.com/clip.mp4",
                "https://cdn.example.com/live.m3u8",
                "https://cdn.example.com/manifest.mpd",
            ),
            MediaGrabber.filterVideoMedia(mixed),
        )
        assertTrue(MediaGrabber.isVideoishMedia("https://cdn.example.com/a.webm"))
        assertFalse(MediaGrabber.isVideoishMedia("https://cdn.example.com/a.mp3"))
        assertEquals(".mp4", MediaGrabber.mediaTypeLabel("https://cdn.example.com/clip.mp4"))
        assertEquals("HLS stream", MediaGrabber.mediaTypeLabel("https://cdn.example.com/a.m3u8"))
        assertEquals(
            listOf("https://cdn.example.com/direct.mp4"),
            MediaGrabber.urlsForPageMediaPicker(
                PageProbeResult.DirectFile,
                "https://cdn.example.com/direct.mp4",
            ),
        )
        val pageMedia = MediaGrabber.urlsForPageMediaPicker(
            PageProbeResult.Media(
                listOf(
                    "https://cdn.example.com/vid.mp4",
                    "https://cdn.example.com/pod.mp3",
                ),
            ),
            "https://pages.example.com/lesson",
        )
        assertEquals(listOf("https://cdn.example.com/vid.mp4"), pageMedia)
    }

    @Test
    fun htmlExtractFindsRelativeVideoAndPrefersMp4() {
        val html = """
            <html><body>
            <video id="video1" controls>
              <source src="mov_bbb.mp4" type="video/mp4">
              <source src="mov_bbb.ogg" type="video/ogg">
            </video>
            <a href="https://www.youtube.com/@w3schools">YouTube channel</a>
            </body></html>
        """.trimIndent()
        val base = "https://www.w3schools.com/html/html5_video.asp"
        val found = MediaGrabber.extractMediaUrlsFromHtml(html, base)
        assertEquals(
            listOf(
                "https://www.w3schools.com/html/mov_bbb.mp4",
                "https://www.w3schools.com/html/mov_bbb.ogg",
            ),
            found,
        )
        assertFalse(MediaGrabber.pageHasYoutube(html))
    }

    @Test
    fun youtubeOnlyPageIsDetectedWithoutDirectMedia() {
        val html = """
            <html><body>
            <h2>Video: Python Introduction</h2>
            <a href="https://youtu.be/xkZMUX_oQX4&list=PLP9IO4UYNF0UgPfkTBECSKIJGdc_9FYZ9">Watch</a>
            <iframe src="https://www.youtube.com/embed/xkZMUX_oQX4"></iframe>
            </body></html>
        """.trimIndent()
        assertTrue(MediaGrabber.pageHasYoutube(html))
        assertTrue(
            MediaGrabber.extractMediaUrlsFromHtml(html, "https://www.w3schools.com/python/").isEmpty(),
        )
    }

    @Test
    fun probePageUrlFindsEmbeddedMp4WithoutSavingHtml() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html; charset=utf-8")
                    .setBody(
                        """
                        <html><body>
                        <video controls><source src="clip.mp4" type="video/mp4"></video>
                        </body></html>
                        """.trimIndent(),
                    ),
            )
            server.start()
            val page = server.url("/lesson.html").toString()
            when (val result = MediaGrabber.probePageUrl(page)) {
                is PageProbeResult.Media -> {
                    assertEquals(listOf(server.url("/clip.mp4").toString()), result.urls)
                }
                else -> error("expected Media, got $result")
            }
        }
    }

    @Test
    fun probePageUrlReportsYoutubeOnly() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html")
                    .setBody(
                        """<html><iframe src="https://www.youtube.com/embed/abc123"></iframe></html>""",
                    ),
            )
            server.start()
            assertEquals(
                PageProbeResult.YoutubeOnly,
                MediaGrabber.probePageUrl(server.url("/py.html").toString()),
            )
        }
    }

    @Test
    fun htmlProbeFailsInsteadOfSavingThePage() {
        MockWebServer().use { server ->
            // HEAD then Range GET — both HTML with no extractable media.
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html; charset=utf-8")
                    .setHeader("Content-Length", "0"),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html; charset=utf-8")
                    .setBody("<html><body>watch page</body></html>"),
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
        assertEquals("Videos", Organizer.categoryFor("chunk.ts"))
        assertEquals("Videos", Organizer.categoryFor("seg.m4s"))
        assertEquals("Videos", Organizer.categoryFor("clip.m4v"))
        assertEquals("Documents", Organizer.categoryFor("notes.pdf"))
        assertEquals("Subtitles", Organizer.categoryFor("movie.vtt"))
        assertEquals("Subtitles", Organizer.categoryFor("movie.srt"))
        assertEquals("Audio", Organizer.categoryFor("track.mp3"))
        assertEquals("Audio", Organizer.categoryFor("track.flac"))
        assertEquals("download", Organizer.safeFilename("../.."))
        assertEquals("Other", Organizer.categoryFor("weird"))
        assertEquals("video/mp4", Organizer.mimeType("clip.mp4"))
        assertEquals("video/mp2t", Organizer.mimeType("chunk.ts"))
        assertEquals("text/vtt", Organizer.mimeType("subs.vtt"))
        assertEquals("image/jpeg", Organizer.mimeType("photo.jpg"))
        assertEquals("stream.ts", Organizer.outputNameForUrl("https://cdn.example/a/stream.m3u8"))
        assertEquals("show.mp4", Organizer.outputNameForUrl("https://cdn.example/a/show.mpd"))
        assertEquals("Download/VDR/Videos/", Organizer.mediaStoreRelativePath("Videos"))
        assertEquals("Downloads/VDR/Videos/clip.mp4", Organizer.publicDisplayPath("Videos", "clip.mp4"))
    }

    @Test
    fun hlsPlaylistParserPicksHighestVariantAndSkipsEncrypted() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
            low.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
            hi.m3u8
        """.trimIndent()
        val parsed = HlsPlaylist.parse(master, "https://cdn.example/master.m3u8") as HlsPlaylist.Parsed.Master
        val best = HlsPlaylist.pickHighestBandwidth(parsed.variants)
        assertEquals(2500000, best.bandwidth)
        assertEquals("https://cdn.example/hi.m3u8", best.uri)

        val media = """
            #EXTM3U
            #EXT-X-TARGETDURATION:4
            #EXTINF:4.0,
            a.ts
            #EXTINF:4.0,
            b.ts
        """.trimIndent()
        val m = (HlsPlaylist.parse(media, "https://cdn.example/hi.m3u8") as HlsPlaylist.Parsed.Media).playlist
        assertEquals(2, m.segments.size)
        assertFalse(m.encrypted)
        assertEquals("https://cdn.example/a.ts", m.segments[0].uri)

        val enc = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="key.key"
            #EXTINF:1,
            a.ts
        """.trimIndent()
        val e = (HlsPlaylist.parse(enc, "https://cdn.example/enc.m3u8") as HlsPlaylist.Parsed.Media).playlist
        assertTrue(e.encrypted)
    }

    @Test
    fun hlsDownloadConcatenatesTsSegments() {
        val segA = byteArrayOf(0x47, 1, 2, 3)
        val segB = byteArrayOf(0x47, 4, 5, 6)
        val segC = byteArrayOf(0x47, 7, 8, 9)
        MockWebServer().use { server ->
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                    val path = request.path ?: ""
                    return when {
                        path.endsWith("/master.m3u8") -> MockResponse()
                            .setHeader("Content-Type", "application/vnd.apple.mpegurl")
                            .setBody(
                                """
                                #EXTM3U
                                #EXT-X-STREAM-INF:BANDWIDTH=1000000
                                media.m3u8
                                """.trimIndent(),
                            )
                        path.endsWith("/media.m3u8") -> MockResponse()
                            .setHeader("Content-Type", "application/vnd.apple.mpegurl")
                            .setBody(
                                """
                                #EXTM3U
                                #EXT-X-TARGETDURATION:2
                                #EXTINF:2.0,
                                a.ts
                                #EXTINF:2.0,
                                b.ts
                                #EXTINF:2.0,
                                c.ts
                                """.trimIndent(),
                            )
                        path.endsWith("/a.ts") -> MockResponse().setBody(Buffer().write(segA))
                        path.endsWith("/b.ts") -> MockResponse().setBody(Buffer().write(segB))
                        path.endsWith("/c.ts") -> MockResponse().setBody(Buffer().write(segC))
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            server.start()
            val dest = File.createTempFile("vdr-hls", ".ts")
            dest.deleteOnExit()
            val done = CountDownLatch(1)
            val task = DownloadTask(
                id = "hls1",
                url = server.url("/master.m3u8").toString(),
                destFile = dest,
                displayName = dest.name,
                category = "Videos",
                bucket = TokenBucket(null),
            ) { t ->
                if (t.status == DownloadStatus.COMPLETED || t.status == DownloadStatus.ERROR) done.countDown()
            }
            task.start()
            assertTrue("timed out status=${task.status} err=${task.errorMessage}", done.await(20, TimeUnit.SECONDS))
            assertEquals("err=${task.errorMessage}", DownloadStatus.COMPLETED, task.status)
            assertEquals(3, task.numSegments)
            assertArrayEquals(segA + segB + segC, task.outputFile.readBytes())
        }
    }

    @Test
    fun dashDownloadConcatenatesInitAndMedia() {
        val init = byteArrayOf(0, 0, 0, 8, 0x66, 0x74, 0x79, 0x70) // tiny fake ftyp
        val m1 = byteArrayOf(1, 2, 3, 4)
        val m2 = byteArrayOf(5, 6, 7, 8)
        MockWebServer().use { server ->
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                    val path = request.path ?: ""
                    return when {
                        path.endsWith("/manifest.mpd") -> {
                            val base = server.url("/").toString()
                            MockResponse()
                                .setHeader("Content-Type", "application/dash+xml")
                                .setBody(
                                    """
                                    <?xml version="1.0"?>
                                    <MPD mediaPresentationDuration="PT6S" type="static">
                                      <Period>
                                        <AdaptationSet mimeType="video/mp4">
                                          <Representation id="1" bandwidth="500000" width="640" height="360">
                                            <SegmentTemplate timescale="1000" duration="2000" startNumber="1"
                                              initialization="init.m4s" media="seg-${'$'}Number${'$'}.m4s"/>
                                          </Representation>
                                          <Representation id="2" bandwidth="2000000" width="1280" height="720">
                                            <SegmentTemplate timescale="1000" duration="2000" startNumber="1"
                                              initialization="init-hi.m4s" media="hi-${'$'}Number${'$'}.m4s"/>
                                          </Representation>
                                        </AdaptationSet>
                                      </Period>
                                    </MPD>
                                    """.trimIndent().replace("BASE", base),
                                )
                        }
                        path.endsWith("/init-hi.m4s") -> MockResponse().setBody(Buffer().write(init))
                        path.endsWith("/hi-1.m4s") -> MockResponse().setBody(Buffer().write(m1))
                        path.endsWith("/hi-2.m4s") -> MockResponse().setBody(Buffer().write(m2))
                        path.endsWith("/hi-3.m4s") -> MockResponse().setBody(Buffer().write(byteArrayOf(9)))
                        else -> MockResponse().setResponseCode(404).setBody("missing $path")
                    }
                }
            }
            server.start()
            val dest = File.createTempFile("vdr-dash", ".mp4")
            dest.deleteOnExit()
            val done = CountDownLatch(1)
            val task = DownloadTask(
                id = "dash1",
                url = server.url("/manifest.mpd").toString(),
                destFile = dest,
                displayName = dest.name,
                category = "Videos",
                bucket = TokenBucket(null),
            ) { t ->
                if (t.status == DownloadStatus.COMPLETED || t.status == DownloadStatus.ERROR) done.countDown()
            }
            task.start()
            assertTrue("timed out status=${task.status} err=${task.errorMessage}", done.await(20, TimeUnit.SECONDS))
            assertEquals("err=${task.errorMessage}", DownloadStatus.COMPLETED, task.status)
            // PT6S / 2s segments → 3 media + init
            assertEquals(4, task.numSegments)
            assertArrayEquals(init + m1 + m2 + byteArrayOf(9), task.outputFile.readBytes())
        }
    }

    @Test
    fun dashDrmManifestIsRejected() {
        val xml = """
            <?xml version="1.0"?>
            <MPD mediaPresentationDuration="PT2S">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <ContentProtection schemeIdUri="urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"/>
                  <Representation id="1" bandwidth="1000">
                    <SegmentTemplate timescale="1" duration="1" startNumber="1"
                      initialization="init.m4s" media="s-${'$'}Number${'$'}.m4s"/>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        val m = DashMpd.parse(xml, "https://cdn.example/drm.mpd")
        assertTrue(m.protected)
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
