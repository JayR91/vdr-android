package com.jayr91.vdr.engine

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.min

enum class DownloadStatus(val label: String) {
    QUEUED("queued"),
    SCHEDULED("scheduled"),
    CONNECTING("connecting"),
    DOWNLOADING("downloading"),
    REMUXING("remuxing…"),
    PAUSED("paused"),
    HELD("focus hold"),
    WIFI_HOLD("waiting for Wi‑Fi"),
    COMPLETED("completed"),
    ERROR("error"),
    CANCELLED("cancelled"),
}

data class TaskSnapshot(
    val id: String,
    val url: String,
    val displayName: String,
    val category: String,
    val destPath: String,
    val contentUri: String?,
    val status: DownloadStatus,
    val totalBytes: Long?,
    val downloadedBytes: Long,
    val speedBps: Double,
    val error: String,
    val numSegments: Int,
    val scheduledAt: Long?,
)

/**
 * Segmented HTTP downloader with optional direct HLS / clear DASH playlist modes.
 */
class DownloadTask(
    val id: String,
    val url: String,
    val destFile: File,
    val displayName: String,
    val category: String,
    val numSegmentsRequested: Int = 8,
    val maxRetries: Int = 5,
    val bucket: TokenBucket,
    val scheduledAt: Long? = null,
    val onUpdate: (DownloadTask) -> Unit,
) {
    /** Final file path (may differ from [destFile] when HLS picks .ts vs .mp4). */
    @Volatile var outputFile: File = destFile
    @Volatile var resolvedDisplayName: String = displayName
    /** May retarget when a “file-looking” URL actually served HTML with embedded media. */
    @Volatile private var activeUrl: String = url
    val stateFile: File get() = File(outputFile.absolutePath + ".vdrstate.json")
    private val running = AtomicBoolean(true)
    private val cancelled = AtomicBoolean(false)
    private val userPaused = AtomicBoolean(false)
    private val wifiHeldWhileActive = AtomicBoolean(false)
    @Volatile var status: DownloadStatus = if (scheduledAt != null) DownloadStatus.SCHEDULED else DownloadStatus.QUEUED
    @Volatile var errorMessage: String = ""
    @Volatile var totalSize: Long? = null
    @Volatile var acceptRanges: Boolean = false
    @Volatile var segments: MutableList<SegmentState> = mutableListOf()
    @Volatile var numSegments: Int = numSegmentsRequested
    @Volatile var publicPath: String? = null
    @Volatile var contentUri: String? = null
    /** "hls", "dash", or null for normal byte-range download. */
    @Volatile var streamKind: String? = null
    private val speed = SpeedTracker()
    private val lock = Any()
    @Volatile private var worker: Thread? = null
    @Volatile private var streamBytes: Long = 0
    @Volatile private var streamMediaUrl: String = ""
    @Volatile private var streamNextIndex: Int = 0
    @Volatile private var lastProbeContentType: String? = null

    fun bytesDownloaded(): Long = if (streamKind != null) {
        synchronized(lock) { segments.count { it.downloaded > 0 }.toLong() }
    } else {
        synchronized(lock) { segments.sumOf { it.downloaded } }
    }

    fun snapshot(): TaskSnapshot = TaskSnapshot(
        id = id,
        url = url,
        displayName = resolvedDisplayName,
        category = category,
        destPath = publicPath ?: outputFile.absolutePath,
        contentUri = contentUri,
        status = status,
        totalBytes = totalSize,
        downloadedBytes = bytesDownloaded(),
        speedBps = if (status == DownloadStatus.DOWNLOADING) speed.bytesPerSecond() else 0.0,
        error = errorMessage,
        numSegments = numSegments,
        scheduledAt = scheduledAt,
    )

    fun start() {
        if (cancelled.get()) return
        if (worker?.isAlive == true) {
            running.set(true)
            setStatus(DownloadStatus.DOWNLOADING)
            return
        }
        worker = thread(name = "vdr-$id", isDaemon = true) { runInternal() }
    }

    fun isUserPaused(): Boolean = userPaused.get()

    fun pause() {
        if (status in setOf(
                DownloadStatus.DOWNLOADING,
                DownloadStatus.CONNECTING,
                DownloadStatus.HELD,
                DownloadStatus.WIFI_HOLD,
                DownloadStatus.QUEUED,
            )
        ) {
            userPaused.set(true)
            running.set(false)
            wifiHeldWhileActive.set(false)
            setStatus(DownloadStatus.PAUSED)
            persist()
        }
    }

    fun holdForFocus() {
        if (userPaused.get()) return
        if (status in setOf(DownloadStatus.DOWNLOADING, DownloadStatus.CONNECTING)) {
            running.set(false)
            setStatus(DownloadStatus.HELD)
            persist()
        }
    }

    fun holdForWifi() {
        if (userPaused.get()) return
        if (status in setOf(
                DownloadStatus.COMPLETED,
                DownloadStatus.ERROR,
                DownloadStatus.CANCELLED,
                DownloadStatus.PAUSED,
                DownloadStatus.WIFI_HOLD,
                DownloadStatus.SCHEDULED,
            )
        ) return
        wifiHeldWhileActive.set(status in setOf(DownloadStatus.DOWNLOADING, DownloadStatus.CONNECTING, DownloadStatus.HELD))
        if (wifiHeldWhileActive.get()) running.set(false)
        setStatus(DownloadStatus.WIFI_HOLD)
        persist()
    }

    fun releaseFromWifi() {
        if (status != DownloadStatus.WIFI_HOLD || userPaused.get()) return
        if (wifiHeldWhileActive.getAndSet(false) && worker?.isAlive == true) {
            resumeInternal()
        } else {
            setStatus(DownloadStatus.QUEUED)
        }
    }

    fun convertWifiHoldToFocusHold() {
        if (status != DownloadStatus.WIFI_HOLD || userPaused.get()) return
        if (wifiHeldWhileActive.getAndSet(false) || worker?.isAlive == true) {
            running.set(false)
            setStatus(DownloadStatus.HELD)
            persist()
        } else {
            setStatus(DownloadStatus.QUEUED)
        }
    }

    fun releaseFromFocus() {
        if (status != DownloadStatus.HELD || userPaused.get()) return
        resumeInternal()
    }

    fun resume() {
        when (status) {
            DownloadStatus.PAUSED -> {
                userPaused.set(false)
                resumeInternal()
            }
            DownloadStatus.WIFI_HOLD -> {
                userPaused.set(false)
                wifiHeldWhileActive.set(false)
                resumeInternal()
            }
            DownloadStatus.HELD -> releaseFromFocus()
            DownloadStatus.ERROR -> start()
            else -> {}
        }
    }

    fun waitForWifiFromPause() {
        if (status != DownloadStatus.PAUSED) return
        userPaused.set(false)
        wifiHeldWhileActive.set(worker?.isAlive == true)
        if (wifiHeldWhileActive.get()) running.set(false)
        setStatus(DownloadStatus.WIFI_HOLD)
        persist()
    }

    fun isCancelled(): Boolean = cancelled.get()

    fun cancel() {
        cancelled.set(true)
        running.set(true)
        setStatus(DownloadStatus.CANCELLED)
        stateFile.delete()
        outputFile.delete()
        if (destFile != outputFile) destFile.delete()
    }

    private fun resumeInternal() {
        running.set(true)
        setStatus(DownloadStatus.DOWNLOADING)
        speed.markStart(if (streamKind != null) streamBytes else bytesDownloaded())
        if (worker?.isAlive != true) {
            worker = thread(name = "vdr-$id", isDaemon = true) { runInternal() }
        }
    }

    private fun setStatus(next: DownloadStatus, error: String = "") {
        status = next
        errorMessage = error
        onUpdate(this)
    }

    private fun persist() {
        try {
            val kind = streamKind
            if (kind != null) {
                saveStreamState(
                    stateFile, url, kind, streamMediaUrl, streamNextIndex, streamBytes,
                    synchronized(lock) { segments.toList() },
                )
            } else {
                saveState(stateFile, url, totalSize, synchronized(lock) { segments.toList() })
            }
        } catch (_: Exception) {
            // Resume sidecar is best-effort.
        }
    }

    private fun runInternal() {
        try {
            cancelled.set(false)
            running.set(true)
            DirectUrl.rejectionMessage(url)?.let { msg ->
                setStatus(DownloadStatus.ERROR, msg)
                return
            }
            setStatus(DownloadStatus.CONNECTING)

            val resumed = loadStreamState(stateFile)
            when {
                resumed?.kind == "hls" || DirectUrl.looksLikeHlsUrl(activeUrl) -> {
                    runHls(resumed?.takeIf { it.kind == "hls" })
                    return
                }
                resumed?.kind == "dash" || DirectUrl.looksLikeDashUrl(activeUrl) -> {
                    runDash(resumed?.takeIf { it.kind == "dash" })
                    return
                }
            }

            val loaded = loadState(stateFile)
            if (loaded != null) {
                totalSize = loaded.first
                segments = loaded.second.toMutableList()
                numSegments = segments.size
                outputFile.parentFile?.mkdirs()
                if (!outputFile.exists()) {
                    RandomAccessFile(outputFile, "rw").use { raf ->
                        totalSize?.let { raf.setLength(it) }
                    }
                }
            } else {
                probe()
                when {
                    DirectUrl.isHlsContentType(lastProbeContentType) -> {
                        runHls(null)
                        return
                    }
                    DirectUrl.isDashContentType(lastProbeContentType) -> {
                        runDash(null)
                        return
                    }
                }
                initSegments()
                outputFile.parentFile?.mkdirs()
                RandomAccessFile(outputFile, "rw").use { raf ->
                    totalSize?.let { raf.setLength(it) }
                }
                persist()
            }

            setStatus(DownloadStatus.DOWNLOADING)
            speed.markStart(bytesDownloaded())

            val threads = segments.mapNotNull { seg ->
                if (seg.isComplete()) null
                else thread(name = "vdr-$id-s${seg.index}", isDaemon = true) { downloadSegment(seg) }
            }
            val stopMonitor = AtomicBoolean(false)
            val monitor = thread(name = "vdr-$id-progress", isDaemon = true) {
                while (!stopMonitor.get()) {
                    Thread.sleep(400)
                    if (status == DownloadStatus.DOWNLOADING) {
                        persist()
                        onUpdate(this)
                    }
                }
            }
            threads.forEach { it.join() }
            stopMonitor.set(true)
            monitor.join(500)

            if (cancelled.get()) {
                setStatus(DownloadStatus.CANCELLED)
                stateFile.delete()
                return
            }
            if (status in setOf(DownloadStatus.PAUSED, DownloadStatus.HELD, DownloadStatus.WIFI_HOLD)) {
                persist()
                return
            }
            val complete = segments.all { it.isComplete() } ||
                (segments.any { it.end == -1L } && status != DownloadStatus.ERROR)
            if (complete) {
                finishWithOptionalRemux()
            } else if (status != DownloadStatus.ERROR) {
                setStatus(DownloadStatus.ERROR, "Incomplete download")
            }
        } catch (e: OutOfMemoryError) {
            // Never let OOM kill the process — mark ERROR so Room does not keep
            // restarting the same huge HLS/DASH task on every launch.
            try {
                setStatus(DownloadStatus.ERROR, "Out of memory — try a smaller file")
            } catch (_: Throwable) {
            }
        } catch (e: Exception) {
            setStatus(DownloadStatus.ERROR, e.message ?: e.toString())
        }
    }

    private fun runHls(resumed: StreamResumeState?) {
        streamKind = "hls"
        setStatus(DownloadStatus.CONNECTING)

        val media: HlsPlaylist.MediaPlaylist
        val nextIndex: Int
        if (resumed != null) {
            streamMediaUrl = resumed.mediaUrl
            nextIndex = resumed.nextIndex
            streamBytes = resumed.bytesDownloaded
            media = fetchAndResolveHls(streamMediaUrl)
            applyOutputExtension(if (media.initMapUri != null) ".mp4" else ".ts", listOf(".m3u8"))
            truncateOutputTo(streamBytes)
        } else {
            media = fetchAndResolveHls(activeUrl)
            if (media.encrypted) {
                setStatus(DownloadStatus.ERROR, HlsPlaylist.ENCRYPTED_ERROR)
                return
            }
            applyOutputExtension(if (media.initMapUri != null) ".mp4" else ".ts", listOf(".m3u8"))
            streamMediaUrl = media.playlistUrl
            nextIndex = 0
            streamBytes = 0
            resetOutputFile()
        }
        if (media.encrypted) {
            setStatus(DownloadStatus.ERROR, HlsPlaylist.ENCRYPTED_ERROR)
            return
        }
        if (media.hasByteRange) {
            setStatus(DownloadStatus.ERROR, HlsPlaylist.BYTERANGE_ERROR)
            return
        }

        val workUrls = buildList {
            media.initMapUri?.let { add(it) }
            media.segments.forEach { add(it.uri) }
        }
        appendStreamParts(workUrls, nextIndex)
    }

    private fun runDash(resumed: StreamResumeState?) {
        streamKind = "dash"
        setStatus(DownloadStatus.CONNECTING)

        val nextIndex: Int
        val workUrls: List<String>
        if (resumed != null) {
            streamMediaUrl = resumed.mediaUrl
            nextIndex = resumed.nextIndex
            streamBytes = resumed.bytesDownloaded
            val manifest = fetchAndParseDash(streamMediaUrl)
            if (manifest.protected) {
                setStatus(DownloadStatus.ERROR, DashMpd.DRM_ERROR)
                return
            }
            if (manifest.usesTimeline) {
                setStatus(DownloadStatus.ERROR, DashMpd.TIMELINE_ERROR)
                return
            }
            if (manifest.unknownLength) {
                setStatus(DownloadStatus.ERROR, DashMpd.UNKNOWN_LENGTH_ERROR)
                return
            }
            val video = manifest.bestVideo ?: run {
                setStatus(DownloadStatus.ERROR, DashMpd.NO_VIDEO_ERROR)
                return
            }
            applyOutputExtension(".mp4", listOf(".mpd"))
            truncateOutputTo(streamBytes)
            workUrls = buildList {
                video.initUrl?.let { add(it) }
                addAll(video.mediaUrls)
            }
        } else {
            val manifest = fetchAndParseDash(activeUrl)
            if (manifest.protected) {
                setStatus(DownloadStatus.ERROR, DashMpd.DRM_ERROR)
                return
            }
            if (manifest.usesTimeline) {
                setStatus(DownloadStatus.ERROR, DashMpd.TIMELINE_ERROR)
                return
            }
            if (manifest.unknownLength) {
                setStatus(DownloadStatus.ERROR, DashMpd.UNKNOWN_LENGTH_ERROR)
                return
            }
            val video = manifest.bestVideo ?: run {
                setStatus(DownloadStatus.ERROR, DashMpd.NO_VIDEO_ERROR)
                return
            }
            applyOutputExtension(".mp4", listOf(".mpd"))
            streamMediaUrl = manifest.mpdUrl
            nextIndex = 0
            streamBytes = 0
            resetOutputFile()
            workUrls = buildList {
                video.initUrl?.let { add(it) }
                addAll(video.mediaUrls)
            }
        }
        appendStreamParts(workUrls, nextIndex)
    }

    private fun appendStreamParts(workUrls: List<String>, startIndex: Int) {
        numSegments = workUrls.size
        // Progress UI uses segment count for streams; byte total is tracked in streamBytes.
        totalSize = null
        segments = workUrls.mapIndexed { i, _ ->
            SegmentState(i, 0, 0, downloaded = if (i < startIndex) 1 else 0)
        }.toMutableList()

        setStatus(DownloadStatus.DOWNLOADING)
        speed.markStart(streamBytes)
        streamNextIndex = startIndex
        persist()

        outputFile.parentFile?.mkdirs()
        if (!outputFile.exists()) outputFile.createNewFile()

        for (i in startIndex until workUrls.size) {
            if (cancelled.get()) {
                setStatus(DownloadStatus.CANCELLED)
                stateFile.delete()
                return
            }
            waitIfPaused()
            if (cancelled.get()) {
                setStatus(DownloadStatus.CANCELLED)
                stateFile.delete()
                return
            }
            if (status in setOf(DownloadStatus.PAUSED, DownloadStatus.HELD, DownloadStatus.WIFI_HOLD)) {
                streamNextIndex = i
                persist()
                return
            }
            val written = appendPartToFile(workUrls[i])
            if (cancelled.get()) {
                setStatus(DownloadStatus.CANCELLED)
                stateFile.delete()
                return
            }
            if (status == DownloadStatus.ERROR) return
            if (status in setOf(DownloadStatus.PAUSED, DownloadStatus.HELD, DownloadStatus.WIFI_HOLD)) {
                streamNextIndex = i
                persist()
                return
            }
            streamBytes += written
            synchronized(lock) {
                if (i < segments.size) segments[i].downloaded = 1
            }
            streamNextIndex = i + 1
            persist()
            onUpdate(this)
        }

        if (cancelled.get()) {
            setStatus(DownloadStatus.CANCELLED)
            stateFile.delete()
            return
        }
        if (status in setOf(DownloadStatus.PAUSED, DownloadStatus.HELD, DownloadStatus.WIFI_HOLD)) {
            return
        }
        finishWithOptionalRemux()
    }

    /** Remux `.ts` / playlist concat to `.mp4` via stream copy; keep original on failure. */
    private fun finishWithOptionalRemux() {
        var note = ""
        if (Remuxer.needsRemux(outputFile, streamKind)) {
            setStatus(DownloadStatus.REMUXING)
            when (val outcome = Remuxer.remuxCopyToMp4(outputFile)) {
                is Remuxer.Outcome.Success -> {
                    outputFile = outcome.file
                    resolvedDisplayName = outcome.file.name
                }
                is Remuxer.Outcome.Failed -> {
                    note = "Kept ${outputFile.extension}; remux skipped (${outcome.message})"
                }
            }
        }
        setStatus(DownloadStatus.COMPLETED, note)
        stateFile.delete()
    }

    private fun applyOutputExtension(ext: String, stripSuffixes: List<String>) {
        val current = outputFile
        if (current.name.endsWith(ext, ignoreCase = true)) {
            resolvedDisplayName = current.name
            return
        }
        var base = current.nameWithoutExtension.ifBlank {
            Organizer.safeFilename(url).substringBeforeLast('.').ifBlank { "stream" }
        }
        for (s in stripSuffixes) {
            base = base.removeSuffix(s.removePrefix(".")).removeSuffix(s)
        }
        if (base.isBlank()) base = "stream"
        val next = File(current.parentFile, "$base$ext")
        if (current.exists() && current != next) {
            current.renameTo(next)
        }
        val oldState = File(current.absolutePath + ".vdrstate.json")
        outputFile = next
        resolvedDisplayName = next.name
        if (oldState.exists() && oldState != stateFile) {
            oldState.renameTo(stateFile)
        }
        if (destFile != next && destFile.exists()) {
            val n = destFile.name.lowercase()
            if (stripSuffixes.any { n.endsWith(it) }) destFile.delete()
        }
    }

    private fun resetOutputFile() {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()
        outputFile.createNewFile()
    }

    private fun truncateOutputTo(bytes: Long) {
        outputFile.parentFile?.mkdirs()
        if (!outputFile.exists()) {
            outputFile.createNewFile()
            return
        }
        if (outputFile.length() > bytes) {
            RandomAccessFile(outputFile, "rw").use { it.setLength(bytes) }
        }
    }

    private fun fetchAndResolveHls(startUrl: String): HlsPlaylist.MediaPlaylist {
        var currentUrl = startUrl
        repeat(3) {
            waitIfPaused()
            if (cancelled.get()) throw IllegalStateException("cancelled")
            val (ct, body) = fetchText(currentUrl)
            if (DirectUrl.isHtmlContentType(ct)) throw IllegalStateException(DirectUrl.HTML_ERROR)
            if (!HlsPlaylist.isPlaylistBody(body) &&
                !DirectUrl.isHlsContentType(ct) &&
                !DirectUrl.looksLikeHlsUrl(currentUrl)
            ) {
                throw IllegalStateException("URL is not an HLS playlist")
            }
            when (val parsed = HlsPlaylist.parse(body, currentUrl)) {
                is HlsPlaylist.Parsed.Master -> {
                    currentUrl = HlsPlaylist.pickHighestBandwidth(parsed.variants).uri
                }
                is HlsPlaylist.Parsed.Media -> return parsed.playlist
            }
        }
        throw IllegalStateException("HLS master playlist too deeply nested")
    }

    private fun fetchAndParseDash(mpdUrl: String): DashMpd.Manifest {
        waitIfPaused()
        if (cancelled.get()) throw IllegalStateException("cancelled")
        val (ct, body) = fetchText(mpdUrl)
        if (DirectUrl.isHtmlContentType(ct)) throw IllegalStateException(DirectUrl.HTML_ERROR)
        if (!DashMpd.isManifestBody(body) &&
            !DirectUrl.isDashContentType(ct) &&
            !DirectUrl.looksLikeDashUrl(mpdUrl)
        ) {
            throw IllegalStateException("URL is not a DASH manifest")
        }
        return DashMpd.parse(body, mpdUrl)
    }

    private fun fetchText(requestUrl: String): Pair<String?, String> {
        var attempt = 0
        var last: Exception? = null
        while (attempt <= maxRetries) {
            if (cancelled.get()) throw IllegalStateException("cancelled")
            try {
                waitIfPaused()
                val req = Request.Builder().url(requestUrl).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                    val ct = resp.header("Content-Type")
                    val body = resp.body?.string() ?: throw IllegalStateException("empty playlist")
                    return ct to body
                }
            } catch (e: Exception) {
                last = e
                attempt++
                if (attempt > maxRetries) break
                backoff(attempt)
            }
        }
        throw last ?: IllegalStateException("playlist fetch failed")
    }

    /**
     * Stream one HLS/DASH part straight onto [outputFile] (append).
     * Never buffers a whole segment in RAM — that OOMed on large fMP4 parts.
     * @return bytes written for this part (0 if cancelled / failed after retries).
     */
    private fun appendPartToFile(partUrl: String): Long {
        var attempt = 0
        while (attempt <= maxRetries) {
            if (cancelled.get()) return 0L
            val startLen = outputFile.length()
            try {
                waitIfPaused()
                if (cancelled.get()) return 0L
                val req = Request.Builder().url(partUrl).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                    val body = resp.body ?: throw IllegalStateException("empty segment")
                    var written = 0L
                    FileOutputStream(outputFile, /* append = */ true).use { fos ->
                        val buf = ByteArray(65536)
                        var read: Int
                        val stream = body.byteStream()
                        while (stream.read(buf).also { read = it } != -1) {
                            if (cancelled.get()) return 0L
                            waitIfPaused()
                            bucket.consume(read, cancelled::get) { running.get() && !cancelled.get() }
                            if (cancelled.get()) return 0L
                            waitIfPaused()
                            fos.write(buf, 0, read)
                            written += read
                            speed.setDownloaded(streamBytes + written)
                            if (status == DownloadStatus.DOWNLOADING) onUpdate(this)
                        }
                        fos.flush()
                    }
                    return written
                }
            } catch (e: OutOfMemoryError) {
                // Truncate partial append from this attempt, then fail cleanly.
                runCatching {
                    if (outputFile.length() > startLen) {
                        RandomAccessFile(outputFile, "rw").use { it.setLength(startLen) }
                    }
                }
                setStatus(DownloadStatus.ERROR, "Out of memory — try a smaller file")
                return 0L
            } catch (e: Exception) {
                runCatching {
                    if (outputFile.length() > startLen) {
                        RandomAccessFile(outputFile, "rw").use { it.setLength(startLen) }
                    }
                }
                if (status == DownloadStatus.ERROR) return 0L
                attempt++
                if (attempt > maxRetries) {
                    setStatus(
                        DownloadStatus.ERROR,
                        "Segment failed after $maxRetries retries: ${e.message}",
                    )
                    return 0L
                }
                backoff(attempt)
            }
        }
        return 0L
    }

    private fun backoff(attempt: Int) {
        val delay = min(1 shl attempt, 30) * 1000L
        val waited = System.currentTimeMillis()
        while (System.currentTimeMillis() - waited < delay) {
            if (cancelled.get()) return
            Thread.sleep(200)
        }
    }

    private val client: OkHttpClient = VdrHttp.newClient()

    private fun probe() {
        try {
            val head = Request.Builder().url(activeUrl).head()
                .header("Connection", "close")
                .build()
            client.newCall(head).execute().use { resp ->
                lastProbeContentType = resp.header("Content-Type")
                // Drain any unexpected HEAD body so keep-alive cannot desync.
                try {
                    resp.body?.string()
                } catch (_: Exception) {
                    resp.body?.close()
                }
                if (DirectUrl.isHtmlContentType(lastProbeContentType)) {
                    // Soft: HTML on HEAD may be a CDN disguise or a description page.
                    lastProbeContentType = null
                } else if (DirectUrl.isHlsContentType(lastProbeContentType) ||
                    DirectUrl.isDashContentType(lastProbeContentType)
                ) {
                    return
                } else {
                    totalSize = resp.header("Content-Length")?.toLongOrNull()
                    acceptRanges = resp.header("Accept-Ranges").orEmpty().equals("bytes", ignoreCase = true)
                }
            }
        } catch (e: IllegalStateException) {
            throw e
        } catch (_: Exception) {
            // Some servers reject HEAD; the range GET below is the fallback.
        }
        if (DirectUrl.isHlsContentType(lastProbeContentType) ||
            DirectUrl.isDashContentType(lastProbeContentType)
        ) return
        if (totalSize != null && totalSize!! > 0) {
            return
        }
        val get = Request.Builder().url(activeUrl).get().header("Range", "bytes=0-0").build()
        client.newCall(get).execute().use { resp ->
            lastProbeContentType = resp.header("Content-Type") ?: lastProbeContentType
            if (DirectUrl.isHtmlContentType(lastProbeContentType)) {
                if (tryRecoverHtmlAsMedia(resp)) return
                throw IllegalStateException(DirectUrl.HTML_ERROR)
            }
            if (!resp.isSuccessful && resp.code != 206) {
                throw IllegalStateException("HTTP ${resp.code}")
            }
            if (DirectUrl.isHlsContentType(lastProbeContentType) ||
                DirectUrl.isDashContentType(lastProbeContentType)
            ) {
                resp.body?.close()
                return
            }
            if (resp.code == 206) {
                acceptRanges = true
                resp.header("Content-Range")?.substringAfter('/')?.toLongOrNull()?.let {
                    if (it > 0) totalSize = it
                }
            } else {
                if (totalSize == null) {
                    totalSize = resp.header("Content-Length")?.toLongOrNull()
                }
            }
            // Consume the 1-byte range body so the connection stays reusable.
            resp.body?.byteStream()?.read()
        }
    }

    /**
     * When probe sees text/html: extract clear media from the body and retarget,
     * or re-GET without Range when the path already looks like a media file.
     * @return true if probe state was recovered (caller should return from probe).
     */
    private fun tryRecoverHtmlAsMedia(rangeResp: okhttp3.Response): Boolean {
        val html = try {
            rangeResp.body?.string().orEmpty().take(2 * 1024 * 1024)
        } catch (_: Exception) {
            ""
        }
        if (html.isNotBlank()) {
            val media = MediaGrabber.extractMediaUrlsFromHtml(html, activeUrl)
            if (media.isNotEmpty()) {
                activeUrl = media.first()
                lastProbeContentType = null
                totalSize = null
                acceptRanges = false
                reprobeActiveUrl()
                return true
            }
        }
        // Path looked like media but CDN served HTML on Range — try plain GET once.
        if (DirectUrl.looksLikeDirectFile(activeUrl) || pathSuggestsMedia(activeUrl)) {
            val plain = Request.Builder().url(activeUrl).get().build()
            client.newCall(plain).execute().use { resp ->
                val ct = resp.header("Content-Type")
                if (DirectUrl.isHtmlContentType(ct)) {
                    val body = resp.body?.string().orEmpty().take(2 * 1024 * 1024)
                    val media = MediaGrabber.extractMediaUrlsFromHtml(body, activeUrl)
                    if (media.isNotEmpty()) {
                        activeUrl = media.first()
                        lastProbeContentType = null
                        totalSize = null
                        acceptRanges = false
                        reprobeActiveUrl()
                        return true
                    }
                    return false
                }
                if (!resp.isSuccessful && resp.code != 206) return false
                lastProbeContentType = ct
                if (DirectUrl.isHlsContentType(ct) || DirectUrl.isDashContentType(ct)) return true
                totalSize = resp.header("Content-Length")?.toLongOrNull()
                acceptRanges = resp.header("Accept-Ranges").orEmpty().equals("bytes", ignoreCase = true)
                resp.body?.close()
                return true
            }
        }
        return false
    }

    private fun reprobeActiveUrl() {
        val get = Request.Builder().url(activeUrl).get().header("Range", "bytes=0-0").build()
        client.newCall(get).execute().use { resp ->
            lastProbeContentType = resp.header("Content-Type")
            if (DirectUrl.isHtmlContentType(lastProbeContentType)) {
                throw IllegalStateException(DirectUrl.HTML_ERROR)
            }
            if (!resp.isSuccessful && resp.code != 206) {
                throw IllegalStateException("HTTP ${resp.code}")
            }
            if (DirectUrl.isHlsContentType(lastProbeContentType) ||
                DirectUrl.isDashContentType(lastProbeContentType)
            ) {
                return
            }
            if (resp.code == 206) {
                acceptRanges = true
                resp.header("Content-Range")?.substringAfter('/')?.toLongOrNull()?.let {
                    if (it > 0) totalSize = it
                }
            } else {
                totalSize = resp.header("Content-Length")?.toLongOrNull()
            }
            resp.body?.byteStream()?.read()
        }
    }

    private fun pathSuggestsMedia(u: String): Boolean {
        val name = try {
            java.net.URI(u.trim().replace(" ", "%20")).path?.substringAfterLast('/')?.lowercase()
        } catch (_: Exception) {
            u.substringAfterLast('/').substringBefore('?').lowercase()
        }.orEmpty()
        return DirectUrl.directFileSuffixes.any { name.endsWith(it) }
    }

    private fun initSegments() {
        val size = totalSize
        val ranges = acceptRanges && size != null && size > 0
        if (!ranges) {
            segments = mutableListOf(SegmentState(0, 0, -1))
            numSegments = 1
            return
        }
        val n = min(numSegmentsRequested, 32).coerceAtLeast(1)
        val segSize = size!! / n
        val list = mutableListOf<SegmentState>()
        var start = 0L
        for (i in 0 until n) {
            val end = if (i == n - 1) size - 1 else start + segSize - 1
            list += SegmentState(i, start, end)
            start = end + 1
        }
        segments = list
        numSegments = n
    }

    private fun downloadSegment(seg: SegmentState) {
        var attempt = 0
        while (attempt <= maxRetries) {
            if (cancelled.get()) return
            try {
                waitIfPaused()
                if (cancelled.get()) return
                val rangeStart = seg.start + seg.downloaded
                if (seg.end != -1L && rangeStart > seg.end) return
                val req = Request.Builder().url(activeUrl).get().apply {
                    if (seg.end != -1L) header("Range", "bytes=$rangeStart-${seg.end}")
                    else if (rangeStart > 0) header("Range", "bytes=$rangeStart-")
                }.build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                    val body = resp.body ?: throw IllegalStateException("empty body")
                    RandomAccessFile(outputFile, "rw").use { raf ->
                        raf.seek(rangeStart)
                        val buf = ByteArray(65536)
                        var read: Int
                        val stream = body.byteStream()
                        while (stream.read(buf).also { read = it } != -1) {
                            if (cancelled.get()) return
                            waitIfPaused()
                            bucket.consume(read, cancelled::get) { running.get() && !cancelled.get() }
                            if (cancelled.get()) return
                            waitIfPaused()
                            raf.write(buf, 0, read)
                            synchronized(lock) { seg.downloaded += read }
                            speed.setDownloaded(bytesDownloaded())
                        }
                    }
                }
                persist()
                onUpdate(this)
                return
            } catch (e: Exception) {
                attempt++
                if (attempt > maxRetries) {
                    setStatus(DownloadStatus.ERROR, "Segment ${seg.index} failed after $maxRetries retries: ${e.message}")
                    return
                }
                backoff(attempt)
            }
        }
    }

    private fun waitIfPaused() {
        while (!running.get() && !cancelled.get()) {
            Thread.sleep(80)
        }
    }
}
