package com.settingpro.installer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class InstallerActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var installButton: Button
    private var apkFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Copy APK from assets to cache on first launch
        copyApkToCache()

        // Simple UI
        createUI()

        // Check if app is already installed
        if (isAppInstalled()) {
            statusText.text = "App is already installed.\nClick to update or reinstall."
            installButton.text = "Update / Reinstall"
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
            setOnClickListener {
                installApk()
            }
        }

        layout.addView(statusText)
        layout.addView(installButton)

        // Constraints
        val constraintSet = androidx.constraintlayout.widget.ConstraintSet().apply {
            clone(layout)

            // Status text - top center
            connect(statusText.id, androidx.constraintlayout.widget.ConstraintSet.TOP, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.TOP, 100)
            connect(statusText.id, androidx.constraintlayout.widget.ConstraintSet.START, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
            connect(statusText.id, androidx.constraintlayout.widget.ConstraintSet.END, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)

            // Install button - bottom center
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

        // Check install permission for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, REQUEST_INSTALL_PERMISSION)
                return
            }
        }

        // Get content URI for the APK file
        val apkUri: Uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )

        // Create install intent
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        try {
            startActivity(intent)
            statusText.text = "Complete the installation in the system dialog"
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_INSTALL_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (packageManager.canRequestPackageInstalls()) {
                    installApk()
                } else {
                    Toast.makeText(this, "Install permission required", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        private const val REQUEST_INSTALL_PERMISSION = 1001
    }
}
