package com.kevin.financeguardian

import android.app.Application
import com.kevin.financeguardian.core.startup.AppStartupRunner
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class FinanceGuardianApplication : Application() {
    @Inject lateinit var appStartupRunner: AppStartupRunner

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appStartupRunner.launch(applicationScope)
    }
}
