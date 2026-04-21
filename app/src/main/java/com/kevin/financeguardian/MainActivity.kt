package com.kevin.financeguardian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kevin.financeguardian.ui.FinanceGuardianApp
import com.kevin.financeguardian.ui.theme.FinanceGuardianTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FinanceGuardianTheme {
                FinanceGuardianApp()
            }
        }
    }
}
