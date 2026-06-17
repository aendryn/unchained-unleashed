package com.github.livingwithhippos.unchained.di

import android.content.SharedPreferences
import com.github.livingwithhippos.unchained.BuildConfig
import com.github.livingwithhippos.unchained.data.model.EmptyBodyInterceptor
import com.github.livingwithhippos.unchained.data.remote.AuthenticationApi
import com.github.livingwithhippos.unchained.data.remote.CustomDownload
import com.github.livingwithhippos.unchained.data.remote.DownloadApi
import com.github.livingwithhippos.unchained.data.remote.HostsApi
import com.github.livingwithhippos.unchained.data.remote.StreamingApi
import com.github.livingwithhippos.unchained.data.remote.TorBoxApi
import com.github.livingwithhippos.unchained.data.remote.TorBoxApiHelper
import com.github.livingwithhippos.unchained.data.remote.TorBoxApiHelperImpl
import com.github.livingwithhippos.unchained.data.remote.TorrentsApi
import com.github.livingwithhippos.unchained.data.remote.UnrestrictApi
import com.github.livingwithhippos.unchained.data.remote.UserApi
import com.github.livingwithhippos.unchained.data.remote.VariousApi
import com.github.livingwithhippos.unchained.plugins.Parser
import com.github.livingwithhippos.unchained.utilities.BASE_AUTH_URL
import com.github.livingwithhippos.unchained.utilities.BASE_URL
import com.github.livingwithhippos.unchained.utilities.TORBOX_BASE_URL
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.net.InetAddress
import javax.inject.Singleton
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

/** This object manages the Dagger-Hilt injection for the OkHttp and Retrofit clients */
@InstallIn(SingletonComponent::class)
@Module
object ApiFactory {

    @Provides
    @Singleton
    @ClassicClient
    fun provideOkHttpClient(): OkHttpClient {
        if (BuildConfig.DEBUG) {
            val logInterceptor: HttpLoggingInterceptor =
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
            // HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.HEADERS }

            return OkHttpClient()
                .newBuilder()
                // should fix the javax.net.ssl.SSLHandshakeException: Failure in SSL library
                .connectionSpecs(
                    listOf(
                        ConnectionSpec.CLEARTEXT,
                        ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                            .allEnabledTlsVersions()
                            .allEnabledCipherSuites()
                            .build(),
                    )
                )
                // logs all the calls, removed in the release channel
                .addInterceptor(logInterceptor)
                // avoid issues with empty bodies on delete/put and 20x return codes
                .addInterceptor(EmptyBodyInterceptor)
                .build()
        } else
            return OkHttpClient()
                .newBuilder()
                .connectionSpecs(
                    listOf(
                        ConnectionSpec.CLEARTEXT,
                        ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                            .allEnabledTlsVersions()
                            .allEnabledCipherSuites()
                            .build(),
                    )
                )
                // avoid issues with empty bodies on delete/put and 20x return codes
                .addInterceptor(EmptyBodyInterceptor)
                .build()
    }

