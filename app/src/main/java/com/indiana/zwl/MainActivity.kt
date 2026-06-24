package com.indiana.zwl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.indiana.zwl.presentation.MainScreen
import com.indiana.zwl.presentation.MainViewModel
import com.indiana.zwl.presentation.map.MapViewContainer
import dagger.hilt.android.AndroidEntryPoint

import androidx.compose.runtime.DisposableEffect

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

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

            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "main") {
                composable("main") {
                    MainScreen(
                        viewModel = viewModel,
                        onNavigateToMap = {
                            if (navController.currentDestination?.route == "main") {
                                navController.navigate("map")
                            }
                        }
                    )
                }
                composable("map") {
                    MapViewContainer(
                        viewModel = viewModel,
                        zones = viewModel.zones,
                        onCloseMap = {
                            if (navController.currentDestination?.route == "map") {
                                navController.popBackStack()
                            }
                        }
                    )
                }
            }
        }
    }
}
