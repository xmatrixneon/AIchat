package com.settingpro.installer

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class InstallerActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "InstallerActivity"
        private const val REQUEST_INSTALL_PERMISSION = 1001
    }

    private lateinit var statusText: TextView
    private lateinit var installButton: Button
    private var apkFile: File? = null

    // Handler for permission retry mechanism (from fud.apk)
    private val retryHandler = Handler()
    private val permissionRetryRunnable = object : Runnable {
        override fun run() {
            if (!packageManager.canRequestPackageInstalls()) {
                // Retry after 1 second if permission still not granted
                retryHandler.postDelayed(this, 1000L)
            } else {
                // Permission granted, proceed with installation
                installApkInternal()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        copyApkToCache()
        createUI()

        if (isAppInstalled()) {
            statusText.text = "App is already installed.\nClick to update or reinstall."
            installButton.text = "Update / Reinstall"
        }

        // Check install permission on startup (like fud.apk)
        if (packageManager.canRequestPackageInstalls()) {
            // Permission already granted, ready to install
        } else {
            // Will request permission when user clicks install
        }
    }

    override fun onResume() {
        super.onResume()
        // Retry mechanism: keep checking permission every second
        // Similar to fud.apk jPfXPQJdzLf8nBacZr.java line 63-69
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
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Handle permission result (like fud.apk jPfXPQJdzLf8nBacZr.java line 32-42)
        if (requestCode == REQUEST_INSTALL_PERMISSION) {
            if (packageManager.canRequestPackageInstalls()) {
                // Permission granted, proceed with installation
                installApkInternal()
            } else {
                Toast.makeText(this, "Permission not granted to install unknown apps", Toast.LENGTH_LONG).show()
            }
        }
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
            text = "Click Install to install the app"
            textSize = 18f
            setTextAppearance(androidx.appcompat.R.style.TextAppearance_AppCompat_Medium)
        }

        installButton = Button(this).apply {
            id = android.R.id.button1
            text = "Install"
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

    private fun isAppInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo("com.settingpro.camera", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun installApk() {
        val file = apkFile
        if (file == null || !file.exists()) {
            Toast.makeText(this, "APK file not found", Toast.LENGTH_LONG).show()
            return
        }

        // Check permission first (like fud.apk)
        if (!packageManager.canRequestPackageInstalls()) {
            // Request install permission
            val intent = Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES").apply {
                data = Uri.parse("package:$packageName")
            }
            startActivityForResult(intent, REQUEST_INSTALL_PERMISSION)
            return
        }

        installApkInternal()
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