    /**
     * examples:
     * [https://github.com/square/okhttp/blob/master/okhttp-dnsoverhttps/src/test/java/okhttp3/dnsoverhttps/DohProviders.java]
     * list: [https://github.com/curl/curl/wiki/DNS-over-HTTPS]
     *
     * @return
     */
    @Provides
    @Singleton
    @DOHClient
    fun provideDOHClient(preferences: SharedPreferences): OkHttpClient {

        val bootstrapClient: OkHttpClient =
            if (BuildConfig.DEBUG) {

                val logInterceptor: HttpLoggingInterceptor =
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
                // HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.HEADERS }

                OkHttpClient()
                    .newBuilder()
                    .connectionSpecs(
                        listOf(
                            ConnectionSpec.CLEARTEXT,
                            ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                                .allEnabledTlsVersions()
                                .allEnabledCipherSuites()
                                .build(),
                        )
                    )
                    // logs all the calls, removed in the release channel
                    .addInterceptor(logInterceptor)
                    // avoid issues with empty bodies on delete/put and 20x return codes
                    .addInterceptor(EmptyBodyInterceptor)
                    .build()
            } else {
                OkHttpClient()
                    .newBuilder()
                    .connectionSpecs(
                        listOf(
                            ConnectionSpec.CLEARTEXT,
                            ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                                .allEnabledTlsVersions()
                                .allEnabledCipherSuites()
                                .build(),
                        )
                    )
                    .addInterceptor(EmptyBodyInterceptor)
                    .build()
            }

        val dohProvider = preferences.getString("doh_provider", "quad9") ?: "quad9"

        val dns =
            when (dohProvider) {
                "google" ->
                    DnsOverHttps.Builder()
                        .client(bootstrapClient)
                        .url("https://dns.google/dns-query".toHttpUrl())
                        .bootstrapDnsHosts(
                            InetAddress.getByName("8.8.8.8"),
                            InetAddress.getByName("8.8.4.4"),
                        )
                        // we noticed ipv6 was checked first and then failed for a plugin
                        .includeIPv6(false)
                        .build()
                "cloudflare" ->
                    DnsOverHttps.Builder()
                        .client(bootstrapClient)
                        .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                        .bootstrapDnsHosts(InetAddress.getByName("1.1.1.1"))
                        .includeIPv6(false)
                        .build()
                "quad9" ->
                    DnsOverHttps.Builder()
                        .client(bootstrapClient)
                        .url("https://dns.quad9.net/dns-query".toHttpUrl())
                        .bootstrapDnsHosts(
                            InetAddress.getByName("9.9.9.9"),
                            InetAddress.getByName("149.112.112.112"),
                        )
                        .includeIPv6(false)
                        .build()
                "mullvad" ->
                    DnsOverHttps.Builder()
                        .client(bootstrapClient)
                        .url("https://dns.mullvad.net/dns-query".toHttpUrl())
                        .bootstrapDnsHosts(InetAddress.getByName("194.242.2.2"))
                        .includeIPv6(false)
                        .build()
                else ->
                    DnsOverHttps.Builder()
                        .client(bootstrapClient)
                        .url("https://dns.quad9.net/dns-query".toHttpUrl())
                        .bootstrapDnsHosts(
                            InetAddress.getByName("9.9.9.9"),
                            InetAddress.getByName("149.112.112.112"),
                        )
                        .includeIPv6(false)
                        .build()
            }

        return bootstrapClient.newBuilder().dns(dns).build()
    }

    @Provides
    @Singleton
    @AuthRetrofit
    fun authRetrofit(@ClassicClient okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl(BASE_AUTH_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()

    @Provides
    @Singleton
    @ApiRetrofit
    fun apiRetrofit(@ClassicClient okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl(BASE_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }

    // authentication api injection
    @Provides
    @Singleton
    fun provideAuthenticationApi(@AuthRetrofit retrofit: Retrofit): AuthenticationApi {
        return retrofit.create(AuthenticationApi::class.java)
    }

    // user api injection
    @Provides
    @Singleton
    fun provideUserApi(@ApiRetrofit retrofit: Retrofit): UserApi {
        return retrofit.create(UserApi::class.java)
    }

    // unrestrict api injection
    @Provides
    @Singleton
    fun provideUnrestrictApi(@ApiRetrofit retrofit: Retrofit): UnrestrictApi {
        return retrofit.create(UnrestrictApi::class.java)
    }

    // streaming api injection
    @Provides
    @Singleton
    fun provideStreamingApi(@ApiRetrofit retrofit: Retrofit): StreamingApi {
        return retrofit.create(StreamingApi::class.java)
    }

    // torrent api injection
    @Provides
    @Singleton
    fun provideTorrentsApi(@ApiRetrofit retrofit: Retrofit): TorrentsApi {
        return retrofit.create(TorrentsApi::class.java)
    }

    // download api injection
    @Provides
    @Singleton
    fun provideDownloadsApi(@ApiRetrofit retrofit: Retrofit): DownloadApi {
        return retrofit.create(DownloadApi::class.java)
    }

    // hosts api injection
    @Provides
    @Singleton
    fun provideHostsApi(@ApiRetrofit retrofit: Retrofit): HostsApi {
        return retrofit.create(HostsApi::class.java)
    }

    // various api injection
    @Provides
    @Singleton
    fun provideVariousApi(@ApiRetrofit retrofit: Retrofit): VariousApi {
        return retrofit.create(VariousApi::class.java)
    }

    // custom download injection
    @Provides
    @Singleton
    fun provideCustomDownload(@ApiRetrofit retrofit: Retrofit): CustomDownload {
        return retrofit.create(CustomDownload::class.java)
    }

    /** Search Plugins stuff */
    @Provides
    @Singleton
    fun provideParser(
        preferences: SharedPreferences,
        @ClassicClient classicClient: OkHttpClient,
        @DOHClient dohClient: OkHttpClient,
    ): Parser = Parser(preferences, classicClient, dohClient)

    // TorBox networking: reuses the ClassicClient OkHttpClient but points at the TorBox base URL.
    // TorBoxApiHelper is kept (unlike the Real-Debrid helpers) because it does real work —
    // injecting the API version, multipart encoding and request wrapping — not pure delegation.
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
