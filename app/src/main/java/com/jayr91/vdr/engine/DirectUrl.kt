package com.jayr91.vdr.engine

import java.net.URI

/**
 * VDR only downloads direct HTTP(S) files. Watch/share pages (YouTube, Odysee, Instagram, …)
 * cannot be extracted in the Play Store build.
 *
 * Block known *page* hosts only — not CDNs (odycdn, googlevideo). A path that already looks
 * like a file (.mp4 etc.) is allowed even on a related domain.
 */
object DirectUrl {
    const val PAGE_ERROR =
        "Odysee/YouTube page links are not direct files. Paste a direct https file link."
    const val HTML_ERROR =
        "This is a web page, not a direct file. Paste a direct https file link."

    private val directFileSuffixes = listOf(".mp4", ".webm", ".mkv", ".m4a", ".mov", ".avi", ".m4v")

    private val httpUrlRegex = Regex("""https?://[^\s<>"'{}|\\^`\[\]]+""")

    fun extractHttpUrls(text: String): List<String> =
        httpUrlRegex.findAll(text)
            .map { it.value.trimEnd('.', ',', ';', ':', ')', ']', '"', '\'') }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()
            .toList()

    fun rejectionMessage(url: String): String? {
        val uri = try {
            URI(url.trim())
        } catch (_: Exception) {
            return null
        }
        val host = (uri.host ?: return null).lowercase().removePrefix("www.")
        if (looksLikeDirectFile(uri.path)) return null
        if (isStreamingHost(host)) return PAGE_ERROR
        return null
    }

    fun isHtmlContentType(contentType: String?): Boolean {
        val ct = contentType.orEmpty().lowercase()
        return ct.contains("text/html") || ct.contains("application/xhtml")
    }

    private fun looksLikeDirectFile(path: String?): Boolean {
        val name = (path ?: "").substringAfterLast('/').lowercase()
        return directFileSuffixes.any { name.endsWith(it) }
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
    )
}
