package com.indiana.zwl.shared.ios

import com.indiana.zwl.shared.di.databaseModule
import com.indiana.zwl.shared.di.iosModule
import com.indiana.zwl.shared.di.repositoryModule
import com.indiana.zwl.shared.di.sharedModule
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(
    cacheDirectory: String,
    appDeclaration: KoinAppDeclaration = {}
) = startKoin {
    appDeclaration()
    modules(sharedModule, databaseModule, repositoryModule, iosModule(cacheDirectory))
}

fun koin() = GlobalContext.get().koin

object IosAppBootstrap {
    fun setup(cacheDirectory: String): ForestApp {
        initKoin(cacheDirectory = cacheDirectory)
        return ForestAppFactory.create()
    }
}