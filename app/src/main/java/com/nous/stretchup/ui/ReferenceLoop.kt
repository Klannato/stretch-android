package com.nous.stretchup.ui

import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.graphics.ImageDecoder
import android.os.Build
import android.widget.ImageView
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Plays a generated reference animation for a stretch (a low-framerate GIF or WebP
 * in assets/ref). Android decodes both natively, so no image library is needed.
 *
 * API 26-27 has no animated decoder, so there the first frame is shown still.
 */
@Composable
fun ReferenceLoop(
    stretchId: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    val drawable = remember(stretchId) { decodeReference(context, stretchId) }

    if (drawable == null) {
        Box(modifier)
        return
    }

    DisposableEffect(stretchId) {
        val animatable = drawable as? Animatable
        animatable?.start()
        onDispose { animatable?.stop() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                this.contentDescription = contentDescription
            }
        },
        update = { view -> view.setImageDrawable(drawable) }
    )
}

private fun decodeReference(context: android.content.Context, stretchId: String): Drawable? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        for (name in listOf("$stretchId.webp", "$stretchId.gif")) {
            runCatching {
                val source = ImageDecoder.createSource(context.assets, "ref/$name")
                return ImageDecoder.decodeDrawable(source)
            }
        }
        return null
    }
    // Still frame for the two older API levels this app still supports.
    return runCatching {
        context.assets.open("ref/$stretchId.gif").use { stream ->
            android.graphics.BitmapFactory.decodeStream(stream)?.let { bmp ->
                android.graphics.drawable.BitmapDrawable(context.resources, bmp)
            }
        }
    }.getOrNull()
}
