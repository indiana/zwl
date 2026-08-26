package com.indiana.zwl.shared.di

import com.indiana.zwl.shared.data.local.DatabaseDriverFactory
import com.indiana.zwl.shared.data.local.SharedDatabase
import com.indiana.zwl.shared.data.remote.BdlArcgisApi
import com.indiana.zwl.shared.data.remote.BdlFireApi
import com.indiana.zwl.shared.data.remote.BdlOgcApi
import com.indiana.zwl.shared.data.remote.HttpClientFactory
import org.koin.dsl.module

val sharedModule = module {
    single { get<HttpClientFactory>().create() }
    single { BdlArcgisApi(get()) }
    single { BdlFireApi(get()) }
    single { BdlOgcApi(get()) }
}

val databaseModule = module {
    single { get<DatabaseDriverFactory>().createDriver() }
    single { SharedDatabase(get()) }
}
