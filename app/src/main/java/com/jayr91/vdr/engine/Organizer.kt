package com.jayr91.vdr.engine

object Organizer {
    private val categories = mapOf(
        "Videos" to setOf(
            ".mp4", ".mkv", ".mov", ".avi", ".webm", ".m4v", ".mpg", ".mpeg", ".ts", ".m4s",
        ),
        "Documents" to setOf(
            ".pdf", ".doc", ".docx", ".txt", ".rtf", ".xls", ".xlsx", ".ppt", ".pptx", ".csv", ".epub",
        ),
        "Subtitles" to setOf(".vtt", ".srt", ".ass", ".ssa"),
        "Zips" to setOf(".zip", ".rar", ".7z", ".tar", ".gz", ".bz2", ".xz", ".apk", ".dmg"),
        "Audio" to setOf(".mp3", ".m4a", ".aac", ".wav", ".flac", ".ogg"),
        "Images" to setOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".heic", ".svg"),
    )

    fun safeFilename(raw: String): String {
        val name = raw.substringAfterLast('/').substringBefore('?').trim()
        val cleaned = name.filter { it.isLetterOrDigit() || it in "-_.() " }.ifBlank { "download" }
        return if (cleaned == "." || cleaned == "..") "download" else cleaned
    }

    fun categoryFor(filename: String): String {
        val ext = filename.substringAfterLast('.', "").lowercase().let { if (it.isEmpty()) "" else ".$it" }
        return categories.entries.firstOrNull { ext in it.value }?.key ?: "Other"
    }

    fun filenameFromUrl(url: String): String {
        // Split the *decoded* path, not the raw URL text.
        //
        // Archive.org serves files at paths like
        // ".../BigBuckBunny_124/Content%2Fbig_buck_bunny_720p_surround.mp4",
        // where %2F is an encoded slash. Slicing the raw string at the last
        // literal '/' therefore yielded "Content%2Fbig_buck_bunny...mp4" as the
        // "filename", and safeFilename() strips '%' as an unsafe character --
        // so the file landed on disk as "Content2Fbig_buck_bunny...mp4". A real
        // download saved under a name that is not its own, on the very entry
        // the picker selects by default.
        val path = DirectUrl.pathOf(url) ?: url
        val name = path.substringAfterLast('/').substringBefore('#')
        return safeFilename(name.ifBlank { "download" })
    }

    /**
     * Dest filename for playlist URLs: HLS → provisional .ts, DASH → .mp4.
     * DownloadTask may rename HLS fMP4 to .mp4 after parsing EXT-X-MAP.
     */
    fun outputNameForUrl(url: String): String {
        val raw = filenameFromUrl(url)
        return when {
            DirectUrl.looksLikeHlsUrl(url) ->
                raw.removeSuffix(".m3u8").removeSuffix(".M3U8").ifBlank { "stream" } + ".ts"
            DirectUrl.looksLikeDashUrl(url) ->
                raw.removeSuffix(".mpd").removeSuffix(".MPD").ifBlank { "stream" } + ".mp4"
            else -> raw
        }
    }

    fun mimeType(filename: String): String {
        val ext = filename.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty() || ext == filename.lowercase()) return "application/octet-stream"
        return mimeByExt[ext] ?: "application/octet-stream"
    }

    /** User-facing location after publish, e.g. Downloads/VDR/Videos/clip.mp4 */
    fun publicDisplayPath(category: String, filename: String): String =
        "Downloads/VDR/$category/$filename"

    /** MediaStore RELATIVE_PATH (API 29+). DIRECTORY_DOWNLOADS is "Download". */
    fun mediaStoreRelativePath(category: String): String = "Download/VDR/$category/"

    private val mimeByExt = mapOf(
        "mp4" to "video/mp4",
        "mkv" to "video/x-matroska",
        "mov" to "video/quicktime",
        "avi" to "video/x-msvideo",
        "webm" to "video/webm",
        "m4v" to "video/x-m4v",
        "mpg" to "video/mpeg",
        "mpeg" to "video/mpeg",
        "ts" to "video/mp2t",
        "m4s" to "video/iso.segment",
        "m3u8" to "application/vnd.apple.mpegurl",
        "mpd" to "application/dash+xml",
        "vtt" to "text/vtt",
        "srt" to "application/x-subrip",
        "ass" to "text/x-ssa",
        "ssa" to "text/x-ssa",
        "pdf" to "application/pdf",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "txt" to "text/plain",
        "rtf" to "application/rtf",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "csv" to "text/csv",
        "epub" to "application/epub+zip",
        "zip" to "application/zip",
        "rar" to "application/vnd.rar",
        "7z" to "application/x-7z-compressed",
        "tar" to "application/x-tar",
        "gz" to "application/gzip",
        "bz2" to "application/x-bzip2",
        "xz" to "application/x-xz",
        "apk" to "application/vnd.android.package-archive",
        "dmg" to "application/x-apple-diskimage",
        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "aac" to "audio/aac",
        "wav" to "audio/wav",
        "flac" to "audio/flac",
        "ogg" to "audio/ogg",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "heic" to "image/heic",
        "svg" to "image/svg+xml",
    )
}
