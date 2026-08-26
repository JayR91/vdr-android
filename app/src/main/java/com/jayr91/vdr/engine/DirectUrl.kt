package com.jayr91.vdr.engine

import java.net.URI
import java.net.URLDecoder

/**
 * VDR downloads direct HTTP(S) files, HLS .m3u8, and clear DASH .mpd manifests.
 * Watch/share pages (YouTube, Odysee, Instagram, ...) cannot be extracted in the Play Store build.
 *
 * Block known *page* hosts only — not CDNs. A path that already looks like a file
 * (.mp4, .m3u8, .mpd, .vtt, etc.) is allowed even on a related domain.
 */
object DirectUrl {
    const val PAGE_ERROR =
        "Odysee/YouTube page links are not direct files. Paste a direct https file link."
    const val HTML_ERROR =
        "This is a web page, not a direct file. Paste a direct https file link."

    /** Path suffixes that mean queue-as-file; never HTML-extract. */
    val directFileSuffixes = listOf(
        ".mp4", ".webm", ".mkv", ".m4a", ".mov", ".avi", ".m4v", ".ogv",
        ".m3u8", ".ts", ".m4s", ".mpd",
        ".mp3", ".aac", ".wav", ".flac", ".ogg",
        ".vtt", ".srt",
        ".pdf", ".zip", ".apk",
    )

    private val httpUrlRegex = Regex("""https?://[^\s<>"'{}|\\^`\[\]]+""", RegexOption.IGNORE_CASE)

    fun extractHttpUrls(text: String): List<String> =
        httpUrlRegex.findAll(text)
            .map { it.value.trimEnd('.', ',', ';', ':', ')', ']', '"', '\'') }
            .map { normalizeHttpUrl(it) }
            .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            .map { ensureLowerScheme(it) }
            .distinct()
            .toList()

    fun rejectionMessage(url: String): String? {
        val uri = try {
            parseUri(url.trim()) ?: return null
        } catch (_: Exception) {
            return null
        }
        val host = (uri.host ?: return null).lowercase().removePrefix("www.")
        if (looksLikeDirectFile(url)) return null
        if (isStreamingHost(host)) return PAGE_ERROR
        return null
    }

    fun looksLikeHlsUrl(url: String): Boolean = pathEndsWith(url, ".m3u8")

    fun looksLikeDashUrl(url: String): Boolean = pathEndsWith(url, ".mpd")

    /**
     * True when the URL path last segment has a clear downloadable extension
     * (.mp4, .pdf, .zip, .m3u8, ...). Query strings and fragments are ignored.
     *
     * HTML *description* pages that embed a filename in the path (e.g. archive.org
     * `/details/…/clip.mp4`, Wikimedia `/wiki/File:….ogg`) are excluded — they
     * serve HTML, not the bytes.
     */
    fun looksLikeDirectFile(url: String): Boolean {
        val path = pathOf(url) ?: return false
        val lower = path.lowercase()
        if (isHtmlDescriptionPath(lower)) return false
        val name = path.substringAfterLast('/').lowercase().substringBefore('?')
        if (name.isEmpty()) return false
        return directFileSuffixes.any { name.endsWith(it) }
    }

    /** Paths that look like a file name but are document/HTML routes. */
    fun isHtmlDescriptionPath(pathLower: String): Boolean {
        val p = pathLower.lowercase()
        if (p.contains("/details/") ||
            p.contains("/wiki/") ||
            p.contains("/w/index.php") ||
            p.contains("/file:") ||
            p.substringAfterLast('/').startsWith("file:")
        ) {
            return true
        }
        // `/embed/{id}/{file.ext}` is often an HTML player (e.g. archive.org), not bytes.
        // `/embed/file.ext` (single segment) can still be a real media file.
        if (Regex(""".*/embed/[^/]+/.+""").matches(p)) return true
        return false
    }

