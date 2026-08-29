package com.jayr91.vdr.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.jayr91.vdr.MainActivity
import com.jayr91.vdr.R
import com.jayr91.vdr.data.DownloadEntity
import com.jayr91.vdr.data.VdrDatabase
import com.jayr91.vdr.data.VdrSettings
import com.jayr91.vdr.billing.ProEntitlement
import com.jayr91.vdr.billing.ProGates
import com.jayr91.vdr.engine.DirectUrl
import com.jayr91.vdr.engine.DownloadStatus
import com.jayr91.vdr.engine.DownloadTask
import com.jayr91.vdr.engine.FocusGuard
import com.jayr91.vdr.engine.Organizer
import com.jayr91.vdr.engine.QueueManager
import com.jayr91.vdr.engine.TaskSnapshot
import com.jayr91.vdr.storage.PublicStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = QueueManager()
    private lateinit var db: VdrDatabase
    private var appForeground = true
    private var focusEnabled = false
    private val createdAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    /** Adds and disk restores that have not reached the queue yet, so settle() cannot stop under them. */
    private val pending = AtomicInteger(0)
    private var isForeground = false
    /**
     * Set when the user swipes the transfer notification away.
     *
     * Dropping setOngoing() alone only makes the notification *dismissable* --
     * the next progress tick would call notify() again and it would spring
     * back within a second, which reads as the app refusing to be dismissed.
     * While this is set we simply stop re-posting. The service stays in the
     * foreground and the download keeps running; only the UI goes quiet, which
     * is what "swipe it away" is asking for.
     */
    private var notifDismissed = false
    private var stopped = false
    private var timedOut = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        db = VdrDatabase.get(this)
        createChannel()
        queue.onUpdate = { task ->
            publishIfCompleted(task)
            persist(task.snapshot())
            _downloads.value = queue.snapshot()
            settle()
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(powerReceiver, filter)
        }
        registerNetworkCallback()
        pending.incrementAndGet()
        scope.launch {
            try {
                val prefs = VdrSettings.read(this@DownloadService)
                focusEnabled = prefs.focusGuard
                queue.setSpeedLimit(if (prefs.speedKb <= 0) null else prefs.speedKb * 1024L)
                queue.setWifiOnly(prefs.wifiOnly)
                queue.setUnmetered(isUnmeteredNetwork())
                restoreFromDisk()
                refreshFocus()
                queue.refreshHolds()
                _downloads.value = queue.snapshot()
            } finally {
                pending.decrementAndGet()
                settle()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A start that came through startForegroundService owes the platform a
        // startForeground() within five seconds, whether or not there turns out
        // to be work to do. Quiet starts are plain startService and owe nothing.
        stopped = false
        if (intent == null || !intent.getBooleanExtra(EXTRA_QUIET, false)) enterForeground()
        when (intent?.action) {
            ACTION_ADD -> {
                val raw = intent.getStringExtra(EXTRA_URL).orEmpty()
                // Defaulting to 8 meant "caller forgot the extra" was
                // indistinguishable from "caller is entitled to 8", and the
                // fallback happened to be a Pro-tier value. Fail closed.
                val segments = intent.getIntExtra(EXTRA_SEGMENTS, ProGates.FREE_MAX_SEGMENTS)
                val schedule = intent.getLongExtra(EXTRA_SCHEDULE, -1L).takeIf { it > 0 }
                val urls = DirectUrl.extractHttpUrls(raw).ifEmpty {
                    listOf(raw.trim()).filter { it.startsWith("http://") || it.startsWith("https://") }
                }
                // Read the entitlement for this add rather than caching it: a
                // cached flag starts false and would silently downgrade a Pro
                // user who shares a link before DataStore has answered. addUrl
                // does file I/O anyway, so it belongs off the main thread.
                pending.incrementAndGet()
                // Queueing something new is worth showing even if the previous
                // notification was dismissed.
                notifDismissed = false
                scope.launch {
                    try {
                        val pro = ProEntitlement.isPro(this@DownloadService)
                        val allowed = ProGates.clampSegments(segments, pro)
                        urls.forEach { addUrl(it, allowed, schedule) }
                    } finally {
                        pending.decrementAndGet()
                        settle()
                    }
                }
            }
            ACTION_NOTIF_DISMISSED -> notifDismissed = true
            ACTION_PAUSE -> intent.getStringExtra(EXTRA_ID)?.let { queue.pause(it) }
            ACTION_RESUME -> intent.getStringExtra(EXTRA_ID)?.let { queue.resume(it) }
            ACTION_CANCEL -> intent.getStringExtra(EXTRA_ID)?.let { id ->
                queue.remove(id)
                scope.launch { db.downloads().delete(id) }
            }
            ACTION_REMOVE -> intent.getStringExtra(EXTRA_ID)?.let { id ->
                queue.remove(id)
                scope.launch { db.downloads().delete(id) }
            }
            ACTION_SPEED -> {
                val kb = intent.getIntExtra(EXTRA_SPEED_KB, 0)
                queue.setSpeedLimit(if (kb <= 0) null else kb * 1024L)
                scope.launch { VdrSettings.setSpeedKb(this@DownloadService, kb) }
                refreshFocus()
            }
            ACTION_FOCUS -> {
                focusEnabled = intent.getBooleanExtra(EXTRA_FOCUS, false)
                scope.launch { VdrSettings.setFocusGuard(this@DownloadService, focusEnabled) }
                refreshFocus()
            }
            ACTION_WIFI_ONLY -> {
                val enabled = intent.getBooleanExtra(EXTRA_WIFI_ONLY, false)
                queue.setWifiOnly(enabled)
                scope.launch { VdrSettings.setWifiOnly(this@DownloadService, enabled) }
            }
            ACTION_FOREGROUND -> {
                appForeground = intent.getBooleanExtra(EXTRA_FOCUS, true)
                refreshFocus()
            }
        }
        _downloads.value = queue.snapshot()
        _focusDetail.value = FocusGuard.detail(queue.focusPolicy)
        settle()
        return START_STICKY
    }

    /**
     * Holds the foreground service open exactly as long as there is a transfer to
     * make progress on, and tears it down the moment there is not. Safe to call
     * from worker threads and repeatedly.
     */
    @Synchronized
    private fun settle() {
        if (stopped) return
        val snap = queue.snapshot()
        when {
            snap.any { it.status in TRANSFER_STATES } -> {
                enterForeground(snap)
                updateNotification(snap)
            }
            // An add or a disk restore is still in flight; nothing to show yet,
            // but stopping now would pull the queue out from under it.
            pending.get() > 0 -> updateNotification(snap)
            else -> stopForegroundAndSelf(snap)
        }
    }

    @Synchronized
    private fun enterForeground(snap: List<TaskSnapshot> = queue.snapshot()) {
        if (isForeground || stopped) return
        val ok = runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                currentNotification(snap),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        }.isSuccess
        if (ok) {
            isForeground = true
            // A new stint is new information, so an earlier dismissal is spent.
            notifDismissed = false
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(IDLE_NOTIF_ID)
        }
    }

    @Synchronized
    private fun stopForegroundAndSelf(snap: List<TaskSnapshot> = queue.snapshot()) {
        if (stopped) return
        if (isForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        postIdleNotice(snap)
        stopped = true
        stopSelf()
    }

    /**
     * Once the service is gone the ongoing notification goes with it, so anything
     * the user paused (or that the platform timed out) still needs a way back.
     */
    private fun postIdleNotice(snap: List<TaskSnapshot>) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val paused = snap.filter { it.status == DownloadStatus.PAUSED }
        val target = paused.firstOrNull()
        if (target == null) {
            nm.cancel(IDLE_NOTIF_ID)
            return
        }
        val text = when {
            timedOut && paused.size == 1 ->
                "Android stopped the transfer after its 6‑hour limit. Resume to finish ${target.displayName}."
            timedOut -> "Android stopped ${paused.size} transfers after their 6‑hour limit. Resume to finish them."
            paused.size == 1 -> "Paused — ${target.displayName}"
            else -> "${paused.size} downloads paused"
        }
        val launch = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            IDLE_NOTIF_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("VDR")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(launch)
                .setOnlyAlertOnce(true)
                .addAction(
                    android.R.drawable.ic_media_play,
                    "Resume",
                    servicePending(ACTION_RESUME, target.id, 24),
                )
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Cancel",
                    servicePending(ACTION_CANCEL, target.id, 25),
                )
                .build(),
        )
    }

    /**
     * The two-argument overload is the one the dataSync cap actually uses. The
     * platform routes the six-hour dataSync/mediaProcessing limit through
     * `Service.callOnTimeLimitExceeded`, which calls only `onTimeout(startId,
     * fgsType)`; the single-argument [onTimeout] is reached solely by
     * `callOnTimeout`, the shortService path, which this service never starts.
     * Overriding just the single-argument form therefore left the cap unhandled,
     * and the platform kills the app when the callback it did call returns with
     * the service still in the foreground.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) = pauseEverythingAndStop()

    /** Retained for a shortService timeout; harmless if both callbacks land. */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int) = pauseEverythingAndStop()

    /** Idempotent: [stopForegroundAndSelf] is guarded by [stopped]. */
    private fun pauseEverythingAndStop() {
        timedOut = true
        queue.snapshot()
            .filter { it.status in TRANSFER_STATES }
            .forEach { queue.pause(it.id) }
        _downloads.value = queue.snapshot()
        stopForegroundAndSelf()
    }

    /** [segments] is already entitlement-clamped by the ACTION_ADD handler. */
    private fun addUrl(url: String, segments: Int, scheduledAt: Long?) {
        val pageError = DirectUrl.rejectionMessage(url)
        val name = Organizer.outputNameForUrl(url)
        val category = when {
            DirectUrl.looksLikeHlsUrl(url) || DirectUrl.looksLikeDashUrl(url) -> "Videos"
            else -> Organizer.categoryFor(name)
        }
        val dest = File(File(getExternalFilesDir(null), category), name).let { original ->
            if (!original.exists()) original
            else {
                val base = original.nameWithoutExtension
                val ext = original.extension.let { if (it.isEmpty()) "" else ".$it" }
                var i = 1
                var candidate: File
                do {
                    candidate = File(original.parentFile, "$base ($i)$ext")
                    i++
                } while (candidate.exists())
                candidate
            }
        }
        dest.parentFile?.mkdirs()
        val id = UUID.randomUUID().toString()
        queue.add(
            url = url,
            destFile = dest,
            displayName = dest.name,
            category = category,
            numSegments = segments,
            scheduledAt = scheduledAt,
            id = id,
            autoStart = pageError == null,
            initialStatus = if (pageError != null) DownloadStatus.ERROR else null,
            initialError = pageError,
        )
    }

    private fun publishIfCompleted(task: DownloadTask) {
        if (task.status != DownloadStatus.COMPLETED) return
        if (!task.contentUri.isNullOrBlank()) return
        val file = task.outputFile
        if (!file.exists()) return
        try {
            val published = PublicStore.publish(
                this,
                file,
                task.resolvedDisplayName,
                task.category,
            )
            task.publicPath = published.displayPath
            task.contentUri = published.contentUri
            file.delete()
            task.stateFile.delete()
            if (task.destFile != file) task.destFile.delete()
        } catch (e: Exception) {
            if (task.errorMessage.isBlank()) {
                task.errorMessage = "Downloaded, but could not save to Downloads/VDR: ${e.message}"
            }
        }
    }

    private suspend fun restoreFromDisk() {
        db.downloads().all().forEach { row ->
            if (queue.find(row.id) != null) return@forEach
            createdAt[row.id] = row.createdAt
            val pageError = DirectUrl.rejectionMessage(row.url)
            val status = DownloadStatus.entries.find { it.label == row.status } ?: DownloadStatus.QUEUED
            val autoStart = pageError == null && (
                status == DownloadStatus.QUEUED ||
                    status == DownloadStatus.DOWNLOADING ||
                    status == DownloadStatus.CONNECTING
                )
            queue.add(
                url = row.url,
                destFile = File(row.destPath),
                displayName = row.displayName,
                category = row.category,
                numSegments = row.numSegments,
                scheduledAt = row.scheduledAt,
                id = row.id,
                autoStart = autoStart,
                initialStatus = when {
                    pageError != null -> DownloadStatus.ERROR
                    autoStart -> DownloadStatus.QUEUED
                    else -> status
                },
                initialError = pageError ?: row.error,
                publishedPath = row.contentUri?.let { row.destPath },
                contentUri = row.contentUri,
            )
        }
        _downloads.value = queue.snapshot()
    }

    private fun persist(snap: TaskSnapshot) {
        if (snap.status == DownloadStatus.CANCELLED) {
            createdAt.remove(snap.id)
            scope.launch { db.downloads().delete(snap.id) }
            return
        }
        val firstSeen = createdAt.getOrPut(snap.id) { System.currentTimeMillis() }
        scope.launch {
            db.downloads().upsert(
                DownloadEntity(
                    id = snap.id,
                    url = snap.url,
                    displayName = snap.displayName,
                    category = snap.category,
                    destPath = snap.destPath,
                    contentUri = snap.contentUri,
                    status = snap.status.label,
                    totalBytes = snap.totalBytes,
                    downloadedBytes = snap.downloadedBytes,
                    error = snap.error,
                    numSegments = snap.numSegments,
                    createdAt = firstSeen,
                    scheduledAt = snap.scheduledAt,
                )
            )
        }
    }

    private fun refreshFocus() {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val charging = bm.isCharging ||
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) == BatteryManager.BATTERY_STATUS_CHARGING
        val onBattery = !charging
        val powerSave = (getSystemService(POWER_SERVICE) as PowerManager).isPowerSaveMode
        val policy = FocusGuard.decidePolicy(focusEnabled, onBattery, powerSave, appForeground)
        queue.applyFocusPolicy(policy)
        _focusDetail.value = FocusGuard.detail(policy)
        _downloads.value = queue.snapshot()
        settle()
    }

    private fun isUnmeteredNetwork(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun refreshWifi() {
        queue.setUnmetered(isUnmeteredNetwork())
        _downloads.value = queue.snapshot()
        settle()
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refreshWifi()
            override fun onLost(network: Network) = refreshWifi()
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) =
                refreshWifi()
        }
        networkCallback = callback
        runCatching { cm.registerDefaultNetworkCallback(callback) }
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshFocus()
    }

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun updateNotification(snap: List<TaskSnapshot>) {
        if (!isForeground || notifDismissed) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, currentNotification(snap))
    }

    private fun currentNotification(snap: List<TaskSnapshot>): Notification {
        val active = snap.filter {
            it.status in setOf(
                DownloadStatus.DOWNLOADING,
                DownloadStatus.CONNECTING,
                DownloadStatus.REMUXING,
            )
        }
        val text = when {
            snap.isEmpty() -> "Starting download…"
            queue.wifiBlocked() -> "Waiting for Wi‑Fi — ${snap.size} in queue"
            active.isEmpty() -> "Idle — ${snap.size} in queue"
            active.size == 1 -> {
                val t = active.first()
                if (t.status == DownloadStatus.REMUXING) {
                    "Remuxing… ${t.displayName}"
                } else {
                    val pct = if (t.totalBytes != null && t.totalBytes > 0)
                        (t.downloadedBytes * 100 / t.totalBytes) else 0
                    "${t.displayName}  $pct%"
                }
            }
            else -> "${active.size} downloads running"
        }
        return notification(text, notificationTarget(snap))
    }

    private fun notificationTarget(snap: List<TaskSnapshot>): TaskSnapshot? {
        val order = listOf(
            DownloadStatus.REMUXING,
            DownloadStatus.DOWNLOADING,
            DownloadStatus.CONNECTING,
            DownloadStatus.WIFI_HOLD,
            DownloadStatus.HELD,
            DownloadStatus.PAUSED,
        )
        return order.firstNotNullOfOrNull { status -> snap.firstOrNull { it.status == status } }
    }

    private fun notification(text: String, target: TaskSnapshot? = null): Notification {
        val launch = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("VDR")
            .setContentText(text)
            .setContentIntent(launch)
            // No setOngoing(): it sets FLAG_NO_CLEAR, which is what pinned this
            // notification in the shade. Since Android 13 a foreground-service
            // notification is meant to be dismissable, and the service keeps
            // running when it is.
            .setDeleteIntent(dismissPending())
            .setOnlyAlertOnce(true)
            .setSilent(true)
        if (target != null && target.status != DownloadStatus.REMUXING) {
            val running = target.status in setOf(
                DownloadStatus.DOWNLOADING,
                DownloadStatus.CONNECTING,
            )
            if (running) {
                builder.addAction(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    servicePending(ACTION_PAUSE, target.id, 21),
                )
            } else {
                builder.addAction(
                    android.R.drawable.ic_media_play,
                    "Resume",
                    servicePending(ACTION_RESUME, target.id, 22),
                )
            }
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                servicePending(ACTION_CANCEL, target.id, 23),
            )
        } else if (target != null) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                servicePending(ACTION_CANCEL, target.id, 23),
            )
        }
        return builder.build()
    }

    private fun dismissPending(): PendingIntent {
        val intent = Intent(this, DownloadService::class.java)
            .setAction(ACTION_NOTIF_DISMISSED)
            .putExtra(EXTRA_QUIET, true)
        // getService, not getForegroundService: the service is already running
        // when its own notification is swiped, and EXTRA_QUIET keeps
        // onStartCommand from calling enterForeground() and immediately
        // re-posting the thing that was just dismissed.
        return PendingIntent.getService(
            this,
            26,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun servicePending(action: String, id: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, DownloadService::class.java)
            .setAction(action)
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getForegroundService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopped = true
        isForeground = false
        unregisterReceiver(powerReceiver)
        networkCallback?.let { cb ->
            runCatching {
                getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb)
            }
        }
        instance = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "vdr_downloads"
        const val NOTIF_ID = 17
        private const val IDLE_NOTIF_ID = 18
        const val ACTION_ADD = "com.jayr91.vdr.ADD"
        const val ACTION_PAUSE = "com.jayr91.vdr.PAUSE"
        const val ACTION_RESUME = "com.jayr91.vdr.RESUME"
        const val ACTION_CANCEL = "com.jayr91.vdr.CANCEL"
        const val ACTION_NOTIF_DISMISSED = "com.jayr91.vdr.NOTIF_DISMISSED"
        const val ACTION_REMOVE = "com.jayr91.vdr.REMOVE"
        const val ACTION_SPEED = "com.jayr91.vdr.SPEED"
        const val ACTION_FOCUS = "com.jayr91.vdr.FOCUS"
        const val ACTION_WIFI_ONLY = "com.jayr91.vdr.WIFI_ONLY"
        const val ACTION_FOREGROUND = "com.jayr91.vdr.FOREGROUND"
        const val EXTRA_URL = "url"
        const val EXTRA_ID = "id"
        const val EXTRA_SEGMENTS = "segments"
        const val EXTRA_SCHEDULE = "schedule"
        const val EXTRA_SPEED_KB = "speed_kb"
        const val EXTRA_FOCUS = "focus"
        const val EXTRA_WIFI_ONLY = "wifi_only"
        private const val EXTRA_QUIET = "quiet_start"

        /** Statuses that represent a transfer the user is waiting on. */
        private val TRANSFER_STATES = setOf(
            DownloadStatus.QUEUED,
            DownloadStatus.SCHEDULED,
            DownloadStatus.CONNECTING,
            DownloadStatus.DOWNLOADING,
            DownloadStatus.REMUXING,
            DownloadStatus.HELD,
            DownloadStatus.WIFI_HOLD,
        )

        /** Actions that create work, so they may promote the service to the foreground. */
        private val WORK_ACTIONS = setOf(ACTION_ADD, ACTION_RESUME)

        private val _downloads = MutableStateFlow<List<TaskSnapshot>>(emptyList())
        val downloads = _downloads.asStateFlow()
        private val _focusDetail = MutableStateFlow(FocusGuard.detail(FocusGuard.POLICY_OFF))
        val focusDetail = _focusDetail.asStateFlow()

        @Volatile var instance: DownloadService? = null

        /**
         * Wakes the service to republish state. Deliberately not a foreground
         * start: opening the app is not by itself a reason to run a data-sync
         * service, so this must not oblige the service to post a notification.
         */
        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java).putExtra(EXTRA_QUIET, true)
            runCatching { context.startService(intent) }
        }

        fun send(context: Context, action: String, block: Intent.() -> Unit = {}) {
            val intent = Intent(context, DownloadService::class.java).setAction(action).apply(block)
            if (action in WORK_ACTIONS) {
                context.startForegroundService(intent)
            } else {
                runCatching { context.startService(intent.putExtra(EXTRA_QUIET, true)) }
            }
        }
    }
}
