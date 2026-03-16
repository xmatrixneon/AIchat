package com.cornspace.aichat.sms

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import com.cornspace.aichat.util.AppLogger

/**
 * Service for responding to messages via RESPOND_VIA_MESSAGE intent.
 * Required component for default SMS app eligibility.
 *
 * This service allows other apps (like incoming call screens) to respond
 * to calls/messages through this app using an SMS response.
 *
 * When a user is receiving a call and chooses to respond with a text message,
 * the system sends a RESPOND_VIA_MESSAGE intent to the default SMS app's service.
 *
 * Note: For the app to appear in the default SMS picker, this component
 * MUST be declared in AndroidManifest.xml with proper intent-filter.
 */
class HeadlessSmsSendService : Service() {

    companion object {
        private const val TAG = "HeadlessSmsSendService"
    }

    override fun onBind(intent: Intent?): IBinder? {
        AppLogger.d(TAG, "onBind called: ${intent?.action}")
        // Return null for a headless service (no direct binding)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.d(TAG, "onStartCommand called: ${intent?.action}")

        intent?.let { incomingIntent ->
            when (incomingIntent.action) {
                "android.intent.action.RESPOND_VIA_MESSAGE" -> {
                    handleRespondViaMessage(incomingIntent)
                }
            }
        }

        // Don't restart if killed
        return START_NOT_STICKY
    }

    /**
     * Handle RESPOND_VIA_MESSAGE intent.
     * This is called when user chooses to respond to a call with a text message.
     */
    private fun handleRespondViaMessage(incomingIntent: Intent) {
        val message = getMessageFromIntent(incomingIntent)
        val recipients = getRecipientsFromIntent(incomingIntent)

        AppLogger.d(TAG, "RESPOND_VIA_MESSAGE: message='$message', recipients=$recipients")

        // In a full SMS app implementation, this would:
        // 1. Open a compose UI with the recipient and optional message
        // 2. Allow user to edit and send the message
        // 3. Send the SMS using SmsManager

        // For this app's purpose, we just log and finish
        // The actual SMS sending is handled by the gateway
    }

    /**
     * Extract message text from RESPOND_VIA_MESSAGE intent.
     */
    private fun getMessageFromIntent(intent: Intent): String? {
        // The message can be in Intent.EXTRA_TEXT or as a String extra
        return when {
            intent.hasExtra(android.content.Intent.EXTRA_TEXT) -> {
                intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
            }
            else -> null
        }
    }

    /**
     * Extract recipients from RESPOND_VIA_MESSAGE intent.
     */
    private fun getRecipientsFromIntent(intent: Intent): Array<String>? {
        val uri = intent.data
        return uri?.scheme?.let { scheme ->
            when (scheme) {
                "sms", "smsto", "mms", "mmsto" -> {
                    // Extract recipient from URI scheme
                    uri.schemeSpecificPart?.split(",")?.toTypedArray()
                }
                else -> null
            }
        }
    }
}
