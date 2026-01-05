package com.pashaoleynik.droiddeploy.install

import android.content.Context
import com.pashaoleynik.droiddeploy.errors.DroidDeployError
import com.pashaoleynik.droiddeploy.logs.Logger
import com.pashaoleynik.droiddeploy.network.DroidDeployApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal class ApkDownloader(
    private val appContext: Context,
    private val api: DroidDeployApi,
    private val logger: Logger
) {
    companion object {
        private const val TAG = "ApkDownloader"
        private const val BUFFER_SIZE = 8192
    }

    suspend fun downloadApk(
        applicationId: String,
        versionCode: Long,
        options: InstallOptions,
        stateFlow: MutableStateFlow<DroidInstallState>
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            logger.d(TAG, "Starting APK download for version $versionCode")

            // Prepare directory and file
            val downloadDir = File(appContext.cacheDir, "droiddeploy")
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            val apkFile = File(downloadDir, "${options.apkFileNamePrefix}-$applicationId-$versionCode.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            logger.d(TAG, "Download path: ${apkFile.absolutePath}")

            // Make API call
            val response = api.downloadApk(applicationId, versionCode)

            if (!response.isSuccessful) {
                logger.e(TAG, "Download failed with code: ${response.code()}")
                return@withContext Result.failure(
                    Exception(
                        DroidDeployError.Http(
                            code = response.code(),
                            message = response.message(),
                            apiErrors = emptyList()
                        ).toString()
                    )
                )
            }

            val responseBody = response.body()
            if (responseBody == null) {
                logger.e(TAG, "Response body is null")
                return@withContext Result.failure(
                    Exception(DroidDeployError.IllegalState("Response body is null").toString())
                )
            }

            // Get total size
            val totalBytes = responseBody.contentLength()
            logger.d(TAG, "Total bytes to download: $totalBytes")

            // Download with progress
            val inputStream = responseBody.byteStream()
            val outputStream = FileOutputStream(apkFile)

            try {
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Long = 0
                var read: Int

                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                    bytesRead += read

                    // Emit progress
                    val progressPercent = if (totalBytes > 0) {
                        ((bytesRead * 100) / totalBytes).toInt()
                    } else {
                        null
                    }

                    stateFlow.value = DroidInstallState.Downloading(
                        progressPercent = progressPercent,
                        bytesRead = bytesRead,
                        totalBytes = if (totalBytes > 0) totalBytes else null
                    )

                    logger.v(TAG, "Downloaded: $bytesRead / $totalBytes bytes")
                }

                outputStream.flush()
                logger.d(TAG, "Download completed successfully")

                Result.success(apkFile)
            } catch (e: IOException) {
                logger.e(TAG, "IOException during download", e)
                apkFile.delete()
                Result.failure(e)
            } finally {
                inputStream.close()
                outputStream.close()
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error downloading APK", e)
            Result.failure(e)
        }
    }
}
