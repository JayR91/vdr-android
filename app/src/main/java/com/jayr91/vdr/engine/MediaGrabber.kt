package com.jayr91.vdr.engine

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * IP-safe in-app media detection: direct file URLs / clear video-audio MIME only.
 * Site-agnostic HTML extraction (tags, meta, JSON-LD, absolute URL regex).
 * No DRM, no other-app overlays, no yt-dlp.
 *
 * Paste / Add routing order:
 * 1. Blocked watch hosts
 * 2. Path looks like a direct file → queue as-is
 * 3. HEAD/GET probe MIME (follow redirects) → queue if downloadable
 * 4. HTML parse for embedded clear media
 * 5. Clear error (never queue HTML as a download)
 */
object MediaGrabber {
    private const val LOG_TAG = "VdrGrabber"
    const val UNSUPPORTED_SITE = "This site isn’t supported."
    const val PROMPT_TITLE = "Download this media?"
    const val FOUND_ON_PAGE_TITLE = "Found media on this page"
    const val YOUTUBE_ONLY_ERROR =
        "This page uses YouTube (or similar); not supported. Paste a page that embeds a direct media file."
    const val NO_MEDIA_ON_PAGE =
        "No direct media file found on this page. Paste a direct file URL " +
            "(…/file.mp4). For archive.org use Download Options → the file link, or paste the details page " +
            "and VDR will pick clear media when present."
    const val NO_VIDEO_ON_PAGE =
        "No video files found on this page. Try a page that embeds a direct .mp4 / .webm / .m3u8 link, " +
            "or paste a direct video URL."
    const val DEMO_PAGE_URL = "https://www.w3schools.com/html/html5_video.asp"
    const val DEMO_CHIP_LABEL = "Try demo: html5_video"

    /** Minimum gap between any two media prompts (different URLs on same page/session). */
    const val PROMPT_GAP_MS = 2 * 60 * 1000L

    /** Same URL: do not re-prompt until this long after dismiss (keeps spam down). */
    const val DISMISS_COOLDOWN_MS = 15 * 60 * 1000L

    /** Internal so Organizer's category table can be tested against it. */
    internal val videoExtensions = setOf(
        "mp4", "webm", "mkv", "mov", "m4v", "avi", "ogv", "ts",
    )

    private val audioOnlyExtensions = setOf(
        "mp3", "m4a", "ogg", "wav", "flac", "aac",
    )

    private val preferredExtOrder = listOf(
        ".mp4", ".webm", ".mkv", ".mov", ".m4a", ".mp3", ".m3u8", ".mpd", ".ogv", ".ogg", ".ts",
        ".pdf", ".zip",
    )

    private val mediaExtPattern =
        """mp4|webm|mkv|mp3|m4a|ogg|ogv|m3u8|mpd|ts|mov|avi|m4v|aac|wav|flac|pdf|zip"""

    /** Trailing boundary so `.webm` does not match inside `.webmanifest`. */
    private val mediaExtBoundary = """(?=[^\w]|$)"""

    /** HLS/DASH numbered segments — noisy if prompted individually. */
    private val segmentName = Regex(
        """(?i)(^|[-_/])(seg(ment)?[-_]?\d+|chunk[-_]?\d+|media[-_]?\d+|\d{2,})\.ts$""",
    )

    /** src / href / data / data-src / poster on common media-bearing tags. */
    private val attrUrlRegex = Regex(
        """(?i)\b(?:src|href|data|data-src|poster)\s*=\s*["']([^"']+)["']""",
    )

    /** Open Graph / Twitter player stream and similar meta content URLs. */
    private val metaPropertyFirst = Regex(
        """(?is)<meta[^>]+(?:property|name)\s*=\s*["'](?:og:video(?::(?:url|secure_url))?|twitter:player:stream|twitter:player)["'][^>]*\bcontent\s*=\s*["']([^"']+)["']""",
    )
    private val metaContentFirst = Regex(
        """(?is)<meta[^>]+\bcontent\s*=\s*["']([^"']+)["'][^>]*(?:property|name)\s*=\s*["'](?:og:video(?::(?:url|secure_url))?|twitter:player:stream|twitter:player)["']""",
    )

