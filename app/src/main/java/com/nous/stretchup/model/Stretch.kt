package com.nous.stretchup.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * One stretch in the rotation: what it is called, how to do it, and when it belongs.
 *
 * There is deliberately no motion data here. What the app shows is footage
 * (`assets/videos/<id>.mp4`), so a stretch is metadata only.
 */
data class Stretch(
    val id: String,
    val name: String,
    val cue: String,
    /** "am", "pm", or both — decides which session pool this belongs to. */
    val tags: List<String>,
    /** True when the stretch is held on each side, so its block is split in half. */
    val twoSided: Boolean
)

object StretchLibrary {

    fun load(context: Context): List<Stretch> {
        val raw = context.assets.open("stretches.json").bufferedReader().use { it.readText() }
        val arr: JSONArray = JSONObject(raw).getJSONArray("stretches")
        return (0 until arr.length()).mapNotNull { i ->
            runCatching { parse(arr.getJSONObject(i)) }.getOrNull()
        }
    }

    private fun parse(o: JSONObject): Stretch {
        val tags = o.optJSONArray("tags")?.let { t ->
            (0 until t.length()).map { t.getString(it) }
        } ?: emptyList()
        return Stretch(
            id = o.getString("id"),
            name = o.getString("name"),
            cue = o.getString("cue"),
            tags = tags,
            twoSided = o.optString("sides", "one") == "two"
        )
    }
}
