package com.garbageguard.app.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Pictures from the Pi: the live frame and the saved snapshots. */
private object PiImages {
    val snapshots = LruCache<String, ImageBitmap>(40)

    suspend fun fetch(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 2500
                conn.readTimeout = 4000
                if (conn.responseCode != 200) null
                else conn.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}

/**
 * The newest camera frame from GET /frame.jpg, refreshed about eight times a
 * second while this is on screen. Null until the first frame arrives, and
 * always null when [url] is null (demo data draws its own picture).
 */
@Composable
fun rememberLiveFrame(url: String?): ImageBitmap? {
    var frame by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        while (true) {
            // The changing query defeats caching, as the API reference advises.
            PiImages.fetch("$url?t=${System.currentTimeMillis()}")?.let { frame = it }
            delay(120)
        }
    }
    return frame
}

/** A saved snapshot from GET /snapshots/<name>, kept in a small memory cache. */
@Composable
fun rememberSnapshot(url: String?): ImageBitmap? {
    var image by remember(url) { mutableStateOf(url?.let { PiImages.snapshots.get(it) }) }
    LaunchedEffect(url) {
        if (url == null || image != null) return@LaunchedEffect
        PiImages.fetch(url)?.let {
            PiImages.snapshots.put(url, it)
            image = it
        }
    }
    return image
}
