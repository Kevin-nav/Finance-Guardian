package com.kevin.financeguardian.core.permissions

enum class FinanceGuardianPermission(val androidPermission: String) {
    ReceiveSms(android.Manifest.permission.RECEIVE_SMS),
    PostNotifications(android.Manifest.permission.POST_NOTIFICATIONS),
}
