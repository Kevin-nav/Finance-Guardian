package com.kevin.financeguardian.domain.model

import com.kevin.financeguardian.domain.parser.GhanaPhoneNumberNormalizer
import java.time.Instant

enum class InstrumentType {
    WALLET,
    BANK_ACCOUNT,
    CARD,
    UNKNOWN,
}

enum class InstrumentProvider {
    MTN,
    TELECEL,
    GCB,
    OTHER,
    UNKNOWN,
}

enum class InstrumentOrigin {
    USER_CONFIRMED,
    SYSTEM_INFERRED,
}

data class OwnedInstrument(
    val id: String,
    val label: String,
    val type: InstrumentType,
    val provider: InstrumentProvider,
    val identifier: String,
    val displayIdentifier: String = identifier,
    val origin: InstrumentOrigin = InstrumentOrigin.USER_CONFIRMED,
    val firstSeenAt: Instant? = null,
    val lastSeenAt: Instant? = null,
    val confidence: Float = if (origin == InstrumentOrigin.USER_CONFIRMED) 1f else 0.75f,
) {
    fun matchesIdentifier(rawIdentifier: String?): Boolean {
        if (rawIdentifier.isNullOrBlank()) return false
        return when (type) {
            InstrumentType.WALLET -> GhanaPhoneNumberNormalizer.sameNumber(identifier, rawIdentifier)
            else -> identifier.equals(rawIdentifier, ignoreCase = true)
        }
    }
}