    /** Absolute http(s) URLs ending in a media extension (optional query). */
    private val absoluteMediaUrlRegex = Regex(
        """(?i)\b(https?://[^\s"'<>\\]+\.(?:$mediaExtPattern)$mediaExtBoundary(?:\?[^\s"'<>\\]*)?)""",
    )

    /** Relative / absolute paths ending in media extensions (before ? or end). */
    private val pathMediaRegex = Regex(
        """(?i)((?:https?:)?/?/?[^\s"'<>\\]+\.(?:$mediaExtPattern)$mediaExtBoundary(?:\?[^\s"'<>\\]*)?)""",
    )

    /** JSON-LD / inline JSON contentUrl / url / embedUrl / downloadUrl values. */
    private val jsonMediaFieldRegex = Regex(
        """(?i)"(?:contentUrl|url|contentURL|embedUrl|downloadUrl)"\s*:\s*"(https?://[^"]+\.(?:$mediaExtPattern)(?:\?[^"]*)?)"""",
    )

    private val youtubeHostHint = Regex(
        """(?i)(?:youtube\.com/(?:embed|watch|shorts)|youtu\.be/)""",
    )

    private val youtubeIframeRegex = Regex(
        """(?i)<iframe[^>]+src\s*=\s*["'][^"']*(?:youtube\.com/embed|youtube-nocookie\.com/embed|youtu\.be/)""",
    )

    private val httpClient: OkHttpClient = VdrHttp.newClient(connectTimeoutSec = 12, readTimeoutSec = 20)

    fun canonicalize(url: String): String {
        val trimmed = url.trim()
        return try {
            val u = URI(trimmed.replace(" ", "%20"))
            // Decoded, so ".../Content%2Fclip.mp4" and ".../Content/clip.mp4"
            // -- the same file, which archive.org links both ways on one page
            // -- canonicalise identically and dedupe into one row instead of
            // two rows with the same name that a user cannot choose between.
            val path = (u.path ?: u.rawPath ?: "").substringBefore('#')
            val scheme = (u.scheme ?: "https").lowercase()
            val host = (u.host ?: "").lowercase()
            val port = when {
                u.port < 0 -> ""
                scheme == "https" && u.port == 443 -> ""
                scheme == "http" && u.port == 80 -> ""
                else -> ":${u.port}"
            }
            "$scheme://$host$port$path"
        } catch (_: Exception) {
            trimmed.substringBefore('#').substringBefore('?')
        }
    }

