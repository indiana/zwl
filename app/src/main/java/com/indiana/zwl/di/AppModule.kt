package com.indiana.zwl.di

import android.content.Context
import com.indiana.zwl.data.repository.CompassRepositoryImpl
import com.indiana.zwl.data.repository.LocationRepositoryImpl
import com.indiana.zwl.domain.SpatialEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideLocationRepository(@ApplicationContext context: Context): LocationRepositoryImpl {
        return LocationRepositoryImpl(context)
    }

    @Provides
    @Singleton
    fun provideCompassRepository(@ApplicationContext context: Context): CompassRepositoryImpl {
        return CompassRepositoryImpl(context)
    }

    @Provides
    @Singleton
    fun provideSpatialEngine(): SpatialEngine {
        return SpatialEngine()
    }
}
