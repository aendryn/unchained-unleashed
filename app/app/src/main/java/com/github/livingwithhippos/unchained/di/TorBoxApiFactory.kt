package com.github.livingwithhippos.unchained.di

import com.github.livingwithhippos.unchained.data.remote.TorBoxApi
import com.github.livingwithhippos.unchained.data.remote.TorBoxApiHelper
import com.github.livingwithhippos.unchained.data.remote.TorBoxApiHelperImpl
import com.github.livingwithhippos.unchained.utilities.TORBOX_BASE_URL
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

/**
 * Provides the TorBox networking stack. It reuses the [ClassicClient] OkHttpClient declared in
 * [ApiFactory] (same TLS/logging/empty-body setup) but points at the TorBox base URL.
 */
@InstallIn(SingletonComponent::class)
@Module
object TorBoxApiFactory {

    @Provides
    @Singleton
    @TorBoxRetrofit
    fun provideTorBoxRetrofit(@ClassicClient okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl(TORBOX_BASE_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(MoshiConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideTorBoxApi(@TorBoxRetrofit retrofit: Retrofit): TorBoxApi =
        retrofit.create(TorBoxApi::class.java)

    @Provides
    @Singleton
    fun provideTorBoxApiHelper(helper: TorBoxApiHelperImpl): TorBoxApiHelper = helper
}