    /**
     * Clear media URL by path extension (video/audio/stream). Excludes noisy HLS .ts segments.
     * For Add/paste “queue directly,” prefer [DirectUrl.looksLikeDirectFile] (includes pdf/zip).
     */
    fun looksLikeMediaUrl(url: String): Boolean {
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            return false
        }
        if (url.startsWith("blob:", ignoreCase = true) || url.startsWith("data:", ignoreCase = true)) {
            return false
        }
        if (!DirectUrl.looksLikeDirectFile(url)) return false
        val path = try {
            URI(url.trim().replace(" ", "%20")).path
        } catch (_: Exception) {
            url.substringBefore('?').substringBefore('#')
        }
        val name = (path ?: "").substringAfterLast('/').lowercase()
        if (name.endsWith(".ts") && segmentName.containsMatchIn(path ?: name)) return false
        // Browse prompts focus on playable media; pdf/zip still queue via looksLikeDirectFile in Add.
        if (name.endsWith(".pdf") || name.endsWith(".zip") || name.endsWith(".apk")) return false
        return true
    }

    fun isMediaMime(contentType: String?): Boolean =
        DirectUrl.isDownloadableContentType(contentType) &&
            !DirectUrl.isHtmlContentType(contentType)

    /**
     * Whether [url] is clear downloadable media (extension and/or MIME),
     * and not a blocked watch page.
     */
    fun isClearDownloadable(url: String, contentType: String? = null): Boolean {
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            return false
        }
        if (DirectUrl.isBlockedWatchPage(url)) return false
        if (DirectUrl.looksLikeDirectFile(url) || looksLikeMediaUrl(url)) return true
        return isMediaMime(contentType) && !DirectUrl.isHtmlContentType(contentType)
    }

    fun displayName(url: String): String {
        return try {
            val path = URI(url.trim().replace(" ", "%20")).path ?: return "media"
            path.substringAfterLast('/').ifBlank { "media" }.substringBefore('?')
        } catch (_: Exception) {
            url.substringAfterLast('/').substringBefore('?').ifBlank { "media" }
        }
    }

    fun resolveAgainstBase(raw: String, baseUrl: String): String? {
        val t = raw.trim()
            .replace("&amp;", "&")
            .replace("\\u002F", "/")
            .replace("\\/", "/")
        if (t.isEmpty() || t.startsWith("javascript:", ignoreCase = true) ||
            t.startsWith("data:", ignoreCase = true) ||
            t.startsWith("blob:", ignoreCase = true) ||
            t.startsWith("#")
        ) {
            return null
        }
        return try {
            URI(baseUrl.trim().replace(" ", "%20")).resolve(t.replace(" ", "%20")).toString()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extract absolute clear-media URLs from HTML — site-agnostic:
     * tag attrs (video/audio/source/a/embed/object), meta og:video / twitter stream,
     * JSON-LD fields, and absolute/relative URLs ending in media extensions.
     */
    fun extractMediaUrlsFromHtml(html: String, baseUrl: String): List<String> {
        val found = LinkedHashSet<String>()

        fun consider(raw: String?) {
            if (raw.isNullOrBlank()) return
            val abs = resolveAgainstBase(raw, baseUrl) ?: return
            if (isClearDownloadable(abs)) found.add(abs)
        }

        for (m in attrUrlRegex.findAll(html)) {
            consider(m.groupValues[1])
        }
        for (m in metaPropertyFirst.findAll(html)) {
            consider(m.groupValues[1])
        }
        for (m in metaContentFirst.findAll(html)) {
            consider(m.groupValues[1])
        }
        for (m in jsonMediaFieldRegex.findAll(html)) {
            consider(m.groupValues[1])
        }
        for (m in absoluteMediaUrlRegex.findAll(html)) {
            consider(m.groupValues[1])
        }
        // Relative /download/… and other path-shaped media links not caught as attrs.
        for (m in pathMediaRegex.findAll(html)) {
            val raw = m.groupValues[1]
            // Skip pure "file.mp4" noise without a slash unless it resolves against base.
            if (!raw.contains('/') && !raw.startsWith("http", ignoreCase = true)) continue
            consider(raw)
        }
        return preferMediaOrder(found.toList())
    }

    /** Video + stream formats for the page-media picker (excludes audio-only like .mp3). */
    fun isVideoishMedia(url: String): Boolean {
        if (!isClearDownloadable(url)) return false
        val name = displayName(url).lowercase()
        if (DirectUrl.looksLikeHlsUrl(url) || DirectUrl.looksLikeDashUrl(url)) return true
        if (name.endsWith(".m3u8") || name.endsWith(".mpd")) return true
        val ext = name.substringAfterLast('.', "")
        if (ext in audioOnlyExtensions) return false
        if (ext in videoExtensions) return true
        return looksLikeMediaUrl(url) && ext !in audioOnlyExtensions
    }

    fun filterVideoMedia(urls: List<String>): List<String> =
        preferMediaOrder(urls.filter { isVideoishMedia(it) })

    /** Short label for list rows: `.mp4`, `HLS stream`, `DASH stream`, … */
    fun mediaTypeLabel(url: String): String {
        val name = displayName(url).lowercase()
        return when {
            DirectUrl.looksLikeHlsUrl(url) || name.endsWith(".m3u8") -> "HLS stream"
            DirectUrl.looksLikeDashUrl(url) || name.endsWith(".mpd") -> "DASH stream"
            else -> {
                val ext = name.substringAfterLast('.', "")
                if (ext.isNotBlank()) ".$ext" else "media"
            }
        }
    }

    /** URLs to show in the page-media picker after probing. */
    fun urlsForPageMediaPicker(result: PageProbeResult, pageUrl: String): List<String> =
        when (result) {
            is PageProbeResult.Media -> filterVideoMedia(result.urls)
            PageProbeResult.DirectFile -> listOf(pageUrl.trim())
            else -> emptyList()
        }

    fun preferMediaOrder(urls: List<String>): List<String> =
        urls.distinctBy { canonicalize(it) }.sortedWith(
            compareBy<String> { url ->
                val name = displayName(url).lowercase()
                preferredExtOrder.indexOfFirst { name.endsWith(it) }.let { if (it < 0) 99 else it }
            }.thenBy { url ->
                val u = url.lowercase()
                val name = displayName(url).lowercase()
                when {
                    u.contains("/preview/") || name.contains("thumb") || name.contains("poster") -> 50
                    u.contains("/download/") -> 0
                    else -> 10
                }
            }.thenByDescending { it.length },
        )

    fun pageHasYoutube(html: String): Boolean =
        youtubeIframeRegex.containsMatchIn(html) || youtubeHostHint.containsMatchIn(html)

    /**
     * Resolve a pasted URL for Add / share:
     * (1) blocked → (2) direct file by path → (3) MIME probe → (4) HTML media → (5) errors.
     */
    fun probePageUrl(pageUrl: String): PageProbeResult {
        val url = pageUrl.trim()
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            return PageProbeResult.None
        }
        DirectUrl.rejectionMessage(url)?.let {
            return PageProbeResult.Blocked(it)
        }
        // (2) Path already looks like a file — never HTML-extract; queue as-is.
        if (DirectUrl.looksLikeDirectFile(url)) {
            return PageProbeResult.DirectFile
        }
        return try {
            val req = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful && resp.code !in listOf(200, 206)) {
                    if (resp.code >= 400) {
                        // Soft: still try body when present (some CDNs wrap errors in HTML).
                        val errBody = peekBodyUtf8(resp, 512 * 1024)
                        if (!errBody.isNullOrBlank()) {
                            val media = extractMediaUrlsFromHtml(errBody, resp.request.url.toString())
                            if (media.isNotEmpty()) return PageProbeResult.Media(media)
                        }
                        return PageProbeResult.None
                    }
                }
                val ct = resp.header("Content-Type")
                val finalUrl = resp.request.url.toString()
                // (3) MIME after redirects (archive.org /download often 302s to a CDN).
                if (DirectUrl.isDownloadableContentType(ct) && !DirectUrl.isHtmlContentType(ct)) {
                    return if (isClearDownloadable(finalUrl, ct) || DirectUrl.looksLikeDirectFile(finalUrl)) {
                        PageProbeResult.Media(listOf(finalUrl))
                    } else {
                        PageProbeResult.DirectFile
                    }
                }
                if (!DirectUrl.isHtmlContentType(ct) && !ct.isNullOrBlank()) {
                    // Non-HTML, non-media — let the download engine handle it.
                    return PageProbeResult.DirectFile
                }
                // (4) HTML page — extract clear media links (never save HTML).
                val html = peekBodyUtf8(resp, 2L * 1024 * 1024) ?: return PageProbeResult.None
                if (html.isBlank()) return PageProbeResult.None
                val media = extractMediaUrlsFromHtml(html, finalUrl)
                if (media.isNotEmpty()) return PageProbeResult.Media(media)
                if (pageHasYoutube(html)) return PageProbeResult.YoutubeOnly
                if (DirectUrl.isHtmlContentType(ct) || htmlLooksLikeHtml(html)) {
                    return PageProbeResult.HtmlNoMedia
                }
                PageProbeResult.DirectFile
            }
        } catch (e: Exception) {
            // Distinguish "we could not read the page" from "the page has no
            // video". Collapsing both into None told the user "No video files
            // found on this page" after a timeout or TLS failure -- a claim
            // about the page's contents that we had never actually seen, and
            // which sends them off blaming the site or the parser.
            Log.w(LOG_TAG, "probe failed for $url", e)
            PageProbeResult.Failed(fetchErrorMessage(e))
        }
    }

    /** Short, honest reason a page could not be read. */
    private fun fetchErrorMessage(e: Exception): String = when (e) {
        is java.net.SocketTimeoutException ->
            "Timed out loading that page. Check your connection and try again."
        is java.net.UnknownHostException ->
            "Couldn't reach that site. Check your connection and try again."
        is javax.net.ssl.SSLException ->
            "Secure connection to that site failed."
        else -> {
            val detail = e.message?.take(120)?.takeIf { it.isNotBlank() }
            if (detail != null) "Couldn't load that page: $detail" else "Couldn't load that page."
        }
    }

    private fun peekBodyUtf8(resp: okhttp3.Response, maxBytes: Long): String? {
        val source = resp.body?.source() ?: return null
        val limited = okio.Buffer()
        // Source.read() fills at most one internal segment (~8 KiB) per call
        // and returns however much it got -- it is not "read up to n bytes".
        // Calling it once therefore scanned only the first few KiB of a page
        // no matter how large the cap said it was, so any <video> tag below
        // the fold read as "no media found on this page". Loop to the cap.
        while (limited.size < maxBytes) {
            val read = source.read(limited, maxBytes - limited.size)
            if (read == -1L) break
        }
        if (limited.size == 0L) return null
        // A cut-off multi-byte character at the cap would otherwise throw.
        return runCatching { limited.readUtf8() }.getOrNull()
    }

    private fun htmlLooksLikeHtml(body: String): Boolean {
        val head = body.take(512).lowercase()
        return head.contains("<html") || head.contains("<!doctype html") || head.contains("<head")
    }
}

