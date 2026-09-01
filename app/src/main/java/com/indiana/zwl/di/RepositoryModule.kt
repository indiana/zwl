package com.indiana.zwl.di

import com.indiana.zwl.domain.repository.ForestBanRepository
import com.indiana.zwl.domain.repository.PoiRepository
import com.indiana.zwl.domain.repository.SavedPointRepository
import com.indiana.zwl.domain.repository.ZoneRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.koin.java.KoinJavaComponent.get
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideZoneRepository(): ZoneRepository {
        return get(ZoneRepository::class.java)
    }

    @Provides
    @Singleton
    fun providePoiRepository(): PoiRepository {
        return get(PoiRepository::class.java)
    }

    @Provides
    @Singleton
    fun provideForestBanRepository(): ForestBanRepository {
        return get(ForestBanRepository::class.java)
    }

    @Provides
    @Singleton
    fun provideSavedPointRepository(): SavedPointRepository {
        return get(SavedPointRepository::class.java)
    }
}
