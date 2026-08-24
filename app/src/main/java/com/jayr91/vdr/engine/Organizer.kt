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
}
