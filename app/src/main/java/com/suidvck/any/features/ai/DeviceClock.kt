package com.suidvck.any.features.ai

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * The phone's clock, injected into every AI request.
 *
 * Models have a training cutoff and live search results can be months old, so questions
 * such as "วันนี้วันที่เท่าไหร่" must be answered from the device, not from memory or a
 * web page. The string is rebuilt for every request so it never goes stale at midnight.
 */
object DeviceClock {

    private val THAI_DAYS = arrayOf(
        "วันอาทิตย์", "วันจันทร์", "วันอังคาร", "วันพุธ",
        "วันพฤหัสบดี", "วันศุกร์", "วันเสาร์"
    )

    private val THAI_MONTHS = arrayOf(
        "มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน", "พฤษภาคม", "มิถุนายน",
        "กรกฎาคม", "สิงหาคม", "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม"
    )

    /** @param now injectable for tests; production always passes the current instant. */
    fun context(now: Date = Date(), zone: TimeZone = TimeZone.getDefault()): String {
        val calendar = Calendar.getInstance(zone).apply { time = now }
        val dayIndex = calendar.get(Calendar.DAY_OF_WEEK) - 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val monthIndex = calendar.get(Calendar.MONTH)
        val year = calendar.get(Calendar.YEAR)

        val englishDate = format("EEEE, d MMMM yyyy", now, zone)
        val time = format("HH:mm", now, zone)
        val offsetMinutes = zone.getOffset(now.time) / 60_000
        val sign = if (offsetMinutes < 0) "-" else "+"
        val absolute = abs(offsetMinutes)
        val offset = String.format(
            Locale.US, "GMT%s%02d:%02d", sign, absolute / 60, absolute % 60
        )
        val zoneLabel = if (zone.id == offset) offset else "$offset, ${zone.id}"

        return buildString {
            appendLine("Current date and time on the user's device (authoritative):")
            appendLine("- English: $englishDate")
            appendLine(
                "- Thai: ${THAI_DAYS[dayIndex]}ที่ $day ${THAI_MONTHS[monthIndex]} " +
                    "${year + 543} (พ.ศ. ${year + 543})"
            )
            appendLine("- Time: $time ($zoneLabel)")
            append(
                "Use this clock for every question about today's date, the day of the week " +
                    "or the current time. It is more reliable than any web result or your " +
                    "training data: never claim a different date."
            )
        }
    }

    private fun format(pattern: String, now: Date, zone: TimeZone): String =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = zone }.format(now)
}
