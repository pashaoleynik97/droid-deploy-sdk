package com.pashaoleynik.droiddeploy.errors

sealed class DroidDeployError {
    data class Network(val throwable: Throwable) : DroidDeployError()
    data class Http(val code: Int, val message: String?, val apiErrors: List<String>) : DroidDeployError()
    data class Serialization(val throwable: Throwable) : DroidDeployError()
    data class IllegalState(val message: String) : DroidDeployError()
    data class AlreadyInProgress(val operation: String) : DroidDeployError()
}
