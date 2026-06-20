package com.indiana.zwl.di

import android.content.Context
import com.indiana.zwl.data.repository.CompassRepositoryImpl
import com.indiana.zwl.data.repository.LocationRepositoryImpl
import com.indiana.zwl.data.repository.MotionDetector
import com.indiana.zwl.domain.CompassRepository
import com.indiana.zwl.domain.LocationRepository
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
    fun provideMotionDetector(@ApplicationContext context: Context): MotionDetector {
        return MotionDetector(context)
    }

    @Provides
    @Singleton
    fun provideLocationRepository(
        @ApplicationContext context: Context,
        motionDetector: MotionDetector
    ): LocationRepository {
        return LocationRepositoryImpl(context, motionDetector)
    }

    @Provides
    @Singleton
    fun provideCompassRepository(@ApplicationContext context: Context): CompassRepository {
        return CompassRepositoryImpl(context)
    }

    @Provides
    @Singleton
    fun provideSpatialEngine(): SpatialEngine {
        return SpatialEngine()
    }
}
