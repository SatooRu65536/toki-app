package dev.satooru.toki.alarm

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dev.satooru.toki.R
import dev.satooru.toki.data.Alarm
import dev.satooru.toki.data.AlarmRepository
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** アラーム鳴動を担うフォアグラウンドサービス(音・振動・通知)。 */
class AlarmService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var currentId = -1
    private val timeoutRunnable = Runnable {
        val a = AlarmRepository.get(currentId)
        endRinging(snooze = a?.snooze == true)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AlarmRepository.init(this)
        Notifications.ensureChannels(this)
        when (intent?.action) {
            ACTION_FIRE -> startRinging(intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1))
            ACTION_STOP -> endRinging(snooze = false)
            ACTION_SNOOZE -> endRinging(snooze = true)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRinging(id: Int) {
        val alarm = AlarmRepository.get(id)
        if (alarm == null) {
            // startForegroundService 後は必ず startForeground が必要
            startForegroundCompat(buildNotification(null))
            stopForegroundCompat()
            stopSelf()
            return
        }
        // 別アラームが鳴動中なら、そちらを終了させる(スヌーズ設定に従う)
        if (currentId != -1 && currentId != id) {
            val old = AlarmRepository.get(currentId)
            finishAlarmState(currentId, snooze = old?.snooze == true)
            stopPlayback()
        }
        currentId = id
        handler.removeCallbacks(timeoutRunnable)

        // 鳴動時点で状態を確定: 繰り返しなし/N分後は無効化、繰り返しありは次回をスケジュール
        val ringing = if (alarm.repeatDays != 0 && !alarm.isCountdown) {
            alarm.copy(snoozeUntil = null)
        } else {
            alarm.copy(snoozeUntil = null, enabled = false)
        }
        AlarmRepository.update(id) { ringing }
        AlarmScheduler.schedule(this, ringing)
        getSystemService(NotificationManager::class.java).cancel(snoozeNotificationId(id))

        startForegroundCompat(buildNotification(alarm))
        ringingId.value = id
        startPlayback(alarm)
        handler.postDelayed(timeoutRunnable, RING_TIMEOUT_MS)
    }

    /** ユーザー操作または自動タイムアウトで鳴動を終える */
    private fun endRinging(snooze: Boolean) {
        val id = currentId
        if (id != -1) finishAlarmState(id, snooze)
        stopPlayback()
        handler.removeCallbacks(timeoutRunnable)
        currentId = -1
        ringingId.value = null
        stopForegroundCompat()
        stopSelf()
    }

    private fun finishAlarmState(id: Int, snooze: Boolean) {
        val alarm = AlarmRepository.get(id) ?: return
        if (snooze && alarm.snooze) {
            val until = System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L
            val updated = AlarmRepository.update(id) { it.copy(enabled = true, snoozeUntil = until) } ?: return
            AlarmScheduler.schedule(this, updated)
            showSnoozeNotification(updated, until)
        }
    }

    private fun showSnoozeNotification(alarm: Alarm, until: Long) {
        val time = Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("H:mm"))
        val dismiss = PendingIntent.getBroadcast(
            this, alarm.id,
            Intent(this, AlarmReceiver::class.java)
                .setAction(AlarmScheduler.ACTION_DISMISS_SNOOZE)
                .putExtra(AlarmScheduler.EXTRA_ID, alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, Notifications.CH_SNOOZE)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("スヌーズ中")
            .setContentText("$time に再度鳴ります")
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(until)
            .setOngoing(true)
            .setTimeoutAfter(alarm.snoozeMinutes * 60_000L + 5_000L)
            .addAction(0, "停止", dismiss)
            .build()
        getSystemService(NotificationManager::class.java).notify(snoozeNotificationId(alarm.id), n)
    }

    // ---- 音・振動 ----

    private fun startPlayback(alarm: Alarm) {
        if (alarm.soundUri != Alarm.SOUND_NONE) playSound(alarm)
        if (alarm.vibrate) Vibration.start(this, alarm.vibrationPattern, loop = true)
    }

    private fun playSound(alarm: Alarm) {
        val uris = listOfNotNull(
            alarm.soundUri?.let { Uri.parse(it) },
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        for (uri in uris) {
            try {
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(this@AlarmService, uri)
                    isLooping = true
                    prepare()
                    start()
                }
                break
            } catch (_: Exception) {
                player?.release()
                player = null
            }
        }
    }

    private fun stopPlayback() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        Vibration.stop(this)
    }

    // ---- 通知 ----

    private fun buildNotification(alarm: Alarm?): Notification {
        val title = "アラーム"
        val ringingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, RingingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(AlarmScheduler.EXTRA_ID, alarm?.id ?: -1),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(this, Notifications.CH_RINGING)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText("アラームが鳴っています")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(ringingIntent)
        // 端末を使用中(点灯・ロック解除済み)は画面上部に出るヘッドアップ通知にし、
        // 消灯中・ロック中のみ全画面の鳴動画面を表示する
        val inUse = getSystemService(PowerManager::class.java).isInteractive &&
            !getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if (!inUse) b.setFullScreenIntent(ringingIntent, true)
        if (alarm?.snooze == true) {
            b.addAction(0, "スヌーズ", servicePendingIntent(ACTION_SNOOZE, 1))
        }
        b.addAction(0, "停止", servicePendingIntent(ACTION_STOP, 2))
        return b.build()
    }

    private fun servicePendingIntent(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code,
            Intent(this, AlarmService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeoutRunnable)
        stopPlayback()
        ringingId.value = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_FIRE = "dev.satooru.toki.service.FIRE"
        const val ACTION_STOP = "dev.satooru.toki.service.STOP"
        const val ACTION_SNOOZE = "dev.satooru.toki.service.SNOOZE"
        const val NOTIFICATION_ID = 1001
        const val RING_TIMEOUT_MS = 60_000L

        /** 現在鳴動中のアラームID(なければ null)。鳴動画面が監視する */
        val ringingId = MutableStateFlow<Int?>(null)

        fun snoozeNotificationId(alarmId: Int) = 2000 + alarmId

        fun fireIntent(context: Context, id: Int): Intent =
            Intent(context, AlarmService::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(AlarmScheduler.EXTRA_ID, id)

        fun stopIntent(context: Context): Intent =
            Intent(context, AlarmService::class.java).setAction(ACTION_STOP)

        fun snoozeIntent(context: Context): Intent =
            Intent(context, AlarmService::class.java).setAction(ACTION_SNOOZE)
    }
}
