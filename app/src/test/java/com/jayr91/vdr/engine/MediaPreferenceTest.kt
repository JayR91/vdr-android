package com.jayr91.vdr.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Candidate ordering. Sharing a page queues whatever lands first here, so a
 * wrong order does not fail loudly -- it hands over a worse file and reports
 * success, which is how a 360p copy arrived from a page offering 1080p.
 */
class MediaPreferenceTest {

    private val dl = "https://video.deeplearning.ai/OpenAI/ChatGPT_Prompt_Engineering_for_Developer/prompt_eng_01"

    @Test
    fun manifestBeatsAnExplicitlyLowResMp4() {
        // The real deeplearning.ai lesson case.
        val ordered = MediaGrabber.preferMediaOrder(
            listOf(
                "$dl/prompt_eng_01_master_360p.mp4?v=1781235070",
                "$dl/prompt_eng_01-master.m3u8?v=1781235070",
                "$dl/prompt_eng_01_master.m3u8?v=1781235070",
            )
        )
        assertTrue(
            "a 360p mp4 must not outrank an HLS ladder, got ${ordered.first()}",
            ordered.first().endsWith(".m3u8") || ordered.first().contains(".m3u8?"),
        )
    }

    @Test
    fun plainFileWinsWhenQualityMatches() {
        // Nothing advertises a resolution: prefer the single request over
        // fetching and stitching segments for the same picture.
        val ordered = MediaGrabber.preferMediaOrder(
            listOf("https://e.com/v/index.m3u8", "https://e.com/v/movie.mp4")
        )
        assertEquals("https://e.com/v/movie.mp4", ordered.first())
    }

    @Test
    fun higherResolutionWinsAmongPlainFiles() {
        val ordered = MediaGrabber.preferMediaOrder(
            listOf(
                "https://e.com/v/clip_360p.mp4",
                "https://e.com/v/clip_1080p.mp4",
                "https://e.com/v/clip_720p.mp4",
            )
        )
        assertEquals("https://e.com/v/clip_1080p.mp4", ordered.first())
    }

    @Test
    fun dimensionStyleNamesAreUnderstood() {
        val ordered = MediaGrabber.preferMediaOrder(
            listOf("https://e.com/v/a_640x360.mp4", "https://e.com/v/b_1920x1080.mp4")
        )
        assertEquals("https://e.com/v/b_1920x1080.mp4", ordered.first())
    }

    @Test
    fun unlabelledIsAssumedGoodSoItBeatsAKnownLowRes() {
        val ordered = MediaGrabber.preferMediaOrder(
            listOf("https://e.com/v/clip_240p.mp4", "https://e.com/v/clip.mp4")
        )
        assertEquals("https://e.com/v/clip.mp4", ordered.first())
    }

    @Test
    fun heightParsingReadsBothStyles() {
        assertEquals(360, MediaGrabber.advertisedHeight("https://e.com/a_360p.mp4"))
        assertEquals(1080, MediaGrabber.advertisedHeight("https://e.com/a_1920x1080.mp4"))
        assertEquals(MediaGrabber.ASSUMED_HEIGHT, MediaGrabber.advertisedHeight("https://e.com/a.mp4"))
        // A version query must not be mistaken for a resolution.
        assertEquals(MediaGrabber.ASSUMED_HEIGHT, MediaGrabber.advertisedHeight("https://e.com/a.mp4?v=1781235070"))
    }

    @Test
    fun postersAndThumbnailsStillSortLast() {
        val ordered = MediaGrabber.preferMediaOrder(
            listOf("https://e.com/v/poster.mp4", "https://e.com/v/movie.mp4")
        )
        assertEquals("https://e.com/v/movie.mp4", ordered.first())
    }
}
