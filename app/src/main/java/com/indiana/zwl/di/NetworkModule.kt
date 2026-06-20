package com.indiana.zwl.di

import com.indiana.zwl.data.remote.BdlArcgisApi
import com.indiana.zwl.data.remote.BdlFireApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://mapserver.bdl.lasy.gov.pl/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideBdlFireApi(retrofit: Retrofit): BdlFireApi {
        return retrofit.create(BdlFireApi::class.java)
    }

    @Provides
    @Singleton
    fun provideBdlArcgisApi(retrofit: Retrofit): BdlArcgisApi {
        return retrofit.create(BdlArcgisApi::class.java)
    }
}
