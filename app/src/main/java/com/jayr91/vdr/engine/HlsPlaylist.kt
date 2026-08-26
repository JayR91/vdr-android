package com.jayr91.vdr.engine

import java.net.URI

/**
 * Minimal HLS playlist parser for direct .m3u8 downloads.
 * Master → highest-bandwidth variant; media → segment list + optional EXT-X-MAP.
 * Encrypted playlists (EXT-X-KEY METHOD other than NONE) are rejected.
 */
object HlsPlaylist {
    const val ENCRYPTED_ERROR = "Encrypted HLS not supported"
    const val BYTERANGE_ERROR =
        "This HLS stream splits segments by byte range, which VDR can't download yet"

    /**
     * Ceiling on segments taken from one playlist. A playlist is remote,
     * attacker-influenceable text; without a cap, a file listing millions of
     * segments turns into an unbounded list allocation before a single byte
     * is fetched.
     */
    const val MAX_SEGMENTS = 20_000

    data class Variant(
        val bandwidth: Int,
        val uri: String,
        val resolution: String? = null,
    )

    data class Segment(
        val uri: String,
        val durationSec: Double? = null,
    )

    data class MediaPlaylist(
        val playlistUrl: String,
        val initMapUri: String?,
        val segments: List<Segment>,
        val encrypted: Boolean,
        /**
         * `#EXT-X-BYTERANGE` means consecutive entries are slices of one file
         * rather than separate files, so the URI repeats. Downloading each
         * entry whole and concatenating produced a file several times the
         * right size, containing the same bytes over and over -- it looked
         * like a completed download and would not play.
         */
        val hasByteRange: Boolean = false,
    )

    sealed class Parsed {
        data class Master(val variants: List<Variant>) : Parsed()
        data class Media(val playlist: MediaPlaylist) : Parsed()
    }

    fun isPlaylistBody(text: String): Boolean =
        text.trimStart().startsWith("#EXTM3U")

    fun parse(text: String, playlistUrl: String): Parsed {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.none { it.startsWith("#EXTM3U") }) {
            throw IllegalStateException("Not an HLS playlist (#EXTM3U missing)")
        }
        val hasMaster = lines.any { it.startsWith("#EXT-X-STREAM-INF") }
        return if (hasMaster) {
            Parsed.Master(parseMaster(lines, playlistUrl))
        } else {
            Parsed.Media(parseMedia(lines, playlistUrl))
        }
    }

    fun pickHighestBandwidth(variants: List<Variant>): Variant {
        require(variants.isNotEmpty()) { "Master playlist has no variants" }
        return variants.maxBy { it.bandwidth }
    }

    fun resolveUri(baseUrl: String, ref: String): String {
        val trimmed = ref.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }
        return try {
            URI(baseUrl).resolve(trimmed).toString()
        } catch (_: Exception) {
            val base = baseUrl.substringBeforeLast('/') + "/"
            base + trimmed.trimStart('/')
        }
    }

    private fun parseMaster(lines: List<String>, playlistUrl: String): List<Variant> {
        val variants = mutableListOf<Variant>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val attrs = parseAttributes(line.substringAfter(':'))
                val bandwidth = attrs["BANDWIDTH"]?.toIntOrNull()
                    ?: attrs["AVERAGE-BANDWIDTH"]?.toIntOrNull()
                    ?: 0
                val resolution = attrs["RESOLUTION"]
                var j = i + 1
                while (j < lines.size && lines[j].startsWith("#")) j++
                if (j < lines.size) {
                    variants += Variant(
                        bandwidth = bandwidth,
                        uri = resolveUri(playlistUrl, lines[j]),
                        resolution = resolution,
                    )
                    i = j
                }
            }
            i++
        }
        if (variants.isEmpty()) throw IllegalStateException("Master playlist has no playable variants")
        return variants
    }

    private fun parseMedia(lines: List<String>, playlistUrl: String): MediaPlaylist {
        var encrypted = false
        var hasByteRange = false
        var initMapUri: String? = null
        val segments = mutableListOf<Segment>()
        var pendingDuration: Double? = null

        for (line in lines) {
            if (segments.size >= MAX_SEGMENTS) break
            when {
                line.startsWith("#EXT-X-BYTERANGE") -> hasByteRange = true
                line.startsWith("#EXT-X-KEY") -> {
                    val attrs = parseAttributes(line.substringAfter(':'))
                    val method = attrs["METHOD"]?.uppercase() ?: "NONE"
                    if (method != "NONE") encrypted = true
                }
                line.startsWith("#EXT-X-MAP") -> {
                    val attrs = parseAttributes(line.substringAfter(':'))
                    attrs["URI"]?.let { initMapUri = resolveUri(playlistUrl, it) }
                }
                line.startsWith("#EXTINF") -> {
                    val raw = line.substringAfter(':').substringBefore(',')
                    pendingDuration = raw.toDoubleOrNull()
                }
                line.startsWith("#") -> {
                    // ignore other tags
                }
                else -> {
                    segments += Segment(
                        uri = resolveUri(playlistUrl, line),
                        durationSec = pendingDuration,
                    )
                    pendingDuration = null
                }
            }
        }
        if (segments.isEmpty()) throw IllegalStateException("Media playlist has no segments")
        return MediaPlaylist(
            playlistUrl = playlistUrl,
            initMapUri = initMapUri,
            segments = segments,
            encrypted = encrypted,
            hasByteRange = hasByteRange,
        )
    }

    /** Parse HLS attribute list: KEY=VALUE,KEY="quoted value" */
    internal fun parseAttributes(raw: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        var i = 0
        val s = raw.trim()
        while (i < s.length) {
            while (i < s.length && (s[i] == ',' || s[i].isWhitespace())) i++
            if (i >= s.length) break
            val eq = s.indexOf('=', i)
            if (eq < 0) break
            val key = s.substring(i, eq).trim()
            i = eq + 1
            if (i >= s.length) break
            val value = if (s[i] == '"') {
                i++
                val end = s.indexOf('"', i)
                if (end < 0) break
                val v = s.substring(i, end)
                i = end + 1
                v
            } else {
                val end = s.indexOf(',', i).let { if (it < 0) s.length else it }
                val v = s.substring(i, end).trim()
                i = end
                v
            }
            if (key.isNotEmpty()) out[key] = value
        }
        return out
    }
}
