package com.kevin.financeguardian.domain.parser

import com.kevin.financeguardian.domain.model.Provider

fun detect(sender: String, body: String): Provider {
    val senderLower = sender.lowercase()
    val normalized = normalizeWhitespace(body)
    val lower = normalized.lowercase()

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
