package com.jayr91.vdr.storage

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
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

    /**
     * Prefer a known MediaStore URI; if missing/stale, look up by display name under
     * Download/VDR/{category}/.
     */
    fun resolveContentUri(
        context: Context,
        displayName: String,
        category: String,
        knownUri: String? = null,
    ): Uri? {
        if (!knownUri.isNullOrBlank()) {
            val uri = Uri.parse(knownUri)
            if (uri.scheme == "content" && contentUriExists(context, uri)) return uri
        }
        return findInMediaStore(context, displayName, category)
            ?: findInMediaStore(context, displayName, category = null)
    }

    /** Opens Downloads/VDR (or the category subfolder) in DocumentsUI / Files. */
    fun openDownloadsFolder(context: Context, category: String? = null): Boolean {
        val folderPaths = buildList {
            if (!category.isNullOrBlank()) add("Download/VDR/$category")
            add("Download/VDR")
            add("Download")
        }
        for (path in folderPaths) {
            val docId = "primary:$path"
            val docUri = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                docId,
            )
            val treeUri = DocumentsContract.buildTreeDocumentUri(
                "com.android.externalstorage.documents",
                docId,
            )
            val candidates = listOf(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(treeUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                Intent("android.provider.action.BROWSE").apply {
                    setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
            for (intent in candidates) {
                if (startSafely(context, intent)) return true
            }
        }
        val downloads = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (startSafely(context, downloads)) return true

        // realme / ColorOS Files sometimes only responds to a plain Downloads path browse.
        val filesBrowse = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2FVDR"),
                DocumentsContract.Document.MIME_TYPE_DIR,
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return startSafely(context, filesBrowse)
    }

    /**
     * Opens a published download. For videos, prefers VLC when installed; otherwise
     * falls back to a system chooser (and a short hint Toast).
     */
    fun openPublishedFile(
        context: Context,
        displayName: String,
        category: String,
        knownUri: String?,
        privateFile: File? = null,
    ): Boolean {
        val uri = resolveContentUri(context, displayName, category, knownUri)
            ?: privateFile?.takeIf { it.exists() }?.let {
                FileProvider.getUriForFile(context, "${context.packageName}.files", it)
            }
            ?: return false
        val mime = context.contentResolver.getType(uri)
            ?: Organizer.mimeType(displayName)
        // Prefer VLC for videos; also octet-stream (extensionless downloads like "720p").
        val preferVlc = mime.startsWith("video/") ||
            category.equals("Videos", ignoreCase = true) ||
            mime == "application/octet-stream"

        if (preferVlc) {
            val vlcPkg = findInstalledVlcPackage(context)
            if (vlcPkg != null) {
                // VLC intent filters expect a video MIME; coerce octet-stream.
                val vlcMime = if (mime.startsWith("video/")) mime else "video/*"
                val vlc = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, vlcMime)
                    setPackage(vlcPkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = android.content.ClipData.newRawUri(displayName, uri)
                }
                if (startSafely(context, vlc)) return true
            } else {
                android.widget.Toast.makeText(
                    context,
                    "Install VLC to play, or pick another app",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }

        val open = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(open, "Open $displayName").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri(displayName, uri)
        }
        return startSafely(context, chooser)
    }

    private fun findInstalledVlcPackage(context: Context): String? {
        val pm = context.packageManager
        for (pkg in VLC_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: Exception) {
                // not installed
            }
        }
        return null
    }

    private val VLC_PACKAGES = listOf("org.videolan.vlc", "org.videolan.vlc.debug")

    private fun startSafely(context: Context, intent: Intent): Boolean =
        try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }

    private fun contentUriExists(context: Context, uri: Uri): Boolean =
        try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns._ID),
                null,
                null,
                null,
            )?.use { it.moveToFirst() } == true
        } catch (_: Exception) {
            false
        }

    private fun findInMediaStore(
        context: Context,
        displayName: String,
        category: String?,
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val base = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "VDR",
            )
            val file = if (category.isNullOrBlank()) {
                base.walkTopDown().firstOrNull { it.isFile && it.name == displayName }
            } else {
                File(File(base, category), displayName).takeIf { it.exists() }
            } ?: return null
            return scanAndWait(context, file, Organizer.mimeType(displayName))
                ?: Uri.fromFile(file)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH)
        val selection = if (category.isNullOrBlank()) {
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        } else {
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND (${MediaStore.MediaColumns.RELATIVE_PATH}=? OR ${MediaStore.MediaColumns.RELATIVE_PATH}=?)"
        }
        val relative = category?.let { Organizer.mediaStoreRelativePath(it) }
        val args = if (category.isNullOrBlank()) {
            arrayOf(displayName, "Download/VDR/%")
        } else {
            val rel = relative!!
            arrayOf(displayName, if (rel.endsWith("/")) rel else "$rel/", rel.removeSuffix("/"))
        }
        return try {
            resolver.query(collection, projection, selection, args, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                if (!cursor.moveToFirst()) return null
                Uri.withAppendedPath(collection, cursor.getLong(idCol).toString())
            }
        } catch (_: Exception) {
            null
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
