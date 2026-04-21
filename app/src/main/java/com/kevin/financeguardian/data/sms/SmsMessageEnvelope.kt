package com.kevin.financeguardian.data.sms

import java.time.Instant

data class SmsMessageEnvelope(
    val sender: String,
    val body: String,
    val receivedAt: Instant,
)
