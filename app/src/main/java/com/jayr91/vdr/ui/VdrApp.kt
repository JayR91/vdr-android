package com.jayr91.vdr.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.jayr91.vdr.billing.ProEntitlement
import com.jayr91.vdr.billing.ProGates
import com.jayr91.vdr.data.VdrPrefs
import com.jayr91.vdr.data.VdrSettings
import com.jayr91.vdr.engine.DirectUrl
import com.jayr91.vdr.engine.DownloadStatus
import com.jayr91.vdr.engine.MediaGrabber
import com.jayr91.vdr.engine.Organizer
import com.jayr91.vdr.engine.PageProbeResult
import com.jayr91.vdr.engine.TaskSnapshot
import com.jayr91.vdr.service.DownloadService
import com.jayr91.vdr.storage.PublicStore
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class VdrScreen { Home, PageMedia }

private val VdrScreenSaver = Saver<VdrScreen, String>(
    save = { it.name },
    restore = { name ->
        when (name) {
            "Browse", "PageMedia" -> VdrScreen.PageMedia
            else -> runCatching { VdrScreen.valueOf(name) }.getOrDefault(VdrScreen.Home)
        }
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VdrApp(
    openBrowse: Boolean = false,
    initialBrowseUrl: String? = null,
    onBrowseConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val billing = rememberBillingManager()
    // Three states, not two: Pro, not Pro, and "DataStore has not answered
    // yet". Collapsing the third into `false` meant a paying customer was
    // treated as free for the first frames after launch -- long enough to be
    // thrown off a Pro screen and shown an upgrade prompt. `prefsState` below
    // already models loading this way.
    val proState by ProEntitlement.isProFlow(context).collectAsState(initial = null)
    val isPro = proState == true
    val proKnown = proState != null
    val downloads by DownloadService.downloads.collectAsState()
    val focusDetail by DownloadService.focusDetail.collectAsState()
    val prefsState by VdrSettings.prefs(context).collectAsState(initial = null)
    val prefs = prefsState ?: VdrPrefs()
    var showAdd by remember { mutableStateOf(false) }
    var showProUpgrade by remember { mutableStateOf(false) }
    var screen by rememberSaveable(stateSaver = VdrScreenSaver) {
        mutableStateOf(if (openBrowse) VdrScreen.PageMedia else VdrScreen.Home)
    }
    var pageMediaSession by remember { mutableIntStateOf(0) }
    var showFabChooser by remember { mutableStateOf(false) }
    var addPrefill by remember { mutableStateOf("") }
    var speedKb by remember { mutableFloatStateOf(0f) }
    var clipboardPrompt by remember { mutableStateOf<List<String>?>(null) }
    var pageMediaSeed by remember { mutableStateOf(initialBrowseUrl) }

    fun requireProOrUpgrade(): Boolean {
        if (isPro) return true
        showProUpgrade = true
        return false
    }

    fun openPageMediaScreen(seed: String? = null) {
        if (!requireProOrUpgrade()) return
        showFabChooser = false
        showAdd = false
        clipboardPrompt = null
        pageMediaSeed = seed ?: clipboardHttpUrls(context).singleOrNull()
        pageMediaSession++
        screen = VdrScreen.PageMedia
    }

    fun openAdd(prefill: String = "") {
        showFabChooser = false
        addPrefill = prefill
        showAdd = true
    }

    /** Queue URLs via DownloadService (Add / clipboard / Share path — never Browse gap). */
    fun queueUrls(urls: List<String>, segments: Int = ProGates.FREE_MAX_SEGMENTS) {
        if (urls.isEmpty()) return
        if (!ProGates.canBatchQueue(urls.size, isPro)) {
            showProUpgrade = true
            return
        }
        val segs = ProGates.clampSegments(segments, isPro)
        urls.forEach { url ->
            DownloadService.send(context, DownloadService.ACTION_ADD) {
                putExtra(DownloadService.EXTRA_URL, url)
                putExtra(DownloadService.EXTRA_SEGMENTS, segs)
            }
        }
        val label = if (urls.size == 1) {
            MediaGrabber.displayName(urls.first())
        } else {
            "${urls.size} URLs"
        }
        Toast.makeText(context, "Queued $label", Toast.LENGTH_SHORT).show()
    }

    /** Paste icon: direct file URL(s) → confirm queue; otherwise open Add with clipboard text. */
    fun pasteFromClipboard() {
        showFabChooser = false
        val urls = clipboardHttpUrls(context)
        when {
            urls.isEmpty() -> {
                val raw = clipboardText(context)
                if (raw.isBlank()) {
                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                } else {
                    openAdd(raw)
                }
            }
            urls.all { DirectUrl.looksLikeDirectFile(it) || DirectUrl.isBlockedWatchPage(it) } -> {
                clipboardPrompt = urls
            }
            else -> openAdd(clipboardText(context))
        }
    }

    LaunchedEffect(openBrowse, initialBrowseUrl, proState) {
        // Deciding before the entitlement is known would consume the browse
        // request and answer it with an upgrade prompt, so a Pro user opening
        // Page Media from outside the app never arrived.
        if (openBrowse && proKnown) {
            onBrowseConsumed()
            if (isPro) {
                if (initialBrowseUrl != null) pageMediaSeed = initialBrowseUrl
                pageMediaSession++
                screen = VdrScreen.PageMedia
            } else {
                showProUpgrade = true
                screen = VdrScreen.Home
            }
        }
    }

    // Drop page-media if entitlement is genuinely lost while on that screen.
    // `screen` is rememberSaveable, so after process death this restores to
    // PageMedia while the entitlement is still loading; acting on the
    // not-yet-known value greeted returning Pro users with an upsell.
    LaunchedEffect(proState, screen) {
        if (proKnown && !isPro && screen == VdrScreen.PageMedia) {
            screen = VdrScreen.Home
            showProUpgrade = true
        }
    }

    if (screen == VdrScreen.PageMedia && isPro) {
        BackHandler { screen = VdrScreen.Home }
        key(pageMediaSession) {
            PageMediaScreen(
                onClose = { screen = VdrScreen.Home },
                initialUrl = pageMediaSeed,
                onDownload = { url ->
                    queueUrls(listOf(url))
                    screen = VdrScreen.Home
                },
            )
        }
        return
    }

    LaunchedEffect(prefs.speedKb) {
        speedKb = prefs.speedKb.toFloat()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    // Clipboard watch is independent of page-media scan flow.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(500)
                if (screen != VdrScreen.Home) continue
                if (showAdd || showFabChooser || clipboardPrompt != null) continue
                val urls = clipboardHttpUrls(context)
                if (urls.isEmpty()) continue
                val key = urls.joinToString("\n")
                val current = VdrSettings.read(context)
                if (key == current.lastClipboardUrl) continue
                // Always offer — do not suppress because a prior download of the same URL exists
                // (that regression hid paste prompts after the test suite filled the queue).
                clipboardPrompt = urls
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    ProTitle(isPro = isPro) {
                        scope.launch {
                            val on = toggleDebugPro(context)
                            Toast.makeText(
                                context,
                                if (on) "Debug Pro unlocked" else "Debug Pro locked",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { openPageMediaScreen() }) {
                        Icon(Icons.Default.Language, contentDescription = "Scan page for video (Pro)")
                    }
                    IconButton(onClick = { pasteFromClipboard() }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Add from clipboard")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showFabChooser = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add or find media")
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
                Text(
                    if (isPro) "Focus Guard" else "Focus Guard (Pro)",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Switch(
                    checked = prefs.focusGuard && isPro,
                    onCheckedChange = { enabled ->
                        if (enabled && !requireProOrUpgrade()) return@Switch
                        DownloadService.send(context, DownloadService.ACTION_FOCUS) {
                            putExtra(DownloadService.EXTRA_FOCUS, enabled)
                        }
                    },
                )
            }
            LaunchedEffect(isPro) {
                if (!isPro && prefs.focusGuard) {
                    DownloadService.send(context, DownloadService.ACTION_FOCUS) {
                        putExtra(DownloadService.EXTRA_FOCUS, false)
                    }
                }
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
            // Page media stays reachable even when the queue is non-empty (not empty-state only).
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { openPageMediaScreen() }) {
                    Icon(Icons.Default.Language, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan page")
                }
                Button(onClick = { pasteFromClipboard() }) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add URL")
                }
            }
            Spacer(Modifier.height(8.dp))
            if (downloads.isEmpty()) {
                Text(
                    "No downloads yet. Paste a page URL that embeds clear media, or a direct file link (…/file.mp4).",
                    modifier = Modifier.padding(top = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Segmented resume, optional Wi‑Fi only (off by default — mobile data works), saves to Downloads/VDR.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
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

    if (showFabChooser) {
        AlertDialog(
            onDismissRequest = { showFabChooser = false },
            title = { Text("Add or scan") },
            text = {
                Text("Add a direct file URL, scan a page for video, or paste from the clipboard.")
            },
            confirmButton = {
                Column {
                    Button(
                        onClick = { openAdd("") },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Add URL") }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { openPageMediaScreen() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Scan page for video") }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { pasteFromClipboard() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Paste from clipboard") }
                    TextButton(
                        onClick = { showFabChooser = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Cancel") }
                }
            },
            dismissButton = {},
        )
    }

    if (showAdd) {
        AddUrlDialog(
            initialText = addPrefill,
            isPro = isPro,
            onNeedPro = { showProUpgrade = true },
            onDismiss = { showAdd = false },
            onConfirm = { urls, segments ->
                queueUrls(urls, segments)
                scope.launch { VdrSettings.setLastClipboardUrl(context, urls.joinToString("\n")) }
                showAdd = false
            },
        )
    }

    val promptUrls = clipboardPrompt
    if (promptUrls != null && screen == VdrScreen.Home) {
        val preview = promptUrls.joinToString("\n")
        val batchNeedsPro = !ProGates.canBatchQueue(promptUrls.size, isPro)
        AlertDialog(
            onDismissRequest = {
                scope.launch { VdrSettings.setLastClipboardUrl(context, preview) }
                clipboardPrompt = null
            },
            title = {
                Text(
                    when {
                        promptUrls.size == 1 -> "Add from clipboard?"
                        batchNeedsPro -> "Batch queue is Pro"
                        else -> "Add ${promptUrls.size} URLs from clipboard?"
                    },
                )
            },
            text = {
                Text(
                    if (batchNeedsPro) {
                        "Free includes one URL at a time. Unlock Pro for ₹1 to queue ${promptUrls.size} URLs together.\n\n$preview"
                    } else {
                        preview
                    },
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (batchNeedsPro) {
                        clipboardPrompt = null
                        showProUpgrade = true
                    } else {
                        queueUrls(promptUrls)
                        scope.launch { VdrSettings.setLastClipboardUrl(context, preview) }
                        clipboardPrompt = null
                    }
                }) {
                    Text(
                        when {
                            batchNeedsPro -> "Unlock Pro"
                            promptUrls.size == 1 -> "Download"
                            else -> "Download all"
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch { VdrSettings.setLastClipboardUrl(context, preview) }
                    clipboardPrompt = null
                }) { Text("Not now") }
            },
        )
    }

    if (showProUpgrade) {
        ProUpgradeDialog(
            billing = billing,
            isPro = isPro,
            onDismiss = { showProUpgrade = false },
        )
    }
}

@Composable
private fun DownloadCard(row: TaskSnapshot) {
    val context = LocalContext.current
    val progress = if (row.totalBytes != null && row.totalBytes > 0)
        (row.downloadedBytes.toFloat() / row.totalBytes).coerceIn(0f, 1f) else 0f
    val completed = row.status == DownloadStatus.COMPLETED
    val canOpenOrShare = completed && (
        !row.contentUri.isNullOrBlank() ||
            File(row.destPath).exists() ||
            row.destPath.startsWith("Downloads/VDR/")
        )
    Card(colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                row.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (canOpenOrShare) {
                    Modifier.clickable { openDownloadedFile(context, row) }
                } else {
                    Modifier
                },
            )
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
                modifier = if (canOpenOrShare) {
                    Modifier.clickable { openDownloadsFolder(context, row) }
                } else {
                    Modifier
                },
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
                }, enabled = row.status != DownloadStatus.REMUXING) {
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
                IconButton(
                    enabled = canOpenOrShare || completed,
                    onClick = { openDownloadsFolder(context, row) },
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Open in Downloads")
                }
                if (canOpenOrShare) {
                    IconButton(onClick = { shareDownloadedFile(context, row) }) {
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
    isPro: Boolean,
    onNeedPro: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (List<String>, Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val maxSeg = ProGates.maxSegments(isPro).toFloat()
    var url by remember { mutableStateOf(initialText) }
    var segments by remember {
        mutableFloatStateOf(ProGates.FREE_MAX_SEGMENTS.toFloat().coerceAtMost(maxSeg))
    }
    var scanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var foundMedia by remember { mutableStateOf<List<String>?>(null) }
    val urls = DirectUrl.extractHttpUrls(url)
    val pageError = urls.singleOrNull()?.let { DirectUrl.rejectionMessage(it) }
    val displayError = scanError ?: pageError

    LaunchedEffect(isPro) {
        if (segments > maxSeg) segments = maxSeg
    }

    val found = foundMedia
    if (found != null && found.isNotEmpty()) {
        val first = found.first()
        AlertDialog(
            onDismissRequest = { foundMedia = null },
            title = { Text(MediaGrabber.FOUND_ON_PAGE_TITLE) },
            text = {
                Text(
                    buildString {
                        append(MediaGrabber.displayName(first))
                        append("\n\n")
                        append(first)
                        if (found.size > 1) append("\n\n(+${found.size - 1} more)")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            confirmButton = {
                Button(onClick = {
                    onConfirm(found, segments.roundToInt())
                    foundMedia = null
                }) { Text("Yes") }
            },
            dismissButton = {
                TextButton(onClick = { foundMedia = null }) { Text("No") }
            },
        )
        return
    }

    fun submit() {
        if (urls.isEmpty()) {
            scanError = "Paste an https:// file or page URL"
            return
        }
        if (pageError != null) return
        if (urls.size > 1 && !ProGates.canBatchQueue(urls.size, isPro)) {
            onNeedPro()
            return
        }
        if (ProGates.segmentsNeedPro(segments.roundToInt()) && !isPro) {
            onNeedPro()
            return
        }
        if (urls.size > 1) {
            onConfirm(urls, segments.roundToInt())
            return
        }
        val single = urls.single()
        // Direct file by path → queue immediately (never gated by page-media scan).
        if (DirectUrl.looksLikeDirectFile(single)) {
            onConfirm(listOf(single), segments.roundToInt())
            return
        }
        // Page HTML probe from Add is free for a single URL (queues found media).
        // Full Scan page UI remains Pro.
        scanning = true
        scanError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { MediaGrabber.probePageUrl(single) }
            scanning = false
            when (result) {
                is PageProbeResult.Media -> foundMedia = result.urls
                is PageProbeResult.Blocked -> scanError = result.message
                PageProbeResult.YoutubeOnly -> scanError = MediaGrabber.YOUTUBE_ONLY_ERROR
                PageProbeResult.HtmlNoMedia -> scanError = MediaGrabber.NO_MEDIA_ON_PAGE
                PageProbeResult.DirectFile -> onConfirm(listOf(single), segments.roundToInt())
                PageProbeResult.None -> scanError = MediaGrabber.NO_MEDIA_ON_PAGE
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!scanning) onDismiss() },
        title = { Text("Add download") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        scanError = null
                    },
                    label = { Text("https://… file or page URL") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                    isError = displayError != null,
                    enabled = !scanning,
                )
                if (displayError != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        displayError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (scanning) {
                    Spacer(Modifier.height(6.dp))
                    Text("Looking for media on this page…", style = MaterialTheme.typography.bodySmall)
                } else if (urls.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (isPro) {
                            "${urls.size} URLs — each valid direct file will queue; watch pages are skipped with an error."
                        } else {
                            "${urls.size} URLs — batch queue is Pro. Unlock or paste one URL."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isPro) "Segments: ${segments.roundToInt()}"
                    else "Segments: ${segments.roundToInt()} (free max ${ProGates.FREE_MAX_SEGMENTS})",
                )
                Slider(
                    value = segments.coerceAtMost(maxSeg),
                    onValueChange = { next ->
                        if (!isPro && next > ProGates.FREE_MAX_SEGMENTS) {
                            segments = ProGates.FREE_MAX_SEGMENTS.toFloat()
                            onNeedPro()
                        } else {
                            segments = next
                        }
                    },
                    valueRange = 1f..maxSeg,
                    steps = (maxSeg.toInt() - 2).coerceAtLeast(0),
                    enabled = !scanning,
                )
                Text(
                    "Paste a direct file link (…/file.mp4) or a page URL. " +
                        "Scan page (globe) is Pro — pick from a full media list.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !scanning && urls.isNotEmpty() && (urls.size > 1 || pageError == null),
                onClick = { submit() },
            ) {
                Text(
                    when {
                        scanning -> "Scanning…"
                        urls.size > 1 && !isPro -> "Unlock Pro"
                        urls.size > 1 -> "Download ${urls.size}"
                        else -> "Download"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !scanning) { Text("Cancel") }
        },
    )
}

private fun openDownloadsFolder(context: Context, row: TaskSnapshot) {
    if (PublicStore.openDownloadsFolder(context, row.category)) return
    // Last resort: open the published file so the user still reaches something useful.
    if (openDownloadedFile(context, row, quiet = true)) return
    Toast.makeText(context, "Could not open Downloads/VDR", Toast.LENGTH_SHORT).show()
}

private fun openDownloadedFile(context: Context, row: TaskSnapshot, quiet: Boolean = false): Boolean {
    val private = File(row.destPath).takeIf { it.isAbsolute && it.exists() }
    val ok = PublicStore.openPublishedFile(
        context = context,
        displayName = row.displayName,
        category = row.category,
        knownUri = row.contentUri,
        privateFile = private,
    )
    if (!ok && !quiet) {
        Toast.makeText(context, "File not found in Downloads/VDR", Toast.LENGTH_SHORT).show()
    }
    return ok
}

private fun shareDownloadedFile(context: Context, row: TaskSnapshot) {
    val private = File(row.destPath).takeIf { it.isAbsolute && it.exists() }
    val uri = PublicStore.resolveContentUri(
        context,
        row.displayName,
        row.category,
        row.contentUri,
    ) ?: private?.let {
        FileProvider.getUriForFile(context, "${context.packageName}.files", it)
    }
    if (uri == null) {
        Toast.makeText(context, "Nothing to share yet", Toast.LENGTH_SHORT).show()
        return
    }
    val mime = context.contentResolver.getType(uri) ?: Organizer.mimeType(row.displayName)
    val send = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chooser = Intent.createChooser(send, "Share ${row.displayName}")
    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    chooser.clipData = ClipData.newRawUri(row.displayName, uri)
    runCatching { context.startActivity(chooser) }
        .onFailure {
            Toast.makeText(context, "Share failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
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
