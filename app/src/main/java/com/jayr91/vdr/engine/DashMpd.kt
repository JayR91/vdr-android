package com.jayr91.vdr.engine

import java.net.URI

/**
 * Minimal DASH MPD parser for clear (non-DRM) on-demand presentations.
 * Picks the highest-bandwidth video Representation and expands SegmentTemplate
 * or SegmentList into init + media URLs. ContentProtection → rejected.
 *
 * Pure-Kotlin (no XmlPullParser) so JVM unit tests can run the same code.
 */
object DashMpd {
    const val DRM_ERROR = "DRM-protected DASH is not supported"
    const val NO_VIDEO_ERROR = "DASH manifest has no clear video Representation"
    const val TIMELINE_ERROR =
        "This DASH stream uses a segment timeline, which VDR can't download yet"
    const val UNKNOWN_LENGTH_ERROR =
        "This DASH stream doesn't declare its duration, so VDR can't tell where it ends"

    data class Representation(
        val id: String,
        val bandwidth: Int,
        val mimeType: String?,
        val width: Int?,
        val height: Int?,
        val initUrl: String?,
        val mediaUrls: List<String>,
    )

    data class Manifest(
        val mpdUrl: String,
        val protected: Boolean,
        val bestVideo: Representation?,
        /**
         * `<SegmentTimeline>` numbers segments by `$Time$`, i.e. by explicit
         * per-segment start times. Expanding that token to a constant made
         * every segment URL identical, so the download "succeeded" while
         * writing the same segment N times -- a plausible-looking file that
         * will not play. Refusing is the honest outcome.
         */
        val usesTimeline: Boolean = false,
        /**
         * Segment count is derived from the presentation duration. Without
         * one there is nothing to derive it from, and the old code guessed
         * three segments -- silently truncating a full-length video to a few
         * seconds and reporting success.
         */
        val unknownLength: Boolean = false,
    )

    fun isManifestBody(text: String): Boolean {
        val t = text.trimStart()
        return t.startsWith("<?xml", ignoreCase = true) ||
            t.contains("<MPD", ignoreCase = true)
    }

    fun parse(xml: String, mpdUrl: String): Manifest {
        val protected = Regex("""<ContentProtection\b""", RegexOption.IGNORE_CASE).containsMatchIn(xml)
        val usesTimeline = Regex("""<SegmentTimeline\b""", RegexOption.IGNORE_CASE).containsMatchIn(xml) ||
            xml.contains("${'$'}Time${'$'}")
        val presentationMs = Regex(
            """mediaPresentationDuration\s*=\s*"([^"]+)"""",
            RegexOption.IGNORE_CASE,
        ).find(xml)?.groupValues?.get(1)?.let { parseDurationMs(it) }

        val mpdBase = parentDir(mpdUrl)
        val representations = mutableListOf<Representation>()
        var lengthUnknown = false

        val adaptationRegex = Regex(
            """<AdaptationSet\b([^>]*)>(.*?)</AdaptationSet>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        for (adaptMatch in adaptationRegex.findAll(xml)) {
            val adaptAttrs = adaptMatch.groupValues[1]
            val adaptBody = adaptMatch.groupValues[2]
            val adaptMime = attr(adaptAttrs, "mimeType") ?: attr(adaptAttrs, "contentType")
            if (!isVideoMime(adaptMime) &&
                !adaptBody.contains("""mimeType="video""", ignoreCase = true) &&
                !adaptBody.contains("video/mp4", ignoreCase = true)
            ) {
                // Still scan Representations — mime may be on Representation only.
            }
            val adaptBase = firstBaseUrl(adaptBody, mpdBase) ?: mpdBase

