package dev.satooru.toki.sleep

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import dev.satooru.toki.MainActivity
import dev.satooru.toki.R
import dev.satooru.toki.alarm.AlarmReceiver
import dev.satooru.toki.alarm.Notifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** N分後に他アプリの音楽・動画の再生を止めるタイマー(同時に1つのみ) */
object SleepTimer {
    const val ACTION_FIRE = "dev.satooru.toki.SLEEP_FIRE"
    const val ACTION_CANCEL = "dev.satooru.toki.SLEEP_CANCEL"
    private const val PREFS = "sleep_timer"
    private const val KEY_END = "end_elapsed"
    private const val NOTIFICATION_ID = 3001

    /** 満了時刻(SystemClock.elapsedRealtime基準)。未設定は null */
    private val _end = MutableStateFlow<Long?>(null)
    val end: StateFlow<Long?> = _end.asStateFlow()

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun init(context: Context) {
        val e = prefs(context).getLong(KEY_END, 0L)
        _end.value = if (e > SystemClock.elapsedRealtime()) e else null
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun start(context: Context, minutes: Int) {
        val end = SystemClock.elapsedRealtime() + minutes * 60_000L // 既存タイマーは置き換える
        prefs(context).edit().putLong(KEY_END, end).apply()
        _end.value = end
        arm(context, end)
        showNotification(context, end)
    }

    /** アプリ更新後などにAlarmManagerへ再登録する */
    fun reschedule(context: Context) {
        init(context)
        val e = _end.value ?: return
        arm(context, e)
        showNotification(context, e)
    }

    /** 再起動で elapsedRealtime が巻き戻るため、タイマーは破棄する */
    fun clearAfterReboot(context: Context) {
        prefs(context).edit().remove(KEY_END).apply()
        _end.value = null
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
        prefs(context).edit().remove(KEY_END).apply()
        _end.value = null
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun arm(context: Context, end: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, end, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, end, pi)
        }
    }

    private fun showNotification(context: Context, end: Long) {
        Notifications.ensureChannels(context)
        val remaining = end - SystemClock.elapsedRealtime()
        val cancel = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, Notifications.CH_SLEEP)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("スリープタイマー")
            .setContentText("終了すると再生を停止します")
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(System.currentTimeMillis() + remaining)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "キャンセル", cancel)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
    }

    /** 満了: 再生中のメディアを停止する */
    fun onFire(context: Context, pending: BroadcastReceiver.PendingResult) {
        prefs(context).edit().remove(KEY_END).apply()
        _end.value = null
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)

        val am = context.getSystemService(AudioManager::class.java)
        // 1) 音声フォーカスを恒久的に奪い、他アプリに再生を止めさせる
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()
        am.requestAudioFocus(request)
        // 2) 再生中のメディアセッションへ一時停止キーを送る(動画アプリ向け)
        if (am.isMusicActive) {
            for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                am.dispatchMediaKeyEvent(KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PAUSE))
            }
        }
        // 他アプリが停止処理を終えるまで少し待ってからフォーカスを手放す
        Handler(Looper.getMainLooper()).postDelayed({
            am.abandonAudioFocusRequest(request)
            pending.finish()
        }, 3_000)
    }
}
