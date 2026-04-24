package com.kevin.financeguardian.core.startup

import android.util.Log
import com.kevin.financeguardian.data.local.DefaultCategorySeeder
import com.kevin.financeguardian.data.learning.LearningBackfillService
import com.kevin.financeguardian.data.transaction.HistoricalTransactionRepairService
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AppStartupRunner internal constructor(
    private val startupTasks: List<StartupTask>,
) {
    @Inject
    constructor(
        defaultCategorySeeder: DefaultCategorySeeder,
        historicalTransactionRepairService: HistoricalTransactionRepairService,
        learningBackfillService: LearningBackfillService,
    ) : this(
        listOf(
            StartupTask("seed default categories") {
                defaultCategorySeeder.seedIfEmpty()
            },
            StartupTask("repair historical transactions") {
                historicalTransactionRepairService.repair()
            },
            StartupTask("backfill learning signals") {
                learningBackfillService.backfill()
            },
        ),
    )

    fun launch(scope: CoroutineScope) {
        scope.launch { runStartupTasks() }
    }

    suspend fun runStartupTasks() {
        startupTasks.forEach { task ->
            runCatching { task.run() }
                .onFailure { throwable ->
                    Log.e(TAG, "Startup task failed: ${task.name}", throwable)
                }
        }
    }

    internal class StartupTask(
        val name: String,
        private val block: suspend () -> Unit,
    ) {
        suspend fun run() {
            block()
        }
    }

    private companion object {
        const val TAG = "AppStartupRunner"
    }
}
