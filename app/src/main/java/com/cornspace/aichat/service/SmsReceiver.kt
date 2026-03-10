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
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == intent.action) {
            Log.d(TAG, "SMS received")
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isEmpty()) {
                    Log.w(TAG, "No messages in intent")
                    return
                }

                val simSlot = getSimSlot(context, intent)

                val fullMessage = StringBuilder()
                var sender: String? = null
                for (smsMessage in messages) {
                    if (sender == null) {
                        sender = smsMessage.displayOriginatingAddress
                    }
                    fullMessage.append(smsMessage.messageBody)
                }

                val timestamp = System.currentTimeMillis()
                val receiverNumber = getPhoneNumberForSlot(context, simSlot)

                Log.d(TAG, "SMS from $sender via SIM $simSlot: ${fullMessage.length} chars")

                val serviceIntent = Intent(context, SmsGatewayService::class.java).apply {
                    putExtra("sms_sender", sender)
                    putExtra("sms_message", fullMessage.toString())
                    putExtra("sms_timestamp", timestamp)
                    putExtra("sms_sim_slot", simSlot)
                    putExtra("sms_receiver_number", receiverNumber)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS", e)
            }
        }
    }

    private fun getSimSlot(context: Context, intent: Intent): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val subscriptionId = intent.getIntExtra("subscription", -1)
                if (subscriptionId != -1) {
                    DeviceUtils.getSimSlotFromSubscription(context, subscriptionId)
                } else {
                    0
                }
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
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                        as SubscriptionManager
                val subscriptionInfoList = subscriptionManager.activeSubscriptionInfoList
                val subInfo = subscriptionInfoList?.find { it.simSlotIndex == slot }
                // Use DeviceUtils to avoid deprecated .number property
                subInfo?.let {
                    DeviceUtils.getPhoneNumberForSubscription(context, it.subscriptionId)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting phone number for slot", e)
            null
        }
    }
}