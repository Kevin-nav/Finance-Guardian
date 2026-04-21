package com.kevin.financeguardian.core.permissions

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidPermissionStatusCheckerTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun currentStatusesReportDeniedPermissionsByDefault() {
        shadowOf(application).denyPermissions(
            FinanceGuardianPermission.ReceiveSms.androidPermission,
            FinanceGuardianPermission.PostNotifications.androidPermission,
        )
        val checker = AndroidPermissionStatusChecker(application)

        val statuses = checker.currentStatuses()

        assertFalse(statuses.receiveSmsGranted)
        assertFalse(statuses.postNotificationsGranted)
    }

    @Test
    fun grantingReceiveSmsReportsSmsGranted() {
        shadowOf(application).grantPermissions(FinanceGuardianPermission.ReceiveSms.androidPermission)
        shadowOf(application).denyPermissions(FinanceGuardianPermission.PostNotifications.androidPermission)
        val checker = AndroidPermissionStatusChecker(application)

        val statuses = checker.currentStatuses()

        assertTrue(statuses.receiveSmsGranted)
        assertFalse(statuses.postNotificationsGranted)
    }

    @Test
    fun grantingPostNotificationsReportsNotificationsGranted() {
        shadowOf(application).denyPermissions(FinanceGuardianPermission.ReceiveSms.androidPermission)
        shadowOf(application).grantPermissions(FinanceGuardianPermission.PostNotifications.androidPermission)
        val checker = AndroidPermissionStatusChecker(application)

        val statuses = checker.currentStatuses()

        assertFalse(statuses.receiveSmsGranted)
        assertTrue(statuses.postNotificationsGranted)
    }
}
