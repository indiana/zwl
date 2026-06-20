package com.indiana.zwl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.indiana.zwl.data.local.ZwlDatabase
import com.indiana.zwl.data.remote.BdlArcgisApi
import com.indiana.zwl.data.remote.BdlFireApi
import com.indiana.zwl.data.repository.CompassRepositoryImpl
import com.indiana.zwl.data.repository.LocationRepositoryImpl
import com.indiana.zwl.domain.SpatialEngine
import com.indiana.zwl.presentation.MainScreen
import com.indiana.zwl.presentation.MainViewModel
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MainScreen(viewModel = viewModel)
        }
    }
}
