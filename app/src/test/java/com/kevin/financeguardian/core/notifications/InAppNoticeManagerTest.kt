package com.kevin.financeguardian.core.notifications

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InAppNoticeManagerTest {
    @Test
    fun show_setsActiveNotice() = runTest {
        val manager = InAppNoticeManager()
        val notice = InAppNotice(
            id = "notice-1",
            message = "Correction saved",
            actionLabel = "Open",
        )

        manager.show(notice)

        assertEquals(notice, manager.notice.value)
    }

    @Test
    fun dismiss_clearsActiveNotice() = runTest {
        val manager = InAppNoticeManager()
        manager.show(
            InAppNotice(
                id = "notice-1",
                message = "Correction saved",
            ),
        )

        manager.dismiss("notice-1")

        assertNull(manager.notice.value)
    }

    @Test
    fun dismiss_ignoresMismatchedNoticeId() = runTest {
        val manager = InAppNoticeManager()
        val notice = InAppNotice(
            id = "notice-1",
            message = "Correction saved",
        )
        manager.show(notice)

        manager.dismiss("notice-2")

        assertEquals(notice, manager.notice.value)
    }
}
