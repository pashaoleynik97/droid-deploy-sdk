package com.pashaoleynik.droiddeploy.updates

import com.pashaoleynik.droiddeploy.errors.DroidDeployError

sealed class FetchResult {
    data class Success(val updated: DroidUpdate) : FetchResult()
    data object InProgress : FetchResult()
    data class Failed(val error: DroidDeployError) : FetchResult()
}
