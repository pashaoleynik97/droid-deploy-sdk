package com.pashaoleynik.droiddeploy

import android.app.Activity
import android.content.Context
import com.pashaoleynik.droiddeploy.di.SdkContainer
import com.pashaoleynik.droiddeploy.install.DroidInstallState
import com.pashaoleynik.droiddeploy.install.InstallOptions
import com.pashaoleynik.droiddeploy.updates.DroidUpdate
import com.pashaoleynik.droiddeploy.updates.FetchResult
import kotlinx.coroutines.flow.StateFlow

object DroidDeploy {
    private const val TAG = "DroidDeploy"

    @Volatile
    private var container: SdkContainer? = null

    fun init(context: Context, block: DroidDeployConfig.() -> Unit) {
        // Prevent double initialization
        if (container != null) {
            throw IllegalStateException("DroidDeploy is already initialized. Call init() only once.")
        }

        val config = DroidDeployConfig().apply(block)

        // Validate configuration
        require(config.apiKey.isNotEmpty()) { "API key must not be empty" }
        require(config.applicationId.isNotEmpty()) { "Application ID must not be empty" }
        require(config.host.isNotEmpty()) { "Host must not be empty" }

        // Create container
        container = SdkContainer(context.applicationContext, config)

        // Start periodic fetch
        requireContainer().updateManager.startPeriodicFetch()

        // Log initialization
        requireContainer().logger.i(TAG, "DroidDeploy SDK initialized successfully")
    }

    val updates: StateFlow<DroidUpdate>
        get() = requireContainer().updateManager.updates

    val installState: StateFlow<DroidInstallState>
        get() = requireContainer().updateManager.installState

    suspend fun forceFetch(): FetchResult {
        return requireContainer().updateManager.forceFetch()
    }

    fun installLatest(activity: Activity, options: InstallOptions = InstallOptions()) {
        requireContainer().updateManager.installLatest(activity, options)
    }

    private fun requireContainer(): SdkContainer {
        return container ?: throw IllegalStateException(
            "DroidDeploy is not initialized. Call DroidDeploy.init() first in your Application.onCreate()"
        )
    }

    // Internal method to stop periodic fetch (for cleanup or testing)
    internal fun stopPeriodicFetch() {
        container?.updateManager?.stopPeriodicFetch()
    }
}
