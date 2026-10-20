package com.indiana.zwl.data.tiledownload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.indiana.zwl.data.tiledownload.TileDownloadContract.KEY_AREA_ID
import com.indiana.zwl.data.tiledownload.TileDownloadContract.KEY_LON_EAST
import com.indiana.zwl.data.tiledownload.TileDownloadContract.KEY_LAT_NORTH
import com.indiana.zwl.data.tiledownload.TileDownloadContract.KEY_LAT_SOUTH
import com.indiana.zwl.data.tiledownload.TileDownloadContract.KEY_LON_WEST
import com.indiana.zwl.data.tiledownload.TileDownloadContract.KEY_MAX_ZOOM
import com.indiana.zwl.data.tiledownload.TileDownloadContract.KEY_MIN_ZOOM
import com.indiana.zwl.data.tiledownload.TileDownloadContract.KEY_MODE
import com.indiana.zwl.data.tiledownload.TileDownloadContract.KEY_STARTED_AT
import com.indiana.zwl.data.tiledownload.TileDownloadContract.MODE_DOWNLOAD
import com.indiana.zwl.data.tiledownload.TileDownloadContract.MODE_REFRESH
import com.indiana.zwl.data.tiledownload.TileDownloadContract.RETRY_BACKOFF_SECONDS
import com.indiana.zwl.data.tiledownload.TileDownloadContract.TAG
import com.indiana.zwl.data.tiledownload.TileDownloadContract.WORK_NAME
import com.indiana.zwl.domain.model.DownloadedArea
import com.indiana.zwl.shared.offline.OfflineLimits
import com.indiana.zwl.shared.offline.Region
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Snapshot of the in-flight tile download that the progress card renders. */
data class TileDownloadUiState(
    val active: Boolean = false,
    val progress: Float = 0f,
    val text: String = ""
)

/** One-shot message for the user (surfaced as a toast while the UI is alive). */
data class TileDownloadMessage(val text: String, val isLong: Boolean = true)

/**
 * Owns the offline tile download as a single WorkManager job.
 *
 * The job runs as a long-running worker, which WorkManager executes inside a
 * `dataSync` foreground service — that is what keeps the download alive with
 * the screen off, in Doze and when the activity is gone. Because the work is
 * persisted by WorkManager, a process kill only *interrupts* it: the worker is
 * re-run with the same input (same `startedAt`, hence the same mbtiles file)
 * and the packer skips the tiles already stored, so the download resumes
 * instead of restarting.
 *
 * The worker is the only writer of [state]; this class is the UI's window onto
 * it plus the entry points for start/cancel.
 */
