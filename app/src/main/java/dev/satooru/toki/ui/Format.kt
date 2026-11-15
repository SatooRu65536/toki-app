package dev.satooru.toki.ui

import android.content.Context
import android.text.format.DateFormat
import dev.satooru.toki.alarm.AlarmScheduler
import dev.satooru.toki.data.Alarm
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val DAY_NAMES = listOf("日", "月", "火", "水", "木", "金", "土")

/** 端末の12/24時間設定に従った時刻表記 */
fun formatTime(context: Context, hour: Int, minute: Int): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "H:mm" else "a h:mm"
    return LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

fun repeatText(alarm: Alarm, now: Long): String {
    val zone = ZoneId.systemDefault()
    return when {
        alarm.isCountdown -> "${alarm.minutes}分後アラーム"
        alarm.repeatDays == 0 -> {
            val next = AlarmScheduler.nextTrigger(alarm.copy(enabled = true, snoozeUntil = null), now)
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val nextDate = next?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            if (nextDate == null || nextDate == today) "今日" else "明日"
        }
        alarm.repeatDays == 0b1111111 -> "毎日"
        alarm.repeatDays == 0b0111110 -> "平日"
        alarm.repeatDays == 0b1000001 -> "週末"
        else -> DAY_NAMES.filterIndexed { i, _ -> alarm.repeatDays and (1 shl i) != 0 }.joinToString("・")
    }
}

fun snoozeText(alarm: Alarm): String =
    if (alarm.snooze) "スヌーズ ${alarm.snoozeMinutes}分" else "スヌーズ なし"

fun remainingText(now: Long, target: Long): String {
    val minutes = ((target - now) / 60_000L).coerceAtLeast(0)
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 -> "${h}時間${m}分後に鳴ります"
        m > 0 -> "${m}分後に鳴ります"
        else -> "まもなく鳴ります"
    }
}

fun formatCountdown(ms: Long): String {
    val total = (ms.coerceAtLeast(0) + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
