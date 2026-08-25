package com.jayr91.vdr.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.jayr91.vdr.data.VdrPrefs
import com.jayr91.vdr.data.VdrSettings
import com.jayr91.vdr.engine.DirectUrl
import com.jayr91.vdr.engine.DownloadStatus
import com.jayr91.vdr.engine.Organizer
import com.jayr91.vdr.engine.TaskSnapshot
import com.jayr91.vdr.service.DownloadService
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VdrApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloads by DownloadService.downloads.collectAsState()
    val focusDetail by DownloadService.focusDetail.collectAsState()
    val prefsState by VdrSettings.prefs(context).collectAsState(initial = null)
    val prefs = prefsState ?: VdrPrefs()
    var showAdd by remember { mutableStateOf(false) }
    var addPrefill by remember { mutableStateOf("") }
    var speedKb by remember { mutableFloatStateOf(0f) }
    var clipboardPrompt by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(prefs.speedKb) {
        speedKb = prefs.speedKb.toFloat()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(300)
            val current = VdrSettings.read(context)
            val urls = clipboardHttpUrls(context)
            if (urls.isEmpty()) return@repeatOnLifecycle
            val key = urls.joinToString("\n")
            if (key == current.lastClipboardUrl) return@repeatOnLifecycle
            val known = DownloadService.downloads.value.map { it.url }.toSet()
            if (urls.all { it in known }) {
                VdrSettings.setLastClipboardUrl(context, key)
                return@repeatOnLifecycle
            }
            clipboardPrompt = urls
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VDR") },
                actions = {
                    IconButton(onClick = {
                        val clip = clipboardText(context)
                        addPrefill = clip.ifBlank { addPrefill }
                        showAdd = true
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Add from clipboard")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                addPrefill = ""
                showAdd = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add URL")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "Segmented, resumable downloads — Android port of VDR",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Wi‑Fi only", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Switch(checked = prefs.wifiOnly, onCheckedChange = {
                    DownloadService.send(context, DownloadService.ACTION_WIFI_ONLY) {
                        putExtra(DownloadService.EXTRA_WIFI_ONLY, it)
                    }
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Focus Guard", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Switch(checked = prefs.focusGuard, onCheckedChange = {
                    DownloadService.send(context, DownloadService.ACTION_FOCUS) {
                        putExtra(DownloadService.EXTRA_FOCUS, it)
                    }
                })
            }
            Text(focusDetail, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                if (speedKb < 1f) "Speed limit: unlimited" else "Speed limit: ${speedKb.roundToInt()} KB/s",
                style = MaterialTheme.typography.titleSmall,
            )
            Slider(
                value = speedKb,
                onValueChange = { speedKb = it },
                onValueChangeFinished = {
                    DownloadService.send(context, DownloadService.ACTION_SPEED) {
                        putExtra(DownloadService.EXTRA_SPEED_KB, speedKb.roundToInt())
                    }
                },
                valueRange = 0f..4096f,
            )
            Spacer(Modifier.height(8.dp))
            if (downloads.isEmpty()) {
                Text(
                    "No downloads yet. Tap + or paste from clipboard to add a direct HTTP(S) file URL.",
                    modifier = Modifier.padding(top = 24.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Direct files, resume, Wi‑Fi only, Downloads/VDR.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    addPrefill = clipboardText(context)
                    showAdd = true
                }) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add from clipboard")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(downloads, key = { it.id }) { row ->
                        DownloadCard(row)
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddUrlDialog(
            initialText = addPrefill,
            onDismiss = { showAdd = false },
            onConfirm = { urls, segments ->
                urls.forEach { url ->
                    DownloadService.send(context, DownloadService.ACTION_ADD) {
                        putExtra(DownloadService.EXTRA_URL, url)
                        putExtra(DownloadService.EXTRA_SEGMENTS, segments)
                    }
                }
                scope.launch { VdrSettings.setLastClipboardUrl(context, urls.joinToString("\n")) }
                showAdd = false
            },
        )
    }

    val promptUrls = clipboardPrompt
    if (promptUrls != null) {
        val preview = promptUrls.joinToString("\n")
        AlertDialog(
            onDismissRequest = {
                scope.launch { VdrSettings.setLastClipboardUrl(context, preview) }
                clipboardPrompt = null
            },
            title = { Text(if (promptUrls.size == 1) "Add from clipboard?" else "Add ${promptUrls.size} URLs from clipboard?") },
            text = {
                Text(
                    preview,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                Button(onClick = {
                    promptUrls.forEach { url ->
                        DownloadService.send(context, DownloadService.ACTION_ADD) {
                            putExtra(DownloadService.EXTRA_URL, url)
                        }
                    }
                    scope.launch { VdrSettings.setLastClipboardUrl(context, preview) }
                    clipboardPrompt = null
                }) { Text(if (promptUrls.size == 1) "Download" else "Download all") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch { VdrSettings.setLastClipboardUrl(context, preview) }
                    clipboardPrompt = null
                }) { Text("Not now") }
            },
        )
    }
}

@Composable
private fun DownloadCard(row: TaskSnapshot) {
    val context = LocalContext.current
    val progress = if (row.totalBytes != null && row.totalBytes > 0)
        (row.downloadedBytes.toFloat() / row.totalBytes).coerceIn(0f, 1f) else 0f
    val canShare = row.status == DownloadStatus.COMPLETED &&
        (!row.contentUri.isNullOrBlank() || File(row.destPath).exists())
    Card(colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(12.dp)) {
            Text(row.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                row.url,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Text(
                "${row.category} · ${row.status.label} · ${humanSize(row.downloadedBytes)} / ${humanSize(row.totalBytes)} · ${humanSize(row.speedBps.toLong())}/s · ${row.numSegments} segments",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )
            Text(
                savedLocationLabel(row),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            if (row.error.isNotBlank()) {
                Text(row.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Row {
                IconButton(onClick = {
                    val pausing = row.status in setOf(
                        DownloadStatus.DOWNLOADING,
                        DownloadStatus.CONNECTING,
                    )
                    val action = if (pausing) DownloadService.ACTION_PAUSE else DownloadService.ACTION_RESUME
                    DownloadService.send(context, action) { putExtra(DownloadService.EXTRA_ID, row.id) }
                }) {
                    Icon(
                        if (row.status == DownloadStatus.DOWNLOADING || row.status == DownloadStatus.CONNECTING)
                            Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Pause or resume",
                    )
                }
                IconButton(onClick = {
                    DownloadService.send(context, DownloadService.ACTION_REMOVE) {
                        putExtra(DownloadService.EXTRA_ID, row.id)
                    }
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                }
                IconButton(onClick = {
                    val mime = Organizer.mimeType(row.displayName)
                    val uri = fileUri(context, row) ?: return@IconButton
                    val open = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, mime)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val chooser = Intent.createChooser(open, "Open ${row.displayName}")
                    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    chooser.clipData = ClipData.newRawUri(row.displayName, uri)
                    runCatching { context.startActivity(chooser) }
                }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Open")
                }
                if (canShare) {
                    IconButton(onClick = {
                        val mime = Organizer.mimeType(row.displayName)
                        val uri = fileUri(context, row) ?: return@IconButton
                        val send = Intent(Intent.ACTION_SEND)
                            .setType(mime)
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        val chooser = Intent.createChooser(send, "Share ${row.displayName}")
                        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        chooser.clipData = ClipData.newRawUri(row.displayName, uri)
                        runCatching { context.startActivity(chooser) }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
            }
        }
    }
}

@Composable
private fun AddUrlDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (List<String>, Int) -> Unit,
) {
    var url by remember { mutableStateOf(initialText) }
    var segments by remember { mutableFloatStateOf(8f) }
    val urls = DirectUrl.extractHttpUrls(url)
    val pageError = urls.singleOrNull()?.let { DirectUrl.rejectionMessage(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add download") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("https://… file URL(s)") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                    isError = pageError != null,
                )
                if (pageError != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(pageError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else if (urls.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${urls.size} URLs — each valid direct file will queue; watch pages are skipped with an error.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Segments: ${segments.roundToInt()}")
                Slider(value = segments, onValueChange = { segments = it }, valueRange = 1f..32f, steps = 30)
                Text(
                    "Paste several links separated by spaces or new lines. Direct HTTP(S) files only.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = urls.isNotEmpty() && (urls.size > 1 || pageError == null),
                onClick = { onConfirm(urls, segments.roundToInt()) },
            ) { Text(if (urls.size > 1) "Download ${urls.size}" else "Download") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun fileUri(context: Context, row: TaskSnapshot): Uri? {
    row.contentUri?.let { return Uri.parse(it) }
    val file = File(row.destPath)
    if (!file.exists()) return null
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

private fun clipboardText(context: Context): String {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    if (!cm.hasPrimaryClip()) return ""
    return cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
}

private fun clipboardHttpUrls(context: Context): List<String> =
    DirectUrl.extractHttpUrls(clipboardText(context))

private fun savedLocationLabel(row: TaskSnapshot): String {
    val publicPath = if (!row.contentUri.isNullOrBlank() && row.destPath.startsWith("Downloads/VDR/")) {
        row.destPath
    } else {
        Organizer.publicDisplayPath(row.category, row.displayName)
    }
    return if (row.status == DownloadStatus.COMPLETED) "Saved to $publicPath" else "Saving to $publicPath"
}

private fun humanSize(n: Long?): String {
    if (n == null) return "?"
    var v = n.toDouble()
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    for (u in units) {
        if (v < 1024) return "%.1f%s".format(v, u)
        v /= 1024
    }
    return "%.1fPB".format(v)
}
