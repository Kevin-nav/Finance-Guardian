package com.kevin.financeguardian.core.notifications

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class InAppNoticeManager @Inject constructor() {
    private val currentNotice = MutableStateFlow<InAppNotice?>(null)

    val notice: StateFlow<InAppNotice?> = currentNotice.asStateFlow()

    fun show(notice: InAppNotice) {
        currentNotice.value = notice
    }

    fun showMessage(
        message: String,
        actionLabel: String? = null,
        severity: InAppNoticeSeverity = InAppNoticeSeverity.Info,
        duration: InAppNoticeDuration = InAppNoticeDuration.Short,
    ) {
        show(
            InAppNotice(
                id = message,
                message = message,
                actionLabel = actionLabel,
                severity = severity,
                duration = duration,
            ),
        )
    }

    fun dismiss(noticeId: String? = null) {
        if (noticeId == null || currentNotice.value?.id == noticeId) {
            currentNotice.value = null
        }
    }
}
