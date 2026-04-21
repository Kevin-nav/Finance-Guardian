package com.kevin.financeguardian.core.time

import java.time.Instant

interface AppClock {
    fun now(): Instant
}
