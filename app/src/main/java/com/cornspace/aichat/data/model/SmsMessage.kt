package com.cornspace.aichat.data.model

enum class SmsStatus {
    PENDING,
    SENT,
    DELIVERED,
    FAILED
}

data class SmsMessage(
    val id: Long = 0,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val simSlot: Int,
    val receiverNumber: String? = null,
    val simCarrier: String? = null,
    val simNetworkType: String? = null,
    val networkType: String? = null,
    val status: SmsStatus = SmsStatus.PENDING
)
