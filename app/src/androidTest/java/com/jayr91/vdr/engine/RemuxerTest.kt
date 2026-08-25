package com.jayr91.vdr.engine

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device coverage for the platform-muxer remux that replaced FFmpeg.
 *
 * MediaExtractor/MediaMuxer are provided by the OS, so this only means anything
 * when run on a real device — hence instrumented rather than a JVM unit test.
 * The asset is a 3-second H.264 + AAC MPEG-TS clip (~80 KB), which is the exact
 * shape HLS delivers.
 */
@RunWith(AndroidJUnit4::class)
class RemuxerTest {

    private fun tsFromAssets(name: String = "sample.ts"): File {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val target = File.createTempFile("vdr-remux-", ".ts")
        ctx.assets.open(name).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    private fun trackMimes(file: File): List<String> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).mapNotNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
            }
        } finally {
            extractor.release()
        }
    }

    @Test
    fun tsIsRemuxedToPlayableMp4() {
        val input = tsFromAssets()
        val sourceMimes = trackMimes(input)
        assertTrue("fixture should carry video", sourceMimes.any { it.startsWith("video/") })
        assertTrue("fixture should carry audio", sourceMimes.any { it.startsWith("audio/") })

        val outcome = Remuxer.remuxCopyToMp4(input)
        assertTrue("remux failed: $outcome", outcome is Remuxer.Outcome.Success)

        val output = (outcome as Remuxer.Outcome.Success).file
        try {
            assertTrue("output missing", output.exists())
            assertTrue("output is empty", output.length() > 0)
            assertEquals("should land as .mp4", "mp4", output.extension.lowercase())

            // The real assertion: the container changed but both elementary
            // streams survived. A stream copy that silently dropped audio would
            // still produce a playable file, so track-level checking is what
            // actually proves the copy was faithful.
            val outMimes = trackMimes(output)
            assertTrue("video track lost in remux", outMimes.any { it.startsWith("video/") })
            assertTrue("audio track lost in remux", outMimes.any { it.startsWith("audio/") })
        } finally {
            output.delete()
            input.delete()
        }
    }

    @Test
    fun needsRemuxFlagsTransportStreams() {
        assertTrue(Remuxer.needsRemux(File("/tmp/a.ts"), null))
        assertTrue(Remuxer.needsRemux(File("/tmp/a.m2ts"), null))
        // A plain .mp4 only needs remuxing when it came from a playlist concat.
        assertTrue(Remuxer.needsRemux(File("/tmp/a.mp4"), "hls"))
        assertTrue(!Remuxer.needsRemux(File("/tmp/a.mp4"), null))
        assertTrue(!Remuxer.needsRemux(File("/tmp/a.zip"), null))
    }

    @Test
    fun emptyInputFailsWithoutCrashing() {
        val empty = File.createTempFile("vdr-empty-", ".ts")
        try {
            val outcome = Remuxer.remuxCopyToMp4(empty)
            assertTrue("empty input should fail cleanly", outcome is Remuxer.Outcome.Failed)
        } finally {
            empty.delete()
        }
    }
}
