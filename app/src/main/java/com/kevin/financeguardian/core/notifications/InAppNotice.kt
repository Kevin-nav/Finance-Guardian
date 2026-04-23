package com.kevin.financeguardian.core.notifications

data class InAppNotice(
    val id: String,
    val message: String,
    val actionLabel: String? = null,
    val severity: InAppNoticeSeverity = InAppNoticeSeverity.Info,
    val duration: InAppNoticeDuration = InAppNoticeDuration.Short,
)

enum class InAppNoticeSeverity {
    Info,
    Success,
    Warning,
    Error,
}

enum class InAppNoticeDuration {
    Short,
    Long,
    Indefinite,
}
