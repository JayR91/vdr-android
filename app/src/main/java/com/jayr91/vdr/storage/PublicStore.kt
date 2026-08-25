package com.jayr91.vdr.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.jayr91.vdr.engine.Organizer
import java.io.File
import java.io.IOException

/**
 * Copies a finished private/temp download into public Downloads/VDR so Files and
 * Gallery can see it. Segmented RandomAccessFile writes stay in app-private storage.
 */
object PublicStore {
    data class Published(
        val contentUri: String,
        val displayPath: String,
        val filename: String,
    )

    fun publish(context: Context, source: File, filename: String, category: String): Published {
        if (!source.exists()) throw IOException("Downloaded file missing: ${source.absolutePath}")
        val mime = Organizer.mimeType(filename)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishViaMediaStore(context, source, filename, category, mime)
        } else {
            publishLegacy(context, source, filename, category, mime)
        }
    }

    @Suppress("NewApi")
    private fun publishViaMediaStore(
        context: Context,
        source: File,
        filename: String,
        category: String,
        mime: String,
    ): Published {
        val resolver = context.contentResolver
        val relative = Organizer.mediaStoreRelativePath(category)
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val unique = uniqueDisplayName(resolver, collection, relative, filename)
        val values = pendingValues(unique, mime, relative, source.length())
        val uri = resolver.insert(collection, values)
            ?: throw IOException("MediaStore insert failed for $unique")
        try {
            resolver.openOutputStream(uri, "w")?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            } ?: throw IOException("Unable to open MediaStore output for $unique")
            val done = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
                put(MediaStore.MediaColumns.SIZE, source.length())
            }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        indexForGallery(context, category, unique, mime)
        return Published(
            contentUri = uri.toString(),
            displayPath = Organizer.publicDisplayPath(category, unique),
            filename = unique,
        )
    }

    @Suppress("DEPRECATION")
    private fun publishLegacy(
        context: Context,
        source: File,
        filename: String,
        category: String,
        mime: String,
    ): Published {
        val dir = File(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "VDR"),
            category,
        )
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory) {
            throw IOException("Could not create ${dir.absolutePath}")
        }
        val dest = uniqueFile(dir, filename)
        source.copyTo(dest, overwrite = false)
        val scanned = scanAndWait(context, dest, mime)
        val uri = scanned ?: Uri.fromFile(dest)
        return Published(
            contentUri = uri.toString(),
            displayPath = Organizer.publicDisplayPath(category, dest.name),
            filename = dest.name,
        )
    }

    private fun pendingValues(filename: String, mime: String, relative: String, size: Long): ContentValues {
        val now = System.currentTimeMillis() / 1000
        return ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.SIZE, size)
            put(MediaStore.MediaColumns.DATE_ADDED, now)
            put(MediaStore.MediaColumns.DATE_MODIFIED, now)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    private fun uniqueDisplayName(
        resolver: android.content.ContentResolver,
        collection: Uri,
        relativePath: String,
        filename: String,
    ): String {
        val base = filename.substringBeforeLast('.', filename)
        val ext = filename.substringAfterLast('.', "").let { if (it.isEmpty() || it == filename) "" else ".$it" }
        var i = 0
        var candidate = filename
        while (nameTaken(resolver, collection, relativePath, candidate)) {
            i++
            candidate = "$base ($i)$ext"
            if (i > 999) break
        }
        return candidate
    }

    private fun nameTaken(
        resolver: android.content.ContentResolver,
        collection: Uri,
        relativePath: String,
        name: String,
    ): Boolean {
        return try {
            val rel = if (relativePath.endsWith("/")) relativePath else "$relativePath/"
            val selection =
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND (${MediaStore.MediaColumns.RELATIVE_PATH}=? OR ${MediaStore.MediaColumns.RELATIVE_PATH}=?)"
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                selection,
                arrayOf(name, rel, rel.removeSuffix("/")),
                null,
            )?.use { it.moveToFirst() } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun uniqueFile(dir: File, filename: String): File {
        val original = File(dir, filename)
        if (!original.exists()) return original
        val base = original.nameWithoutExtension
        val ext = original.extension.let { if (it.isEmpty()) "" else ".$it" }
        var i = 1
        var candidate: File
        do {
            candidate = File(dir, "$base ($i)$ext")
            i++
        } while (candidate.exists())
        return candidate
    }

    @Suppress("DEPRECATION")
    private fun indexForGallery(context: Context, category: String, filename: String, mime: String) {
        if (category !in setOf("Videos", "Images", "Audio")) return
        val publicFile = File(
            File(
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "VDR"),
                category,
            ),
            filename,
        )
        MediaScannerConnection.scanFile(
            context,
            arrayOf(publicFile.absolutePath),
            arrayOf(mime),
            null,
        )
    }

    private fun scanAndWait(context: Context, file: File, mime: String): Uri? {
        var result: Uri? = null
        val lock = java.lang.Object()
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime)) { _, uri ->
            synchronized(lock) {
                result = uri
                lock.notifyAll()
            }
        }
        synchronized(lock) {
            if (result == null) {
                try {
                    lock.wait(8_000)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        return result
    }
}
