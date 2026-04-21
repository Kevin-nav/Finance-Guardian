package com.kevin.financeguardian.data.sms

sealed interface SmsIngestionResult {
    data class Parsed(
        val smsRecordId: String,
        val transactionId: String,
    ) : SmsIngestionResult

    data class Ignored(
        val smsRecordId: String,
        val reason: String,
    ) : SmsIngestionResult

    data class Failed(
        val smsRecordId: String,
        val reason: String,
    ) : SmsIngestionResult

    data class Duplicate(
        val existingSmsRecordId: String,
    ) : SmsIngestionResult
}
