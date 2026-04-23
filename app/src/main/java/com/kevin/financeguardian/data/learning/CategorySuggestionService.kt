package com.kevin.financeguardian.data.learning

import com.kevin.financeguardian.data.local.dao.LearningSignalDao
import com.kevin.financeguardian.data.local.entity.LearningSignalEntity
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Transaction
import javax.inject.Inject

open class CategorySuggestionService @Inject constructor(
    private val learningSignalDao: LearningSignalDao,
) {
    open suspend fun suggestFor(transaction: Transaction): LearningSuggestion {
        val features = LearningFeatureExtractor.fromTransaction(transaction)
        val scored = linkedMapOf<String, ScoreAccumulator>()

        suspend fun applySignals(
            signals: List<LearningSignalEntity>,
            baseWeight: Float,
            reason: String,
        ) {
            for (signal in signals) {
                val categoryId = signal.categoryId ?: continue
                val amountModifier = if (signal.amountBucket == features.amountBucket) 1f else 0.6f
                val accumulator = scored.getOrPut(categoryId) {
                    ScoreAccumulator(moneyMovementType = signal.moneyMovementType)
                }
                accumulator.score += signal.weight * baseWeight * amountModifier
                accumulator.reasons += reason
                accumulator.moneyMovementType = signal.moneyMovementType
            }
        }

        features.normalizedPhone?.let {
            applySignals(
                signals = learningSignalDao.findByNormalizedPhone(it),
                baseWeight = 1.0f,
                reason = "matched phone history",
            )
        }
        features.normalizedMerchantName?.let {
            applySignals(
                signals = learningSignalDao.findByNormalizedMerchantName(it),
                baseWeight = 0.75f,
                reason = "matched merchant history",
            )
        }
        features.normalizedReference?.let {
            applySignals(
                signals = learningSignalDao.findByNormalizedReference(it),
                baseWeight = 0.6f,
                reason = "matched reference history",
            )
        }
        val merchantName = features.normalizedMerchantName
        val reference = features.normalizedReference
        if (merchantName != null && reference != null) {
            val comboKey = LearningFeatureExtractor.merchantReferenceSignalKey(
                provider = transaction.provider,
                normalizedMerchantName = merchantName,
                normalizedReference = reference,
            )
            learningSignalDao.getBySignalKey(comboKey)?.let { comboSignal ->
                applySignals(
                    signals = listOf(comboSignal),
                    baseWeight = 1.1f,
                    reason = "matched merchant and reference history",
                )
            }
        }

        val best = scored.maxByOrNull { it.value.score }
            ?: return LearningSuggestion(
                suggestedCategoryId = null,
                suggestedMoneyMovementType = null,
                confidence = 0f,
                reasons = emptyList(),
            )
        val secondBest = scored
            .filterKeys { it != best.key }
            .maxOfOrNull { it.value.score }
            ?: 0f
        val confidence = ((best.value.score - secondBest) / (best.value.score + 0.5f))
            .coerceIn(0f, 1f)

        return LearningSuggestion(
            suggestedCategoryId = best.key,
            suggestedMoneyMovementType = best.value.moneyMovementType,
            confidence = confidence,
            reasons = best.value.reasons.distinct(),
        )
    }

    private data class ScoreAccumulator(
        var score: Float = 0f,
        var moneyMovementType: MoneyMovementType? = null,
        val reasons: MutableList<String> = mutableListOf(),
    )
}
