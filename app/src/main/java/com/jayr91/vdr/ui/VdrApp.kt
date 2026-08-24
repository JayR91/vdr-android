package com.jayr91.vdr.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.jayr91.vdr.engine.DownloadStatus
import com.jayr91.vdr.engine.TaskSnapshot
import com.jayr91.vdr.service.DownloadService
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VdrApp() {
    val context = LocalContext.current
    val downloads by DownloadService.downloads.collectAsState()
    val focusDetail by DownloadService.focusDetail.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var speedKb by remember { mutableFloatStateOf(0f) }
    var focusOn by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("VDR") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
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
                Text("Focus Guard", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Switch(checked = focusOn, onCheckedChange = {
                    focusOn = it
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
                    "No downloads yet. Tap + to add an HTTP(S) file URL, or share a link into VDR.",
                    modifier = Modifier.padding(top = 24.dp),
                )
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
            onDismiss = { showAdd = false },
            onConfirm = { url, segments ->
                DownloadService.send(context, DownloadService.ACTION_ADD) {
                    putExtra(DownloadService.EXTRA_URL, url)
                    putExtra(DownloadService.EXTRA_SEGMENTS, segments)
                }
                showAdd = false
            },
        )
    }
}

@Composable
private fun DownloadCard(row: TaskSnapshot) {
    val context = LocalContext.current
    val progress = if (row.totalBytes != null && row.totalBytes > 0)
        (row.downloadedBytes.toFloat() / row.totalBytes).coerceIn(0f, 1f) else 0f
    Card(colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(12.dp)) {
            Text(row.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${row.category} · ${row.status.label} · ${humanSize(row.downloadedBytes)} / ${humanSize(row.totalBytes)} · ${humanSize(row.speedBps.toLong())}/s · ${row.numSegments} segments",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )
            if (row.error.isNotBlank()) {
                Text(row.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Row {
                IconButton(onClick = {
                    val action = if (row.status == DownloadStatus.DOWNLOADING || row.status == DownloadStatus.CONNECTING || row.status == DownloadStatus.HELD)
                        DownloadService.ACTION_PAUSE else DownloadService.ACTION_RESUME
                    DownloadService.send(context, action) { putExtra(DownloadService.EXTRA_ID, row.id) }
                }) {
                    Icon(
                        if (row.status == DownloadStatus.DOWNLOADING) Icons.Default.Pause else Icons.Default.PlayArrow,
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
                    val file = File(row.destPath)
                    if (!file.exists()) return@IconButton
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    val open = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(open, "Open ${row.displayName}"))
                }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Open")
                }
            }
        }
    }
}

@Composable
private fun AddUrlDialog(onDismiss: () -> Unit, onConfirm: (String, Int) -> Unit) {
    var url by remember { mutableStateOf("") }
    var segments by remember { mutableFloatStateOf(8f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add download") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("https://… file URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("Segments: ${segments.roundToInt()}")
                Slider(value = segments, onValueChange = { segments = it }, valueRange = 1f..32f, steps = 30)
                Text(
                    "Direct HTTP(S) files only. YouTube/streaming extraction is not included in this Play Store build.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = url.startsWith("http://") || url.startsWith("https://"),
                onClick = { onConfirm(url.trim(), segments.roundToInt()) },
            ) { Text("Download") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
