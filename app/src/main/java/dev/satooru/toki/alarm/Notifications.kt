package dev.satooru.toki.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Notifications {
    const val CH_RINGING = "alarm_ringing"
    const val CH_SNOOZE = "alarm_snooze"
    const val CH_SLEEP = "sleep_timer"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_RINGING, "アラーム", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "アラーム鳴動中の通知"
                setSound(null, null) // 音はサービス側で再生する
                enableVibration(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SNOOZE, "スヌーズ", NotificationManager.IMPORTANCE_LOW).apply {
                description = "スヌーズ中の通知"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SLEEP, "スリープタイマー", NotificationManager.IMPORTANCE_LOW).apply {
                description = "再生停止タイマーの残り時間"
            }
        )
    }
}
