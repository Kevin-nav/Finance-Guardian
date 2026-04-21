package com.kevin.financeguardian.domain.parser

import com.kevin.financeguardian.domain.model.Provider

interface ProviderParser {
    val provider: Provider
    fun parse(input: SmsParseInput, normalizedBody: String): SmsParseResult?
}
