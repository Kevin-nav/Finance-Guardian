package com.kevin.financeguardian.data.sms

import java.time.Instant

object SmsEnvelopeSanitizer {
    const val MAX_SENDER_CHARS = 128
    const val MAX_BODY_CHARS = 4_096
    const val MAX_MULTIPART_SEGMENTS = 10

    fun sanitize(
        sender: String,
        body: String,
        receivedAt: Instant,
    ): SmsMessageEnvelope? {
        val cleanSender = sender.trim()
        val cleanBody = body.trim()

        if (cleanSender.isBlank() || cleanBody.isBlank()) return null
        if (cleanSender.length > MAX_SENDER_CHARS) return null
        if (cleanBody.length > MAX_BODY_CHARS) return null

        return SmsMessageEnvelope(
            sender = cleanSender,
            body = cleanBody,
            receivedAt = receivedAt,
        )
    }
}
