package com.jayr91.vdr

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.jayr91.vdr.service.DownloadService
import com.jayr91.vdr.ui.VdrApp
import com.jayr91.vdr.ui.theme.VdrTheme

class MainActivity : ComponentActivity() {
    private val notifyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

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
        handleShare(intent)
        setContent {
            VdrTheme {
                VdrApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return
        val url = Regex("https?://\\S+").find(text)?.value ?: return
        DownloadService.send(this, DownloadService.ACTION_ADD) {
            putExtra(DownloadService.EXTRA_URL, url)
        }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        super.onDestroy()
    }
}
