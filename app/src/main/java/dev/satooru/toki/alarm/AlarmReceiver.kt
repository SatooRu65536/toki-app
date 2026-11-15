package dev.satooru.toki.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dev.satooru.toki.data.AlarmRepository
import dev.satooru.toki.sleep.SleepTimer

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        AlarmRepository.init(app)
        when (intent.action) {
            AlarmScheduler.ACTION_FIRE -> {
                val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
                ContextCompat.startForegroundService(app, AlarmService.fireIntent(app, id))
            }

            AlarmScheduler.ACTION_DISMISS_SNOOZE -> {
                AlarmScheduler.cancelSnooze(app, intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1))
            }

            SleepTimer.ACTION_FIRE -> {
                SleepTimer.onFire(app, goAsync())
            }

            SleepTimer.ACTION_CANCEL -> SleepTimer.cancel(app)

            Intent.ACTION_BOOT_COMPLETED -> {
                SleepTimer.clearAfterReboot(app)
                AlarmScheduler.rescheduleAll(app)
            }

            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                SleepTimer.reschedule(app)
                AlarmScheduler.rescheduleAll(app)
            }

            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                AlarmScheduler.rescheduleAll(app)
            }
        }
    }
}
