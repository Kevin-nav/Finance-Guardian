package com.kevin.financeguardian.domain.parser

import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.domain.parser.provider.GcbBankParser
import com.kevin.financeguardian.domain.parser.provider.GenericGhanaMoneyParser
import com.kevin.financeguardian.domain.parser.provider.MtnMomoParser
import com.kevin.financeguardian.domain.parser.provider.TelecelCashParser

class FinanceGuardianSmsParser(
    private val parsers: List<ProviderParser> = listOf(
        MtnMomoParser(),
        TelecelCashParser(),
        GcbBankParser(),
        GenericGhanaMoneyParser(),
    ),
) : SmsTransactionParser {
    override fun parse(input: SmsParseInput): SmsParseResult {
        val body = normalizeWhitespace(input.body)
        if (body.isBlank()) return SmsParseResult.Ignored("Blank message")
        if (isGloballyIgnored(body)) return SmsParseResult.Ignored("Non-transactional or failed message")

        val provider = detect(input.sender, body)
        val orderedParsers = when (provider) {
            Provider.UNKNOWN -> parsers
            else -> parsers.filter { it.provider == provider } + parsers.filter { it.provider == Provider.UNKNOWN }
        }

        for (parser in orderedParsers) {
            when (val result = parser.parse(input, body)) {
                is SmsParseResult.Parsed,
                is SmsParseResult.Ignored,
                -> return result
                is SmsParseResult.Failed -> continue
                null -> continue
            }
        }

        return SmsParseResult.Ignored("No financial transaction pattern matched")
    }

    private fun isGloballyIgnored(body: String): Boolean {
        val lower = body.lowercase()
        return lower.startsWith("failed.") ||
            lower.startsWith("transaction failed") ||
            lower.contains("was unsuccessful") ||
            lower.contains("not have enough money") ||
            lower.contains("will never call") ||
            lower.contains("never share your pin") ||
            lower.contains("wishing you a peaceful") ||
            lower.matches(Regex(""".*confirmed\. your telecel cash wallet balance is\s+ghs[0-9,. ]+.*"""))
    }
}
