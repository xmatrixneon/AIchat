package com.cornspace.aichat.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cornspace.aichat.util.AppLogger

/**
 * BroadcastReceiver for WAP_PUSH_DELIVER action (MMS messages).
 * Required component for default SMS app eligibility.
 *
 * This receiver receives MMS messages when this app is set as the default SMS app.
 * The WAP_PUSH_DELIVER broadcast is only sent to the current default SMS app.
 *
 * Note: For the app to appear in the default SMS picker, this component
 * MUST be declared in AndroidManifest.xml with proper intent-filter and mimeType.
 */
class MmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        AppLogger.d(TAG, "WAP_PUSH_DELIVER received: ${intent.action}")

        // WAP_PUSH_DELIVER is sent only to the default SMS app
        // This receiver processes incoming MMS when the app is the default handler

        when (intent.action) {
            "android.provider.Telephony.WAP_PUSH_DELIVER" -> {
                // Handle incoming MMS
                val mimeType = intent.type
                AppLogger.d(TAG, "Processing MMS with mimeType: $mimeType")
            }
        }
    }
}
