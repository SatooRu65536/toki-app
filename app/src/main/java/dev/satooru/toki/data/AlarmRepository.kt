package dev.satooru.toki.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** アラームをSharedPreferences(JSON)に永続化する。外部ライブラリ不要。 */
object AlarmRepository {
    private const val PREFS = "alarms"
    private const val KEY_LIST = "list"
    private const val KEY_NEXT_ID = "next_id"
    private const val PRUNE_AFTER_MS = 24L * 60 * 60 * 1000

    private lateinit var prefs: SharedPreferences
    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_LIST, null)
        val list = if (json == null) emptyList() else runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { Alarm.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        // 停止済みのN分後アラームは24時間後に自動削除する
        val now = System.currentTimeMillis()
        val pruned = list.filterNot { it.isCountdown && !it.enabled && (it.atMillis ?: 0) < now - PRUNE_AFTER_MS }
        _alarms.value = pruned
        if (pruned.size != list.size) persist()
    }

    fun get(id: Int): Alarm? = _alarms.value.firstOrNull { it.id == id }

    /** id==0 なら新規採番して追加、そうでなければ置換する。保存後のアラームを返す。 */
    @Synchronized
    fun upsert(alarm: Alarm): Alarm {
        val saved = if (alarm.id == 0) alarm.copy(id = nextId()) else alarm
        val list = _alarms.value
        _alarms.value = if (list.any { it.id == saved.id }) {
            list.map { if (it.id == saved.id) saved else it }
        } else {
            list + saved
        }
        persist()
        return saved
    }

    @Synchronized
    fun update(id: Int, transform: (Alarm) -> Alarm): Alarm? {
        val current = get(id) ?: return null
        return upsert(transform(current).copy(id = id))
    }

    @Synchronized
    fun delete(id: Int) {
        _alarms.value = _alarms.value.filterNot { it.id == id }
        persist()
    }

    private fun nextId(): Int {
        val id = prefs.getInt(KEY_NEXT_ID, 1)
        prefs.edit().putInt(KEY_NEXT_ID, id + 1).apply()
        return id
    }

    private fun persist() {
        val arr = JSONArray()
        _alarms.value.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }
}
