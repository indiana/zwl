package com.indiana.zwl.shared.ios

import com.indiana.zwl.domain.repository.ForestBanRepository
import com.indiana.zwl.domain.repository.PoiRepository
import com.indiana.zwl.domain.repository.ZoneRepository
import com.indiana.zwl.shared.data.remote.BdlArcgisApi
import com.indiana.zwl.shared.data.remote.BdlFireApi
import com.indiana.zwl.shared.di.databaseModule
import com.indiana.zwl.shared.di.iosModule
import com.indiana.zwl.shared.di.repositoryModule
import com.indiana.zwl.shared.di.sharedModule
import com.indiana.zwl.shared.offline.MbtilesStore
import io.ktor.client.HttpClient
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(
    cacheDirectory: String,
    appDeclaration: KoinAppDeclaration = {}
): Koin = startKoin {
    appDeclaration()
    modules(sharedModule, databaseModule, repositoryModule, iosModule(cacheDirectory))
}.koin

object IosAppBootstrap {
    fun setup(cacheDirectory: String): ForestApp {
        val koin = initKoin(cacheDirectory = cacheDirectory)
        return ForestApp(
            zoneRepository = koin.get<ZoneRepository>(),
            poiRepository = koin.get<PoiRepository>(),
            forestBanRepository = koin.get<ForestBanRepository>(),
            arcgisApi = koin.get<BdlArcgisApi>(),
            fireApi = koin.get<BdlFireApi>(),
            offlineStore = koin.get<MbtilesStore>(),
            httpClient = koin.get<HttpClient>()
        )
    }
}