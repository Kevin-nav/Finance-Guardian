package com.kevin.financeguardian.core.startup

import com.kevin.financeguardian.data.local.DefaultCategorySeeder
import com.kevin.financeguardian.data.learning.LearningBackfillService
import com.kevin.financeguardian.data.transaction.HistoricalTransactionRepairService
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AppStartupRunner @Inject constructor(
    private val defaultCategorySeeder: DefaultCategorySeeder,
    private val historicalTransactionRepairService: HistoricalTransactionRepairService,
    private val learningBackfillService: LearningBackfillService,
) {
    fun launch(scope: CoroutineScope) {
        scope.launch { runStartupTasks() }
    }

    suspend fun runStartupTasks() {
        defaultCategorySeeder.seedIfEmpty()
        historicalTransactionRepairService.repair()
        learningBackfillService.backfill()
    }
}
