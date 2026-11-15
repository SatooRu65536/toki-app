package dev.satooru.toki

import dev.satooru.toki.alarm.AlarmScheduler
import dev.satooru.toki.data.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class AlarmSchedulerTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    // 2026-11-16 は月曜日
    private fun at(day: Int, h: Int, m: Int) =
        ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun oneShot_laterToday() {
        val a = Alarm(id = 1, hour = 8, minute = 0)
        assertEquals(at(16, 8, 0), AlarmScheduler.nextTrigger(a, at(16, 7, 0), zone))
    }

    @Test fun oneShot_pastTimeGoesToTomorrow() {
        val a = Alarm(id = 1, hour = 8, minute = 0)
        assertEquals(at(17, 8, 0), AlarmScheduler.nextTrigger(a, at(16, 9, 0), zone))
    }

    @Test fun weekdaysOnly_skipsWeekend() {
        val weekdays = 0b0111110
        val a = Alarm(id = 1, hour = 8, minute = 0, repeatDays = weekdays)
        // 金曜(20日)9時 → 次は月曜(23日)
        assertEquals(at(23, 8, 0), AlarmScheduler.nextTrigger(a, at(20, 9, 0), zone))
    }

    @Test fun disabled_returnsNull() {
        assertNull(AlarmScheduler.nextTrigger(Alarm(id = 1, hour = 8, minute = 0, enabled = false), at(16, 7, 0), zone))
    }

    @Test fun snoozeUntil_takesPriority() {
        val a = Alarm(id = 1, hour = 8, minute = 0, snoozeUntil = at(16, 8, 9))
        assertEquals(at(16, 8, 9), AlarmScheduler.nextTrigger(a, at(16, 8, 1), zone))
    }

    @Test fun countdown_usesAbsoluteTime() {
        val a = Alarm(id = 1, hour = 7, minute = 30, atMillis = at(16, 7, 30), minutes = 30)
        assertEquals(at(16, 7, 30), AlarmScheduler.nextTrigger(a, at(16, 7, 0), zone))
    }
}
