package com.kevin.financeguardian.domain.parser

fun normalizeWhitespace(body: String): String =
    body
        .replace('\u00a0', ' ')
        .replace(Regex("""\s+"""), " ")
        .trim()
