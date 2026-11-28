package com.andrewmacheret.i5

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.andrewmacheret.i5.core.FetchResult
import com.andrewmacheret.i5.core.Fetcher
import com.andrewmacheret.i5.core.ServerStatus
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
import java.time.Instant

/** The one place that holds the server's data and knows when it's being refreshed. */
// Only holds the application context, which lives as long as the app, so it can't leak
@SuppressLint("StaticFieldLeak")
object Lanes {
    private lateinit var app: Context
    private val fetcher by lazy {
        val agent = "I5Express-Android/${BuildConfig.VERSION_NAME} (+https://github.com/andrewmacheret/i5-android)"
        Fetcher(File(app.noBackupFilesDir, "server")) { Fetcher.httpGet(it, agent) }
    }
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
        // Older versions saved WSDOT's page here. The app doesn't read it anymore.
        scope.launch(Dispatchers.IO) { File(app.noBackupFilesDir, "wsdot").deleteRecursively() }
    }

    private val scenario: Scenarios.Scenario?
        get() = if (BuildConfig.DEBUG) Scenarios.current(app) else null

    val inScenario: Boolean get() = scenario != null

    fun now(): Instant = scenario?.now ?: Instant.now()

    /** Reads the saved copy from disk the first time. Call off the main thread. */
    suspend fun load(): FetchResult = withContext(Dispatchers.IO) { current() }

    private fun current(): FetchResult {
        scenario?.let { return it.result }
        return result.value ?: fetcher.saved().also { result.compareAndSet(null, it) }
    }

    fun snapshot(): Snapshot {
        val s = scenario
        val r = current()
        return Snapshot.make(r.status, now(), refreshing = _refreshing.value || s?.refreshing == true, loadError = r.error)
    }

    /** True before the app has any answer from the server, saved or new */
    fun loading(snap: Snapshot): Boolean = snap.status == ServerStatus.EMPTY && snap.refreshing

    /**
     * Downloads the server's status. If a download is already running, waits for that one instead.
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
            val started = System.nanoTime()
            return withContext(Dispatchers.IO) { fetcher.load() }.also {
                result.value = it
                val ms = (System.nanoTime() - started) / 1_000_000
                val p = it.status.published
                Log.i("I5", "read the server in $ms ms: ${p?.segments?.size ?: 0} segments, ${p?.doubts?.size ?: 0} doubts, error=${it.error}")
            }
        } finally {
            _refreshing.value = false
            // Always redraw, so "Checking…" can't stay on the widget
            withContext(NonCancellable) { Sync.run(app) }
        }
    }

    /** For the app screen, which shows any error itself. When opening the app, follows the update settings. */
    fun refreshInBackground(opening: Boolean = false) {
        scope.launch {
            try {
                if (!opening || Updates.dueOnOpen(app, load().loadedAt)) refresh()
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
