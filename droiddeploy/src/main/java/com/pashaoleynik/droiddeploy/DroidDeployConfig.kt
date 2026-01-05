package com.pashaoleynik.droiddeploy

import android.content.Context
import com.pashaoleynik.droiddeploy.logs.DroidDeployLog
import java.util.concurrent.TimeUnit

class DroidDeployConfig internal constructor() {
    internal var apiKey: String = ""
        private set
    internal var applicationId: String = ""
        private set
    internal var host: String = ""
        private set
    internal var fetchIntervalMillis: Long = TimeUnit.MINUTES.toMillis(30)
        private set
    internal var debugLogsEnabled: Boolean = false
        private set
    internal var debugLogsListener: ((DroidDeployLog) -> Unit)? = null
        private set
    internal var versionCodeProvider: VersionCodeProvider? = null
        private set

    fun setApiKey(apiKey: String) {
        this.apiKey = apiKey
    }

    fun setApplicationId(applicationId: String) {
        this.applicationId = applicationId
    }

    fun setHost(host: String) {
        this.host = host.removeSuffix("/")
    }

    fun setFetchInterval(intervalMillis: Long) {
        this.fetchIntervalMillis = intervalMillis
    }

    fun setDebugLogsEnabled(enabled: Boolean) {
        this.debugLogsEnabled = enabled
    }

    fun setDebugLogsListener(listener: (DroidDeployLog) -> Unit) {
        this.debugLogsListener = listener
    }

    fun setVersionCodeProvider(provider: VersionCodeProvider) {
        this.versionCodeProvider = provider
    }
}

typealias VersionCodeProvider = (Context) -> Long?
