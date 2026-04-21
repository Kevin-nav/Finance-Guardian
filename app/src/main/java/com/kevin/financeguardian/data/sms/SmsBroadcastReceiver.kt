package com.kevin.financeguardian.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.kevin.financeguardian.core.time.AppClock
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SmsBroadcastReceiver : BroadcastReceiver() {
    @Inject lateinit var extractor: SmsIntentExtractor
    @Inject lateinit var ingestionService: SmsIngestionService
    @Inject lateinit var clock: AppClock

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                extractor.extract(intent, clock.now()).forEach { envelope ->
                    runCatching { ingestionService.ingest(envelope) }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
