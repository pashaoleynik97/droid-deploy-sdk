package com.pashaoleynik.droiddeploy.di

import android.content.Context
import com.pashaoleynik.droiddeploy.DroidDeployConfig
import com.pashaoleynik.droiddeploy.VersionCodeProvider
import com.pashaoleynik.droiddeploy.install.ApkDownloader
import com.pashaoleynik.droiddeploy.install.PackageInstallerInstaller
import com.pashaoleynik.droiddeploy.logs.Logger
import com.pashaoleynik.droiddeploy.network.ApiKeyAuthenticator
import com.pashaoleynik.droiddeploy.network.AuthHeaderInterceptor
import com.pashaoleynik.droiddeploy.network.DroidDeployApi
import com.pashaoleynik.droiddeploy.network.HostSelectionInterceptor
import com.pashaoleynik.droiddeploy.network.HostStore
import com.pashaoleynik.droiddeploy.network.InMemoryTokenStore
import com.pashaoleynik.droiddeploy.network.OkHttpLoggingInterceptor
import com.pashaoleynik.droiddeploy.updates.DefaultVersionCodeProvider
import com.pashaoleynik.droiddeploy.updates.UpdateManager
import com.pashaoleynik.droiddeploy.updates.UpdatesRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

internal class SdkContainer(
    private val appContext: Context,
    private val config: DroidDeployConfig
) {
    // Logger
    val logger = Logger(config.debugLogsEnabled, config.debugLogsListener)

    // Network stores
    val hostStore = HostStore(config.host)
    val tokenStore = InMemoryTokenStore()

    // Moshi
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // OkHttp client with interceptors and authenticator
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HostSelectionInterceptor(hostStore))
        .addInterceptor(AuthHeaderInterceptor(tokenStore))
        .apply {
            if (config.debugLogsEnabled) {
                addInterceptor(OkHttpLoggingInterceptor(logger))
            }
        }
        .authenticator(
            ApiKeyAuthenticator(
                baseUrlProvider = { hostStore.get() },
                apiKeyProvider = { config.apiKey },
                tokenStore = tokenStore,
                logger = logger
            )
        )
        .build()

    // Retrofit
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(hostStore.get())
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    // API
    val api: DroidDeployApi = retrofit.create(DroidDeployApi::class.java)

    // Version code provider
    val versionCodeProvider: VersionCodeProvider = config.versionCodeProvider ?: DefaultVersionCodeProvider()

    // Repository
    val updatesRepository = UpdatesRepository(
        appContext = appContext,
        api = api,
        applicationIdProvider = { config.applicationId },
        versionCodeProvider = { versionCodeProvider(appContext) },
        logger = logger,
        fetchIntervalMillis = config.fetchIntervalMillis
    )

    // Downloader and Installer
    val downloader = ApkDownloader(appContext, api, logger)
    val installer = PackageInstallerInstaller(appContext, logger)

    // Update Manager
    val updateManager = UpdateManager(
        updatesRepository = updatesRepository,
        downloader = downloader,
        installer = installer,
        logger = logger,
        applicationIdProvider = { config.applicationId }
    )
}
