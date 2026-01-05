package com.pashaoleynik.droiddeploy.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
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
        if (intent == null) return

        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        Log.d(TAG, "Received install result for session $sessionId: status=$status, message=$message")

        // Invoke the callback
        callbacks[sessionId]?.invoke(status, message)

        // Clean up the callback after invocation
        callbacks.remove(sessionId)
    }
}
