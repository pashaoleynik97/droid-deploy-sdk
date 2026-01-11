package com.pashaoleynik.droiddeploy.install

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.pashaoleynik.droiddeploy.errors.DroidDeployError
import com.pashaoleynik.droiddeploy.logs.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

internal class PackageInstallerInstaller(
    private val appContext: Context,
    private val logger: Logger
) {
    companion object {
        private const val TAG = "PackageInstallerInstaller"
        const val INSTALL_ACTION = "com.pashaoleynik.droiddeploy.INSTALL_ACTION"
        private const val BUFFER_SIZE = 8192
    }

    suspend fun installApk(
        activity: Activity,
        apkFile: File,
        stateFlow: MutableStateFlow<DroidInstallState>,
        options: InstallOptions
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            logger.d(TAG, "Starting APK installation: ${apkFile.absolutePath}")

            if (!apkFile.exists()) {
                logger.e(TAG, "APK file does not exist")
                return@withContext Result.failure(
                    Exception(DroidDeployError.IllegalState("APK file does not exist").toString())
                )
            }

            val packageInstaller = appContext.packageManager.packageInstaller

            // Create session parameters
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(appContext.packageName)

            // Create session
            val sessionId = packageInstaller.createSession(params)
            logger.d(TAG, "Created installation session: $sessionId")

            val session = packageInstaller.openSession(sessionId)

            try {
                // Write APK to session
                val outputStream = session.openWrite("base.apk", 0, -1)
                val inputStream = FileInputStream(apkFile)

                try {
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read: Int

                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }

                    session.fsync(outputStream)
                    logger.d(TAG, "APK written to session successfully")
                } finally {
                    outputStream.close()
                    inputStream.close()
                }

                // Create pending intent for the result
                val intent = Intent(INSTALL_ACTION).apply {
                    setPackage(appContext.packageName)
                }

                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    appContext,
                    sessionId,
                    intent,
                    flags
                )

                // Register callback to listen for result
                DroidDeployInstallReceiver.registerCallback(
                    sessionId = sessionId,
                    autoRelaunch = options.autoRelaunchAfterInstall
                ) { status, message ->
                    logger.d(TAG, "Install status received: $status, message: $message")
                    when (status) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            logger.d(TAG, "User action required, checking for confirmation intent")
                            // The system wants to show the user a confirmation dialog
                            // This should happen automatically when session.commit() is called
                            stateFlow.value = DroidInstallState.Installing
                        }
                        PackageInstaller.STATUS_SUCCESS -> {
                            stateFlow.value = DroidInstallState.Installed
                        }
                        PackageInstaller.STATUS_FAILURE_ABORTED -> {
                            stateFlow.value = DroidInstallState.Cancelled("Installation aborted by user")
                        }
                        else -> {
                            val error = DroidDeployError.IllegalState(
                                message ?: "Installation failed with status: $status"
                            )
                            stateFlow.value = DroidInstallState.Failed(error)
                        }
                    }
                }

                // Commit the session
                logger.d(TAG, "Committing installation session")
                session.commit(pendingIntent.intentSender)

                Result.success(Unit)
            } catch (e: Exception) {
                logger.e(TAG, "Error during installation", e)
                session.abandon()
                Result.failure(e)
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error installing APK", e)
            Result.failure(e)
        }
    }
}