@Singleton
class TileDownloadController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(TileDownloadUiState())
    val state: StateFlow<TileDownloadUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<TileDownloadMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<TileDownloadMessage> = _messages.asSharedFlow()

    // Progress is reported per tile; coalesce it so neither Compose nor the
    // notification is updated more than a few times per second.
    private var lastPublishAt = 0L
    private var lastPublishedProgress = -1f

    fun startDownload(
        region: Region,
        minZoom: Int = OfflineLimits.MIN_ZOOM,
        maxZoom: Int = OfflineLimits.MAX_ZOOM
    ) {
        if (refuseIfBusy()) return
        scope.launch {
            if (isInFlight()) return@launch
            enqueue(
                workDataOf(
                    KEY_MODE to MODE_DOWNLOAD,
                    KEY_STARTED_AT to System.currentTimeMillis(),
                    KEY_LAT_SOUTH to region.latSouth,
                    KEY_LAT_NORTH to region.latNorth,
                    KEY_LON_WEST to region.lonWest,
                    KEY_LON_EAST to region.lonEast,
                    KEY_MIN_ZOOM to minZoom,
                    KEY_MAX_ZOOM to maxZoom
                ),
                "Rozpoczynanie pobierania..."
            )
        }
    }

    fun startRefresh(area: DownloadedArea) {
        if (refuseIfBusy()) return
        scope.launch {
            if (isInFlight()) return@launch
            enqueue(
                workDataOf(
                    KEY_MODE to MODE_REFRESH,
                    KEY_STARTED_AT to System.currentTimeMillis(),
                    KEY_AREA_ID to area.id,
                    KEY_LAT_SOUTH to area.latSouth,
                    KEY_LAT_NORTH to area.latNorth,
                    KEY_LON_WEST to area.lonWest,
                    KEY_LON_EAST to area.lonEast,
                    KEY_MIN_ZOOM to area.minZoom,
                    KEY_MAX_ZOOM to area.maxZoom
                ),
                "Rozpoczynanie odświeżania..."
            )
        }
    }

    /** Cancels the running download/refresh; a cancelled download drops its partial file. */
    fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    /**
     * File names owned by work that is still pending or running. The offline
     * janitor must not wipe them as orphans — they are resumable partials.
     */
    suspend fun protectedFileNames(): Set<String> =
        withContext(Dispatchers.IO) {
            workManager.getWorkInfosForUniqueWork(WORK_NAME).get()
                .filter { info ->
                    info.state == WorkInfo.State.RUNNING ||
                        info.state == WorkInfo.State.ENQUEUED ||
                        info.state == WorkInfo.State.BLOCKED
                }
                // A refresh always writes a throwaway file: it is never resumed,
                // so it must stay an orphan candidate.
                .filter { it.progress.getString(KEY_MODE) == MODE_DOWNLOAD }
                .mapNotNull { it.progress.getString(KEY_STARTED_AT)?.toLongOrNull() }
                .map { "area_$it.mbtiles" }
                .toSet()
        }

    // ---- worker-facing -----------------------------------------------------

    /** Called as soon as the worker starts, including on a resumed run. */
    fun onWorkerStarted(text: String) {
        resetThrottle()
        _state.value = TileDownloadUiState(active = true, progress = 0f, text = text)
    }

    /**
     * Publishes progress, coalesced to at most one update per
     * [MIN_PUBLISH_INTERVAL_MS] unless the value moved by [PROGRESS_STEP].
     * Returns true when the caller should also refresh the notification.
     */
    fun publishProgress(progress: Float, text: String): Boolean {
        val now = System.currentTimeMillis()
        val movedEnough = progress - lastPublishedProgress >= PROGRESS_STEP
        if (lastPublishAt != 0L && now - lastPublishAt < MIN_PUBLISH_INTERVAL_MS && !movedEnough) {
            return false
        }
        lastPublishAt = now
        lastPublishedProgress = progress
        _state.value = _state.value.copy(progress = progress, text = text)
        return true
    }

    /** Status line without progress movement ("Utrata połączenia — ponawiam..."). */
    fun publishStatus(text: String) {
        _state.value = _state.value.copy(text = text)
    }

    /** Terminal state: clears the progress card and surfaces [message] once. */
    fun publishFinished(message: String?) {
        _state.value = TileDownloadUiState()
        resetThrottle()
        if (message != null) emit(message, isLong = true)
    }

    // ---- internals ---------------------------------------------------------

    /** True when a download is already running and the request must be dropped. */
    private fun refuseIfBusy(): Boolean {
        if (!_state.value.active) return false
        emit("Pobieranie już trwa — najpierw je anuluj.", isLong = false)
        return true
    }

    private fun enqueue(input: Data, initialText: String) {
        val request = OneTimeWorkRequestBuilder<TileDownloadWorker>()
            .setInputData(input)
            // Losing the network stops the job; it is re-enqueued and resumes
            // from the tiles already packed.
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        _state.value = TileDownloadUiState(active = true, progress = 0f, text = initialText)
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Whether WorkManager still holds this job. Also drops a stale "active"
     * state left behind by work that ended without the worker reporting back
     * (cancelled while still blocked, for instance).
     */
    private suspend fun isInFlight(): Boolean {
        val infos = withContext(Dispatchers.IO) {
            workManager.getWorkInfosForUniqueWork(WORK_NAME).get()
        }
        val inFlight = infos.any { info ->
            info.state == WorkInfo.State.RUNNING ||
                info.state == WorkInfo.State.ENQUEUED ||
                info.state == WorkInfo.State.BLOCKED
        }
        if (!inFlight && _state.value.active) {
            _state.value = TileDownloadUiState()
        }
        return inFlight
    }

    private fun emit(text: String, isLong: Boolean) {
        _messages.tryEmit(TileDownloadMessage(text, isLong))
    }

    private fun resetThrottle() {
        lastPublishAt = 0L
        lastPublishedProgress = -1f
    }

    companion object {
        private const val MIN_PUBLISH_INTERVAL_MS = 250L
        private const val PROGRESS_STEP = 0.02f
    }
}
