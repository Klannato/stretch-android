package com.nous.stretchup.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject

/**
 * Imported stretch footage, keyed by stretch id.
 *
 * The app stores only a `content://` URI per video — the footage stays wherever the
 * user put it, and is never copied into app storage. Access survives a reboot because
 * the URI permission is persisted when the file is picked (see MainActivity).
 */
class VideoLibrary(context: Context) {

    private val prefs = Prefs(context.applicationContext)

    /** stretch id -> ordered list of video URIs (one per camera angle). */
    fun videos(): Map<String, List<String>> {
        val raw = prefs.videoIndex
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            root.keys().asSequence().associateWith { key ->
                val arr = root.getJSONArray(key)
                (0 until arr.length()).map { arr.getString(it) }
            }
        }.getOrDefault(emptyMap())
    }

    fun videosFor(stretchId: String): List<String> = videos()[stretchId].orEmpty()

    fun count(stretchId: String): Int = videosFor(stretchId).size

    /** Files the user picked that could not be matched to a stretch by filename. */
    fun pending(): List<String> {
        val raw = prefs.pendingVideos
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    fun add(stretchId: String, uri: String) {
        val map = videos().toMutableMap()
        val list = map[stretchId].orEmpty().toMutableList()
        if (uri !in list) list.add(uri)
        map[stretchId] = list
        write(map)
    }

    fun addPending(uris: List<String>) {
        val list = (pending() + uris).distinct()
        prefs.pendingVideos = org.json.JSONArray(list).toString()
    }

    fun resolvePending(uri: String, stretchId: String) {
        add(stretchId, uri)
        prefs.pendingVideos = org.json.JSONArray(pending().filter { it != uri }).toString()
    }

    fun clear(stretchId: String) {
        val map = videos().toMutableMap()
        map.remove(stretchId)
        write(map)
    }

    fun clearAll() {
        prefs.videoIndex = ""
        prefs.pendingVideos = ""
    }

    private fun write(map: Map<String, List<String>>) {
        val root = JSONObject()
        map.forEach { (k, v) -> root.put(k, org.json.JSONArray(v)) }
        prefs.videoIndex = root.toString()
    }

    companion object {
        /**
         * Matches a picked file to a stretch id by name, so bulk import "just works"
         * when files are named after the stretch. Accepts an angle suffix, e.g.
         * `side-bend.mp4`, `side-bend_2.mp4`, `side-bend-angle1.mov`.
         */
        fun stretchIdForFileName(fileName: String, ids: Collection<String>): String? {
            val base = fileName.substringBeforeLast('.').lowercase()
                .trim()
                .replace('_', '-')
                .replace(' ', '-')
            ids.firstOrNull { it == base }?.let { return it }
            // longest prefix wins, so "side-bend-x" never resolves to something shorter
            return ids.filter { base.startsWith("$it-") }.maxByOrNull { it.length }
        }

        fun displayName(context: Context, uri: String): String =
            runCatching {
                context.contentResolver.query(Uri.parse(uri), null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
                }
            }.getOrNull() ?: Uri.parse(uri).lastPathSegment ?: uri
    }
}
