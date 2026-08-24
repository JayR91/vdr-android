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
}

fun loadState(file: File): Pair<Long?, List<SegmentState>>? {
    if (!file.exists()) return null
    return try {
        val o = JSONObject(file.readText())
        val total = if (o.isNull("total_size")) null else o.getLong("total_size")
        total to o.getJSONArray("segments").toSegments()
    } catch (_: Exception) {
        null
    }
}
