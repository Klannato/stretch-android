package com.nous.stretchup.data

import android.content.Context

/**
 * Resolves which footage to play for a stretch, in priority order:
 *
 *  1. clips bundled in `assets/videos/<id>.mp4` — added by whoever builds the app
 *  2. clips the user imported at runtime (only reachable in debug builds)
 *
 * Bundled clips are plain assets, so a release build with footage needs no permissions,
 * no document picker, and no storage access at all.
 */
object Footage {

    /** ExoPlayer needs the triple-slash form; DefaultDataSource maps it to the assets. */
    private const val ASSET_SCHEME = "asset:///videos/"

    fun bundled(context: Context, stretchId: String): List<String> {
        val files = runCatching { context.assets.list("videos")?.toList() }.getOrNull().orEmpty()
        return files
            .filter { name ->
                if (!name.endsWith(".mp4", ignoreCase = true)) return@filter false
                val stem = name.removeSuffix(".mp4").removeSuffix(".MP4")
                if (!stem.startsWith(stretchId)) return@filter false
                // must be the id itself or an explicit angle suffix, so "quad" never
                // swallows "quadriceps-something"
                val rest = stem.removePrefix(stretchId)
                rest.isEmpty() || rest.startsWith("_") || rest.startsWith("-")
            }
            .sorted()
            .map { ASSET_SCHEME + it }
    }

    /** Everything playable for this stretch, bundled first. */
    fun forStretch(context: Context, stretchId: String): List<String> =
        bundled(context, stretchId) + VideoLibrary(context).videosFor(stretchId)
}
