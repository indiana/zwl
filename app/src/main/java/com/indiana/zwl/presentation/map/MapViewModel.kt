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
import java.io.File
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

    var savedMapCenterLat: Double? = null
        private set
    var savedMapCenterLng: Double? = null
        private set
    var savedMapZoom: Double? = null
        private set

    fun saveMapState(lat: Double?, lng: Double?, zoom: Double?) {
        if (lat != null) savedMapCenterLat = lat
        if (lng != null) savedMapCenterLng = lng
        if (zoom != null) savedMapZoom = zoom
    }

    fun downloadMapArea(
        latSouth: Double, latNorth: Double,
        lonWest: Double, lonEast: Double,
        cacheDir: File
    ) {
        viewModelScope.launch {
            OfflineMapDownloader.downloadArea(
                latSouth = latSouth, latNorth = latNorth,
                lonWest = lonWest, lonEast = lonEast,
                cacheDir = cacheDir,
                client = okHttpClient,
                onProgress = { progress, text ->
                    _isDownloadingArea.value = true
                    _downloadProgress.value = progress
                    _downloadText.value = text
                },
                onSuccess = { count ->
                    _isDownloadingArea.value = false
                    _downloadEvent.tryEmit(
                        DownloadEvent.ToastMessage(
                            "Pobrano pomyślnie $count kafelków do cache offline!",
                            isLong = true
                        )
                    )
                },
                onError = { msg ->
                    _isDownloadingArea.value = false
                    _downloadEvent.tryEmit(DownloadEvent.ToastMessage(msg, isLong = true))
                }
            )
        }
    }
}
