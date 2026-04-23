package com.kevin.financeguardian.data.local

interface AppDataResetter {
    suspend fun resetAllData()
}
