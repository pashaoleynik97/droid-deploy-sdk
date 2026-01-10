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

        // Static registry for callbacks
        private val callbacks = ConcurrentHashMap<Int, (Int, String?) -> Unit>()

        internal fun registerCallback(sessionId: Int, callback: (Int, String?) -> Unit) {
            callbacks[sessionId] = callback
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

        // Invoke the callback
        callbacks[sessionId]?.invoke(status, message)

        // Don't remove callback for PENDING_USER_ACTION, as we'll get another callback
        // after the user approves/denies
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) {
            callbacks.remove(sessionId)
        }
    }
}
