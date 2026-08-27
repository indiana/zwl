package com.indiana.zwl.shared.di

import com.indiana.zwl.shared.data.local.DatabaseDriverFactory
import com.indiana.zwl.shared.data.offline.IosMbtilesStore
import com.indiana.zwl.shared.data.remote.HttpClientFactory
import com.indiana.zwl.shared.offline.MbtilesStore
import org.koin.core.module.Module
import org.koin.dsl.module

fun iosModule(cacheDirectory: String): Module = module {
    single { DatabaseDriverFactory() }
    single { HttpClientFactory() }
    single<MbtilesStore> { IosMbtilesStore() }
}