package com.indiana.zwl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.indiana.zwl.presentation.MainScreen
import com.indiana.zwl.presentation.MainViewModel
import com.indiana.zwl.presentation.ZoneDetailViewModel
import com.indiana.zwl.presentation.map.MapViewModel
import dagger.hilt.android.AndroidEntryPoint

import androidx.compose.runtime.DisposableEffect

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val zoneDetailViewModel: ZoneDetailViewModel by viewModels()
    private val mapViewModel: MapViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DisposableEffect(Unit) {
                viewModel.startTracking()

                try {
                    val file = java.io.File(cacheDir, "crash_log.txt")
                    if (file.exists()) {
                        val crashText = file.readText()
                        viewModel.setDebugError("Wykryto poprzednią awarię aplikacji (Crash Log):\n\n$crashText")
                        file.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                onDispose {
                    viewModel.stopTracking()
                }
            }

            MainScreen(
                viewModel = viewModel,
                zoneDetailViewModel = zoneDetailViewModel,
                mapViewModel = mapViewModel
            )
        }
    }
}