    fun isHtmlContentType(contentType: String?): Boolean {
        val ct = contentType.orEmpty().lowercase()
        return ct.contains("text/html") || ct.contains("application/xhtml")
    }

    fun isHlsContentType(contentType: String?): Boolean {
        val ct = contentType.orEmpty().lowercase()
        return ct.contains("application/vnd.apple.mpegurl") ||
            ct.contains("application/x-mpegurl") ||
            ct.contains("audio/mpegurl") ||
            ct.contains("audio/x-mpegurl")
    }

    fun isDashContentType(contentType: String?): Boolean {
        val ct = contentType.orEmpty().lowercase()
        return ct.contains("application/dash+xml") ||
            ct.contains("application/mpd")
    }

    // video/*, audio/*, HLS/DASH, or generic binary that may be a file download.
    fun isDownloadableContentType(contentType: String?): Boolean {
        val ct = contentType.orEmpty().lowercase().substringBefore(';').trim()
        if (ct.isEmpty()) return false
        if (ct.startsWith("video/") || ct.startsWith("audio/")) return true
        if (isHlsContentType(ct) || isDashContentType(ct)) return true
        if (ct == "application/octet-stream" || ct == "binary/octet-stream") return true
        if (ct == "application/pdf" || ct == "application/zip") return true
        return false
    }

    private fun pathEndsWith(url: String, suffix: String): Boolean {
        val path = pathOf(url) ?: return false
        val name = path.substringAfterLast('/').lowercase().substringBefore('?')
        return name.endsWith(suffix)
    }

    private fun pathOf(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        // The (String, Charset) overload of decode() only exists from API 33.
        // minSdk here is 26 and core library desugaring is off, so on Android
        // 8 through 12 that call is a NoSuchMethodError -- and pathOf() runs
        // for every URL the app looks at, so the crash would have been the
        // first thing most users saw. The (String, String) overload has been
        // present since API 1 and behaves identically.
        parseUri(trimmed)?.rawPath?.let { return URLDecoder.decode(it, "UTF-8") }
        // Lenient fallback when java.net.URI rejects spaces / odd characters.
        val noFrag = trimmed.substringBefore('#')
        val noQuery = noFrag.substringBefore('?')
        val afterScheme = noQuery.substringAfter("://", missingDelimiterValue = "")
        if (afterScheme.isEmpty()) return null
        val path = afterScheme.substringAfter('/', missingDelimiterValue = "")
        return if (path.isEmpty() && !afterScheme.contains('/')) "" else "/$path"
    }

    private fun parseUri(url: String): URI? = try {
        URI(url.trim().replace(" ", "%20"))
    } catch (_: Exception) {
        null
    }

    private fun normalizeHttpUrl(raw: String): String = raw.trim()

    private fun ensureLowerScheme(url: String): String {
        val i = url.indexOf("://")
        if (i <= 0) return url
        return url.substring(0, i).lowercase() + url.substring(i)
    }

    private fun isStreamingHost(host: String): Boolean {
        if (host in exactHosts) return true
        return hostSuffixes.any { host == it || host.endsWith(".$it") }
    }

    private val exactHosts = setOf(
        "youtu.be",
        "fb.watch",
        "x.com",
    )

    private val hostSuffixes = listOf(
        "youtube.com",
        "odysee.com",
        "lbry.tv",
        "instagram.com",
        "instagr.am",
        "tiktok.com",
        "facebook.com",
        "twitter.com",
        "vimeo.com",
        "dailymotion.com",
        "twitch.tv",
        "reddit.com",
        "snapchat.com",
        "netflix.com",
        "disneyplus.com",
        "hulu.com",
        "primevideo.com",
        "max.com",
        "hbomax.com",
        "spotify.com",
    )

    /** True when this URL is a blocked watch/share page (not a direct file). */
    fun isBlockedWatchPage(url: String): Boolean = rejectionMessage(url) != null
}
