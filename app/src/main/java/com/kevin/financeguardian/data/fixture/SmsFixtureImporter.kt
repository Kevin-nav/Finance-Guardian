package com.kevin.financeguardian.data.fixture

interface SmsFixtureImporter {
    suspend fun importJson(json: String): List<SmsFixtureImportResult>
}
