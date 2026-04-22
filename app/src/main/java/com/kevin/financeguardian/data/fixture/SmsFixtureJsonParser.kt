package com.kevin.financeguardian.data.fixture

import com.kevin.financeguardian.data.sms.SmsEnvelopeSanitizer
import com.kevin.financeguardian.domain.model.Provider
import java.time.Instant
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object SmsFixtureJsonParser {
    fun parseMany(json: String): List<SmsFixture> {
        val trimmed = json.trim()
        if (trimmed.isBlank()) throw SmsFixtureParseException("Fixture JSON is blank")
        return try {
            when {
                trimmed.startsWith("[") -> parseArray(JSONArray(trimmed))
                trimmed.startsWith("{") -> listOf(parseObject(JSONObject(trimmed), 0))
                else -> throw SmsFixtureParseException("Fixture JSON must be an object or array")
            }
        } catch (error: JSONException) {
            throw SmsFixtureParseException(error.message ?: "Invalid fixture JSON")
        }
    }

    private fun parseArray(array: JSONArray): List<SmsFixture> =
        List(array.length()) { index -> parseObject(array.getJSONObject(index), index) }

    private fun parseObject(obj: JSONObject, index: Int): SmsFixture {
        val label = "fixture[$index]"
        val provider = requiredString(obj, "provider", label).let { raw ->
            runCatching { Provider.valueOf(raw) }.getOrElse {
                throw SmsFixtureParseException("$label has invalid provider: $raw")
            }
        }
        val sender = requiredString(
            obj = obj,
            field = "sender",
            label = label,
            maxLength = SmsEnvelopeSanitizer.MAX_SENDER_CHARS,
        )
        val body = requiredString(
            obj = obj,
            field = "body",
            label = label,
            maxLength = SmsEnvelopeSanitizer.MAX_BODY_CHARS,
        )
        val receivedAt = requiredString(obj, "receivedAt", label).let { raw ->
            runCatching { Instant.parse(raw) }.getOrElse {
                throw SmsFixtureParseException("$label has invalid receivedAt: $raw")
            }
        }
        return SmsFixture(provider, sender, body, receivedAt)
    }

    private fun requiredString(
        obj: JSONObject,
        field: String,
        label: String,
        maxLength: Int? = null,
    ): String {
        val value = obj.optString(field).trim()
        if (value.isBlank()) throw SmsFixtureParseException("$label missing required field: $field")
        if (maxLength != null && value.length > maxLength) {
            throw SmsFixtureParseException("$label field too long: $field")
        }
        return value
    }
}
