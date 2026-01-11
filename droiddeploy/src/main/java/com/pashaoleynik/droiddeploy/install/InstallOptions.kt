package com.pashaoleynik.droiddeploy.install

data class InstallOptions(
    val deleteApkAfterInstallAttempt: Boolean = true,
    val apkFileNamePrefix: String = "droiddeploy",
    val autoRelaunchAfterInstall: Boolean = false
)
