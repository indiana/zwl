package com.indiana.zwl.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.indiana.zwl.data.tiledownload.TileDownloadController
import com.indiana.zwl.domain.model.DownloadedArea
import com.indiana.zwl.domain.repository.OfflineAreaRepository
import com.indiana.zwl.domain.usecase.ZoneBounds
import com.indiana.zwl.presentation.DownloadEvent
import com.indiana.zwl.shared.offline.OfflineAreaFiles
import com.indiana.zwl.shared.offline.OfflineLimits
import com.indiana.zwl.shared.offline.Region
import com.indiana.zwl.shared.offline.TileMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val tileDownloads: TileDownloadController,
    private val offlineAreaRepository: OfflineAreaRepository,
    private val offlineAreaFiles: OfflineAreaFiles
) : ViewModel() {

    // The download itself lives in a WorkManager job owned by the controller
    // (it must outlive this ViewModel and the screen), so these are views onto
    // its state rather than state of our own.
    val isDownloadingArea: StateFlow<Boolean> = tileDownloads.state
        .map { it.active }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tileDownloads.state.value.active)

    val downloadProgress: StateFlow<Float> = tileDownloads.state
        .map { it.progress }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tileDownloads.state.value.progress)

    val downloadText: StateFlow<String> = tileDownloads.state
        .map { it.text }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tileDownloads.state.value.text)

    private val _downloadEvent = MutableSharedFlow<DownloadEvent>()
    val downloadEvent: SharedFlow<DownloadEvent> = _downloadEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            tileDownloads.messages.collect { message ->
                _downloadEvent.emit(DownloadEvent.ToastMessage(message.text, message.isLong))
            }
        }
    }

    // Tap on a managed area -> fly the map camera to its bbox.
    private val _flyToArea = MutableSharedFlow<DownloadedArea>(extraBufferCapacity = 1)
    val flyToArea: SharedFlow<DownloadedArea> = _flyToArea.asSharedFlow()

    // Tap on a search hit -> fly the map camera to the zone's bbox.
    private val _flyToZone = MutableSharedFlow<ZoneBounds>(extraBufferCapacity = 1)
    val flyToZone: SharedFlow<ZoneBounds> = _flyToZone.asSharedFlow()

    // "Szukaj strefy" full-screen overlay (same lifetime rules as
    // `showOfflineAreas` below — must survive switching the bottom tab).
    private val _showZoneSearch = MutableStateFlow(false)
    val showZoneSearch: StateFlow<Boolean> = _showZoneSearch.asStateFlow()

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

    // "Pobrane obszary" is a full-screen map overlay whose open state must
    // survive switching to the Status tab and back (ViewModel scope, not a
    // MapViewContainer-local remember which is disposed with the map).
    private val _showOfflineAreas = MutableStateFlow(false)
    val showOfflineAreas: StateFlow<Boolean> = _showOfflineAreas.asStateFlow()

    val offlineAreas: StateFlow<List<DownloadedArea>> = offlineAreaRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun downloadMapArea(
        latSouth: Double, latNorth: Double,
        lonWest: Double, lonEast: Double
    ) {
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
        tileDownloads.cancel()
    }

    private fun startDownload(region: Region) {
        tileDownloads.startDownload(region, OfflineLimits.MIN_ZOOM, OfflineLimits.MAX_ZOOM)
    }

    fun refreshArea(area: DownloadedArea) {
        tileDownloads.startRefresh(area)
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

    fun openZoneSearch() {
        _showZoneSearch.value = true
    }

    fun closeZoneSearch() {
        _showZoneSearch.value = false
    }

    fun focusZone(bounds: ZoneBounds) {
        _flyToZone.tryEmit(bounds)
    }

    fun offlineFilePath(fileName: String): String = offlineAreaFiles.filePath(fileName)

    fun offlineFileExists(fileName: String): Boolean =
        File(offlineAreaFiles.filePath(fileName)).isFile
}
