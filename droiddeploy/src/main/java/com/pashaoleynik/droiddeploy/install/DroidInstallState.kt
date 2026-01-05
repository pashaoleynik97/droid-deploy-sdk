package com.pashaoleynik.droiddeploy.install

import com.pashaoleynik.droiddeploy.errors.DroidDeployError
import java.io.File

sealed class DroidInstallState {
    data object Idle : DroidInstallState()
    data object Preparing : DroidInstallState()
    data class Downloading(
        val progressPercent: Int?,
        val bytesRead: Long,
        val totalBytes: Long?
    ) : DroidInstallState()
    data class Downloaded(val file: File) : DroidInstallState()
    data object Installing : DroidInstallState()
    data object Installed : DroidInstallState()
    data class Cancelled(val reason: String? = null) : DroidInstallState()
    data class Failed(val error: DroidDeployError) : DroidInstallState()
}
