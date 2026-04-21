package com.kevin.financeguardian.core.permissions

interface PermissionStatusChecker {
    fun isGranted(permission: FinanceGuardianPermission): Boolean

    fun currentStatuses(): AppPermissionStatuses
}
