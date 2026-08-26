package com.jayr91.vdr

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.jayr91.vdr.engine.DirectUrl
import com.jayr91.vdr.engine.MediaGrabber
import com.jayr91.vdr.engine.PageProbeResult
import com.jayr91.vdr.service.DownloadService
import com.jayr91.vdr.ui.VdrApp
import com.jayr91.vdr.ui.theme.VdrTheme
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN_BROWSE = "com.jayr91.vdr.OPEN_BROWSE"
        const val EXTRA_BROWSE_URL = "com.jayr91.vdr.BROWSE_URL"
    }

    private val notifyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val storagePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val openBrowseFlow = MutableStateFlow(false)
    private val browseUrlFlow = MutableStateFlow<String?>(null)

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_STOP) {
            DownloadService.send(this, DownloadService.ACTION_FOREGROUND) {
                putExtra(DownloadService.EXTRA_FOCUS, event == Lifecycle.Event.ON_START)
            }
        }
        if (event == Lifecycle.Event.ON_START) DownloadService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DownloadService.start(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        if (Build.VERSION.SDK_INT >= 33) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT < 29) {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        consumeBrowseExtras(intent)
        handleShare(intent)
        setContent {
            val openBrowse by openBrowseFlow.collectAsState()
            val browseUrl by browseUrlFlow.collectAsState()
            VdrTheme {
                VdrApp(
                    openBrowse = openBrowse,
                    initialBrowseUrl = browseUrl,
                    onBrowseConsumed = { openBrowseFlow.value = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeBrowseExtras(intent)
        handleShare(intent)
    }

    private fun consumeBrowseExtras(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_BROWSE, false) == true) {
            browseUrlFlow.value = intent.getStringExtra(EXTRA_BROWSE_URL)
            openBrowseFlow.value = true
        }
    }

    private fun handleShare(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return
        val urls = DirectUrl.extractHttpUrls(text)
        urls.forEach { url ->
            if (DirectUrl.looksLikeDirectFile(url) || DirectUrl.isBlockedWatchPage(url)) {
                DownloadService.send(this, DownloadService.ACTION_ADD) {
                    putExtra(DownloadService.EXTRA_URL, url)
                }
                return@forEach
            }
            val appCtx = applicationContext
            thread(name = "vdr-share-probe", isDaemon = true) {
                when (val result = MediaGrabber.probePageUrl(url)) {
                    is PageProbeResult.Media -> {
                        // Queue the best candidate only (preferMediaOrder already applied).
                        val best = result.urls.firstOrNull() ?: return@thread
                        DownloadService.send(appCtx, DownloadService.ACTION_ADD) {
                            putExtra(DownloadService.EXTRA_URL, best)
                        }
                    }
                    is PageProbeResult.DirectFile -> DownloadService.send(appCtx, DownloadService.ACTION_ADD) {
                        putExtra(DownloadService.EXTRA_URL, url)
                    }
                    else -> { /* do not queue HTML */ }
                }
            }
        }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        super.onDestroy()
    }
}