            val repRegex = Regex(
                """<Representation\b([^>]*)>(.*?)</Representation>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
            for (repMatch in repRegex.findAll(adaptBody)) {
                val repAttrs = repMatch.groupValues[1]
                val repBody = repMatch.groupValues[2]
                val mime = attr(repAttrs, "mimeType") ?: adaptMime
                if (!isVideoMime(mime)) continue

                val id = attr(repAttrs, "id").orEmpty().ifBlank { "0" }
                val bandwidth = attr(repAttrs, "bandwidth")?.toIntOrNull() ?: 0
                val width = attr(repAttrs, "width")?.toIntOrNull()
                val height = attr(repAttrs, "height")?.toIntOrNull()
                val repBase = firstBaseUrl(repBody, adaptBase) ?: adaptBase

                val templateTag = Regex(
                    """<SegmentTemplate\b([^>]*)/?>""",
                    RegexOption.IGNORE_CASE,
                ).find(repBody) ?: Regex(
                    """<SegmentTemplate\b([^>]*)/?>""",
                    RegexOption.IGNORE_CASE,
                ).find(adaptBody)

                var initUrl: String? = null
                val mediaUrls = mutableListOf<String>()

                if (templateTag != null) {
                    val tAttrs = templateTag.groupValues[1]
                    val timescale = attr(tAttrs, "timescale")?.toLongOrNull() ?: 1L
                    val duration = attr(tAttrs, "duration")?.toLongOrNull() ?: 0L
                    val startNumber = attr(tAttrs, "startNumber")?.toIntOrNull() ?: 1
                    val initialization = attr(tAttrs, "initialization")
                    val media = attr(tAttrs, "media")
                    if (!initialization.isNullOrBlank()) {
                        initUrl = resolveUri(repBase, expandTemplate(initialization, id, bandwidth, 0))
                    }
                    if (!media.isNullOrBlank()) {
                        val count = segmentCount(duration, timescale, presentationMs)
                        if (count == null) {
                            lengthUnknown = true
                        } else {
                            for (i in 0 until count) {
                                val number = startNumber + i
                                mediaUrls += resolveUri(repBase, expandTemplate(media, id, bandwidth, number))
                            }
                        }
                    }
                } else {
                    Regex(
                        """<Initialization\b([^>]*)/?>""",
                        RegexOption.IGNORE_CASE,
                    ).find(repBody)?.let { initTag ->
                        val src = attr(initTag.groupValues[1], "sourceURL")
                            ?: attr(initTag.groupValues[1], "sourceUrl")
                        if (!src.isNullOrBlank()) initUrl = resolveUri(repBase, src)
                    }
                    Regex(
                        """<SegmentURL\b([^>]*)/?>""",
                        RegexOption.IGNORE_CASE,
                    ).findAll(repBody).forEach { seg ->
                        attr(seg.groupValues[1], "media")?.let { mediaUrls += resolveUri(repBase, it) }
                    }
                }

                if (mediaUrls.isEmpty()) continue
                representations += Representation(
                    id = id,
                    bandwidth = bandwidth,
                    mimeType = mime,
                    width = width,
                    height = height,
                    initUrl = initUrl,
                    mediaUrls = mediaUrls,
                )
            }
        }

        val best = representations.maxByOrNull { it.bandwidth }
        return Manifest(
            mpdUrl = mpdUrl,
            protected = protected,
            bestVideo = best,
            usesTimeline = usesTimeline,
            // Only meaningful when nothing else produced a playable list: a
            // manifest can carry one representation we cannot size alongside
            // another (SegmentList) that we can.
            unknownLength = lengthUnknown && best == null,
        )
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
            val base = if (baseUrl.endsWith("/")) baseUrl else baseUrl.substringBeforeLast('/', baseUrl) + "/"
            base + trimmed.trimStart('/')
        }
    }

    private fun attr(attrs: String, name: String): String? {
        val m = Regex(
            """\b$name\s*=\s*"([^"]*)"""",
            RegexOption.IGNORE_CASE,
        ).find(attrs) ?: Regex(
            """\b$name\s*=\s*'([^']*)'""",
            RegexOption.IGNORE_CASE,
        ).find(attrs)
        return m?.groupValues?.get(1)
    }

    private fun firstBaseUrl(body: String, parent: String): String? {
        val m = Regex(
            """<BaseURL[^>]*>([^<]+)</BaseURL>""",
            RegexOption.IGNORE_CASE,
        ).find(body) ?: return null
        val raw = m.groupValues[1].trim()
        if (raw.isEmpty()) return null
        val resolved = resolveUri(parent, raw)
        return if (resolved.endsWith("/")) resolved else "$resolved/"
    }

    private fun isVideoMime(mime: String?): Boolean {
        val m = mime.orEmpty().lowercase()
        return m.startsWith("video/") || m == "video"
    }

    private fun parentDir(url: String): String {
        val noQuery = url.substringBefore('?')
        return if (noQuery.endsWith("/")) noQuery else noQuery.substringBeforeLast('/', noQuery) + "/"
    }

    /**
     * `$Number%05d$` and friends must be expanded before the plain `$Number$`
     * form, or the padded token is left with a stray `%05d` in the middle of
     * the filename.
     *
     * The pattern is built from an escaped literal `$`. Writing it as an
     * unescaped `$` -- which is what `${'$'}` produces inside a raw string --
     * makes the regex engine read it as an end-of-input anchor instead, so
     * the pattern could never match and every padded template survived into
     * the request URL verbatim. Padded numbering is the common case in real
     * manifests, so this quietly 404'd whole streams.
     */
    private val paddedNumberToken = Regex("""\${'$'}Number%0(\d+)d\${'$'}""")

    private fun expandTemplate(template: String, repId: String, bandwidth: Int, number: Int): String =
        template
            .replace("\$RepresentationID\$", repId)
            .replace("\$Bandwidth\$", bandwidth.toString())
            .replace(paddedNumberToken) { m ->
                number.toString().padStart(m.groupValues[1].toInt(), '0')
            }
            .replace("\$Number\$", number.toString())

    /**
     * Number of media segments, or null when the manifest does not say.
     *
     * This used to fall back to three whenever the duration or segment length
     * was missing, which turned an unanswerable question into a wrong answer:
     * a feature-length video came out as a few seconds of footage and the
     * download still reported success. Returning null lets the caller refuse.
     */
    internal fun segmentCount(duration: Long, timescale: Long, presentationMs: Long?): Int? {
        if (duration <= 0 || timescale <= 0) return null
        if (presentationMs == null || presentationMs <= 0) return null
        val segMs = (duration * 1000.0 / timescale).coerceAtLeast(1.0)
        return (presentationMs.toDouble() / segMs).toInt().coerceIn(1, MAX_SEGMENTS)
    }

    /** Ceiling on expanded segment URLs, so a hostile manifest cannot OOM us. */
    const val MAX_SEGMENTS = 5000

    /** Parse ISO-8601 duration like PT1M30.5S → milliseconds. */
    internal fun parseDurationMs(raw: String): Long? {
        val s = raw.trim()
        if (!s.startsWith("PT", ignoreCase = true)) return null
        var rest = s.substring(2)
        var ms = 0.0
        Regex("""(\d+(?:\.\d+)?)H""", RegexOption.IGNORE_CASE).find(rest)?.let {
            ms += it.groupValues[1].toDouble() * 3600_000
            rest = rest.replace(it.value, "")
        }
        Regex("""(\d+(?:\.\d+)?)M""", RegexOption.IGNORE_CASE).find(rest)?.let {
            ms += it.groupValues[1].toDouble() * 60_000
            rest = rest.replace(it.value, "")
        }
        Regex("""(\d+(?:\.\d+)?)S""", RegexOption.IGNORE_CASE).find(rest)?.let {
            ms += it.groupValues[1].toDouble() * 1000
        }
        return ms.toLong().takeIf { it > 0 }
    }
}
