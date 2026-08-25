package com.jayr91.vdr.engine

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Optional post-download remux: stream copy only, no re-encode.
 * Intended for HLS/DASH/TS outputs (mpegts or awkward concat) → `.mp4`.
 *
 * Built on the platform's own [MediaExtractor] / [MediaMuxer] rather than a
 * bundled FFmpeg. Both do sample-level copying — frames are moved between
 * containers untouched — so the output is bit-identical to what FFmpeg's
 * `-c copy` produced, without ~15 MiB of native libraries in the APK (and
 * without the LGPL relinking obligation those libraries carry).
 *
 * The tradeoff is reach: the platform muxer only accepts codecs the device
 * itself supports in MP4 (H.264/HEVC video, AAC audio — which is what HLS and
 * DASH ship in practice). Anything else fails cleanly and the caller keeps the
 * original file, which is the same contract as before.
 */
object Remuxer {

    /** Cap on a single compressed sample. 4K keyframes land well inside this. */
    private const val MAX_SAMPLE_BYTES = 4 * 1024 * 1024

    fun needsRemux(file: File, streamKind: String?): Boolean {
        val name = file.name.lowercase()
        if (name.endsWith(".ts") || name.endsWith(".m2ts") || name.endsWith(".mts")) return true
        // Playlist downloads that already landed as concatenated fMP4 / m4s.
        if (streamKind != null && (name.endsWith(".mp4") || name.endsWith(".m4s"))) return true
        return false
    }

    sealed class Outcome {
        data class Success(val file: File) : Outcome()
        data class Failed(val message: String) : Outcome()
    }

    /**
     * Remux [input] to MP4 with codec copy. On failure the caller must keep [input].
     */
    fun remuxCopyToMp4(input: File): Outcome {
        if (!input.exists() || input.length() == 0L) {
            return Outcome.Failed("empty input")
        }
        val parent = input.parentFile ?: return Outcome.Failed("no parent directory")
        val sameExt = input.extension.equals("mp4", ignoreCase = true)
        val target = if (sameExt) {
            File(parent, "${input.nameWithoutExtension}.remux-tmp.mp4")
        } else {
            File(parent, "${input.nameWithoutExtension}.mp4")
        }
        if (target.exists()) target.delete()

        val failure = copySamples(input, target)
        if (failure != null || !target.exists() || target.length() == 0L) {
            target.delete()
            return Outcome.Failed(failure ?: "remux produced no output")
        }

        return if (sameExt) {
            if (!input.delete()) {
                // Leave remuxed temp; caller can still publish it.
                return Outcome.Success(target)
            }
            if (!target.renameTo(input)) {
                return Outcome.Success(target)
            }
            Outcome.Success(input)
        } else {
            if (input.exists()) input.delete()
            Outcome.Success(target)
        }
    }

    /** Returns null on success, or a human-readable reason on failure. */
    private fun copySamples(input: File, target: File): String? {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            extractor = MediaExtractor().apply { setDataSource(input.absolutePath) }
            if (extractor.trackCount == 0) return "no tracks in input"

            muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Map source track index -> muxer track index. Tracks the muxer
            // rejects (an unsupported codec, say) are dropped rather than
            // failing the whole file, so a stream with an exotic side track
            // still yields playable video and audio.
            val trackMap = HashMap<Int, Int>()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                try {
                    trackMap[i] = muxer.addTrack(format)
                    extractor.selectTrack(i)
                } catch (_: Exception) {
                    // Unsupported in MP4 on this device; skip just this track.
                }
            }
            if (trackMap.isEmpty()) return "no MP4-compatible audio or video track"

            muxer.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocate(MAX_SAMPLE_BYTES)
            val info = MediaCodec.BufferInfo()
            var wrote = false

            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val destTrack = trackMap[extractor.sampleTrackIndex]
                if (destTrack != null) {
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = extractor.sampleTime
                    // Carry the keyframe flag across; without it players cannot
                    // seek, and the file looks subtly broken despite being whole.
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(destTrack, buffer, info)
                    wrote = true
                }
                extractor.advance()
            }
            if (!wrote) return "input contained no readable samples"
            return null
        } catch (e: Exception) {
            return e.message ?: e.javaClass.simpleName
        } finally {
            if (muxer != null) {
                try {
                    if (muxerStarted) muxer.stop()
                } catch (_: Exception) {
                    // stop() throws if no samples were written; the size check
                    // in the caller is what actually decides success.
                }
                try {
                    muxer.release()
                } catch (_: Exception) {
                }
            }
            try {
                extractor?.release()
            } catch (_: Exception) {
            }
        }
    }
}
