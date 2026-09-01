package com.indiana.zwl.shared.di

import com.indiana.zwl.domain.repository.ForestBanRepository
import com.indiana.zwl.domain.repository.PoiRepository
import com.indiana.zwl.domain.repository.SavedPointRepository
import com.indiana.zwl.domain.repository.ZoneRepository
import com.indiana.zwl.domain.usecase.GetForestStandUseCase
import com.indiana.zwl.shared.data.local.DatabaseDriverFactory
import com.indiana.zwl.shared.data.local.SharedDatabase
import com.indiana.zwl.shared.data.remote.BdlArcgisApi
import com.indiana.zwl.shared.data.remote.BdlFireApi
import com.indiana.zwl.shared.data.remote.BdlOgcApi
import com.indiana.zwl.shared.data.remote.HttpClientFactory
import com.indiana.zwl.shared.data.repository.ForestBanRepositoryImpl
import com.indiana.zwl.shared.data.repository.PoiRepositoryImpl
import com.indiana.zwl.shared.data.repository.SavedPointRepositoryImpl
import com.indiana.zwl.shared.data.repository.ZoneRepositoryImpl
import org.koin.dsl.module

val sharedModule = module {
    single { get<HttpClientFactory>().create() }
    single { BdlArcgisApi(get()) }
    single { BdlFireApi(get()) }
    single { BdlOgcApi(get()) }
    single { GetForestStandUseCase(get()) }
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
}
