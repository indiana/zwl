package com.indiana.zwl.di

import com.indiana.zwl.domain.usecase.GetForestStandUseCase
import com.indiana.zwl.shared.data.remote.BdlArcgisApi
import com.indiana.zwl.shared.data.remote.BdlFireApi
import com.indiana.zwl.shared.data.remote.BdlOgcApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.koin.java.KoinJavaComponent.get
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideBdlFireApi(): BdlFireApi {
        return get(BdlFireApi::class.java)
    }

    @Provides
    @Singleton
    fun provideBdlArcgisApi(): BdlArcgisApi {
        return get(BdlArcgisApi::class.java)
    }

    @Provides
    @Singleton
    fun provideBdlOgcApi(): BdlOgcApi {
        return get(BdlOgcApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGetForestStandUseCase(ogcApi: BdlOgcApi): GetForestStandUseCase {
        return GetForestStandUseCase(ogcApi)
    }
}
