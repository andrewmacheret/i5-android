package com.andrewmacheret.i5.core

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant

sealed interface DataSource {
    /** Freshly read from WSDOT */
    data class Live(val at: Instant) : DataSource

    /** Using the last good copy, saved at this time */
    data class Cached(val at: Instant) : DataSource

    /** Nothing good saved, using the schedule baked into the app */
    data object BuiltIn : DataSource

    /** When WSDOT's page was read, or null for the built-in schedule */
    val fetchedAt: Instant?
        get() = when (this) {
            is Live -> at
            is Cached -> at
            BuiltIn -> null
        }
}

data class FetchResult(
    val info: ExpressLanesInfo,
    val source: DataSource,
    val error: String? = null,
    /** WSDOT's page loaded but couldn't be read */
    val pageChanged: Boolean = false,
)

/** Downloads the WSDOT page and keeps the last good copy on disk. */
class Fetcher(private val cacheDir: File, private val download: (String) -> String = ::httpGet) {
    private val pageFile get() = File(cacheDir, "express-lanes.html")

    // Remembers that the newest page couldn't be read, until a good one comes in.
    // Without it a restart or a failed download would quietly trust the old copy again.
    private val problemFile get() = File(cacheDir, "page-problem.txt")

    /** Blocking. Call from a background thread. */
    fun load(now: Instant = Instant.now()): FetchResult {
        val html = try {
            download(Sources.EXPRESS_LANES)
        } catch (e: Exception) {
            return cachedOrBuiltIn(now, e.message ?: e.javaClass.simpleName)
        }
        val info = Parser.parse(html, now)
        cacheDir.mkdirs()
        if (!info.scheduleFromPage) {
            // Don't overwrite the last good copy with a page we can't read
            problemFile.writeText(info.warnings.joinToString("\n"))
            return cachedOrBuiltIn(now, "Couldn't read WSDOT's page")
        }
        // Write then rename so a crash never leaves half a file
        val tmp = File(cacheDir, "express-lanes.html.tmp")
        tmp.writeText(html)
        tmp.renameTo(pageFile)
        pageFile.setLastModified(now.toEpochMilli())
        problemFile.delete()
        return FetchResult(info, DataSource.Live(now))
    }

    fun cachedOrBuiltIn(now: Instant = Instant.now(), error: String? = null): FetchResult {
        val file = pageFile
        val cached = if (file.exists()) Parser.parse(file.readText(), now).takeIf { it.scheduleFromPage } else null
        val base = if (cached != null) {
            FetchResult(cached, DataSource.Cached(Instant.ofEpochMilli(file.lastModified())), error)
        } else {
            FetchResult(ExpressLanesInfo.BUILT_IN, DataSource.BuiltIn, error)
        }
        val problems = problemFile.takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() } ?: return base
        return base.copy(pageChanged = true, info = base.info.copy(warnings = base.info.warnings + problems))
    }

    companion object {
        fun httpGet(url: String): String {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) I5Express")
            conn.useCaches = false
            try {
                if (conn.responseCode != 200) throw IllegalStateException("WSDOT returned HTTP ${conn.responseCode}")
                return conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
    }
}
