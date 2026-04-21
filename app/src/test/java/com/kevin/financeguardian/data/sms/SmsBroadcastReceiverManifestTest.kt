package com.kevin.financeguardian.data.sms

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmsBroadcastReceiverManifestTest {
    @Test
    fun receiverIsRegisteredWithBroadcastSmsPermission() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getReceiverInfo(
            ComponentName(context, SmsBroadcastReceiver::class.java),
            PackageManager.GET_META_DATA,
        )

        assertTrue(info.enabled)
        assertTrue(info.exported)
        assertEquals("android.permission.BROADCAST_SMS", info.permission)
    }
}
