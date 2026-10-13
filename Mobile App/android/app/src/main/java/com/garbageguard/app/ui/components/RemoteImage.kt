package com.garbageguard.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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

/** How pictures that live in the Firebase database are fetched. Set once at start-up. */
object RemoteImages {
    const val CLOUD = "gg-cloud://"

    /** Takes "preview" or "snaps/<id>" and returns the decoded picture. */
    @Volatile
    var cloud: (suspend (String) -> Bitmap?)? = null

    /** Told whether a live screen is open, so the Pi sends the Firebase preview only then. */
    @Volatile
    var onLive: ((Boolean) -> Unit)? = null
    private var liveScreens = 0

    fun liveOpened() {
        liveScreens++
        onLive?.invoke(true)
    }

    fun liveClosed() {
        liveScreens = (liveScreens - 1).coerceAtLeast(0)
        onLive?.invoke(liveScreens > 0)
    }

    /** The small copy of a Firebase snapshot, for lists. Pictures from the Pi itself are unchanged. */
    fun thumb(url: String?): String? =
        if (url != null && url.startsWith(CLOUD + "snaps/")) "$url/thumb" else url
}

/** Pictures from the Pi: the live frame and the saved snapshots. */
private object PiImages {
    val snapshots = LruCache<String, ImageBitmap>(40)

    suspend fun fetch(url: String): ImageBitmap? {
        if (url.startsWith(RemoteImages.CLOUD)) {
            return RemoteImages.cloud?.invoke(url.removePrefix(RemoteImages.CLOUD))?.asImageBitmap()
        }
        return fetchHttp(url)
    }

    private suspend fun fetchHttp(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
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
    DisposableEffect(url) {
        val remote = url?.startsWith(RemoteImages.CLOUD) == true
        if (remote) RemoteImages.liveOpened()
        onDispose { if (remote) RemoteImages.liveClosed() }
    }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        val remote = url.startsWith(RemoteImages.CLOUD)
        while (true) {
            // Over Firebase the Pi posts a still every 3 s, and each download costs allowance, so no faster.
            // Locally the changing query defeats caching, as the API reference advises.
            PiImages.fetch(if (remote) url else "$url?t=${System.currentTimeMillis()}")?.let { frame = it }
            delay(if (remote) 3000 else 120)
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
