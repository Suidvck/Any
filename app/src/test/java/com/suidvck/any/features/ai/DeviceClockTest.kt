package com.suidvck.any.features.ai

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class DeviceClockTest {

    @Test
    fun reportsTodayInEnglishAndThaiBuddhistCalendar() {
        val zone = TimeZone.getTimeZone("Asia/Bangkok")
        val context = DeviceClock.context(
            at(zone, 2026, Calendar.OCTOBER, 31, 14, 5),
            zone
        )

        assertTrue(context.contains("English: Saturday, 31 October 2026"))
        assertTrue(context.contains("วันเสาร์ที่ 31 ตุลาคม 2569"))
        assertTrue(context.contains("Time: 14:05 (GMT+07:00, Asia/Bangkok)"))
        assertTrue(context.contains("authoritative"))
    }

    @Test
    fun usesTheDeviceTimeZoneAcrossDayBoundaries() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val context = DeviceClock.context(
            at(zone, 2026, Calendar.JANUARY, 1, 0, 30),
            zone
        )

        assertTrue(context.contains("English: Thursday, 1 January 2026"))
        assertTrue(context.contains("วันพฤหัสบดีที่ 1 มกราคม 2569"))
        assertTrue(context.contains("Time: 00:30 (GMT-05:00, America/New_York)"))
    }

    private fun at(
        zone: TimeZone,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Date = Calendar.getInstance(zone).apply {
        clear()
        set(year, month, day, hour, minute, 0)
    }.time
}
