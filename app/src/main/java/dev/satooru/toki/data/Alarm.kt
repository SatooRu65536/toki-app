package dev.satooru.toki.data

import org.json.JSONObject

/**
 * アラーム1件。通常アラーム(時刻指定)とN分後アラーム(atMillis != null)の両方を表す。
 *
 * repeatDays: ビットマスク。bit0=日, bit1=月, ... bit6=土。0 は繰り返しなし。
 */
data class Alarm(
    val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val repeatDays: Int = 0,
    val soundUri: String? = null,
    val snooze: Boolean = true,
    /** 鳴動時に振動するか */
    val vibrate: Boolean = true,
    /** バイブレーションのパターン番号(Vibration.patterns の位置) */
    val vibrationPattern: Int = 0,
    /** スヌーズ間隔(分) */
    val snoozeMinutes: Int = 9,
    val enabled: Boolean = true,
    /** N分後アラームの鳴動予定時刻(絶対時刻, epoch millis)。通常アラームは null */
    val atMillis: Long? = null,
    /** N分後アラームの N。通常アラームは 0 */
    val minutes: Int = 0,
    /** スヌーズ中の再鳴動時刻。スヌーズ中でなければ null */
    val snoozeUntil: Long? = null,
) {
    val isCountdown: Boolean get() = atMillis != null

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("repeatDays", repeatDays)
        put("soundUri", soundUri ?: JSONObject.NULL)
        put("snooze", snooze)
        put("vibrate", vibrate)
        put("vibrationPattern", vibrationPattern)
        put("snoozeMinutes", snoozeMinutes)
        put("enabled", enabled)
        put("atMillis", atMillis ?: JSONObject.NULL)
        put("minutes", minutes)
        put("snoozeUntil", snoozeUntil ?: JSONObject.NULL)
    }

    companion object {
        /** soundUri にこの値が入っているときはサウンドなし(無音) */
        const val SOUND_NONE = "none"

        fun fromJson(o: JSONObject): Alarm = Alarm(
            id = o.getInt("id"),
            hour = o.getInt("hour"),
            minute = o.getInt("minute"),
            repeatDays = o.optInt("repeatDays", 0),
            soundUri = if (o.isNull("soundUri")) null else o.getString("soundUri"),
            snooze = o.optBoolean("snooze", true),
            vibrate = o.optBoolean("vibrate", true),
            vibrationPattern = o.optInt("vibrationPattern", 0),
            snoozeMinutes = o.optInt("snoozeMinutes", 9),
            enabled = o.optBoolean("enabled", true),
            atMillis = if (o.isNull("atMillis")) null else o.getLong("atMillis"),
            minutes = o.optInt("minutes", 0),
            snoozeUntil = if (o.isNull("snoozeUntil")) null else o.getLong("snoozeUntil"),
        )
    }
}
