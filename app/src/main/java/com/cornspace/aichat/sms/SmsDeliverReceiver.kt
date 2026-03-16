package com.cornspace.aichat.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cornspace.aichat.util.AppLogger

/**
 * BroadcastReceiver for SMS_DELIVER action.
 * Required component for default SMS app eligibility.
 *
 * This receiver receives SMS messages when this app is set as the default SMS app.
 * The SMS_DELIVER broadcast is only sent to the current default SMS app.
 *
 * Note: For the app to appear in the default SMS picker, this component
 * MUST be declared in AndroidManifest.xml with proper intent-filter.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsDeliverReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        AppLogger.d(TAG, "SMS_DELIVER received: ${intent.action}")

        // SMS_DELIVER is sent only to the default SMS app
        // This receiver processes incoming SMS when the app is the default handler

        when (intent.action) {
            "android.provider.Telephony.SMS_DELIVER" -> {
                // Handle incoming SMS
                // The actual SMS processing is done by SmsReceiver
                AppLogger.d(TAG, "Processing SMS_DELIVER broadcast")
            }
        }
    }
}
