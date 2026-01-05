package com.pashaoleynik.droiddeploy.updates

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

internal class DefaultVersionCodeProvider : (Context) -> Long? {
    override fun invoke(context: Context): Long? {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }

            // Use longVersionCode for API 29+
            packageInfo.longVersionCode
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}
