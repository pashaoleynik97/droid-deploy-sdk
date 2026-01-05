package com.pashaoleynik.droiddeploy.logs

internal class Logger(
    private val debugLogsEnabled: Boolean,
    private val debugLogsListener: ((DroidDeployLog) -> Unit)?
) {
    fun v(tag: String, message: String, throwable: Throwable? = null) {
        log(DroidDeployLog.Level.VERBOSE, tag, message, throwable)
    }

    fun d(tag: String, message: String, throwable: Throwable? = null) {
        log(DroidDeployLog.Level.DEBUG, tag, message, throwable)
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        log(DroidDeployLog.Level.INFO, tag, message, throwable)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(DroidDeployLog.Level.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(DroidDeployLog.Level.ERROR, tag, message, throwable)
    }

    private fun log(level: DroidDeployLog.Level, tag: String, message: String, throwable: Throwable?) {
        if (debugLogsEnabled && debugLogsListener != null) {
            debugLogsListener.invoke(DroidDeployLog(level, tag, message, throwable))
        }
    }
}
