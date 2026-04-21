package com.kevin.financeguardian.domain.parser

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dateTimeSecondsFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val dateTimeMinutesFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun parseDateTimeInstant(value: String): Instant? =
    runCatching {
        val formatter = if (value.trim().length == 16) dateTimeMinutesFormatter else dateTimeSecondsFormatter
        LocalDateTime.parse(value.trim(), formatter).toInstant(ZoneOffset.UTC)
    }.getOrNull()

fun parseDateAndTimeInstant(date: String, time: String): Instant? =
    runCatching {
        LocalDateTime.of(
            LocalDate.parse(date.trim()),
            LocalTime.parse(time.trim()),
        ).toInstant(ZoneOffset.UTC)
    }.getOrNull()

fun parseMtnCompactInstant(value: String, receivedAt: Instant): Instant? =
    runCatching {
        val year = value.substring(0, 4).toInt()
        val day = value.substring(4, 6).toInt()
        val hour = value.substring(6, 8).toInt()
        val minute = value.substring(8, 10).toInt()
        val second = value.substring(10, 12).toInt()
        val month = LocalDateTime.ofInstant(receivedAt, ZoneOffset.UTC).monthValue
        LocalDateTime.of(year, month, day, hour, minute, second).toInstant(ZoneOffset.UTC)
    }.getOrNull()
