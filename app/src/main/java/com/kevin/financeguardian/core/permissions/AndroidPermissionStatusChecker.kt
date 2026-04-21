package com.kevin.financeguardian.core.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidPermissionStatusChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : PermissionStatusChecker {
    override fun isGranted(permission: FinanceGuardianPermission): Boolean =
        ContextCompat.checkSelfPermission(context, permission.androidPermission) == PackageManager.PERMISSION_GRANTED

    override fun currentStatuses(): AppPermissionStatuses =
        AppPermissionStatuses(
            receiveSmsGranted = isGranted(FinanceGuardianPermission.ReceiveSms),
            postNotificationsGranted = isGranted(FinanceGuardianPermission.PostNotifications),
        )
}
