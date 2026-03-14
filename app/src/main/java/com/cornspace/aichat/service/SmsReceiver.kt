package com.cornspace.aichat.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import com.cornspace.aichat.util.DeviceUtils
import kotlinx.coroutines.*

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) return

        Log.d(TAG, "SMS received")

        // FIX #4: All telephony calls (getSimSlot, getPhoneNumberForSlot,
        // getNetworkTypeForSlot) acquire binder locks to the telephony service.
        // Running them synchronously on the main thread risks hitting the 10-second
        // ANR timeout on slow/budget devices. goAsync() extends the deadline and
        // lets us do the work on a background thread.
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        scope.launch {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isNullOrEmpty()) {
                    Log.w(TAG, "No messages in intent")
                    return@launch
                }

                // FIX #3: Use the public documented constant for the subscription
                // extra on API 30+. SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX
                // ("android.telephony.extra.SUBSCRIPTION_INDEX") is the API-30+
                // standard. The previous "subscription" string is an undocumented
                // OEM convention that works on some devices but not all.
                val simSlot = getSimSlot(context, intent)

                // Use actual SMS send timestamp, not receive time.
                val timestamp = messages.first().timestampMillis

                // Reassemble multipart SMS.
                var sender: String? = null
                val fullMessage = StringBuilder()
                for (sms in messages) {
                    if (sender == null) sender = sms.displayOriginatingAddress
                    fullMessage.append(sms.messageBody)
                }

                // Guard null sender — malformed SMS PDUs can omit the originating address.
                if (sender == null) {
                    Log.w(TAG, "SMS has null sender, dropping")
                    return@launch
                }

                val receiverNumber = getPhoneNumberForSlot(context, simSlot)
                val simCarrier = getCarrierForSlot(context, simSlot)
                val simNetworkType = getNetworkTypeForSlot(context, simSlot)

                Log.d(TAG, "SMS from $sender via SIM slot $simSlot: ${fullMessage.length} chars")

                val serviceIntent = Intent(context, SmsGatewayService::class.java).apply {
                    putExtra("sms_sender", sender)
                    putExtra("sms_message", fullMessage.toString())
                    putExtra("sms_timestamp", timestamp)
                    putExtra("sms_sim_slot", simSlot)
                    putExtra("sms_receiver_number", receiverNumber)
                    putExtra("sim_carrier", simCarrier)
                    putExtra("sim_network_type", simNetworkType)
                }

                // If the service is already foregrounded, a plain startService() is
                // enough and avoids the ANR risk from startForegroundService() when
                // the service is busy and delays its startForeground() call.
                if (SmsGatewayService.isServiceRunning()) {
                    context.startService(serviceIntent)
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS", e)
            } finally {
                scope.cancel()
                pendingResult.finish()
            }
        }
    }

    private fun getSimSlot(context: Context, intent: Intent): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // FIX #3 (applied): Use the documented public constant rather than the
                // undocumented "subscription" string. Both resolve to the same extra key
                // on AOSP, but SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX is the
                // stable, Play-Store-safe reference.
                val subscriptionId = intent.getIntExtra(
                    SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID
                )
                if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    DeviceUtils.getSimSlotFromSubscription(context, subscriptionId)
                } else {
                    0
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // FIX #3 (applied): "slot" is the most common OEM key but is not
                // standardised. Cross-check a list of known OEM keys and fall back
                // to deriving the slot from the subscription ID if all are absent.
                // Known keys: "slot" (AOSP/Sony/HTC), "phone" (Qualcomm BSP),
                //             "simSlot" (some MTK), "simId" (older Samsung).
                val slotFromSlot  = intent.getIntExtra("slot",    -1)
                val slotFromPhone = intent.getIntExtra("phone",   -1)
                val slotFromSimId = intent.getIntExtra("simId",   -1)
                val slotFromSim   = intent.getIntExtra("simSlot", -1)

                val oemSlot = listOf(slotFromSlot, slotFromPhone, slotFromSimId, slotFromSim)
                    .firstOrNull { it != -1 }

                if (oemSlot != null) {
                    oemSlot
                } else {
                    // Last resort: derive slot from subscription ID via SubscriptionManager.
                    val subId = intent.getIntExtra("subscription", -1)
                    if (subId != -1) DeviceUtils.getSimSlotFromSubscription(context, subId) else 0
                }
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
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                    as? SubscriptionManager ?: return null
            val subInfo = sm.activeSubscriptionInfoList?.find { it.simSlotIndex == slot }
                ?: return null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // API 30+: use DeviceUtils helper which handles READ_PHONE_NUMBERS.
                DeviceUtils.getPhoneNumberForSubscription(context, subInfo.subscriptionId)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                // FIX #2: SubscriptionInfo.number is available since API 22 without
                // requiring READ_PHONE_NUMBERS. The previous code only ran this branch
                // on API 30+, silently returning null on API 22–29 devices.
                @Suppress("DEPRECATION")
                subInfo.number?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting phone number for slot $slot", e)
            null
        }
    }

    private fun getCarrierForSlot(context: Context, slot: Int): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                        as? SubscriptionManager ?: return null
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

    private fun getNetworkTypeForSlot(context: Context, slot: Int): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                        as? SubscriptionManager ?: return null
                val subInfo = sm.activeSubscriptionInfoList?.find { it.simSlotIndex == slot }
                    ?: return null
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE)
                        as? TelephonyManager ?: return null
                val subTm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    tm.createForSubscriptionId(subInfo.subscriptionId)
                } else tm

                // FIX #1: SMS travels over the voice radio, not the data radio.
                // dataNetworkType returns what the data bearer is using (typically LTE
                // or 5G NR), which differs from the voice/SMS path on many devices
                // (e.g. VoLTE off → data=LTE but SMS=3G). voiceNetworkType is the
                // correct field to report for an SMS gateway.
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    subTm.voiceNetworkType
                } else {
                    @Suppress("DEPRECATION")
                    subTm.networkType // returns voice type on pre-R
                }
                networkTypeToString(type)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network type for slot $slot", e)
            null
        }
    }

    // CDMA/EVDO/IDEN constants are deprecated in the Android SDK because those
    // radio technologies are decommissioned, but there is no replacement constant —
    // they still appear on real devices and must be handled. @Suppress is the
    // correct approach: we are intentionally referencing them for legacy coverage.
    @Suppress("DEPRECATION")
    private fun networkTypeToString(networkType: Int): String = when (networkType) {
        // FIX #5: NETWORK_TYPE_UNKNOWN (0) means "radio not yet determined" and
        // is distinct from a truly unrecognised type code. Named explicitly so the
        // server can tell the difference between "not ready" and "unknown type".
        TelephonyManager.NETWORK_TYPE_UNKNOWN -> "Unknown"

        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_CDMA,
        TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_IDEN  -> "2G"

        TelephonyManager.NETWORK_TYPE_UMTS,
        TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_EVDO_B,
        TelephonyManager.NETWORK_TYPE_EHRPD,
        TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"

        TelephonyManager.NETWORK_TYPE_LTE   -> "LTE"
        TelephonyManager.NETWORK_TYPE_NR    -> "5G NR"
        else -> "Unrecognised($networkType)"
    }
}