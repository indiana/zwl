package com.example.zwl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.zwl.data.local.ZwlDatabase
import com.example.zwl.data.remote.BdlArcgisApi
import com.example.zwl.data.remote.BdlFireApi
import com.example.zwl.data.repository.CompassRepositoryImpl
import com.example.zwl.data.repository.LocationRepositoryImpl
import com.example.zwl.domain.SpatialEngine
import com.example.zwl.presentation.MainScreen
import com.example.zwl.presentation.MainViewModel
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = ZwlDatabase.getDatabase(applicationContext)
        val zoneDao = database.zoneDao()

        val locationRepository = LocationRepositoryImpl(applicationContext)
        val compassRepository = CompassRepositoryImpl(applicationContext)

        val retrofit = Retrofit.Builder()
            .baseUrl("https://wfs.bdl.lasy.gov.pl/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val fireApi = retrofit.create(BdlFireApi::class.java)
        val arcgisApi = retrofit.create(BdlArcgisApi::class.java)

        val spatialEngine = SpatialEngine()

        val viewModelFactory = MainViewModel.Factory(
            zoneDao = zoneDao,
            locationRepository = locationRepository,
            compassRepository = compassRepository,
            fireApi = fireApi,
            arcgisApi = arcgisApi,
            spatialEngine = spatialEngine
        )
        val viewModel: MainViewModel by viewModels { viewModelFactory }

        setContent {
            MainScreen(viewModel = viewModel)
        }
    }
}
