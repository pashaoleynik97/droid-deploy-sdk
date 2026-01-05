package com.pashaoleynik.droiddeploy.updates

import com.pashaoleynik.droiddeploy.errors.DroidDeployError
import com.pashaoleynik.droiddeploy.network.VersionDto

data class DroidUpdate(
    val available: Boolean = false,
    val installedVersionCode: Long? = null,
    val latest: VersionDto? = null,
    val lastCheckedAtMillis: Long? = null,
    val error: DroidDeployError? = null
)
