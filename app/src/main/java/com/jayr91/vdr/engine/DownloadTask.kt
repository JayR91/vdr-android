package com.jayr91.vdr.engine

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.min

enum class DownloadStatus(val label: String) {
    QUEUED("queued"),
    SCHEDULED("scheduled"),
    CONNECTING("connecting"),
    DOWNLOADING("downloading"),
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
 * Segmented HTTP downloader — port of VDR engine.DownloadTask.
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
    val stateFile: File = File(destFile.absolutePath + ".vdrstate.json")
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
    /** User-facing Downloads/VDR/... path after MediaStore publish. */
    @Volatile var publicPath: String? = null
    /** content:// MediaStore URI used to open the published file. */
    @Volatile var contentUri: String? = null
    private val speed = SpeedTracker()
    private val lock = Any()
    @Volatile private var worker: Thread? = null

    fun bytesDownloaded(): Long = synchronized(lock) { segments.sumOf { it.downloaded } }

    fun snapshot(): TaskSnapshot = TaskSnapshot(
        id = id,
        url = url,
        displayName = displayName,
        category = category,
        destPath = publicPath ?: destFile.absolutePath,
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
        destFile.delete()
    }

    private fun resumeInternal() {
        running.set(true)
        setStatus(DownloadStatus.DOWNLOADING)
        speed.markStart(bytesDownloaded())
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
            saveState(stateFile, url, totalSize, synchronized(lock) { segments.toList() })
        } catch (_: Exception) {
            // Resume sidecar is best-effort; never fail the transfer because of it.
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

            val loaded = loadState(stateFile)
            if (loaded != null) {
                totalSize = loaded.first
                segments = loaded.second.toMutableList()
                numSegments = segments.size
                destFile.parentFile?.mkdirs()
                if (!destFile.exists()) {
                    RandomAccessFile(destFile, "rw").use { raf ->
                        totalSize?.let { raf.setLength(it) }
                    }
                }
            } else {
                probe()
                initSegments()
                destFile.parentFile?.mkdirs()
                RandomAccessFile(destFile, "rw").use { raf ->
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
                setStatus(DownloadStatus.COMPLETED)
                stateFile.delete()
            } else if (status != DownloadStatus.ERROR) {
                setStatus(DownloadStatus.ERROR, "Incomplete download")
            }
        } catch (e: Exception) {
            setStatus(DownloadStatus.ERROR, e.message ?: e.toString())
        }
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun probe() {
        try {
            val head = Request.Builder().url(url).head().build()
            client.newCall(head).execute().use { resp ->
                if (DirectUrl.isHtmlContentType(resp.header("Content-Type"))) {
                    throw IllegalStateException(DirectUrl.HTML_ERROR)
                }
                totalSize = resp.header("Content-Length")?.toLongOrNull()
                acceptRanges = resp.header("Accept-Ranges").orEmpty().equals("bytes", ignoreCase = true)
            }
        } catch (e: IllegalStateException) {
            throw e
        } catch (_: Exception) {
            // Some servers reject HEAD; the range GET below is the fallback.
        }
        if (totalSize != null && totalSize!! > 0) return
        val get = Request.Builder().url(url).get().header("Range", "bytes=0-0").build()
        client.newCall(get).execute().use { resp ->
            if (DirectUrl.isHtmlContentType(resp.header("Content-Type"))) {
                throw IllegalStateException(DirectUrl.HTML_ERROR)
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
            // Drain at most one byte so a 206 body is consumed; abort a mistaken 200.
            resp.body?.byteStream()?.read()
        }
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
                val req = Request.Builder().url(url).get().apply {
                    if (seg.end != -1L) header("Range", "bytes=$rangeStart-${seg.end}")
                    else if (rangeStart > 0) header("Range", "bytes=$rangeStart-")
                }.build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                    val body = resp.body ?: throw IllegalStateException("empty body")
                    RandomAccessFile(destFile, "rw").use { raf ->
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
                val backoff = min(1 shl attempt, 30) * 1000L
                val waited = System.currentTimeMillis()
                while (System.currentTimeMillis() - waited < backoff) {
                    if (cancelled.get()) return
                    Thread.sleep(200)
                }
            }
        }
    }

    private fun waitIfPaused() {
        while (!running.get() && !cancelled.get()) {
            Thread.sleep(80)
        }
    }
}
