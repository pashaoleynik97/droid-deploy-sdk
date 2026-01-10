package com.pashaoleynik.droiddeploy.updates

import android.content.Context
import com.pashaoleynik.droiddeploy.errors.DroidDeployError
import com.pashaoleynik.droiddeploy.logs.Logger
import com.pashaoleynik.droiddeploy.network.DroidDeployApi
import com.pashaoleynik.droiddeploy.network.VersionDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

internal class UpdatesRepository(
    private val appContext: Context,
    private val api: DroidDeployApi,
    private val applicationIdProvider: () -> String,
    private val versionCodeProvider: () -> Long?,
    private val logger: Logger,
    private val fetchIntervalMillis: Long
) {
    companion object {
        private const val TAG = "UpdatesRepository"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fetchMutex = Mutex()

    private val _updates = MutableStateFlow(
        DroidUpdate(
            available = false,
            installedVersionCode = versionCodeProvider(),
            latest = null,
            lastCheckedAtMillis = null,
            error = null
        )
    )
    val updates: StateFlow<DroidUpdate> = _updates.asStateFlow()

    private var periodicFetchJob: Job? = null

    fun startPeriodicFetch() {
        stopPeriodicFetch()
        periodicFetchJob = scope.launch {
            while (true) {
                delay(fetchIntervalMillis)
                logger.d(TAG, "Periodic fetch triggered")
                fetchLatestInternal()
            }
        }
        logger.d(TAG, "Started periodic fetch with interval ${fetchIntervalMillis}ms")
    }

    fun stopPeriodicFetch() {
        periodicFetchJob?.cancel()
        periodicFetchJob = null
        logger.d(TAG, "Stopped periodic fetch")
    }

    suspend fun forceFetch(): FetchResult {
        logger.d(TAG, "Force fetch requested")
        if (!fetchMutex.tryLock()) {
            logger.d(TAG, "Fetch already in progress")
            return FetchResult.InProgress
        }

        return try {
            fetchLatestInternal()
            FetchResult.Success(_updates.value)
        } catch (e: Exception) {
            logger.e(TAG, "Force fetch failed", e)
            val error = mapException(e)
            FetchResult.Failed(error)
        } finally {
            fetchMutex.unlock()
        }
    }

    private suspend fun fetchLatestInternal() {
        fetchMutex.withLock {
            try {
                logger.d(TAG, "Fetching latest version")
                val applicationId = applicationIdProvider()
                val installedVersionCode = versionCodeProvider()

                logger.d(TAG, "Application ID: $applicationId, Installed version: $installedVersionCode")

                val response = api.getLatestVersion(applicationId)

                if (response.isSuccessful) {
                    val restResponse = response.body()
                    if (restResponse != null && restResponse.success && restResponse.data != null) {
                        val latestVersion = restResponse.data
                        logger.d(TAG, "Latest version: ${latestVersion.versionCode}")

                        val available = isUpdateAvailable(installedVersionCode, latestVersion)

                        _updates.value = DroidUpdate(
                            available = available,
                            installedVersionCode = installedVersionCode,
                            latest = latestVersion,
                            lastCheckedAtMillis = System.currentTimeMillis(),
                            error = null
                        )

                        logger.d(TAG, "Update available: $available")
                    } else {
                        val error = DroidDeployError.Http(
                            code = response.code(),
                            message = restResponse?.message,
                            apiErrors = restResponse?.errors ?: emptyList()
                        )
                        updateWithError(error)
                    }
                } else {
                    val error = DroidDeployError.Http(
                        code = response.code(),
                        message = response.message(),
                        apiErrors = emptyList()
                    )
                    updateWithError(error)
                }
            } catch (e: Exception) {
                logger.e(TAG, "Error fetching latest version", e)
                val error = mapException(e)
                updateWithError(error)
            }
        }
    }

    private fun isUpdateAvailable(installedVersionCode: Long?, latestVersion: VersionDto): Boolean {
        val serverVersionCode = latestVersion.versionCode

        // If either version code is null, update is not available
        if (serverVersionCode == null || installedVersionCode == null) {
            return false
        }

        return serverVersionCode > installedVersionCode
    }

    private fun updateWithError(error: DroidDeployError) {
        _updates.value = _updates.value.copy(
            error = error,
            lastCheckedAtMillis = System.currentTimeMillis()
        )
    }

    private fun mapException(e: Exception): DroidDeployError {
        return when (e) {
            is IOException -> DroidDeployError.Network(e)
            else -> DroidDeployError.IllegalState(e.message ?: "Unknown error")
        }
    }
}
