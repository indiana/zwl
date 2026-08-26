package com.indiana.zwl.di

import com.indiana.zwl.shared.data.local.SharedDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.koin.java.KoinJavaComponent.get
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSharedDatabase(): SharedDatabase {
        return get(SharedDatabase::class.java)
    }
}
