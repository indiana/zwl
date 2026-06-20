package com.indiana.zwl.di

import android.content.Context
import com.indiana.zwl.data.local.ZoneDao
import com.indiana.zwl.data.local.ZwlDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideZwlDatabase(@ApplicationContext context: Context): ZwlDatabase {
        return ZwlDatabase.getDatabase(context)
    }

    @Provides
    fun provideZoneDao(database: ZwlDatabase): ZoneDao {
        return database.zoneDao()
    }
}
