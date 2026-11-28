package com.andrewmacheret.i5.core

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant

data class FetchResult(
    /** The server's answer, or EMPTY when there's none yet */
    val status: ServerStatus,
    /** When the app got it from the server, or null when there's none */
    val loadedAt: Instant?,
    /** Why the last download failed */
    val error: String? = null,
)

/** Downloads the server's status and keeps the last good copy on disk. */
class Fetcher(
    private val cacheDir: File,
    private val download: (String) -> String = { httpGet(it, DEFAULT_USER_AGENT) },
) {
    private val file get() = File(cacheDir, "status.json")

    /** Blocking. Call from a background thread. */
    fun load(now: Instant = Instant.now()): FetchResult {
        val text = try {
            download(Sources.STATUS)
        } catch (e: Exception) {
            return saved(e.message ?: e.javaClass.simpleName)
        }
        val status = try {
            StatusJson.decode(text)
        } catch (e: Exception) {
            return saved("The server sent something the app couldn't read")
        }
        // Don't replace a good copy with one that has no timeline
        if (status.published == null && saved().status.published != null) return saved("The server hasn't published a schedule yet")
        cacheDir.mkdirs()
        // Write then rename so a crash never leaves half a file
        val tmp = File(cacheDir, "status.json.tmp")
        tmp.writeText(text)
        tmp.renameTo(file)
        file.setLastModified(now.toEpochMilli())
        return FetchResult(status, now)
    }

    /** The last good copy, or EMPTY */
    fun saved(error: String? = null): FetchResult {
        val f = file
        val status = if (f.exists()) runCatching { StatusJson.decode(f.readText()) }.getOrNull() else null
        return if (status != null) FetchResult(status, Instant.ofEpochMilli(f.lastModified()), error) else FetchResult(ServerStatus.EMPTY, null, error)
    }

    companion object {
        const val DEFAULT_USER_AGENT = "I5Express-Android (+https://github.com/andrewmacheret/i5-android)"

        fun httpGet(url: String, userAgent: String = DEFAULT_USER_AGENT): String {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "application/json")
            conn.useCaches = false
            try {
                if (conn.responseCode != 200) throw IllegalStateException("The server returned HTTP ${conn.responseCode}")
                return conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
    }
}
