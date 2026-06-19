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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = ZwlDatabase.getDatabase(applicationContext)
        val zoneDao = database.zoneDao()

        val locationRepository = LocationRepositoryImpl(applicationContext)
        val compassRepository = CompassRepositoryImpl(applicationContext)

        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://mapserver.bdl.lasy.gov.pl/")
            .client(okHttpClient)
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
