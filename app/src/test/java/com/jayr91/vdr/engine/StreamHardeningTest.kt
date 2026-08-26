package com.jayr91.vdr.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressions for stream-manifest handling.
 *
 * Every case here previously produced a *plausible* result rather than an
 * error: a download that reported success while writing a file that would not
 * play, or a page scan that reported "no media" for a page that had plenty.
 * Silent wrongness is the thing being pinned, so each test asserts the
 * failure is now visible.
 */
class StreamHardeningTest {

    // --- DASH: padded $Number%0Nd$ templates --------------------------------

    @Test
    fun paddedNumberTemplateIsExpanded() {
        val xml = """
            <?xml version="1.0"?>
            <MPD mediaPresentationDuration="PT6S" type="static">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <Representation id="v0" bandwidth="800000">
                    <SegmentTemplate timescale="1000" duration="2000" startNumber="1"
                      initialization="init.m4s" media="seg-${'$'}Number%05d${'$'}.m4s"/>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        val rep = DashMpd.parse(xml, "https://cdn.example/v/manifest.mpd").bestVideo
        assertNotNull(rep)
        // The literal token used to survive into the URL, 404ing every segment.
        rep!!.mediaUrls.forEach {
            assertFalse("template left unexpanded: $it", it.contains("%05d") || it.contains("${'$'}Number"))
        }
        assertEquals("https://cdn.example/v/seg-00001.m4s", rep.mediaUrls.first())
        assertEquals("https://cdn.example/v/seg-00003.m4s", rep.mediaUrls.last())
    }

    @Test
    fun plainNumberTemplateStillWorks() {
        val xml = """
            <?xml version="1.0"?>
            <MPD mediaPresentationDuration="PT4S" type="static">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <Representation id="v0" bandwidth="800000">
                    <SegmentTemplate timescale="1000" duration="2000" startNumber="1"
                      initialization="init.m4s" media="seg-${'$'}Number${'$'}.m4s"/>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        val rep = DashMpd.parse(xml, "https://cdn.example/v/manifest.mpd").bestVideo
        assertNotNull(rep)
        assertEquals("https://cdn.example/v/seg-1.m4s", rep!!.mediaUrls.first())
        assertEquals(2, rep.mediaUrls.size)
    }

    // --- DASH: cases we must refuse rather than fake -------------------------

    @Test
    fun segmentTimelineIsFlagged() {
        val xml = """
            <?xml version="1.0"?>
            <MPD mediaPresentationDuration="PT6S">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <Representation id="v0" bandwidth="800000">
                    <SegmentTemplate timescale="1000" media="seg-${'$'}Time${'$'}.m4s">
                      <SegmentTimeline><S t="0" d="2000" r="2"/></SegmentTimeline>
                    </SegmentTemplate>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        // $Time$ used to be replaced with a constant "0", making every segment
        // URL identical -- the same two seconds written N times.
        assertTrue(DashMpd.parse(xml, "https://cdn.example/t.mpd").usesTimeline)
    }

    @Test
    fun missingDurationIsNotGuessed() {
        // No mediaPresentationDuration: the old code invented three segments,
        // truncating a full video to a few seconds and calling it complete.
        assertNull(DashMpd.segmentCount(duration = 2000, timescale = 1000, presentationMs = null))
        assertNull(DashMpd.segmentCount(duration = 0, timescale = 1000, presentationMs = 6000))
        assertEquals(3, DashMpd.segmentCount(duration = 2000, timescale = 1000, presentationMs = 6000))
    }

    @Test
    fun segmentCountIsCapped() {
        // A manifest claiming a century of video must not expand unbounded.
        val huge = DashMpd.segmentCount(duration = 1, timescale = 1000, presentationMs = 100L * 365 * 86400 * 1000)
        assertEquals(DashMpd.MAX_SEGMENTS, huge)
    }

    // --- HLS: byte-range playlists ------------------------------------------

    @Test
    fun byteRangePlaylistIsFlagged() {
        val text = """
            #EXTM3U
            #EXT-X-TARGETDURATION:10
            #EXTINF:10.0,
            #EXT-X-BYTERANGE:75232@0
            video.ts
            #EXTINF:10.0,
            #EXT-X-BYTERANGE:82112@75232
            video.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val parsed = HlsPlaylist.parse(text, "https://cdn.example/v/index.m3u8")
        val media = (parsed as HlsPlaylist.Parsed.Media).playlist
        // Both entries are slices of one file: downloading each whole and
        // concatenating produced a double-length file of repeated bytes.
        assertTrue(media.hasByteRange)
    }

    @Test
    fun ordinaryPlaylistIsNotFlagged() {
        val text = """
            #EXTM3U
            #EXTINF:10.0,
            a.ts
            #EXTINF:10.0,
            b.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val media = (HlsPlaylist.parse(text, "https://cdn.example/v/i.m3u8") as HlsPlaylist.Parsed.Media).playlist
        assertFalse(media.hasByteRange)
        assertEquals(2, media.segments.size)
        assertEquals("https://cdn.example/v/a.ts", media.segments.first().uri)
    }

    @Test
    fun hugePlaylistIsCapped() {
        // Playlists are remote text; an unbounded one should not be able to
        // exhaust memory before a single byte is fetched.
        val body = buildString {
            append("#EXTM3U\n")
            repeat(HlsPlaylist.MAX_SEGMENTS + 500) { append("#EXTINF:1.0,\ns$it.ts\n") }
        }
        val media = (HlsPlaylist.parse(body, "https://cdn.example/v/i.m3u8") as HlsPlaylist.Parsed.Media).playlist
        assertEquals(HlsPlaylist.MAX_SEGMENTS, media.segments.size)
    }

    @Test
    fun encryptedPlaylistStillRejected() {
        val text = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="k.key"
            #EXTINF:10.0,
            a.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val media = (HlsPlaylist.parse(text, "https://cdn.example/v/i.m3u8") as HlsPlaylist.Parsed.Media).playlist
        assertTrue(media.encrypted)
    }
}
