package com.indiana.zwl.data.tiledownload

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
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
import com.indiana.zwl.data.tiledownload.TileDownloadContract.MAX_WORK_ATTEMPTS
import com.indiana.zwl.data.tiledownload.TileDownloadContract.MODE_DOWNLOAD
import com.indiana.zwl.data.tiledownload.TileDownloadContract.MODE_REFRESH
import com.indiana.zwl.data.tiledownload.TileDownloadContract.NOTIFICATION_ID
import com.indiana.zwl.domain.repository.OfflineAreaRepository
import com.indiana.zwl.shared.offline.OfflineAreaDownloadCoordinator
import com.indiana.zwl.shared.offline.OfflineLimits
import com.indiana.zwl.shared.offline.Region
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Runs one offline tile download (or refresh) as WorkManager long-running work.
 *
 * `doWork` promotes itself to a `dataSync` foreground service *before* the
 * download starts — that is what keeps the process alive and the network
 * available with the screen off, in Doze and with the activity gone.
 *
 * Input data (including `startedAt`, hence the mbtiles file name) is persisted
 * by WorkManager, so when the process is killed the job is simply re-run with
 * the same input and the packer skips the tiles already stored: the download
 * resumes instead of restarting.
 */
@HiltWorker
class TileDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val controller: TileDownloadController,
    private val coordinator: OfflineAreaDownloadCoordinator,
    private val offlineAreaRepository: OfflineAreaRepository,
    private val notifications: TileDownloadNotifications
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val startedAt = inputData.getLong(KEY_STARTED_AT, 0L)
        val mode = inputData.getString(KEY_MODE) ?: MODE_DOWNLOAD
        val refreshing = mode == MODE_REFRESH
        val label = if (refreshing) "Rozpoczynanie odświeżania..." else "Rozpoczynanie pobierania..."

        // Announce the file this run owns before the first tile is written, so
        // the janitor can tell a resumable partial from an orphan.
        setProgress(workDataOf(KEY_STARTED_AT to startedAt, KEY_MODE to mode))
        setForeground(foregroundInfo(label, 0f))
        controller.onWorkerStarted(label)

        val onProgress: (Float, String) -> Unit = { progress, text ->
            if (controller.publishProgress(progress, text)) {
                setForegroundAsync(foregroundInfo(text, progress))
            }
        }

        var failure: String? = null
        var successMessage: String? = null
        try {
            if (refreshing) {
                refreshArea(onProgress, { successMessage = it }, { failure = it })
            } else {
                downloadArea(startedAt, onProgress, { successMessage = it }, { failure = it })
            }
        } catch (e: CancellationException) {
            // User cancel, lost network constraint or system preemption — the
            // partial file stays for the resumed run.
            controller.publishFinished(null)
            throw e
        } catch (e: Exception) {
            failure = "Błąd podczas pobierania: ${e.message ?: e.javaClass.simpleName}"
        }

        return when {
            failure == null -> {
                controller.publishFinished(successMessage)
                Result.success()
            }
            // runAttemptCount is 0-based: allow MAX_WORK_ATTEMPTS runs in total.
            runAttemptCount + 1 < MAX_WORK_ATTEMPTS -> {
                controller.publishStatus("Utrata połączenia — ponawiam za chwilę...")
                Result.retry()
            }
            else -> {
                controller.publishFinished(failure)
                Result.failure()
            }
        }
    }

    private suspend fun downloadArea(
        startedAt: Long,
        onProgress: (Float, String) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        coordinator.download(
            region = Region(
                latSouth = inputData.getDouble(KEY_LAT_SOUTH, 0.0),
                latNorth = inputData.getDouble(KEY_LAT_NORTH, 0.0),
                lonWest = inputData.getDouble(KEY_LON_WEST, 0.0),
                lonEast = inputData.getDouble(KEY_LON_EAST, 0.0)
            ),
            minZoom = inputData.getInt(KEY_MIN_ZOOM, OfflineLimits.MIN_ZOOM),
            maxZoom = inputData.getInt(KEY_MAX_ZOOM, OfflineLimits.MAX_ZOOM),
            startedAt = startedAt,
            keepPartialOnCancellation = true,
            onProgress = onProgress,
            onSuccess = { count ->
                onSuccess("Pobrano pomyślnie $count kafelków do map offline!")
            },
            onError = onError
        )
    }

    private suspend fun refreshArea(
        onProgress: (Float, String) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val areaId = inputData.getLong(KEY_AREA_ID, -1L)
        val area = offlineAreaRepository.getAll().firstOrNull { it.id == areaId }
        if (area == null) {
            onError("Nie znaleziono obszaru do odświeżenia.")
            return
        }
        coordinator.refresh(
            area = area,
            onProgress = onProgress,
            onSuccess = { count -> onSuccess("Obszar odświeżony ($count kafelków)") },
            onError = onError
        )
    }

    private fun foregroundInfo(text: String, progress: Float): ForegroundInfo =
        ForegroundInfo(
            NOTIFICATION_ID,
            notifications.build(text = text, progress = progress),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
}
