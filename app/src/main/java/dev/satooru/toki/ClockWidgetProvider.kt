package dev.satooru.toki

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.JapaneseChronology
import java.time.chrono.JapaneseDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * 秒まで表示する時計ウィジェット。
 * 時刻は TextClock が毎秒更新する。日付(和暦)は日付が変わるタイミングで更新する。
 * 横幅3マス以上のときは左に日付、右に時刻を並べる。
 */
class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, manager, it) }
        scheduleMidnight(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle,
    ) {
        update(context, manager, appWidgetId)
    }

    override fun onEnabled(context: Context) = scheduleMidnight(context)

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(midnightIntent(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_MIDNIGHT,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
                if (ids.isNotEmpty()) {
                    ids.forEach { update(context, manager, it) }
                    scheduleMidnight(context)
                }
            }
        }
    }

    private fun update(context: Context, manager: AppWidgetManager, id: Int) {
        val widthDp = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val wide = widthDp >= WIDE_MIN_WIDTH_DP
        val views = RemoteViews(
            context.packageName,
            if (wide) R.layout.widget_clock_wide else R.layout.widget_clock,
        )
        if (wide) {
            val today = LocalDate.now()
            views.setTextViewText(R.id.date_year, yearText(today))
            views.setTextViewText(R.id.date_day, dayText(today))
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        manager.updateAppWidget(id, views)
    }

    private fun midnightIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0,
            Intent(context, ClockWidgetProvider::class.java).setAction(ACTION_MIDNIGHT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** 次の0時(少し後)に日付表示を更新する */
    private fun scheduleMidnight(context: Context) {
        val next = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).plusSeconds(1)
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC, next.toInstant().toEpochMilli(), midnightIntent(context),
        )
    }

    companion object {
        private const val ACTION_MIDNIGHT = "dev.satooru.toki.WIDGET_MIDNIGHT"

        /** これ以上の幅(dp)なら横3マス以上とみなす(3マス ≒ 180dp) */
        private const val WIDE_MIN_WIDTH_DP = 170

        /** 例: 2026年（令和8年） */
        fun yearText(date: LocalDate): String {
            val japanese = JapaneseDate.from(date)
            val era = DateTimeFormatter.ofPattern("GGGG", Locale.JAPAN)
                .withChronology(JapaneseChronology.INSTANCE).format(japanese)
            val yearOfEra = japanese.get(ChronoField.YEAR_OF_ERA)
            val y = if (yearOfEra == 1) "元" else yearOfEra.toString()
            return "${date.year}年（${era}${y}年）"
        }

        /** 例: 11月16日 月曜日(日付と曜日の間は半角空白) */
        fun dayText(date: LocalDate): String {
            val dow = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.JAPAN)
            return "${date.monthValue}月${date.dayOfMonth}日 $dow"
        }
    }
}
