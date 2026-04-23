package com.kevin.financeguardian.data.fixture

import com.kevin.financeguardian.data.sms.SmsIngestionService
import com.kevin.financeguardian.data.sms.SmsMessageEnvelope
import javax.inject.Inject

class SmsFixtureImportService @Inject constructor(
    private val ingestionService: SmsIngestionService,
) : SmsFixtureImporter {
    override suspend fun importJson(json: String): List<SmsFixtureImportResult> =
        importFixtures(SmsFixtureJsonParser.parseMany(json))

    suspend fun importFixtures(fixtures: List<SmsFixture>): List<SmsFixtureImportResult> =
        fixtures.map { fixture ->
            SmsFixtureImportResult(
                fixture = fixture,
                ingestionResult = ingestionService.ingest(
                    SmsMessageEnvelope(
                        sender = fixture.sender,
                        body = fixture.body,
                        receivedAt = fixture.receivedAt,
                    ),
                ),
            )
        }
}
