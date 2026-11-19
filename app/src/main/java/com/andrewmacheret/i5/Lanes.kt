package com.andrewmacheret.i5

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.andrewmacheret.i5.core.FetchResult
import com.andrewmacheret.i5.core.Fetcher
import com.andrewmacheret.i5.core.Snapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.Instant

/** The one place that holds WSDOT's data and knows when it's being refreshed. */
// Only holds the application context, which lives as long as the app, so it can't leak
@SuppressLint("StaticFieldLeak")
object Lanes {
    private lateinit var app: Context
    private val fetcher by lazy { Fetcher(File(app.noBackupFilesDir, "wsdot")) }
    private var inFlight: Deferred<FetchResult>? = null
    private val result = MutableStateFlow<FetchResult?>(null)
    private val _refreshing = MutableStateFlow(false)
    private val _version = MutableStateFlow(0L)

    /** For work that should outlive a screen, like a refresh started from the app */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Goes up on every sync so the widget and screen redraw with the current time */
    val version: StateFlow<Long> = _version.asStateFlow()

    fun init(context: Context) {
        app = context.applicationContext
    }

    private val scenario: Scenarios.Scenario?
        get() = if (BuildConfig.DEBUG) Scenarios.current(app) else null

    val inScenario: Boolean get() = scenario != null

    fun now(): Instant = scenario?.now ?: Instant.now()

    /** Reads the saved copy from disk the first time. Call off the main thread. */
    suspend fun load(): FetchResult = withContext(Dispatchers.IO) { current() }

    private fun current(): FetchResult {
        scenario?.let { return it.result }
        return result.value ?: fetcher.cachedOrBuiltIn().also { result.compareAndSet(null, it) }
    }

    fun snapshot(): Snapshot {
        val s = scenario
        return Snapshot.make(current(), now(), refreshing = _refreshing.value || s?.refreshing == true)
    }

    /**
     * Downloads WSDOT's page. If a download is already running, waits for that one instead.
     * The download runs in [scope], so a caller giving up doesn't stop it halfway.
     */
    suspend fun refresh(): FetchResult {
        val job = synchronized(this) {
            inFlight?.takeIf { it.isActive } ?: scope.async { download() }.also { inFlight = it }
        }
        return job.await()
    }

    private suspend fun download(): FetchResult {
        if (inScenario) {
            Sync.run(app)
            return current()
        }
        load()
        _refreshing.value = true
        try {
            Sync.run(app)
            return withContext(Dispatchers.IO) { fetcher.load() }.also { result.value = it }
        } finally {
            _refreshing.value = false
            // Always redraw, so "Checking…" can't stay on the widget
            withContext(NonCancellable) { Sync.run(app) }
        }
    }

    /** For the app screen, which shows any error itself. Skips data newer than `ifOlderThan`. */
    fun refreshInBackground(ifOlderThan: Duration? = null) {
        scope.launch {
            try {
                val fetched = load().source.fetchedAt
                val fresh = ifOlderThan != null && fetched != null && Duration.between(fetched, Instant.now()) in Duration.ZERO..ifOlderThan
                if (!fresh) refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("I5", "refresh failed", e)
            }
        }
    }

    internal fun bump() {
        _version.value++
    }
}