/** Result of probing a pasted page URL for clear media. */
sealed class PageProbeResult {
    data class Media(val urls: List<String>) : PageProbeResult()
    data class Blocked(val message: String) : PageProbeResult()
    /**
     * The page could not be fetched at all. Distinct from [HtmlNoMedia]: we
     * are reporting our own failure, not a fact about the page.
     */
    data class Failed(val message: String) : PageProbeResult()
    data object YoutubeOnly : PageProbeResult()
    data object HtmlNoMedia : PageProbeResult()
    /** Not HTML / looks like a real file — queue as-is. */
    data object DirectFile : PageProbeResult()
    data object None : PageProbeResult()
}

/** Per-session prompt dedupe + global gap between successive prompts. */
class GrabPromptSession(
    private val cooldownMs: Long = MediaGrabber.DISMISS_COOLDOWN_MS,
    private val promptGapMs: Long = MediaGrabber.PROMPT_GAP_MS,
) {
    private val offeredOrQueued = ConcurrentHashMap<String, Long>()
    private val dismissedAt = ConcurrentHashMap<String, Long>()
    @Volatile private var lastPromptAt: Long = 0L

    /**
     * Ms until a new (different-URL) Browse prompt may surface; 0 if allowed now.
     * Browse-only: never used by Add / clipboard / Share.
     * If [lastPromptAt] is in the future (clock skew), reset so prompts are not blocked forever.
     */
    fun millisUntilNextPrompt(now: Long = System.currentTimeMillis()): Long {
        if (lastPromptAt <= 0L) return 0L
        val elapsed = now - lastPromptAt
        if (elapsed < 0L) {
            lastPromptAt = 0L
            return 0L
        }
        val wait = promptGapMs - elapsed
        return if (wait > 0L) wait else 0L
    }

    fun notePromptInteraction(now: Long = System.currentTimeMillis()) {
        lastPromptAt = now
    }

    /** Returns true once when this URL should surface a prompt (respects global gap). */
    fun tryBeginOffer(url: String, now: Long = System.currentTimeMillis()): Boolean {
        if (!MediaGrabber.isClearDownloadable(url)) return false
        val key = MediaGrabber.canonicalize(url)
        if (offeredOrQueued.containsKey(key)) return false
        val dismissed = dismissedAt[key]
        if (dismissed != null && now - dismissed < cooldownMs) return false
        if (millisUntilNextPrompt(now) > 0L) return false
        if (offeredOrQueued.putIfAbsent(key, now) != null) return false
        lastPromptAt = now
        return true
    }

    fun shouldOffer(url: String, now: Long = System.currentTimeMillis()): Boolean {
        val key = MediaGrabber.canonicalize(url)
        if (!MediaGrabber.isClearDownloadable(url)) return false
        if (offeredOrQueued.containsKey(key)) return false
        val dismissed = dismissedAt[key] ?: return true
        return now - dismissed >= cooldownMs
    }

    fun markOffered(url: String, now: Long = System.currentTimeMillis()) {
        offeredOrQueued[MediaGrabber.canonicalize(url)] = now
        lastPromptAt = now
    }

    fun markDismissed(url: String, now: Long = System.currentTimeMillis()) {
        val key = MediaGrabber.canonicalize(url)
        offeredOrQueued.remove(key)
        dismissedAt[key] = now
        lastPromptAt = now
    }

    fun markQueued(url: String, now: Long = System.currentTimeMillis()) {
        val key = MediaGrabber.canonicalize(url)
        offeredOrQueued[key] = now
        dismissedAt.remove(key)
        lastPromptAt = now
    }
}
