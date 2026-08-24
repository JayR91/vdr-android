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
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.jayr91.vdr.MainActivity
import com.jayr91.vdr.R
import com.jayr91.vdr.data.DownloadEntity
import com.jayr91.vdr.data.VdrDatabase
import com.jayr91.vdr.engine.DownloadStatus
import com.jayr91.vdr.engine.FocusGuard
import com.jayr91.vdr.engine.Organizer
import com.jayr91.vdr.engine.QueueManager
import com.jayr91.vdr.engine.TaskSnapshot
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

    override fun onCreate() {
        super.onCreate()
        instance = this
        db = VdrDatabase.get(this)
        createChannel()
        startForeground(NOTIF_ID, notification("VDR is ready"))
        queue.onUpdate = { task ->
            persist(task.snapshot())
            _downloads.value = queue.snapshot()
            updateNotification()
        }
        registerReceiver(powerReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        refreshFocus()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ADD -> {
                val url = intent.getStringExtra(EXTRA_URL)?.trim().orEmpty()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    addUrl(url, intent.getIntExtra(EXTRA_SEGMENTS, 8), intent.getLongExtra(EXTRA_SCHEDULE, -1L).takeIf { it > 0 })
                }
            }
            ACTION_PAUSE -> intent.getStringExtra(EXTRA_ID)?.let { queue.pause(it) }
            ACTION_RESUME -> intent.getStringExtra(EXTRA_ID)?.let { queue.resume(it) }
            ACTION_CANCEL -> intent.getStringExtra(EXTRA_ID)?.let { queue.cancel(it) }
            ACTION_REMOVE -> intent.getStringExtra(EXTRA_ID)?.let { queue.remove(it) }
            ACTION_SPEED -> {
                val kb = intent.getIntExtra(EXTRA_SPEED_KB, 0)
                queue.setSpeedLimit(if (kb <= 0) null else kb * 1024L)
                refreshFocus()
            }
            ACTION_FOCUS -> {
                focusEnabled = intent.getBooleanExtra(EXTRA_FOCUS, false)
                refreshFocus()
            }
            ACTION_FOREGROUND -> {
                appForeground = intent.getBooleanExtra(EXTRA_FOCUS, true)
                refreshFocus()
            }
        }
        _downloads.value = queue.snapshot()
        _focusDetail.value = FocusGuard.detail(queue.focusPolicy)
        return START_STICKY
    }

    private fun addUrl(url: String, segments: Int, scheduledAt: Long?) {
        val name = Organizer.filenameFromUrl(url)
        val category = Organizer.categoryFor(name)
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
        )
    }

    private fun persist(snap: TaskSnapshot) {
        scope.launch {
            db.downloads().upsert(
                DownloadEntity(
                    id = snap.id,
                    url = snap.url,
                    displayName = snap.displayName,
                    category = snap.category,
                    destPath = snap.destPath,
                    status = snap.status.label,
                    totalBytes = snap.totalBytes,
                    downloadedBytes = snap.downloadedBytes,
                    error = snap.error,
                    numSegments = snap.numSegments,
                    createdAt = System.currentTimeMillis(),
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
        val active = queue.snapshot().filter { it.status == DownloadStatus.DOWNLOADING }
        val text = when {
            active.isEmpty() -> "Idle — ${queue.snapshot().size} in queue"
            active.size == 1 -> {
                val t = active.first()
                val pct = if (t.totalBytes != null && t.totalBytes > 0)
                    (t.downloadedBytes * 100 / t.totalBytes) else 0
                "${t.displayName}  $pct%"
            }
            else -> "${active.size} downloads running"
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notification(text))
    }

    private fun notification(text: String): Notification {
        val launch = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("VDR")
            .setContentText(text)
            .setContentIntent(launch)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterReceiver(powerReceiver)
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
        const val ACTION_FOREGROUND = "com.jayr91.vdr.FOREGROUND"
        const val EXTRA_URL = "url"
        const val EXTRA_ID = "id"
        const val EXTRA_SEGMENTS = "segments"
        const val EXTRA_SCHEDULE = "schedule"
        const val EXTRA_SPEED_KB = "speed_kb"
        const val EXTRA_FOCUS = "focus"

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
