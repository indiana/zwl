package com.indiana.zwl.di

import com.indiana.zwl.data.repository.ForestBanRepositoryImpl
import com.indiana.zwl.data.repository.PoiRepositoryImpl
import com.indiana.zwl.data.repository.ZoneRepositoryImpl
import com.indiana.zwl.domain.repository.ForestBanRepository
import com.indiana.zwl.domain.repository.PoiRepository
import com.indiana.zwl.domain.repository.ZoneRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindZoneRepository(impl: ZoneRepositoryImpl): ZoneRepository

    @Binds
    @Singleton
    abstract fun bindPoiRepository(impl: PoiRepositoryImpl): PoiRepository

    @Binds
    @Singleton
    abstract fun bindForestBanRepository(impl: ForestBanRepositoryImpl): ForestBanRepository
}
