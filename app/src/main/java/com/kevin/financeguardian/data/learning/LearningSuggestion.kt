package com.kevin.financeguardian.data.learning

import com.kevin.financeguardian.domain.model.MoneyMovementType

data class LearningSuggestion(
    val suggestedCategoryId: String?,
    val suggestedMoneyMovementType: MoneyMovementType?,
    val confidence: Float,
    val reasons: List<String>,
) {
    val shouldAutoApply: Boolean get() = confidence >= AUTO_APPLY_THRESHOLD
    val shouldSuggestForReview: Boolean get() = confidence in REVIEW_THRESHOLD..<AUTO_APPLY_THRESHOLD

    companion object {
        const val AUTO_APPLY_THRESHOLD: Float = 0.85f
        const val REVIEW_THRESHOLD: Float = 0.55f
    }
}
