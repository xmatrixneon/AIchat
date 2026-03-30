package com.settingpro.installer

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class InstallerActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "InstallerActivity"
    }

    private lateinit var statusText: TextView
    private lateinit var installButton: Button
    private var apkFile: File? = null

    // Fix #7: Flag to prevent onResume from re-triggering after installation starts
    private var installationStarted = false

    // Fix #5: Replace deprecated startActivityForResult with ActivityResultLauncher
    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        stopMonitoringService()
        if (packageManager.canRequestPackageInstalls()) {
            installApkInternal()
        } else {
            Toast.makeText(this, "Permission not granted to install unknown apps", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        copyApkToCache()
        createUI()

        if (packageManager.canRequestPackageInstalls()) {
            Log.d(TAG, "Permission granted, starting installation immediately...")
            installationStarted = true
            installApkInternal()
        } else {
            Log.d(TAG, "Permission not granted, requesting...")
            requestInstallPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        // Fix #4: Only check permission here — do NOT start polling loop.
        // PermissionMonitorService handles detection and will re-launch this activity.
        // Fix #7: Skip if installation already kicked off
        if (!installationStarted && packageManager.canRequestPackageInstalls()) {
            installationStarted = true
            installApkInternal()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoringService()
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
            // Fix #9: Always re-copy to avoid stale/corrupted cached APK
            assets.open("release.apk").use { input ->
                apkFile!!.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy APK to cache", e)
            Toast.makeText(this, "Failed to prepare APK: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun installApk() {
        val file = apkFile
        if (file == null || !file.exists()) {
            Toast.makeText(this, "APK file not found", Toast.LENGTH_LONG).show()
            return
        }
        if (!packageManager.canRequestPackageInstalls()) {
            requestInstallPermission()
            return
        }
        installationStarted = true
        installApkInternal()
    }

    private fun requestInstallPermission() {
        // Start foreground service to monitor permission changes while we're in Settings
        val serviceIntent = Intent(this, PermissionMonitorService::class.java).apply {
            action = PermissionMonitorService.ACTION_START_MONITORING
        }
        startForegroundService(serviceIntent)

        // Fix #5: Use launcher instead of deprecated startActivityForResult
        val intent = Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES").apply {
            data = Uri.parse("package:$packageName")
        }
        installPermissionLauncher.launch(intent)
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

            session.openWrite("base.apk", 0, file.length()).use { output ->
                file.inputStream().use { input ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }

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
            installationStarted = false
            statusText.text = "Click Install to install the app"
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}