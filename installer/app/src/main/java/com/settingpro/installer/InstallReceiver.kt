package com.settingpro.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast

class InstallReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "InstallReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)

        Log.d(TAG, "onReceive called - status: $status, packageName: $packageName")

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                Log.d(TAG, "STATUS_PENDING_USER_ACTION - showing confirmation dialog")
                @Suppress("DEPRECATION")
                val confirmationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                confirmationIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Log.d(TAG, "STATUS_SUCCESS - launching app: $packageName")
                // Launch the installed app using package name from intent
                packageName?.let { pkg ->
                    // Try launch intent first (for apps with LAUNCHER category)
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)

                    if (launchIntent != null) {
                        Log.d(TAG, "Launch intent found for $pkg, starting activity...")
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        context.startActivity(launchIntent)
                    } else {
                        // For stealth apps without LAUNCHER, try to launch MainActivity explicitly
                        Log.d(TAG, "No launch intent found, trying explicit MainActivity launch...")
                        try {
                            val intent = Intent().apply {
                                setClassName(pkg, "$pkg.MainActivity")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                action = Intent.ACTION_MAIN
                            }
                            context.startActivity(intent)
                            Log.d(TAG, "MainActivity launched explicitly")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to launch MainActivity: ${e.message}")
                        }
                    }
                } ?: Log.e(TAG, "Package name is null!")
            }

            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_ABORTED,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?: "Unknown error"
                Log.e(TAG, "Install failed: $message, status: $status")
                Toast.makeText(context, "Install failed: $message", Toast.LENGTH_LONG).show()
            }
        }
    }
}
