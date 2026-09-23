package com.indiana.zwl.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.indiana.zwl.domain.model.DownloadedArea
import com.indiana.zwl.domain.repository.OfflineAreaRepository
import com.indiana.zwl.presentation.DownloadEvent
import com.indiana.zwl.shared.offline.MbtilesStoreFactory
import com.indiana.zwl.shared.offline.OfflineAreaDownloadCoordinator
import com.indiana.zwl.shared.offline.OfflineAreaFiles
import com.indiana.zwl.shared.offline.OfflineAreaNames
import com.indiana.zwl.shared.offline.OfflineLimits
import com.indiana.zwl.shared.offline.Region
import com.indiana.zwl.shared.offline.TileMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.TimeZone
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    okHttpClient: OkHttpClient,
    private val offlineAreaRepository: OfflineAreaRepository,
    private val offlineAreaFiles: OfflineAreaFiles
) : ViewModel() {

    private val _isDownloadingArea = MutableStateFlow(false)
    val isDownloadingArea: StateFlow<Boolean> = _isDownloadingArea.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _downloadText = MutableStateFlow("")
    val downloadText: StateFlow<String> = _downloadText.asStateFlow()

    private val _downloadEvent = MutableSharedFlow<DownloadEvent>()
    val downloadEvent: SharedFlow<DownloadEvent> = _downloadEvent.asSharedFlow()

    // Tap on a managed area -> fly the map camera to its bbox.
    private val _flyToArea = MutableSharedFlow<DownloadedArea>(extraBufferCapacity = 1)
    val flyToArea: SharedFlow<DownloadedArea> = _flyToArea.asSharedFlow()

    // Non-null -> UI shows a modal explanation why the download cannot start
    // (size above the absolute safety ceiling). Toasts are too easy to miss.
    private val _downloadBlockedMessage = MutableStateFlow<String?>(null)
    val downloadBlockedMessage: StateFlow<String?> = _downloadBlockedMessage.asStateFlow()

    // Non-null -> area is large (> CONFIRM_TILES_THRESHOLD) and the UI must
    // ask the user whether to continue. `_pendingRegion` remembers what to
    // start once confirmed.
    private val _downloadConfirmMessage = MutableStateFlow<String?>(null)
    val downloadConfirmMessage: StateFlow<String?> = _downloadConfirmMessage.asStateFlow()
    private var pendingRegion: Region? = null

    // In-flight download/refresh — kept so the user can cancel it from the
    // progress card. `downloadGeneration` guards the completion cleanup against
    // a new download started right after a cancellation.
    private var downloadJob: Job? = null
    private var downloadGeneration = 0

    // "Pobrane obszary" is a full-screen map overlay whose open state must
    // survive switching to the Status tab and back (ViewModel scope, not a
    // MapViewContainer-local remember which is disposed with the map).
    private val _showOfflineAreas = MutableStateFlow(false)
    val showOfflineAreas: StateFlow<Boolean> = _showOfflineAreas.asStateFlow()

    val offlineAreas: StateFlow<List<DownloadedArea>> = offlineAreaRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val coordinator = OfflineAreaDownloadCoordinator(
        repository = offlineAreaRepository,
        storeFactory = MbtilesStoreFactory { fileName ->
            SqliteMbtilesStore(File(offlineAreaFiles.areasDirPath(), fileName))
        },
        fetcherProvider = { OkHttpTileFetcher(okHttpClient) },
        files = offlineAreaFiles,
        nameFormatter = { now ->
            OfflineAreaNames.autoName(now, TimeZone.getDefault().getOffset(now) / 60_000)
        }
    )

    fun downloadMapArea(
        latSouth: Double, latNorth: Double,
        lonWest: Double, lonEast: Double
    ) {
        if (downloadJob?.isActive == true) return
        val region = Region(latSouth, latNorth, lonWest, lonEast)
        val estimated = TileMath.estimateTileCount(region)
        when {
            // Absolute safety ceiling — refuse outright.
            estimated > OfflineLimits.MAX_TILES -> {
                _downloadBlockedMessage.value =
                    "Ten widok obejmuje $estimated kafelków — maksymalnie ${OfflineLimits.MAX_TILES}. " +
                        "Przybliż mapę i spróbuj ponownie."
            }
            // Big but allowed — ask the user first.
            estimated > OfflineLimits.CONFIRM_TILES_THRESHOLD -> {
                pendingRegion = region
                _downloadConfirmMessage.value =
                    "Ten obszar obejmuje $estimated kafelków. Pobieranie może potrwać dłuższą chwilę. Kontynuować?"
            }
            else -> startDownload(region)
        }
    }

    fun confirmDownload() {
        val region = pendingRegion ?: return
        pendingRegion = null
        _downloadConfirmMessage.value = null
        startDownload(region)
    }

    fun dismissDownloadConfirm() {
        pendingRegion = null
        _downloadConfirmMessage.value = null
    }

    /** Cancels the running download/refresh; the coordinator drops the partial file. */
    fun cancelDownload() {
        downloadJob?.cancel()
    }

    private fun startDownload(region: Region) {
        if (downloadJob?.isActive == true) return
        // Feedback must be immediate: the progress card is the only visible
        // confirmation, and the first onProgress arrives only after the size
        // check + store open.
        _downloadProgress.value = 0f
        _downloadText.value = "Rozpoczynanie pobierania..."
        _isDownloadingArea.value = true
        val generation = ++downloadGeneration
        downloadJob = viewModelScope.launch {
            try {
                coordinator.download(
                    region = region,
                    onProgress = { progress, text ->
                        _downloadProgress.value = progress
                        _downloadText.value = text
                    },
                    onSuccess = { count ->
                        _downloadEvent.tryEmit(
                            DownloadEvent.ToastMessage(
                                "Pobrano pomyślnie $count kafelków do map offline!",
                                isLong = true
                            )
                        )
                    },
                    onError = { msg ->
                        _downloadEvent.tryEmit(DownloadEvent.ToastMessage(msg, isLong = true))
                    }
                )
            } catch (e: CancellationException) {
                _downloadEvent.tryEmit(
                    DownloadEvent.ToastMessage("Pobieranie anulowane", isLong = true)
                )
                throw e
            } catch (e: Exception) {
                _downloadEvent.tryEmit(
                    DownloadEvent.ToastMessage(
                        "Błąd podczas pobierania: ${e.message ?: e.javaClass.simpleName}",
                        isLong = true
                    )
                )
            } finally {
                if (generation == downloadGeneration) {
                    _isDownloadingArea.value = false
                    _downloadProgress.value = 0f
                    downloadJob = null
                }
            }
        }
    }

    fun refreshArea(area: DownloadedArea) {
        if (downloadJob?.isActive == true) return
        _downloadProgress.value = 0f
        _downloadText.value = "Rozpoczynanie odświeżania..."
        _isDownloadingArea.value = true
        val generation = ++downloadGeneration
        downloadJob = viewModelScope.launch {
            try {
                coordinator.refresh(
                    area = area,
                    onProgress = { progress, text ->
                        _downloadProgress.value = progress
                        _downloadText.value = text
                    },
                    onSuccess = { count ->
                        _downloadEvent.tryEmit(
                            DownloadEvent.ToastMessage(
                                "Obszar odświeżony ($count kafelków)",
                                isLong = true
                            )
                        )
                    },
                    onError = { msg ->
                        _downloadEvent.tryEmit(DownloadEvent.ToastMessage(msg, isLong = true))
                    }
                )
            } catch (e: CancellationException) {
                _downloadEvent.tryEmit(
                    DownloadEvent.ToastMessage("Odświeżanie anulowane", isLong = true)
                )
                throw e
            } catch (e: Exception) {
                _downloadEvent.tryEmit(
                    DownloadEvent.ToastMessage(
                        "Błąd podczas odświeżania: ${e.message ?: e.javaClass.simpleName}",
                        isLong = true
                    )
                )
            } finally {
                if (generation == downloadGeneration) {
                    _isDownloadingArea.value = false
                    _downloadProgress.value = 0f
                    downloadJob = null
                }
            }
        }
    }

    fun deleteArea(area: DownloadedArea) {
        viewModelScope.launch {
            try {
                offlineAreaRepository.delete(area.id)
                offlineAreaFiles.deleteFile(area.fileName)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _downloadEvent.tryEmit(
                    DownloadEvent.ToastMessage("Błąd podczas usuwania obszaru: ${e.message}", isLong = true)
                )
            }
        }
    }

    fun deleteAllAreas() {
        viewModelScope.launch {
            try {
                val areas = offlineAreaRepository.getAll()
                offlineAreaRepository.deleteAll()
                areas.forEach { offlineAreaFiles.deleteFile(it.fileName) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _downloadEvent.tryEmit(
                    DownloadEvent.ToastMessage("Błąd podczas usuwania obszarów: ${e.message}", isLong = true)
                )
            }
        }
    }

    fun renameArea(id: Long, name: String) {
        viewModelScope.launch {
            try {
                offlineAreaRepository.rename(id, name)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _downloadEvent.tryEmit(
                    DownloadEvent.ToastMessage("Błąd podczas zmiany nazwy: ${e.message}", isLong = true)
                )
            }
        }
    }

    fun dismissDownloadBlocked() {
        _downloadBlockedMessage.value = null
    }

    fun openOfflineAreas() {
        _showOfflineAreas.value = true
    }

    fun closeOfflineAreas() {
        _showOfflineAreas.value = false
    }

    fun focusArea(area: DownloadedArea) {
        _flyToArea.tryEmit(area)
    }

    fun offlineFilePath(fileName: String): String = offlineAreaFiles.filePath(fileName)

    fun offlineFileExists(fileName: String): Boolean =
        File(offlineAreaFiles.filePath(fileName)).isFile
}
