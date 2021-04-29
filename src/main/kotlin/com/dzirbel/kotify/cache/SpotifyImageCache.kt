package com.dzirbel.kotify.cache

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.dzirbel.kotify.Application
import com.dzirbel.kotify.Logger
import com.dzirbel.kotify.network.Spotify
import com.dzirbel.kotify.network.await
import com.dzirbel.kotify.ui.util.assertNotOnUIThread
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skija.Image
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.TimeSource

sealed class ImageCacheEvent {
    data class InMemory(val url: String) : ImageCacheEvent()
    data class OnDisk(val url: String, val duration: Duration, val cacheFile: File) : ImageCacheEvent()
    data class Fetch(val url: String, val duration: Duration, val cacheFile: File?) : ImageCacheEvent()
}

/**
 * A simple disk cache for images loaded from Spotify's image CDN.
 */
object SpotifyImageCache {
    /**
     * Represents the current state of the image cache.
     *
     * Creating a new object with the default values reflects the current state.
     *
     * We use a single object for this rather than many [androidx.compose.runtime.MutableState] instances so that
     * updates only trigger a single recomposition.
     */
    data class State(
        val inMemoryCount: Int = totalCompleted.get(),
        val diskCount: Int = IMAGES_DIR.list()?.size ?: 0,
        val totalDiskSize: Int = IMAGES_DIR.listFiles()?.sumBy { it.length().toInt() } ?: 0
    )

    private const val SPOTIFY_IMAGE_URL_PREFIX = "https://i.scdn.co/image/"
    private val IMAGES_DIR by lazy {
        Application.cacheDir.resolve("images")
            .also { it.mkdirs() }
            .also { require(it.isDirectory) { "could not create image cache directory $it" } }
    }

    private val imageJobs: MutableMap<String, Deferred<ImageBitmap?>> = ConcurrentHashMap()

    private var totalCompleted = AtomicInteger()

    /**
     * The current [State] of the cache.
     */
    var state by mutableStateOf(State())
        private set

    /**
     * Clears the in-memory and disk cache.
     */
    fun clear(scope: CoroutineScope = GlobalScope) {
        scope.launch {
            imageJobs.clear()
            totalCompleted.set(0)
            IMAGES_DIR.deleteRecursively()
            state = State()
        }
    }

    /**
     * Resets the in-memory cache, for use from unit tests.
     */
    internal fun testReset() {
        imageJobs.clear()
        totalCompleted.set(0)
        state = State()
    }

    /**
     * Immediately returns the in-memory cached [ImageBitmap] for [url], if these is one.
     */
    fun getInMemory(url: String): ImageBitmap? {
        return imageJobs[url]?.getCompleted()?.also {
            Logger.ImageCache.handleImageCacheEvent(ImageCacheEvent.InMemory(url = url))
        }
    }

    /**
     * Synchronously loads all the given [urls] from the file cache, if they are not currently in the in-memory cache or
     * already being loaded. This is useful for batch loading a set of images all at once.
     */
    fun loadFromFileCache(urls: List<String>) {
        runBlocking {
            urls
                .filter { url -> !imageJobs.containsKey(url) }
                .map { url -> Pair(url, async { fromFileCache(url) }) }
                .forEach { (url, deferred) ->
                    val (_, image) = deferred.await()
                    if (image != null) {
                        imageJobs.putIfAbsent(url, CompletableDeferred(image))
                    }
                }
        }
    }

    /**
     * Fetches the [ImageBitmap] from the given [url] or cache.
     */
    suspend fun get(
        url: String,
        scope: CoroutineScope,
        context: CoroutineContext = EmptyCoroutineContext,
        client: OkHttpClient = Spotify.configuration.okHttpClient
    ): ImageBitmap? {
        val deferred = imageJobs.getOrPut(url) {
            scope.async(context = context) {
                assertNotOnUIThread()

                val (cacheFile, image) = fromFileCache(url)
                image ?: fromRemote(url = url, cacheFile = cacheFile, client = client)
            }.also { deferred ->
                deferred.invokeOnCompletion { error ->
                    if (error == null) {
                        state = State()
                    }
                }
            }
        }

        if (deferred.isCompleted) {
            Logger.ImageCache.handleImageCacheEvent(ImageCacheEvent.InMemory(url = url))
            return deferred.getCompleted()
        }

        return deferred.await()
    }

    private fun fromFileCache(url: String): Pair<File?, ImageBitmap?> {
        val start = TimeSource.Monotonic.markNow()
        var cacheFile: File? = null
        if (url.startsWith(SPOTIFY_IMAGE_URL_PREFIX)) {
            val imageHash = url.substring(SPOTIFY_IMAGE_URL_PREFIX.length)
            cacheFile = IMAGES_DIR.resolve(imageHash)

            if (cacheFile.isFile) {
                val image = Image.makeFromEncoded(cacheFile.readBytes()).asImageBitmap()

                totalCompleted.incrementAndGet()
                Logger.ImageCache.handleImageCacheEvent(
                    ImageCacheEvent.OnDisk(url = url, duration = start.elapsedNow(), cacheFile = cacheFile)
                )

                return Pair(cacheFile, image)
            }
        }

        return Pair(cacheFile, null)
    }

    private suspend fun fromRemote(url: String, cacheFile: File?, client: OkHttpClient): ImageBitmap? {
        val start = TimeSource.Monotonic.markNow()
        val request = Request.Builder().url(url).build()

        return client.newCall(request).await()
            .use { response ->
                @Suppress("BlockingMethodInNonBlockingContext")
                response.body?.bytes()
            }
            ?.takeIf { bytes -> bytes.isNotEmpty() }
            ?.let { bytes ->
                val image = Image.makeFromEncoded(bytes).asImageBitmap()

                cacheFile?.let {
                    IMAGES_DIR.mkdirs()
                    it.writeBytes(bytes)
                }

                totalCompleted.incrementAndGet()
                Logger.ImageCache.handleImageCacheEvent(
                    ImageCacheEvent.Fetch(url = url, duration = start.elapsedNow(), cacheFile = cacheFile)
                )

                image
            }
    }
}