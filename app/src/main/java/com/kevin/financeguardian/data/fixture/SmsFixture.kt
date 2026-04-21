package com.kevin.financeguardian.data.fixture

import com.kevin.financeguardian.domain.model.Provider
import java.time.Instant

data class SmsFixture(
    val provider: Provider,
    val sender: String,
    val body: String,
    val receivedAt: Instant,
)
