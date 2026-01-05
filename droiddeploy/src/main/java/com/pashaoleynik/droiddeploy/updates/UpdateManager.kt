package com.pashaoleynik.droiddeploy.updates

import android.app.Activity
import com.pashaoleynik.droiddeploy.errors.DroidDeployError
import com.pashaoleynik.droiddeploy.install.ApkDownloader
import com.pashaoleynik.droiddeploy.install.DroidInstallState
import com.pashaoleynik.droiddeploy.install.InstallOptions
import com.pashaoleynik.droiddeploy.install.PackageInstallerInstaller
import com.pashaoleynik.droiddeploy.logs.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

internal class UpdateManager(
    private val updatesRepository: UpdatesRepository,
    private val downloader: ApkDownloader,
    private val installer: PackageInstallerInstaller,
    private val logger: Logger,
    private val applicationIdProvider: () -> String
) {
    companion object {
        private const val TAG = "UpdateManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val installMutex = Mutex()

    val updates: StateFlow<DroidUpdate> = updatesRepository.updates

    private val _installState = MutableStateFlow<DroidInstallState>(DroidInstallState.Idle)
    val installState: StateFlow<DroidInstallState> = _installState.asStateFlow()

    private var downloadedApkFile: File? = null

    fun startPeriodicFetch() {
        updatesRepository.startPeriodicFetch()
    }

    fun stopPeriodicFetch() {
        updatesRepository.stopPeriodicFetch()
    }

    suspend fun forceFetch(): FetchResult {
        return updatesRepository.forceFetch()
    }

    fun installLatest(activity: Activity, options: InstallOptions) {
        // Check if install is already in progress
        if (!installMutex.tryLock()) {
            logger.w(TAG, "Install already in progress")
            _installState.value = DroidInstallState.Failed(
                DroidDeployError.AlreadyInProgress("install")
            )
            return
        }

        scope.launch {
            try {
                _installState.value = DroidInstallState.Preparing
                logger.d(TAG, "Starting installation process")

                // Get latest version info
                val latestUpdate = updates.value.latest
                if (latestUpdate == null || latestUpdate.versionCode == null) {
                    logger.e(TAG, "No latest version available")
                    _installState.value = DroidInstallState.Failed(
                        DroidDeployError.IllegalState("No latest version available")
                    )
                    return@launch
                }

                val versionCode = latestUpdate.versionCode
                val applicationId = applicationIdProvider()

                logger.d(TAG, "Installing version $versionCode for application $applicationId")

                // Download APK
                _installState.value = DroidInstallState.Downloading(
                    progressPercent = 0,
                    bytesRead = 0,
                    totalBytes = null
                )

                val downloadResult = downloader.downloadApk(
                    applicationId = applicationId,
                    versionCode = versionCode,
                    options = options,
                    stateFlow = _installState
                )

                if (downloadResult.isFailure) {
                    logger.e(TAG, "Download failed", downloadResult.exceptionOrNull())
                    _installState.value = DroidInstallState.Failed(
                        DroidDeployError.Network(
                            downloadResult.exceptionOrNull() ?: Exception("Download failed")
                        )
                    )
                    return@launch
                }

                val apkFile = downloadResult.getOrNull()!!
                downloadedApkFile = apkFile

                logger.d(TAG, "APK downloaded successfully: ${apkFile.absolutePath}")
                _installState.value = DroidInstallState.Downloaded(apkFile)

                // Install APK
                _installState.value = DroidInstallState.Installing
                logger.d(TAG, "Starting installation")

                val installResult = installer.installApk(
                    activity = activity,
                    apkFile = apkFile,
                    stateFlow = _installState
                )

                if (installResult.isFailure) {
                    logger.e(TAG, "Installation failed", installResult.exceptionOrNull())
                    _installState.value = DroidInstallState.Failed(
                        DroidDeployError.IllegalState(
                            installResult.exceptionOrNull()?.message ?: "Installation failed"
                        )
                    )
                    cleanupApkFile(apkFile, options)
                    return@launch
                }

                // Installation is now in progress, the receiver will update the state
                // Clean up APK file after install if requested
                if (options.deleteApkAfterInstallAttempt) {
                    // Wait a bit for the installation to start
                    kotlinx.coroutines.delay(2000)
                    cleanupApkFile(apkFile, options)
                }

            } catch (e: Exception) {
                logger.e(TAG, "Error during installation", e)
                _installState.value = DroidInstallState.Failed(
                    DroidDeployError.IllegalState(e.message ?: "Unknown error")
                )
            } finally {
                installMutex.unlock()
            }
        }
    }

    private fun cleanupApkFile(apkFile: File, options: InstallOptions) {
        if (options.deleteApkAfterInstallAttempt && apkFile.exists()) {
            try {
                apkFile.delete()
                logger.d(TAG, "Cleaned up APK file: ${apkFile.absolutePath}")
            } catch (e: Exception) {
                logger.w(TAG, "Failed to delete APK file", e)
            }
        }
    }
}
