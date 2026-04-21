package com.kevin.financeguardian.core.startup

import com.kevin.financeguardian.data.local.DefaultCategorySeeder
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AppStartupRunner @Inject constructor(
    private val defaultCategorySeeder: DefaultCategorySeeder,
) {
    fun launch(scope: CoroutineScope) {
        scope.launch { runStartupTasks() }
    }

    suspend fun runStartupTasks() {
        defaultCategorySeeder.seedIfEmpty()
    }
}
