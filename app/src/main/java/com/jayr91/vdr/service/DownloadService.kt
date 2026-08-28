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

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = QueueManager()
    private lateinit var db: VdrDatabase
    private var appForeground = true
    private var focusEnabled = false
    private val createdAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        db = VdrDatabase.get(this)
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            notification("VDR is ready"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        queue.onUpdate = { task ->
            publishIfCompleted(task)
            persist(task.snapshot())
            _downloads.value = queue.snapshot()
            updateNotification()
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(powerReceiver, filter)
        }
        registerNetworkCallback()
        scope.launch {
            val prefs = VdrSettings.read(this@DownloadService)
            focusEnabled = prefs.focusGuard
            queue.setSpeedLimit(if (prefs.speedKb <= 0) null else prefs.speedKb * 1024L)
            queue.setWifiOnly(prefs.wifiOnly)
            queue.setUnmetered(isUnmeteredNetwork())
            restoreFromDisk()
            refreshFocus()
            queue.refreshHolds()
            _downloads.value = queue.snapshot()
            updateNotification()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
                scope.launch {
                    val pro = ProEntitlement.isPro(this@DownloadService)
                    val allowed = ProGates.clampSegments(segments, pro)
                    urls.forEach { addUrl(it, allowed, schedule) }
                }
            }
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
        updateNotification()
        return START_STICKY
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
        updateNotification()
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
        updateNotification()
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

    private fun updateNotification() {
        val snap = queue.snapshot()
        val active = snap.filter {
            it.status in setOf(
                DownloadStatus.DOWNLOADING,
                DownloadStatus.CONNECTING,
                DownloadStatus.REMUXING,
            )
        }
        val text = when {
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
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notification(text, notificationTarget(snap)))
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
            .setOngoing(true)
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
        const val ACTION_ADD = "com.jayr91.vdr.ADD"
        const val ACTION_PAUSE = "com.jayr91.vdr.PAUSE"
        const val ACTION_RESUME = "com.jayr91.vdr.RESUME"
        const val ACTION_CANCEL = "com.jayr91.vdr.CANCEL"
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

        private val _downloads = MutableStateFlow<List<TaskSnapshot>>(emptyList())
        val downloads = _downloads.asStateFlow()
        private val _focusDetail = MutableStateFlow(FocusGuard.detail(FocusGuard.POLICY_OFF))
        val focusDetail = _focusDetail.asStateFlow()

        @Volatile var instance: DownloadService? = null

        fun start(context: Context) {
            context.startForegroundService(Intent(context, DownloadService::class.java))
        }

        fun send(context: Context, action: String, block: Intent.() -> Unit = {}) {
            start(context)
            context.startForegroundService(Intent(context, DownloadService::class.java).setAction(action).apply(block))
        }
    }
}
