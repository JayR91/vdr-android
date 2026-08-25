package com.jayr91.vdr.engine

object Organizer {
    private val categories = mapOf(
        "Videos" to setOf(".mp4", ".mkv", ".mov", ".avi", ".webm", ".m4v", ".mpg", ".mpeg"),
        "Documents" to setOf(".pdf", ".doc", ".docx", ".txt", ".rtf", ".xls", ".xlsx", ".ppt", ".pptx", ".csv", ".epub"),
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
        return safeFilename(url.substringAfterLast('/').ifBlank { "download" })
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
