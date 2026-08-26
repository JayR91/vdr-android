package com.jayr91.vdr.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SegmentState(
    val index: Int,
    val start: Long,
    val end: Long, // -1 means unknown total length
    var downloaded: Long = 0,
) {
    fun isComplete(): Boolean = end != -1L && start + downloaded > end
}

fun List<SegmentState>.toJson(): JSONArray {
    val arr = JSONArray()
    forEach { s ->
        arr.put(
            JSONObject()
                .put("index", s.index)
                .put("start", s.start)
                .put("end", s.end)
                .put("downloaded", s.downloaded)
        )
    }
    return arr
}

fun JSONArray.toSegments(): List<SegmentState> {
    val out = mutableListOf<SegmentState>()
    for (i in 0 until length()) {
        val o = getJSONObject(i)
        out += SegmentState(
            index = o.getInt("index"),
            start = o.getLong("start"),
            end = o.getLong("end"),
            downloaded = o.optLong("downloaded", 0),
        )
    }
    return out
}

fun saveState(file: File, url: String, totalSize: Long?, segments: List<SegmentState>) {
    try {
        val tmp = File(file.absolutePath + ".tmp")
        tmp.writeText(
            JSONObject()
                .put("url", url)
                .put("total_size", totalSize ?: JSONObject.NULL)
                .put("segments", segments.toJson())
                .toString()
        )
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    } catch (_: Exception) {
        // Ignore unwritable sidecar files.
    }
}

fun loadState(file: File): Pair<Long?, List<SegmentState>>? {
    if (!file.exists()) return null
    return try {
        val o = JSONObject(file.readText())
        if (o.has("stream_kind")) return null
        val total = if (o.isNull("total_size")) null else o.getLong("total_size")
        total to o.getJSONArray("segments").toSegments()
    } catch (_: Exception) {
        null
    }
}

data class StreamResumeState(
    val kind: String,
    val mediaUrl: String,
    val nextIndex: Int,
    val bytesDownloaded: Long,
    val segments: List<SegmentState>,
)

fun saveStreamState(
    file: File,
    url: String,
    kind: String,
    mediaUrl: String,
    nextIndex: Int,
    bytesDownloaded: Long,
    segments: List<SegmentState>,
) {
    try {
        val tmp = File(file.absolutePath + ".tmp")
        tmp.writeText(
            JSONObject()
                .put("url", url)
                .put("stream_kind", kind)
                .put("media_url", mediaUrl)
                .put("next_index", nextIndex)
                .put("bytes_downloaded", bytesDownloaded)
                .put("segments", segments.toJson())
                .toString()
        )
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    } catch (_: Exception) {
        // Ignore unwritable sidecar files.
    }
}

fun loadStreamState(file: File): StreamResumeState? {
    if (!file.exists()) return null
    return try {
        val o = JSONObject(file.readText())
        val kind = o.optString("stream_kind", "")
        if (kind.isBlank()) return null
        StreamResumeState(
            kind = kind,
            mediaUrl = o.getString("media_url"),
            nextIndex = o.getInt("next_index"),
            bytesDownloaded = o.optLong("bytes_downloaded", 0),
            segments = o.optJSONArray("segments")?.toSegments().orEmpty(),
        )
    } catch (_: Exception) {
        null
    }
}

/** @deprecated use [saveStreamState] */
fun saveHlsState(
    file: File,
    url: String,
    mediaUrl: String,
    nextIndex: Int,
    bytesDownloaded: Long,
    segments: List<SegmentState>,
) = saveStreamState(file, url, "hls", mediaUrl, nextIndex, bytesDownloaded, segments)

/** @deprecated use [loadStreamState] */
fun loadHlsState(file: File): StreamResumeState? =
    loadStreamState(file)?.takeIf { it.kind == "hls" }
