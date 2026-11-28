package com.andrewmacheret.i5

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

/**
 * WSDOT's camera pictures, straight from its image server, kept in memory only.
 * Each address includes a version that changes every 5 minutes, so a new one means a new picture.
 */
object CameraImages {
    // About 30 pictures at WSDOT's 335 x 244 size
    private val cache = object : LruCache<String, Bitmap>(10 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** The picture at this exact address, if it's already loaded */
    fun cached(url: String): Bitmap? = cache.get(url)

    /** Null when the picture couldn't be loaded */
    suspend fun load(url: String): Bitmap? {
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val conn = URI(url).toURL().openConnection() as HttpURLConnection
                conn.connectTimeout = 20_000
                conn.readTimeout = 20_000
                conn.setRequestProperty("User-Agent", "I5Express-Android/${BuildConfig.VERSION_NAME}")
                try {
                    if (conn.responseCode != 200) return@withContext null
                    conn.inputStream.use { BitmapFactory.decodeStream(it) }?.also { cache.put(url, it) }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w("I5", "camera picture failed: ${e.message}")
                null
            }
        }
    }
}
