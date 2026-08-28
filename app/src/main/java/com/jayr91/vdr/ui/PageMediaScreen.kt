package com.jayr91.vdr.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jayr91.vdr.engine.DirectUrl
import com.jayr91.vdr.engine.MediaGrabber
import com.jayr91.vdr.engine.PageProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ScanPhase { Idle, Scanning, Done }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageMediaScreen(
    onClose: () -> Unit,
    initialUrl: String? = null,
    onDownload: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var urlText by remember { mutableStateOf(initialUrl?.trim().orEmpty()) }
    var phase by remember { mutableStateOf(ScanPhase.Idle) }
    var error by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var scannedPage by remember { mutableStateOf<String?>(null) }

    fun normalizeUrl(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return ""
        if (t.startsWith("http://") || t.startsWith("https://")) return t
        return "https://$t"
    }

    fun scan() {
        val urls = DirectUrl.extractHttpUrls(urlText)
        when {
            urls.isEmpty() -> {
                error = "Paste a full https:// page or file URL"
                found = emptyList()
                selected = null
                phase = ScanPhase.Done
                return
            }
            urls.size > 1 -> {
                error = "Paste one URL at a time"
                found = emptyList()
                selected = null
                phase = ScanPhase.Done
                return
            }
        }
        val page = normalizeUrl(urls.single())
        val blocked = DirectUrl.rejectionMessage(page)
        if (blocked != null) {
            error = blocked
            found = emptyList()
            selected = null
            scannedPage = page
            phase = ScanPhase.Done
            return
        }
        phase = ScanPhase.Scanning
        error = null
        found = emptyList()
        selected = null
        scannedPage = page
        scope.launch {
            val result = withContext(Dispatchers.IO) { MediaGrabber.probePageUrl(page) }
            val items = MediaGrabber.urlsForPageMediaPicker(result, page)
            phase = ScanPhase.Done
            when {
                items.isNotEmpty() -> {
                    found = items
                    selected = items.first()
                    error = null
                }
                result is PageProbeResult.Blocked -> error = result.message
                // Our failure to read the page, not a verdict about it.
                result is PageProbeResult.Failed -> error = result.message
                result is PageProbeResult.YoutubeOnly -> error = MediaGrabber.YOUTUBE_ONLY_ERROR
                result is PageProbeResult.HtmlNoMedia -> error = MediaGrabber.NO_VIDEO_ON_PAGE
                result is PageProbeResult.DirectFile && !MediaGrabber.isVideoishMedia(page) ->
                    error = "This URL is not a video file. Paste a page with embedded video or a direct video link."
                else -> error = MediaGrabber.NO_VIDEO_ON_PAGE
            }
        }
    }

    LaunchedEffect(initialUrl) {
        val seed = initialUrl?.trim().orEmpty()
        if (seed.startsWith("http://") || seed.startsWith("https://")) {
            urlText = seed
            scan()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Page media") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
            )
        },
        bottomBar = {
            if (found.isNotEmpty()) {
                Button(
                    onClick = { selected?.let(onDownload) },
                    enabled = selected != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        if (selected != null) {
                            "Download ${MediaGrabber.displayName(selected!!)}"
                        } else {
                            "Download"
                        },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        if (phase == ScanPhase.Done) {
                            error = null
                        }
                    },
                    label = { Text("Page or video URL") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    enabled = phase != ScanPhase.Scanning,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val clip = clipboardText(context)
                        if (clip.isNotBlank()) urlText = clip.trim()
                    },
                    enabled = phase != ScanPhase.Scanning,
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { scan() },
                enabled = phase != ScanPhase.Scanning && urlText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (phase == ScanPhase.Scanning) "Scanning…" else "Scan page")
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuggestionChip(
                    onClick = {
                        urlText = "https://samplelib.com/sample-mp4.html"
                        scan()
                    },
                    label = { Text("samplelib mp4") },
                )
                SuggestionChip(
                    onClick = {
                        urlText = MediaGrabber.DEMO_PAGE_URL
                        scan()
                    },
                    label = { Text(MediaGrabber.DEMO_CHIP_LABEL) },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Paste a web page URL — VDR lists every clear video file found (.mp4, .webm, .m3u8, .mpd). " +
                    "Pick one and tap Download. YouTube and similar watch sites are not supported.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(12.dp))
            when (phase) {
                ScanPhase.Scanning -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.width(12.dp))
                        Text("Looking for video on this page…")
                    }
                }
                ScanPhase.Done -> {
                    if (error != null) {
                        Text(
                            error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else if (found.isEmpty()) {
                        Text(
                            MediaGrabber.NO_VIDEO_ON_PAGE,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    } else {
                        Text(
                            "${found.size} video${if (found.size == 1) "" else "s"} on page",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        scannedPage?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f, fill = true),
                        ) {
                            items(found, key = { MediaGrabber.canonicalize(it) }) { item ->
                                MediaPickRow(
                                    url = item,
                                    selected = item == selected,
                                    onSelect = { selected = item },
                                )
                            }
                        }
                    }
                }
                ScanPhase.Idle -> {
                    Text(
                        "Enter a URL and tap Scan page.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaPickRow(
    url: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Text(
                    MediaGrabber.displayName(url),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    MediaGrabber.mediaTypeLabel(url),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    url,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }
        }
    }
}

private fun clipboardText(context: android.content.Context): String {
    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
        as android.content.ClipboardManager
    if (!cm.hasPrimaryClip()) return ""
    return cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
}
