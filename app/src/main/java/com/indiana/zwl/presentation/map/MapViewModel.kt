package com.indiana.zwl.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.indiana.zwl.presentation.DownloadEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.layer.cache.TileCache
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    private val _isDownloadingArea = MutableStateFlow(false)
    val isDownloadingArea: StateFlow<Boolean> = _isDownloadingArea

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress

    private val _downloadText = MutableStateFlow("")
    val downloadText: StateFlow<String> = _downloadText

    private val _downloadEvent = MutableSharedFlow<DownloadEvent>()
    val downloadEvent = _downloadEvent.asSharedFlow()

    var savedMapCenter: LatLong? = null
        private set
    var savedMapZoom: Byte? = null
        private set

    fun saveMapState(center: LatLong?, zoom: Byte?) {
        if (center != null) savedMapCenter = center
        if (zoom != null) savedMapZoom = zoom
    }

    fun downloadMapArea(bbox: BoundingBox, tileSize: Int, tileCache: TileCache) {
        viewModelScope.launch {
            OfflineMapDownloader.downloadArea(bbox, tileSize, tileCache, okHttpClient).collect { status ->
                when (status) {
                    is DownloadStatus.Start -> {
                        _isDownloadingArea.value = true
                        _downloadProgress.value = 0f
                        _downloadText.value = "Rozpoczynanie pobierania..."
                    }
                    is DownloadStatus.Progress -> {
                        _downloadProgress.value = status.progress
                        _downloadText.value = status.text
                    }
                    is DownloadStatus.Finished -> {
                        _isDownloadingArea.value = false
                        _downloadEvent.emit(DownloadEvent.ToastMessage(
                            "Pobrano pomyślnie ${status.successCount} z ${status.total} kafelków do cache offline!",
                            isLong = true
                        ))
                    }
                    is DownloadStatus.Message -> {
                        _isDownloadingArea.value = false
                        _downloadEvent.emit(DownloadEvent.ToastMessage(status.msg, isLong = true))
                    }
                }
            }
        }
    }
}
