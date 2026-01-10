package com.pashaoleynik97.droiddeploysdk

import android.app.Application
import android.util.Log
import com.pashaoleynik.droiddeploy.DroidDeploy
import java.util.concurrent.TimeUnit

class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize DroidDeploy SDK
        DroidDeploy.init(this) {
            setApiKey("EWN6D3bkuHcZ5Ti24eQ6rjzeGYpcHM2kit2o9ge54b4")
            setApplicationId("3c516f2a-722e-48c2-95a5-f3bb54700738")
            setHost("http://10.0.2.2:8080")
            setFetchInterval(TimeUnit.SECONDS.toMillis(30))

            // Enable debug logging
            setDebugLogsEnabled(true)
            setDebugLogsListener { log ->
                when (log.level) {
                    com.pashaoleynik.droiddeploy.logs.DroidDeployLog.Level.VERBOSE ->
                        Log.v(log.tag, log.message, log.throwable)
                    com.pashaoleynik.droiddeploy.logs.DroidDeployLog.Level.DEBUG ->
                        Log.d(log.tag, log.message, log.throwable)
                    com.pashaoleynik.droiddeploy.logs.DroidDeployLog.Level.INFO ->
                        Log.i(log.tag, log.message, log.throwable)
                    com.pashaoleynik.droiddeploy.logs.DroidDeployLog.Level.WARN ->
                        Log.w(log.tag, log.message, log.throwable)
                    com.pashaoleynik.droiddeploy.logs.DroidDeployLog.Level.ERROR ->
                        Log.e(log.tag, log.message, log.throwable)
                }
            }
        }

        Log.i("DemoApplication", "DroidDeploy SDK initialized")
    }
}
