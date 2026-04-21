package com.kevin.financeguardian.data.fixture

import com.kevin.financeguardian.data.sms.SmsIngestionResult

data class SmsFixtureImportResult(
    val fixture: SmsFixture,
    val ingestionResult: SmsIngestionResult,
)
