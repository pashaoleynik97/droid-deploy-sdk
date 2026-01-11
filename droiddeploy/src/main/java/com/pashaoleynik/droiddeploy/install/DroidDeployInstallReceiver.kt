package com.pashaoleynik.droiddeploy.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

class DroidDeployInstallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DroidDeployInstallReceiver"

        private data class CallbackInfo(
            val callback: (Int, String?) -> Unit,
            val autoRelaunch: Boolean
        )

        // Static registry for callbacks
        private val callbacks = ConcurrentHashMap<Int, CallbackInfo>()

        internal fun registerCallback(
            sessionId: Int,
            autoRelaunch: Boolean,
            callback: (Int, String?) -> Unit
        ) {
            callbacks[sessionId] = CallbackInfo(callback, autoRelaunch)
        }

        internal fun unregisterCallback(sessionId: Int) {
            callbacks.remove(sessionId)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || context == null) return

        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        Log.d(TAG, "Received install result for session $sessionId: status=$status, message=$message")

        // If user action is required, launch the confirmation intent
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }

            if (confirmationIntent != null) {
                Log.d(TAG, "Launching user confirmation intent")
                confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(confirmationIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start confirmation intent", e)
                }
            } else {
                Log.e(TAG, "No confirmation intent found in PENDING_USER_ACTION response")
            }
        }

        // Get callback info
        val callbackInfo = callbacks[sessionId]

        // Invoke the callback
        callbackInfo?.callback?.invoke(status, message)

        // Handle auto-relaunch on successful installation
        if (status == PackageInstaller.STATUS_SUCCESS && callbackInfo?.autoRelaunch == true) {
            Log.d(TAG, "Auto-relaunching app after successful installation")
            relaunchApp(context)
        }

        // Don't remove callback for PENDING_USER_ACTION, as we'll get another callback
        // after the user approves/denies
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) {
            callbacks.remove(sessionId)
        }
    }

    private fun relaunchApp(context: Context) {
        try {
            // Get the launch intent to find the main activity
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)

            if (launchIntent != null) {
                val componentName = launchIntent.component
                if (componentName != null) {
                    val packageName = componentName.packageName
                    val activityName = componentName.className

                    Log.d(TAG, "Attempting to relaunch: $packageName/$activityName")

                    // Use a shell command to restart the app after a delay
                    // This runs independently of the app process
                    val command = "sleep 2 && am start -n $packageName/$activityName -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --activity-clear-task --activity-new-task"

                    Thread {
                        try {
                            Log.d(TAG, "Executing relaunch command: $command")
                            Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                            Log.d(TAG, "Relaunch command executed successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to execute relaunch command", e)
                        }
                    }.start()
                } else {
                    Log.e(TAG, "Could not get component name from launch intent")
                }
            } else {
                Log.e(TAG, "Could not get launch intent for package: ${context.packageName}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule app relaunch", e)
        }
    }
}
