package com.kevin.financeguardian.data.sms

import android.content.Intent
import android.provider.Telephony
import java.time.Instant
import javax.inject.Inject

class SmsIntentExtractor @Inject constructor() {
    fun extract(intent: Intent, fallbackReceivedAt: Instant): List<SmsMessageEnvelope> {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return emptyList()

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty()
        if (messages.isEmpty()) return emptyList()

        return messages
            .mapIndexed { index, message ->
                ExtractedPart(
                    index = index,
                    sender = message.originatingAddress.orEmpty(),
                    body = message.messageBody.orEmpty(),
                    timestampMillis = message.timestampMillis,
                )
            }
            .filter { it.sender.isNotBlank() && it.body.isNotBlank() }
            .groupBy { it.sender to it.timestampMillis }
            .values
            .mapNotNull { parts ->
                val sorted = parts.sortedBy { it.index }
                val sender = sorted.firstOrNull()?.sender.orEmpty()
                val body = sorted.joinToString(separator = "") { it.body }
                if (sender.isBlank() || body.isBlank()) {
                    null
                } else {
                    SmsMessageEnvelope(
                        sender = sender,
                        body = body,
                        receivedAt = sorted
                            .firstOrNull { it.timestampMillis > 0L }
                            ?.timestampMillis
                            ?.let(Instant::ofEpochMilli)
                            ?: fallbackReceivedAt,
                    )
                }
            }
    }

    private data class ExtractedPart(
        val index: Int,
        val sender: String,
        val body: String,
        val timestampMillis: Long,
    )
}
