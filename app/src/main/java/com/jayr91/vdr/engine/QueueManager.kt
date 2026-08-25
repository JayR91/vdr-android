package com.jayr91.vdr.engine

import java.io.File
import java.util.UUID

class QueueManager(
    var maxConcurrent: Int = 3,
) {
    val bucket = TokenBucket(null)
    private val tasks = mutableListOf<DownloadTask>()
    private val lock = Any()
    var userLimit: Long? = null
        private set
    var focusPolicy: String = FocusGuard.POLICY_OFF
        private set
    var wifiOnly: Boolean = false
        private set
    var unmetered: Boolean = true
        private set
    var onUpdate: ((DownloadTask) -> Unit)? = null

    fun wifiBlocked(): Boolean = wifiOnly && !unmetered

    fun snapshot(): List<TaskSnapshot> = synchronized(lock) { tasks.map { it.snapshot() } }

    fun setSpeedLimit(bytesPerSec: Long?) {
        userLimit = bytesPerSec
        applyBucket()
    }

    fun applyFocusPolicy(policy: String) {
        focusPolicy = policy
        applyBucket()
        refreshHolds()
    }

    fun setWifiOnly(enabled: Boolean) {
        wifiOnly = enabled
        refreshHolds()
    }

    fun setUnmetered(value: Boolean) {
        if (unmetered == value) return
        unmetered = value
        refreshHolds()
    }

    fun refreshHolds() {
        val copy = synchronized(lock) { tasks.toList() }
        copy.forEach { t ->
            if (t.isUserPaused()) return@forEach
            if (t.status in setOf(
                    DownloadStatus.COMPLETED,
                    DownloadStatus.ERROR,
                    DownloadStatus.CANCELLED,
                    DownloadStatus.SCHEDULED,
                )
            ) return@forEach
            when {
                wifiBlocked() -> t.holdForWifi()
                focusPolicy == FocusGuard.POLICY_HOLD -> {
                    if (t.status == DownloadStatus.WIFI_HOLD) t.convertWifiHoldToFocusHold()
                    t.holdForFocus()
                }
                else -> {
                    if (t.status == DownloadStatus.WIFI_HOLD) t.releaseFromWifi()
                    if (t.status == DownloadStatus.HELD) t.releaseFromFocus()
                }
            }
        }
        maybeStart()
    }

    private fun applyBucket() {
        val focusCap = if (focusPolicy == FocusGuard.POLICY_CRAWL) FocusGuard.CRAWL_BYTES_PER_SEC else null
        val limits = listOfNotNull(userLimit, focusCap)
        bucket.setRate(if (limits.isEmpty()) null else limits.minOrNull())
    }

    fun add(
        url: String,
        destFile: File,
        displayName: String,
        category: String,
        numSegments: Int = 8,
        scheduledAt: Long? = null,
        id: String = UUID.randomUUID().toString(),
        autoStart: Boolean = true,
        initialStatus: DownloadStatus? = null,
        initialError: String? = null,
        publishedPath: String? = null,
        contentUri: String? = null,
    ): DownloadTask {
        val task = DownloadTask(
            id = id,
            url = url,
            destFile = destFile,
            displayName = displayName,
            category = category,
            numSegmentsRequested = numSegments,
            bucket = bucket,
            scheduledAt = scheduledAt,
            onUpdate = { t ->
                onUpdate?.invoke(t)
                if (t.status in setOf(DownloadStatus.COMPLETED, DownloadStatus.ERROR, DownloadStatus.CANCELLED)) {
                    maybeStart()
                }
            },
        )
        if (initialStatus != null) task.status = initialStatus
        if (!initialError.isNullOrBlank()) task.errorMessage = initialError
        task.publicPath = publishedPath
        task.contentUri = contentUri
        synchronized(lock) { tasks.add(task) }
        onUpdate?.invoke(task)
        if (!autoStart) return task
        if (scheduledAt != null && scheduledAt > System.currentTimeMillis()) {
            Thread {
                while (!task.isCancelled() && System.currentTimeMillis() < scheduledAt) {
                    Thread.sleep(1000)
                }
                if (!task.isCancelled()) {
                    task.status = DownloadStatus.QUEUED
                    refreshHolds()
                }
            }.start()
        } else {
            refreshHolds()
        }
        return task
    }

    fun pause(id: String) = find(id)?.pause()
    fun resume(id: String) {
        val t = find(id) ?: return
        if (wifiBlocked()) {
            if (t.status == DownloadStatus.PAUSED) t.waitForWifiFromPause()
            else t.holdForWifi()
            return
        }
        t.resume()
        maybeStart()
    }
    fun cancel(id: String) {
        find(id)?.cancel()
        maybeStart()
    }

    fun remove(id: String) {
        val t = find(id) ?: return
        t.cancel()
        synchronized(lock) { tasks.removeAll { it.id == id } }
        onUpdate?.invoke(t)
    }

    fun find(id: String): DownloadTask? = synchronized(lock) { tasks.find { it.id == id } }

    fun maybeStart() {
        if (focusPolicy == FocusGuard.POLICY_HOLD) return
        if (wifiBlocked()) return
        val toStart = synchronized(lock) {
            val active = tasks.count {
                it.status in setOf(DownloadStatus.CONNECTING, DownloadStatus.DOWNLOADING)
            }
            val queued = tasks.filter { it.status == DownloadStatus.QUEUED }
            queued.take((maxConcurrent - active).coerceAtLeast(0))
        }
        toStart.forEach { it.start() }
    }
}
