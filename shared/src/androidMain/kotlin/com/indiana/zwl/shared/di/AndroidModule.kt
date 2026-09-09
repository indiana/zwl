package com.indiana.zwl.shared.di

import com.indiana.zwl.shared.data.local.DatabaseDriverFactory
import com.indiana.zwl.shared.data.remote.HttpClientFactory
import com.indiana.zwl.shared.offline.AndroidOfflineAreaFiles
import com.indiana.zwl.shared.offline.OfflineAreaFiles
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidModule = module {
    single { DatabaseDriverFactory(androidContext()) }
    single { HttpClientFactory(androidContext()) }
    single<OfflineAreaFiles> { AndroidOfflineAreaFiles(androidContext()) }
}
