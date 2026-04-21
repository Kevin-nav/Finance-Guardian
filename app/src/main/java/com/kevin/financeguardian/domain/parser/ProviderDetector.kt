package com.kevin.financeguardian.domain.parser

import com.kevin.financeguardian.domain.model.Provider

fun detect(sender: String, body: String): Provider {
    val senderLower = sender.trim().lowercase()
    val normalized = normalizeWhitespace(body)
    val lower = normalized.lowercase()

    val senderProvider = when (senderLower) {
        "mobilemoney" -> Provider.MTN_MOMO
        "t-cash" -> Provider.TELECEL_CASH
        "gcb bank" -> Provider.GCB
        else -> null
    }
    if (senderProvider != null) {
        return senderProvider
    }

    if (lower.contains("telecel cash") || lower.contains("sendi k3k3")) {
        return Provider.TELECEL_CASH
    }

    if (lower.contains("a/c no:") || lower.contains("prepaid card")) {
        return Provider.GCB
    }

    if (
        senderLower.contains("mtn") ||
        lower.contains("financial transaction id") ||
        lower.contains("momo app") ||
        lower.contains("y'ello") ||
        lower.startsWith("payment made for ghs") ||
        lower.startsWith("payment received for ghs") ||
        lower.startsWith("payment for ghs") ||
        lower.startsWith("your payment of ghs")
    ) {
        return Provider.MTN_MOMO
    }

    return Provider.UNKNOWN
}
