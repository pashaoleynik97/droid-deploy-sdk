package com.pashaoleynik.droiddeploy.logs

data class DroidDeployLog(
    val level: Level,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
) {
    enum class Level {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR
    }
}
