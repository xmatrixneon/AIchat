package com.cornspace.aichat.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import com.cornspace.aichat.util.DeviceUtils

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) return

        Log.d(TAG, "SMS received")

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) {
                Log.w(TAG, "No messages in intent")
                return
            }

            val simSlot = getSimSlot(context, intent)

            // BUG 1 FIX: use actual SMS timestamp not System.currentTimeMillis()
            // System.currentTimeMillis() gives receive time, not send time
            val timestamp = messages.first().timestampMillis

            // Reassemble multipart SMS
            var sender: String? = null
            val fullMessage = StringBuilder()
            for (sms in messages) {
                if (sender == null) sender = sms.displayOriginatingAddress
                fullMessage.append(sms.messageBody)
            }

            // BUG 2 FIX: guard null sender — malformed SMS can have null address
            if (sender == null) {
                Log.w(TAG, "SMS has null sender, dropping")
                return
            }

            val receiverNumber = getPhoneNumberForSlot(context, simSlot)
            val simCarrier = getCarrierForSlot(context, simSlot)

            Log.d(TAG, "SMS from $sender via SIM slot $simSlot: ${fullMessage.length} chars")

            // BUG 3 FIX: if service is already running, just send intent normally
            // startForegroundService from BroadcastReceiver on Android 12+ can ANR
            // if the service takes too long to call startForeground()
            val serviceIntent = Intent(context, SmsGatewayService::class.java).apply {
                putExtra("sms_sender", sender)
                putExtra("sms_message", fullMessage.toString())
                putExtra("sms_timestamp", timestamp)
                putExtra("sms_sim_slot", simSlot)
                putExtra("sms_receiver_number", receiverNumber)
                putExtra("sim_carrier", simCarrier)
            }

            if (SmsGatewayService.isServiceRunning()) {
                // Service already running — just send intent, no need to start
                context.startService(serviceIntent)
            } else {
                // Service not running — start it as foreground
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS", e)
        }
    }

    private fun getSimSlot(context: Context, intent: Intent): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val subscriptionId = intent.getIntExtra("subscription", -1)
                if (subscriptionId != -1) {
                    DeviceUtils.getSimSlotFromSubscription(context, subscriptionId)
                } else 0
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val slotIndex = intent.getIntExtra("slot", -1)
                if (slotIndex != -1) slotIndex else 0
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting SIM slot", e)
            0
        }
    }

    private fun getPhoneNumberForSlot(context: Context, slot: Int): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                        as SubscriptionManager
                val subInfo = sm.activeSubscriptionInfoList?.find { it.simSlotIndex == slot }
                subInfo?.let { DeviceUtils.getPhoneNumberForSubscription(context, it.subscriptionId) }
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting phone number for slot $slot", e)
            null
        }
    }

    // BUG 4 FIX: carrier was never fetched — always sent null to server
    private fun getCarrierForSlot(context: Context, slot: Int): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                        as SubscriptionManager
                sm.activeSubscriptionInfoList
                    ?.find { it.simSlotIndex == slot }
                    ?.carrierName
                    ?.toString()
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting carrier for slot $slot", e)
            null
        }
    }
}