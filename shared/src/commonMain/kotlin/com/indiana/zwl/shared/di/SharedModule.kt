package com.indiana.zwl.shared.di

import com.indiana.zwl.domain.repository.ForestBanRepository
import com.indiana.zwl.domain.repository.OfflineAreaRepository
import com.indiana.zwl.domain.repository.PoiRepository
import com.indiana.zwl.domain.repository.SavedPointRepository
import com.indiana.zwl.domain.repository.WaterSourceRepository
import com.indiana.zwl.domain.repository.ZoneRepository
import com.indiana.zwl.domain.usecase.GetForestStandForPointUseCase
import com.indiana.zwl.domain.usecase.GetForestStandUseCase
import com.indiana.zwl.domain.usecase.GetSoilCoverForPointUseCase
import com.indiana.zwl.shared.data.local.DatabaseDriverFactory
import com.indiana.zwl.shared.data.local.SharedDatabase
import com.indiana.zwl.shared.data.remote.BdlArcgisApi
import com.indiana.zwl.shared.data.remote.BdlFireApi
import com.indiana.zwl.shared.data.remote.BdlOgcApi
import com.indiana.zwl.shared.data.remote.BdlStandDescriptionApi
import com.indiana.zwl.shared.data.remote.HttpClientFactory
import com.indiana.zwl.shared.data.repository.ForestBanRepositoryImpl
import com.indiana.zwl.shared.data.repository.OfflineAreaRepositoryImpl
import com.indiana.zwl.shared.data.repository.PoiRepositoryImpl
import com.indiana.zwl.shared.data.repository.SavedPointRepositoryImpl
import com.indiana.zwl.shared.data.repository.WaterSourceRepositoryImpl
import com.indiana.zwl.shared.data.repository.ZoneRepositoryImpl
import com.indiana.zwl.shared.data.water.WaterDataApi
import com.indiana.zwl.shared.data.water.WaterSyncManager
import com.indiana.zwl.shared.offline.OfflineAreaJanitor
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val sharedModule = module {
    single { get<HttpClientFactory>().create() }
    single { BdlArcgisApi(get()) }
    single { BdlFireApi(get()) }
    single { BdlOgcApi(get()) }
    single { BdlStandDescriptionApi(get()) }
    single { GetForestStandUseCase(get()) }
    single { GetSoilCoverForPointUseCase(get()) }
    single { GetForestStandForPointUseCase(get(), get()) }
    single { Json { ignoreUnknownKeys = true; isLenient = true } }
    single { WaterDataApi(get()) }
    single { WaterSyncManager(get(), get(), get(), get()) }
}

val databaseModule = module {
    single { get<DatabaseDriverFactory>().createDriver() }
    single { SharedDatabase(get()) }
}

val repositoryModule = module {
    single<ZoneRepository> { ZoneRepositoryImpl(get()) }
    single<ForestBanRepository> { ForestBanRepositoryImpl(get()) }
    single<PoiRepository> { PoiRepositoryImpl(get()) }
    single<SavedPointRepository> { SavedPointRepositoryImpl(get()) }
    single<OfflineAreaRepository> { OfflineAreaRepositoryImpl(get()) }
    single<WaterSourceRepository> { WaterSourceRepositoryImpl(get()) }
    single { OfflineAreaJanitor(get(), get()) }
}
