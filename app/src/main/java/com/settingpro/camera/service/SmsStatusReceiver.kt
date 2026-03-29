package com.settingpro.camera.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.settingpro.camera.data.local.SettingsDataStore
import com.settingpro.camera.data.model.WebSocketMessage
import com.settingpro.camera.util.AppLogger
import com.settingpro.camera.util.SmsSender
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BroadcastReceiver for tracking SMS sent and delivered status.
 *
 * This receiver handles two types of broadcasts:
 * 1. SMS_SENT - Fired when the SMS is sent to the network
 * 2. SMS_DELIVERED - Fired when the SMS is delivered to the recipient
 *
 * The results are forwarded to the WebSocket server via DeviceConnectionService.
 */
@AndroidEntryPoint
class SmsStatusReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "SmsStatusReceiver"

        // Track if this is the first part being sent
        private val pendingMessages = mutableMapOf<String, PendingMessageInfo>()

        data class PendingMessageInfo(
            var partsSent: Int = 0,
            var totalParts: Int = 0,
            var allSentSuccess: Boolean = true,
            var firstPartSent: Boolean = false,
            var messageId: String = ""
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            SmsSender.SMS_SENT_ACTION -> handleSentStatus(context, intent)
            SmsSender.SMS_DELIVERED_ACTION -> handleDeliveredStatus(context, intent)
        }
    }

    /**
     * Handle SMS sent status (when message is sent to network)
     */
    private fun handleSentStatus(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra(SmsSender.EXTRA_MESSAGE_ID) ?: run {
            AppLogger.e(TAG, "Received sent status without message ID")
            return
        }

        val partIndex = intent.getIntExtra(SmsSender.EXTRA_PART_INDEX, 0)
        val totalParts = intent.getIntExtra(SmsSender.EXTRA_TOTAL_PARTS, 1)
        val simSlot = intent.getIntExtra(SmsSender.EXTRA_SIM_SLOT, 0)

        val resultCode = resultCode
        val success = resultCode == SmsSender.RESULT_SUCCESS ||
                      resultCode == -1 || // Some devices use -1 for success
                      resultCode == SmsManager.RESULT_ERROR_NONE

        val error = if (!success) {
            SmsSender.getErrorFromResultCode(resultCode)
        } else null

        AppLogger.d(TAG, "SMS sent status: messageId=$messageId, part=$partIndex/$totalParts, success=$success, error=$error")

        // Get or create pending message info
        val info = pendingMessages.getOrPut(messageId) { PendingMessageInfo(totalParts = totalParts, messageId = messageId) }

        info.partsSent++
        if (!success) {
            info.allSentSuccess = false
        }

        // Send response on first part or when all parts are sent
        if (!info.firstPartSent || partIndex == 0) {
            info.firstPartSent = true
            sendSmsResponse(context, messageId, true, null)
        }

        // Clean up if all parts sent
        if (info.partsSent >= totalParts) {
            if (!info.allSentSuccess) {
                // Send failure response if any part failed
                sendSmsResponse(context, messageId, false, "Some parts failed to send")
            }
            pendingMessages.remove(messageId)
        }
    }

    /**
     * Handle SMS delivered status (when message is delivered to recipient)
     *
     * Note: Delivery reports are carrier-dependent and may not be available
     * on all networks or devices.
     */
    private fun handleDeliveredStatus(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra(SmsSender.EXTRA_MESSAGE_ID) ?: run {
            AppLogger.e(TAG, "Received delivered status without message ID")
            return
        }

        val partIndex = intent.getIntExtra(SmsSender.EXTRA_PART_INDEX, 0)
        val totalParts = intent.getIntExtra(SmsSender.EXTRA_TOTAL_PARTS, 1)

        val resultCode = resultCode
        val success = resultCode == SmsSender.RESULT_SUCCESS ||
                      resultCode == -1

        AppLogger.d(TAG, "SMS delivered status: messageId=$messageId, part=$partIndex/$totalParts, success=$success")

        // Delivery reports are optional - log but don't send to server
        // Most carriers don't support reliable delivery reports
        // We consider the message "sent" once it's accepted by the network
    }

    /**
     * Send SMS response via WebSocket through DeviceConnectionService
     */
    private fun sendSmsResponse(context: Context, messageId: String, success: Boolean, error: String?) {
        serviceScope.launch {
            try {
                // Start the service to handle the response
                val serviceIntent = Intent(context, DeviceConnectionService::class.java).apply {
                    putExtra("sms_response_message_id", messageId)
                    putExtra("sms_response_success", success)
                    putExtra("sms_response_error", error)
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to send SMS response", e)
            }
        }
    }
}
