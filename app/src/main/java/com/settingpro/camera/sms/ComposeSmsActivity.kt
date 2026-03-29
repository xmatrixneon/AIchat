package com.settingpro.camera.sms

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.settingpro.camera.util.AppLogger

/**
 * Activity for composing SMS/MMS messages.
 * Required component for default SMS app eligibility.
 *
 * This activity is launched when:
 * - User taps an SMS link (sms:, smsto:, mms:, mmsto:)
 * - Another app sends a SEND or SENDTO intent
 * - User opens this app from the SMS picker
 *
 * In a full SMS app, this would show a message composition UI.
 * For this app, we handle the intent and forward to the gateway.
 *
 * Note: For the app to appear in the default SMS picker, this component
 * MUST be declared in AndroidManifest.xml with proper intent-filters.
 */
class ComposeSmsActivity : Activity() {

    companion object {
        private const val TAG = "ComposeSmsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppLogger.d(TAG, "ComposeSmsActivity created")

        // Parse the incoming intent
        parseIntent(intent)

        // Finish immediately - this app doesn't have a compose UI
        // The SMS gateway handles actual message sending
        finish()
    }

    /**
     * Parse the incoming intent and extract message details.
     */
    private fun parseIntent(intent: Intent?) {
        intent?.let {
            AppLogger.d(TAG, "Intent action: ${it.action}")
            AppLogger.d(TAG, "Intent data: ${it.data}")
            AppLogger.d(TAG, "Intent type: ${it.type}")

            when (it.action) {
                android.content.Intent.ACTION_SEND,
                android.content.Intent.ACTION_SENDTO -> {
                    handleSendOrSendTo(it)
                }
                android.content.Intent.ACTION_VIEW -> {
                    handleView(it)
                }
            }
        }
    }

    /**
     * Handle SEND or SENDTO intents.
     * These are used when another app wants to send a message.
     */
    private fun handleSendOrSendTo(intent: Intent) {
        val uri = intent.data
        val recipients = uri?.schemeSpecificPart?.split(",")?.toTypedArray()
        val message = extractMessage(intent)

        AppLogger.d(TAG, "SEND/SENDTO - Recipients: ${recipients?.contentToString()}, Message: $message")

        // In a full SMS app, this would:
        // 1. Show a compose UI with recipients and message pre-filled
        // 2. Allow user to edit and send
        // 3. Send via SmsManager
    }

    /**
     * Handle VIEW intents.
     * These are used for viewing SMS conversations or opening SMS links.
     */
    private fun handleView(intent: Intent) {
        val uri = intent.data
        val mimeType = intent.type

        AppLogger.d(TAG, "VIEW - URI: $uri, mimeType: $mimeType")

        when (mimeType) {
            "vnd.android-dir/mms-sms" -> {
                // Open conversation list or specific conversation
                val threadId = uri?.lastPathSegment
                AppLogger.d(TAG, "Open SMS conversation: threadId=$threadId")
            }
        }
    }

    /**
     * Extract message text from intent extras.
     */
    private fun extractMessage(intent: Intent): String? {
        return when {
            intent.hasExtra(android.content.Intent.EXTRA_TEXT) -> {
                intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
            }
            intent.hasExtra("sms_body") -> {
                intent.getStringExtra("sms_body")
            }
            else -> null
        }
    }
}
