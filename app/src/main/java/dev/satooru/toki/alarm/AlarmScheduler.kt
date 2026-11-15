package dev.satooru.toki.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.satooru.toki.MainActivity
import dev.satooru.toki.data.Alarm
import dev.satooru.toki.data.AlarmRepository
import java.time.Instant
import java.time.ZoneId

object AlarmScheduler {
    const val ACTION_FIRE = "dev.satooru.toki.ALARM_FIRE"
    const val ACTION_DISMISS_SNOOZE = "dev.satooru.toki.DISMISS_SNOOZE"
    const val EXTRA_ID = "alarm_id"

    /** 次の鳴動時刻(epoch millis)。無効・該当なしは null。 */
    fun nextTrigger(a: Alarm, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (!a.enabled) return null
        a.snoozeUntil?.let { return it }
        a.atMillis?.let { return it }
        val nowInstant = Instant.ofEpochMilli(now)
        val today = nowInstant.atZone(zone).toLocalDate()
        for (d in 0..7) {
            val date = today.plusDays(d.toLong())
            val candidate = date.atTime(a.hour, a.minute).atZone(zone).toInstant()
            if (!candidate.isAfter(nowInstant)) continue
            val bit = 1 shl (date.dayOfWeek.value % 7)
            if (a.repeatDays == 0 || (a.repeatDays and bit) != 0) return candidate.toEpochMilli()
        }
        return null
    }

    /** 全アラームのうち最も近い鳴動時刻 */
    fun nextAlarmMillis(alarms: List<Alarm>, now: Long): Long? =
        alarms.mapNotNull { nextTrigger(it, now) }.minOrNull()

    private fun firePendingIntent(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun schedule(context: Context, alarm: Alarm) {
        val am = context.getSystemService(AlarmManager::class.java)
        val trigger = nextTrigger(alarm, System.currentTimeMillis())
        val op = firePendingIntent(context, alarm.id)
        if (trigger == null) {
            am.cancel(op)
            return
        }
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // setAlarmClock は Doze 中でも正確に鳴り、追加権限も不要
        am.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, show), op)
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(firePendingIntent(context, id))
    }

    fun rescheduleAll(context: Context) {
        AlarmRepository.init(context)
        AlarmRepository.alarms.value.forEach { schedule(context, it) }
    }

    // ---- 操作 ----

    fun save(context: Context, alarm: Alarm): Alarm {
        // N分後アラームは1つのみ。新しく有効にした場合は他のN分後アラームを置き換える
        if (alarm.isCountdown && alarm.enabled) {
            AlarmRepository.alarms.value
                .filter { it.isCountdown && it.id != alarm.id }
                .forEach { delete(context, it.id) }
        }
        val saved = AlarmRepository.upsert(alarm)
        schedule(context, saved)
        return saved
    }

    fun delete(context: Context, id: Int) {
        cancel(context, id)
        AlarmRepository.delete(id)
        context.getSystemService(NotificationManager::class.java).cancel(AlarmService.snoozeNotificationId(id))
    }

    fun setEnabled(context: Context, alarm: Alarm, enabled: Boolean): Alarm {
        var updated = alarm.copy(enabled = enabled, snoozeUntil = null)
        if (enabled && alarm.isCountdown) updated = withCountdownFrom(updated, System.currentTimeMillis())
        return save(context, updated)
    }

    /** N分後アラームの鳴動時刻を now + minutes に確定する */
    fun withCountdownFrom(alarm: Alarm, now: Long): Alarm {
        val at = now + alarm.minutes * 60_000L
        val t = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        return alarm.copy(atMillis = at, hour = t.hour, minute = t.minute)
    }

    /** スヌーズ中のアラームを停止する(通知の「停止」) */
    fun cancelSnooze(context: Context, id: Int) {
        AlarmRepository.init(context)
        val a = AlarmRepository.get(id)
        if (a != null) {
            val updated = AlarmRepository.update(id) {
                it.copy(snoozeUntil = null, enabled = it.repeatDays != 0 && !it.isCountdown)
            }
            if (updated != null) schedule(context, updated)
        }
        context.getSystemService(NotificationManager::class.java).cancel(AlarmService.snoozeNotificationId(id))
    }
}
